package com.example.AI.Doc.Assistant.controller;

import com.example.AI.Doc.Assistant.service.RagChatService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final RagChatService ragChatService;

    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @PostMapping("/ask")
    public String ask(@RequestBody String question,
                      Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();

        return ragChatService.ask(userId, question);
    }
}
