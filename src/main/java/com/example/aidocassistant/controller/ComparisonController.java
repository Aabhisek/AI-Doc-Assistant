package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.request.CompareRequest;
import com.example.aidocassistant.dto.response.ComparisonResponse;
import com.example.aidocassistant.service.ComparisonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compare")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonService comparisonService;

    @PostMapping
    public ResponseEntity<ComparisonResponse> compare(@Valid @RequestBody CompareRequest request) {
        return ResponseEntity.ok(comparisonService.compare(request.documentIdA(), request.documentIdB()));
    }
}
