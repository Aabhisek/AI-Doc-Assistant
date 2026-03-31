package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.response.SummaryResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.service.SummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SummaryController.class)
@ActiveProfiles("test")
class SummaryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SummaryService summaryService;

    // ── POST /api/summary/{documentId} ────────────────────────────────────────

    @Test
    void summarize_returns200WithSummaryAndKeyPoints() throws Exception {
        SummaryResponse response = new SummaryResponse(
                "This document covers microservices architecture.",
                "- Use loose coupling\n- Deploy independently");
        when(summaryService.summarize("doc-1")).thenReturn(response);

        mockMvc.perform(post("/api/summary/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("This document covers microservices architecture."))
                .andExpect(jsonPath("$.keyPoints").value("- Use loose coupling\n- Deploy independently"));
    }

    @Test
    void summarize_returns200WithEmptyKeyPointsForUnstructuredResponse() throws Exception {
        SummaryResponse response = new SummaryResponse("Plain text summary.", "");
        when(summaryService.summarize("doc-2")).thenReturn(response);

        mockMvc.perform(post("/api/summary/doc-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Plain text summary."))
                .andExpect(jsonPath("$.keyPoints").value(""));
    }

    @Test
    void summarize_returns404WhenDocumentNotFound() throws Exception {
        when(summaryService.summarize("ghost"))
                .thenThrow(new ResourceNotFoundException("Document not found: ghost"));

        mockMvc.perform(post("/api/summary/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Document not found: ghost"));
    }
}
