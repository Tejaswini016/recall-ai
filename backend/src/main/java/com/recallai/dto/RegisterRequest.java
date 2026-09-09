package com.recallai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only considers the first 72 bytes, so longer passwords are rejected up front.
        @NotBlank @Size(min = 8, max = 72) String password) {
}
