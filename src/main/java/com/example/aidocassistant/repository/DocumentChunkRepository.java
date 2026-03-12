package com.example.aidocassistant.repository;

import com.example.aidocassistant.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /** Retrieves all chunks for a document in the order they appear in the original text. */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(String documentId);

    void deleteByDocumentId(String documentId);
}
