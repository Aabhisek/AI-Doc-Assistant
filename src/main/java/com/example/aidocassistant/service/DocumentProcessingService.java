package com.example.aidocassistant.service;

import com.example.aidocassistant.exception.DocumentProcessingException;
import com.example.aidocassistant.model.Document;
import com.example.aidocassistant.model.Document.DocumentStatus;
import com.example.aidocassistant.model.DocumentChunk;
import com.example.aidocassistant.repository.DocumentChunkRepository;
import com.example.aidocassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;

/**
 * Handles the document ingestion pipeline: text extraction → chunking → embedding → storage.
 *
 * Why chunk documents instead of storing them whole?
 *   Vector similarity search works best on short, focused passages. A 50-page PDF would
 *   produce a single massive embedding that averages out all topics, making it hard to
 *   retrieve the specific paragraph that answers a narrow question. Chunking lets us
 *   retrieve only the most relevant passages.
 *
 * Why use overlapping chunks?
 *   If a sentence is split across two chunk boundaries, neither chunk alone contains the
 *   full sentence. A 50-token overlap between adjacent chunks ensures every sentence
 *   appears complete in at least one chunk.
 */
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    /**
     * Ingestion pipeline steps:
     *   1. Parse the file with Apache Tika (auto-detects PDF / DOCX / TXT format)
     *   2. Split the text into 500-token chunks with 50-token overlap
     *   3. Attach documentId as metadata on every chunk (needed for per-document filtering)
     *   4. Embed all chunks using Jina AI cloud API and persist to pgvector
     *   5. Save chunk text to the relational DB for later citation display
     */
    @Transactional
    public void ingest(Document doc, Path filePath) {
        doc.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(doc);

        try {
            // Step 1 — Tika extracts plain text from any supported file format
            var reader = new TikaDocumentReader(new FileSystemResource(filePath));
            List<org.springframework.ai.document.Document> rawDocs = reader.get();

            // Step 2 — Split into overlapping chunks of ~500 tokens
            var splitter = new TokenTextSplitter(500, 50, 5, 10_000, true);
            List<org.springframework.ai.document.Document> chunks = splitter.apply(rawDocs);

            // Step 3 — Tag every chunk with the parent documentId so we can later
            // restrict similarity searches to just this document's vectors
            chunks.forEach(chunk -> chunk.getMetadata().put("documentId", doc.getId()));

            // Step 4 — Compute embeddings and write to pgvector (one batch call)
            vectorStore.add(chunks);

            // Step 5 — Mirror the chunks in the relational DB for citation lookup
            for (int i = 0; i < chunks.size(); i++) {
                var chunk = chunks.get(i);
                var dbChunk = new DocumentChunk();
                dbChunk.setDocument(doc);
                dbChunk.setContent(chunk.getText());
                dbChunk.setChunkIndex(i);
                dbChunk.setVectorStoreId(chunk.getId());
                chunkRepository.save(dbChunk);
            }

            doc.setChunkCount(chunks.size());
            doc.setStatus(DocumentStatus.READY);
            documentRepository.save(doc);

            log.info("Ingested '{}' — {} chunks created", doc.getName(), chunks.size());

        } catch (Exception e) {
            doc.setStatus(DocumentStatus.FAILED);
            documentRepository.save(doc);
            throw new DocumentProcessingException("Ingestion failed for: " + doc.getName(), e);
        }
    }

    /**
     * Deletes all vector embeddings for a document using a metadata filter.
     * Called before deleting the document record so we don't leave orphaned vectors.
     */
    public void removeFromVectorStore(String documentId) {
        try {
            var b = new FilterExpressionBuilder();
            vectorStore.delete(b.eq("documentId", documentId).build());
            log.info("Removed vector embeddings for document {}", documentId);
        } catch (Exception e) {
            // Log but don't fail the deletion — stale vectors don't break anything
            log.warn("Could not remove vectors for document {}: {}", documentId, e.getMessage());
        }
    }
}
