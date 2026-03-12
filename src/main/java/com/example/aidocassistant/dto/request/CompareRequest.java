package com.example.aidocassistant.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompareRequest(
        @NotBlank(message = "First document ID is required") String documentIdA,
        @NotBlank(message = "Second document ID is required") String documentIdB) {
}
