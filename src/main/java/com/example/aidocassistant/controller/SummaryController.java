package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.response.SummaryResponse;
import com.example.aidocassistant.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService summaryService;

    @PostMapping("/{documentId}")
    public ResponseEntity<SummaryResponse> summarize(@PathVariable String documentId) {
        return ResponseEntity.ok(summaryService.summarize(documentId));
    }
}
