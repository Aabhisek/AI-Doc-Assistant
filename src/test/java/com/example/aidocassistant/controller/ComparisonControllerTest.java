package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.response.ComparisonResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.service.ComparisonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ComparisonController.class)
@ActiveProfiles("test")
class ComparisonControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ComparisonService comparisonService;

    // ── POST /api/compare ─────────────────────────────────────────────────────

    @Test
    void compare_returns200WithSimilaritiesAndDifferences() throws Exception {
        ComparisonResponse response = new ComparisonResponse(
                "- Both cover data governance",
                "- Doc B includes GDPR clauses");
        when(comparisonService.compare("doc-a", "doc-b")).thenReturn(response);

        String body = """
                {"documentIdA": "doc-a", "documentIdB": "doc-b"}
                """;

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similarities").value("- Both cover data governance"))
                .andExpect(jsonPath("$.differences").value("- Doc B includes GDPR clauses"));
    }

    @Test
    void compare_returns400WhenDocumentIdAIsBlank() throws Exception {
        String body = """
                {"documentIdA": "", "documentIdB": "doc-b"}
                """;

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.documentIdA").exists());
    }

    @Test
    void compare_returns400WhenDocumentIdBIsBlank() throws Exception {
        String body = """
                {"documentIdA": "doc-a", "documentIdB": ""}
                """;

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.documentIdB").exists());
    }

    @Test
    void compare_returns404WhenFirstDocumentNotFound() throws Exception {
        when(comparisonService.compare("gone-a", "doc-b"))
                .thenThrow(new ResourceNotFoundException("Document not found: gone-a"));

        String body = """
                {"documentIdA": "gone-a", "documentIdB": "doc-b"}
                """;

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Document not found: gone-a"));
    }

    @Test
    void compare_returns404WhenSecondDocumentNotFound() throws Exception {
        when(comparisonService.compare("doc-a", "gone-b"))
                .thenThrow(new ResourceNotFoundException("Document not found: gone-b"));

        String body = """
                {"documentIdA": "doc-a", "documentIdB": "gone-b"}
                """;

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Document not found: gone-b"));
    }

    @Test
    void compare_returns400WhenBothIdsAreBlank() throws Exception {
        String body = """
                {"documentIdA": "", "documentIdB": ""}
                """;

        mockMvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.documentIdA").exists())
                .andExpect(jsonPath("$.fields.documentIdB").exists());
    }
}
