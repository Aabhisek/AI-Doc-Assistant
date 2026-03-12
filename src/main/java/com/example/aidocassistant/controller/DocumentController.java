package com.example.aidocassistant.controller;

import com.example.aidocassistant.dto.response.DocumentResponse;
import com.example.aidocassistant.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /** Upload a PDF, DOCX, or TXT file. Blocks until ingestion completes. */
    @PostMapping("/upload")
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(documentService.upload(file));
    }

    /** Returns all uploaded documents with their current status. */
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list() {
        return ResponseEntity.ok(documentService.listAll());
    }

    /** Returns a single document by ID — useful for status polling. */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(documentService.getById(id));
    }

    /** Deletes the document, its chunks, and its vector embeddings. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
