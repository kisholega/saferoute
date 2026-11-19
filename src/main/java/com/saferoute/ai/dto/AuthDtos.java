package com.saferoute.ai.dto;

import java.time.LocalDateTime;

import com.saferoute.ai.model.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies for /api/auth. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Enter your name.") @Size(min = 2, max = 80, message = "Name must be 2 to 80 characters.") String name,
            @NotBlank(message = "Enter your email.") @Email(message = "Enter a valid email address.") @Size(max = 120) String email,
            @NotBlank(message = "Enter your mobile number.") @Size(max = 25) String phone,
            @NotBlank(message = "Choose a password.") @Size(min = 8, max = 100, message = "Use at least 8 characters.") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your email.") String email,
            @NotBlank(message = "Enter your password.") String password) {
    }

    public record ProfileRequest(
            @NotBlank(message = "Enter your name.") @Size(min = 2, max = 80, message = "Name must be 2 to 80 characters.") String name,
            @NotBlank(message = "Enter your mobile number.") @Size(max = 25) String phone) {
    }

    /** What the browser is allowed to know about the signed-in user. */
    public record UserView(Long id, String name, String email, String phone, LocalDateTime createdAt) {
        public static UserView of(User user) {
            return new UserView(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getCreatedAt());
        }
    }
}
