package com.example.aidocassistant.dto.response;

import java.util.List;

public record ExtractionResponse(
        String category,
        List<String> items) {
}
