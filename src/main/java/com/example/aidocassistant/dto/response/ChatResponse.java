package com.example.aidocassistant.dto.response;

import java.util.List;

public record ChatResponse(
        String answer,
        List<CitationResponse> citations) {
}
