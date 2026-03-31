package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.response.ChatResponse;
import com.example.aidocassistant.dto.response.CitationResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean RagService ragService;

    // ── POST /api/chat ────────────────────────────────────────────────────────

    @Test
    void chat_returns200WithAnswerAndCitations() throws Exception {
        CitationResponse citation = new CitationResponse(
                "doc-1", "spec.pdf", "The system uses Java.", 0.9);
        ChatResponse chatResponse = new ChatResponse(
                "The system is built with Java.", List.of(citation));
        when(ragService.answer("doc-1", "What language is used?")).thenReturn(chatResponse);

        String body = """
                {"question": "What language is used?", "documentId": "doc-1"}
                """;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The system is built with Java."))
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.citations[0].score").value(0.9));
    }

    @Test
    void chat_returns200WithEmptyCitationsWhenNoneFound() throws Exception {
        ChatResponse noInfoResponse = new ChatResponse(
                "I could not find relevant information in this document to answer your question.",
                List.of());
        when(ragService.answer("doc-2", "Unrelated question?")).thenReturn(noInfoResponse);

        String body = """
                {"question": "Unrelated question?", "documentId": "doc-2"}
                """;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations", hasSize(0)))
                .andExpect(jsonPath("$.answer").value(containsString("could not find")));
    }

    @Test
    void chat_returns400WhenQuestionIsBlank() throws Exception {
        String body = """
                {"question": "", "documentId": "doc-1"}
                """;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.question").exists());
    }

    @Test
    void chat_returns400WhenDocumentIdIsNull() throws Exception {
        String body = """
                {"question": "What is this?"}
                """;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.documentId").exists());
    }

    @Test
    void chat_returns404WhenDocumentNotFound() throws Exception {
        when(ragService.answer("missing", "Any question?"))
                .thenThrow(new ResourceNotFoundException("Document not found: missing"));

        String body = """
                {"question": "Any question?", "documentId": "missing"}
                """;

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Document not found: missing"));
    }

    @Test
    void chat_returns400WhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
