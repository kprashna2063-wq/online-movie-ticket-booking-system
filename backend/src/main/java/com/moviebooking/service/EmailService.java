package com.moviebooking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends e-mails asynchronously so the response time of "forgot password" does not
 * reveal whether an account exists.
 *
 * If no SMTP server is configured (spring.mail.host is empty) the reset link is written
 * to the application log instead. That is meant for local development only.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.from:no-reply@moviebook.local}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
    }

    @Async
    public void sendPasswordResetEmail(String to, String fullName, String resetLink, long validMinutes) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP is not configured. DEV ONLY - password reset link for {}: {}", to, resetLink);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("MovieBook - reset your password");
        message.setText("""
                Hi %s,

                We received a request to reset your MovieBook password.
                Open the link below to choose a new one. It works once and expires in %d minutes:

                %s

                If you did not request this, you can safely ignore this email.
                """.formatted(fullName, validMinutes, resetLink));

        try {
            sender.send(message);
        } catch (MailException ex) {
            log.error("Could not send password reset email to {}", to, ex);
        }
    }
}
