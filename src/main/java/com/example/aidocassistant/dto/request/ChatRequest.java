package com.example.aidocassistant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRequest(
        @NotBlank(message = "Question must not be blank") String question,
        @NotNull(message = "Document ID is required") String documentId) {
}
