package com.moviebooking.service;

import com.moviebooking.entity.PasswordResetToken;
import com.moviebooking.entity.User;
import com.moviebooking.exception.ApiException;
import com.moviebooking.repository.PasswordResetTokenRepository;
import com.moviebooking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Forgot-password flow:
 *  1. requestReset  - creates a random single-use token, stores only its SHA-256 hash,
 *                     and e-mails a link containing the raw token.
 *  2. resetPassword - verifies the token (exists, unused, not expired), then sets the new password.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final long tokenMinutes;
    private final String frontendBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                @Value("${app.password-reset.token-minutes:30}") long tokenMinutes,
                                @Value("${app.frontend-base-url:http://localhost:8080}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.tokenMinutes = tokenMinutes;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    /** Always returns normally, whether or not the e-mail belongs to an account. */
    @Transactional
    public void requestReset(String rawEmail) {
        userRepository.findByEmail(AuthService.normalizeEmail(rawEmail)).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now();
            tokenRepository.invalidateActiveTokens(user, now); // only the newest link works

            String rawToken = generateToken();
            tokenRepository.save(new PasswordResetToken(user, sha256Hex(rawToken), now.plusMinutes(tokenMinutes)));

            String link = frontendBaseUrl + "/reset-password.html?token="
                    + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), link, tokenMinutes);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        LocalDateTime now = LocalDateTime.now();

        PasswordResetToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken.trim()))
                .orElseThrow(PasswordResetService::invalidToken);

        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw invalidToken();
        }
        // Atomic "use once": only the first concurrent request gets 1 here.
        if (tokenRepository.markUsed(token.getId(), now) != 1) {
            throw invalidToken();
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.invalidateActiveTokens(user, now); // kill any other outstanding links
    }

    private String generateToken() {
        byte[] bytes = new byte[32]; // 256 bits of randomness
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /** One message for unknown, used and expired tokens, so nothing is leaked. */
    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "This reset link is invalid or has expired. Please request a new one.");
    }
}
