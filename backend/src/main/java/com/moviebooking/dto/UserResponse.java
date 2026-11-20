package com.moviebooking.dto;

import com.moviebooking.entity.User;

/** Safe view of a user: never contains the password hash. */
public record UserResponse(Long id, String fullName, String email, String phone, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name());
    }
}
