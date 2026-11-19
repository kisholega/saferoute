package com.saferoute.ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for creating or updating an emergency contact. The owner is always the signed-in user. */
public record EmergencyContactRequest(
        @NotBlank(message = "Name is required.")
        @Size(min = 2, max = 80, message = "Name must be 2 to 80 characters.")
        String name,

        @NotBlank(message = "Phone number is required.")
        @Size(max = 25, message = "Phone number is too long.")
        String phone,

        /** Optional: alerts are also emailed here when email sending is configured. */
        @Email(message = "Enter a valid email address.")
        @Size(max = 120, message = "Email is too long.")
        String email,

        @NotBlank(message = "Relationship is required.")
        @Size(max = 40, message = "Relationship must be at most 40 characters.")
        String relation,

        /** true makes this the primary contact. false/null leaves the current primary unchanged. */
        Boolean isPrimary) {
}
