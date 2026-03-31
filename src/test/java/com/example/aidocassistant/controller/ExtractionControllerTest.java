package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.response.ExtractionResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.service.ExtractionService;
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

@WebMvcTest(ExtractionController.class)
@ActiveProfiles("test")
class ExtractionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ExtractionService extractionService;

    // ── POST /api/extract ─────────────────────────────────────────────────────

    @Test
    void extract_returns200WithExtractedItems() throws Exception {
        ExtractionResponse response = new ExtractionResponse("skills", List.of("Java", "Python", "Docker"));
        when(extractionService.extract("doc-1", "skills")).thenReturn(response);

        String body = """
                {"documentId": "doc-1", "category": "skills"}
                """;

        mockMvc.perform(post("/api/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("skills"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0]").value("Java"))
                .andExpect(jsonPath("$.items[2]").value("Docker"));
    }

    @Test
    void extract_returns200WithNoneFoundItem() throws Exception {
        ExtractionResponse response = new ExtractionResponse("phone numbers", List.of("None found"));
        when(extractionService.extract("doc-2", "phone numbers")).thenReturn(response);

        String body = """
                {"documentId": "doc-2", "category": "phone numbers"}
                """;

        mockMvc.perform(post("/api/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0]").value("None found"));
    }

    @Test
    void extract_returns400WhenDocumentIdIsBlank() throws Exception {
        String body = """
                {"documentId": "", "category": "skills"}
                """;

        mockMvc.perform(post("/api/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.documentId").exists());
    }

    @Test
    void extract_returns400WhenCategoryIsBlank() throws Exception {
        String body = """
                {"documentId": "doc-1", "category": ""}
                """;

        mockMvc.perform(post("/api/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.category").exists());
    }

    @Test
    void extract_returns404WhenDocumentNotFound() throws Exception {
        when(extractionService.extract("no-doc", "dates"))
                .thenThrow(new ResourceNotFoundException("Document not found: no-doc"));

        String body = """
                {"documentId": "no-doc", "category": "dates"}
                """;

        mockMvc.perform(post("/api/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Document not found: no-doc"));
    }
}
