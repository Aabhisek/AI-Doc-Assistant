package com.example.aidocassistant.dto.response;

import com.example.aidocassistant.model.Document;

import java.time.LocalDateTime;

/**
 * What the API returns when a client asks about a document.
 * Uses a Java record for immutability and zero boilerplate.
 */
public record DocumentResponse(
        String id,
        String name,
        String fileType,
        long fileSizeBytes,
        LocalDateTime uploadedAt,
        String status,
        int chunkCount) {

    /** Convenience factory — converts the JPA entity to a response record. */
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getName(),
                doc.getFileType(),
                doc.getFileSizeBytes(),
                doc.getUploadedAt(),
                doc.getStatus().name(),
                doc.getChunkCount()
        );
    }
}
