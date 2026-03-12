package com.example.aidocassistant.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A single text segment (chunk) produced during document ingestion.
 *
 * When a document is ingested, its full text is split into overlapping chunks
 * of ~500 tokens. Each chunk is independently embedded and stored in pgvector.
 * We mirror that in this relational table so we can retrieve the original text
 * for displaying citations in the chat UI.
 */
@Entity
@Table(name = "document_chunks")
@Getter
@Setter
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** Position of this chunk within the document (0-based). */
    private int chunkIndex;

    /**
     * The ID assigned by Spring AI when the chunk was added to the pgvector store.
     * Links this relational record back to its vector embedding row.
     */
    private String vectorStoreId;
}
