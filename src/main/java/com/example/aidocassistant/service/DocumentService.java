package com.example.aidocassistant.service;

import com.example.aidocassistant.dto.response.DocumentResponse;
import com.example.aidocassistant.exception.DocumentProcessingException;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.model.Document;
import com.example.aidocassistant.model.Document.DocumentStatus;
import com.example.aidocassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages the document lifecycle: upload, list, retrieve, delete.
 * Delegates the heavy ingestion work to DocumentProcessingService.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService processingService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    @Transactional
    public DocumentResponse upload(MultipartFile file) {
        validateFileType(file);

        Path uploadPath = Path.of(uploadDir);
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new DocumentProcessingException("Could not create upload directory", e);
        }

        // Prefix with a UUID to guarantee a unique filename on disk
        String storedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path destination = uploadPath.resolve(storedName);

        // Persist the document record immediately so the client can see it right away.
        // Status starts at UPLOADING and transitions through PROCESSING → READY.
        Document doc = new Document();
        doc.setName(file.getOriginalFilename());
        doc.setFileType(file.getContentType());
        doc.setStoredFileName(storedName);
        doc.setFileSizeBytes(file.getSize());
        doc.setUploadedAt(LocalDateTime.now());
        doc.setStatus(DocumentStatus.UPLOADING);
        doc = documentRepository.save(doc);

        try {
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            processingService.ingest(doc, destination);
        } catch (IOException e) {
            doc.setStatus(DocumentStatus.FAILED);
            documentRepository.save(doc);
            throw new DocumentProcessingException("Failed to save file to disk: " + file.getOriginalFilename(), e);
        }

        // Re-fetch to get the updated status and chunkCount from processingService
        return DocumentResponse.from(documentRepository.findById(doc.getId()).orElse(doc));
    }

    public List<DocumentResponse> listAll() {
        return documentRepository.findAll().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    public DocumentResponse getById(String id) {
        return documentRepository.findById(id)
                .map(DocumentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    @Transactional
    public void delete(String id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));

        // Remove the physical file from disk
        try {
            Files.deleteIfExists(Path.of(uploadDir).resolve(doc.getStoredFileName()));
        } catch (IOException e) {
            log.warn("Could not delete file {} from disk: {}", doc.getStoredFileName(), e.getMessage());
        }

        // Remove embeddings from pgvector before deleting the DB record
        processingService.removeFromVectorStore(id);

        documentRepository.delete(doc);
    }

    private void validateFileType(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType + ". Accepted: PDF, DOCX, TXT");
        }
    }
}
