package com.example.aidocassistant.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ExtractionRequest(
        @NotBlank(message = "Document ID is required") String documentId,
        @NotBlank(message = "Category is required (e.g. 'skills', 'dates', 'names', 'action items')") String category) {
}
