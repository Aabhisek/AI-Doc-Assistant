package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.request.ChatRequest;
import com.example.aidocassistant.dto.response.ChatResponse;
import com.example.aidocassistant.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ragService.answer(request.documentId(), request.question()));
    }
}
