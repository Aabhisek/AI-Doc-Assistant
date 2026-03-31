package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.response.DocumentResponse;
import com.example.aidocassistant.exception.DocumentProcessingException;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@ActiveProfiles("test")
class DocumentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean DocumentService documentService;

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 3, 12, 10, 0);

    // ── POST /api/documents/upload ────────────────────────────────────────────

    @Test
    void upload_returns200WithDocumentResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "pdf content".getBytes());
        DocumentResponse response = new DocumentResponse(
                "doc-1", "report.pdf", "application/pdf", 11L, FIXED_TIME, "READY", 3);
        when(documentService.upload(any())).thenReturn(response);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.name").value("report.pdf"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(3));
    }

    @Test
    void upload_returns400WhenFileTypeIsInvalid() throws Exception {
        MockMultipartFile badFile = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "jpg bytes".getBytes());
        when(documentService.upload(any()))
                .thenThrow(new IllegalArgumentException("Unsupported file type: image/jpeg. Accepted: PDF, DOCX, TXT"));

        mockMvc.perform(multipart("/api/documents/upload").file(badFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Unsupported file type")));
    }

    @Test
    void upload_returns500WhenProcessingFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupt.pdf", "application/pdf", "bad data".getBytes());
        when(documentService.upload(any()))
                .thenThrow(new DocumentProcessingException("Failed to save file to disk: corrupt.pdf",
                        new RuntimeException("disk full")));

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value(containsString("Failed to save file")));
    }

    // ── GET /api/documents ────────────────────────────────────────────────────

    @Test
    void list_returns200WithAllDocuments() throws Exception {
        List<DocumentResponse> docs = List.of(
                new DocumentResponse("id-1", "a.pdf", "application/pdf", 100L, FIXED_TIME, "READY", 2),
                new DocumentResponse("id-2", "b.txt", "text/plain", 50L, FIXED_TIME, "PROCESSING", 0)
        );
        when(documentService.listAll()).thenReturn(docs);

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("id-1"))
                .andExpect(jsonPath("$[1].id").value("id-2"));
    }

    @Test
    void list_returns200WithEmptyArrayWhenNoneExist() throws Exception {
        when(documentService.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /api/documents/{id} ───────────────────────────────────────────────

    @Test
    void getById_returns200WhenDocumentExists() throws Exception {
        DocumentResponse doc = new DocumentResponse(
                "abc", "spec.pdf", "application/pdf", 200L, FIXED_TIME, "READY", 5);
        when(documentService.getById("abc")).thenReturn(doc);

        mockMvc.perform(get("/api/documents/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc"))
                .andExpect(jsonPath("$.chunkCount").value(5));
    }

    @Test
    void getById_returns404WhenDocumentNotFound() throws Exception {
        when(documentService.getById("ghost"))
                .thenThrow(new ResourceNotFoundException("Document not found: ghost"));

        mockMvc.perform(get("/api/documents/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Document not found: ghost"));
    }

    // ── DELETE /api/documents/{id} ────────────────────────────────────────────

    @Test
    void delete_returns204WhenDocumentIsDeleted() throws Exception {
        doNothing().when(documentService).delete("del-id");

        mockMvc.perform(delete("/api/documents/del-id"))
                .andExpect(status().isNoContent());

        verify(documentService).delete("del-id");
    }

    @Test
    void delete_returns404WhenDocumentNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Document not found: missing"))
                .when(documentService).delete("missing");

        mockMvc.perform(delete("/api/documents/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Document not found: missing"));
    }
}
