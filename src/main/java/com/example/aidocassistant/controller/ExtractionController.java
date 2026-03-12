package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.request.ExtractionRequest;
import com.example.aidocassistant.dto.response.ExtractionResponse;
import com.example.aidocassistant.service.ExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/extract")
@RequiredArgsConstructor
public class ExtractionController {

    private final ExtractionService extractionService;

    @PostMapping
    public ResponseEntity<ExtractionResponse> extract(@Valid @RequestBody ExtractionRequest request) {
        return ResponseEntity.ok(extractionService.extract(request.documentId(), request.category()));
    }
}
