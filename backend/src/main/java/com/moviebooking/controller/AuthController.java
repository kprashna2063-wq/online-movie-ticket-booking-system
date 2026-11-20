package com.moviebooking.controller;

import com.moviebooking.dto.ForgotPasswordRequest;
import com.moviebooking.dto.LoginRequest;
import com.moviebooking.dto.RegisterRequest;
import com.moviebooking.dto.ResetPasswordRequest;
import com.moviebooking.dto.UserResponse;
import com.moviebooking.entity.User;
import com.moviebooking.service.AuthService;
import com.moviebooking.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Authentication API.
 * Logout (POST /api/auth/logout) is handled by Spring Security's logout filter, see SecurityConfig.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String RESET_REQUEST_MESSAGE =
            "If an account exists for that email, a password reset link has been sent.";

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Account created. You can now log in.", "user", UserResponse.from(user)));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        // Throws AuthenticationException (-> 401) on a wrong e-mail or password.
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        AuthService.normalizeEmail(request.email()), request.password()));

        // Prevent session fixation: new session id if a session already exists.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse); // stores it in the session

        return UserResponse.from(authService.getByEmail(authentication.getName()));
    }

    /** Returns the logged-in user; 401 when nobody is logged in. The frontend uses this to build the navbar. */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(authService.getByEmail(authentication.getName()));
    }

    /** Always answers 200 with the same text so attackers cannot discover which e-mails are registered. */
    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return Map.of("message", RESET_REQUEST_MESSAGE);
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return Map.of("message", "Your password has been reset. You can now log in.");
    }
}
