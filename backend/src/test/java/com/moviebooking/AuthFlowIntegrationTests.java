package com.moviebooking;

import com.moviebooking.entity.PasswordResetToken;
import com.moviebooking.repository.PasswordResetTokenRepository;
import com.moviebooking.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTests {

    private static final String PASSWORD = "Sup3rSecret!";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PasswordResetTokenRepository tokenRepository;

    @MockitoBean
    EmailService emailService;

    // ---------------------------------------------------------------- helpers

    private String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions postJson(String url, String json, MockHttpSession session) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json);
        if (session != null) {
            request.session(session);
        }
        return mockMvc.perform(request);
    }

    private void register(String email) throws Exception {
        postJson("/api/auth/register", """
                {"fullName":"Test User","email":"%s","phone":"9800000000","password":"%s"}
                """.formatted(email, PASSWORD), null).andExpect(status().isCreated());
    }

    private ResultActions login(String email, String password, MockHttpSession session) throws Exception {
        return postJson("/api/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password), session);
    }

    /** Triggers "forgot password" and returns the raw token from the e-mailed link. */
    private String requestResetToken(String email) throws Exception {
        postJson("/api/auth/forgot-password", """
                {"email":"%s"}
                """.formatted(email), null).andExpect(status().isOk());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(email), anyString(), link.capture(), anyLong());
        String url = link.getValue();
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    // ------------------------------------------------------- login and logout

    @Test
    void registerLoginAndMeWork() throws Exception {
        String email = newEmail();
        register(email);

        MockHttpSession session = new MockHttpSession();
        login(email, PASSWORD, session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String email = newEmail();
        register(email);
        login(email, "not-the-password", null).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        String email = newEmail();
        register(email);
        postJson("/api/auth/register", """
                {"fullName":"Other","email":"%s","phone":"9800000000","password":"%s"}
                """.formatted(email.toUpperCase(), PASSWORD), null).andExpect(status().isConflict());
    }

    @Test
    void protectedEndpointNeedsLogin() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        String email = newEmail();
        register(email);

        MockHttpSession session = new MockHttpSession();
        login(email, PASSWORD, session).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        postJson("/api/auth/logout", "{}", session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully."));

        // The same session must no longer be authenticated.
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutSessionIsHarmless() throws Exception {
        postJson("/api/auth/logout", "{}", null).andExpect(status().isOk());
    }

    // ---------------------------------------------------------- forgot password

    @Test
    void forgotPasswordForUnknownEmailLooksIdenticalAndSendsNothing() throws Exception {
        postJson("/api/auth/forgot-password", """
                {"email":"nobody-%s@example.com"}
                """.formatted(UUID.randomUUID()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void resetPasswordFullFlowAndTokenIsSingleUse() throws Exception {
        String email = newEmail();
        register(email);
        String token = requestResetToken(email);

        postJson("/api/auth/reset-password", """
                {"token":"%s","newPassword":"BrandNewPass1"}
                """.formatted(token), null).andExpect(status().isOk());

        login(email, PASSWORD, null).andExpect(status().isUnauthorized());      // old password is dead
        login(email, "BrandNewPass1", null).andExpect(status().isOk());          // new one works

        postJson("/api/auth/reset-password", """
                {"token":"%s","newPassword":"AnotherPass22"}
                """.formatted(token), null).andExpect(status().isBadRequest());  // token cannot be reused
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        postJson("/api/auth/reset-password", """
                {"token":"definitely-not-a-real-token","newPassword":"BrandNewPass1"}
                """, null).andExpect(status().isBadRequest());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String email = newEmail();
        register(email);
        String token = requestResetToken(email);

        for (PasswordResetToken stored : tokenRepository.findAll()) {
            if (stored.getUser().getEmail().equals(email)) {
                stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
                tokenRepository.save(stored);
            }
        }

        postJson("/api/auth/reset-password", """
                {"token":"%s","newPassword":"BrandNewPass1"}
                """.formatted(token), null).andExpect(status().isBadRequest());
        login(email, PASSWORD, null).andExpect(status().isOk()); // password unchanged
    }

    @Test
    void weakNewPasswordIsRejected() throws Exception {
        String email = newEmail();
        register(email);
        String token = requestResetToken(email);

        postJson("/api/auth/reset-password", """
                {"token":"%s","newPassword":"short"}
                """.formatted(token), null).andExpect(status().isBadRequest());
    }
}
