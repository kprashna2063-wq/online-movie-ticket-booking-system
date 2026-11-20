package com.moviebooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 120, message = "Full name must be at most 120 characters.")
        String fullName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 160, message = "Email must be at most 160 characters.")
        String email,

        @NotBlank(message = "Phone number is required.")
        @Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$", message = "Enter a valid phone number.")
        String phone,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters long.")
        String password
) {
}
