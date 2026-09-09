package com.recallai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CardRequest(
        @NotBlank @Size(max = 2000) String question,
        @NotBlank @Size(max = 5000) String answer,
        @Size(max = 5000) String explanation,
        @Size(max = 150) String topic,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags) {
}
