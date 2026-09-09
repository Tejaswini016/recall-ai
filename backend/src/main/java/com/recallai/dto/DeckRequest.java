package com.recallai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DeckRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 2000) String description,
        @Size(max = 100) String subject,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags) {
}
