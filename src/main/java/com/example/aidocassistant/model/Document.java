package com.example.aidocassistant.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an uploaded document.
 *
 * After upload the document goes through these status transitions:
 *   UPLOADING → PROCESSING → READY
 *                           → FAILED (on error)
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Original filename as provided by the user. */
    @Column(nullable = false)
    private String name;

    /** MIME type: application/pdf, application/vnd.openxmlformats-officedocument.wordprocessingml.document, text/plain */
    @Column(nullable = false)
    private String fileType;

    /** UUID-prefixed filename used on disk to avoid name collisions. */
    @Column(nullable = false)
    private String storedFileName;

    private long fileSizeBytes;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    /** How many text chunks were created during ingestion. */
    private int chunkCount;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentChunk> chunks = new ArrayList<>();

    public enum DocumentStatus {
        UPLOADING,   // File is being written to disk
        PROCESSING,  // Tika parsing + embedding generation in progress
        READY,       // Fully indexed; available for Q&A, summarization, etc.
        FAILED       // Ingestion failed; see server logs for details
    }
}
