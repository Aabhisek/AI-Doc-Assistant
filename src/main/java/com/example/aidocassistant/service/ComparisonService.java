package com.example.aidocassistant.service;

import com.example.aidocassistant.dto.response.ComparisonResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.model.DocumentChunk;
import com.example.aidocassistant.repository.DocumentChunkRepository;
import com.example.aidocassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Compares two documents by sending the first portion of each to the LLM.
 *
 * Why cap each document at 50 000 chars?
 *   Groq's llama-3.1-8b-instant supports a 128k-token context window.
 *   At ~4 chars/token, two documents × 50 000 chars ≈ 25 000 tokens of document text,
 *   leaving plenty of room for the prompt instructions and the model's response.
 */
@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final ChatClient chatClient;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    // ~1 500 tokens per doc — keeps total prompt under 4 000 tokens on Groq free tier
    private static final int MAX_CHARS_PER_DOC = 6_000;

    public ComparisonResponse compare(String documentIdA, String documentIdB) {
        var docA = documentRepository.findById(documentIdA)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentIdA));
        var docB = documentRepository.findById(documentIdB)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentIdB));

        String textA = getDocumentText(documentIdA);
        String textB = getDocumentText(documentIdB);

        String prompt = """
                Compare the two documents below and respond using this exact format:

                SIMILARITIES:
                [Bullet-point list of key similarities, each starting with "- "]

                DIFFERENCES:
                [Bullet-point list of key differences, each starting with "- "]

                DOCUMENT A — %s:
                %s

                DOCUMENT B — %s:
                %s
                """.formatted(docA.getName(), textA, docB.getName(), textB);

        String raw = chatClient.prompt(prompt).call().content();
        return parseComparison(raw);
    }

    private String getDocumentText(String documentId) {
        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
        String text = chunks.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.joining("\n\n"));
        return text.length() > MAX_CHARS_PER_DOC ? text.substring(0, MAX_CHARS_PER_DOC) : text;
    }

    private ComparisonResponse parseComparison(String raw) {
        if (raw.contains("SIMILARITIES:") && raw.contains("DIFFERENCES:")) {
            int simStart = raw.indexOf("SIMILARITIES:") + "SIMILARITIES:".length();
            int diffStart = raw.indexOf("DIFFERENCES:");
            String similarities = raw.substring(simStart, diffStart).strip();
            String differences = raw.substring(diffStart + "DIFFERENCES:".length()).strip();
            return new ComparisonResponse(similarities, differences);
        }
        return new ComparisonResponse(raw.strip(), "");
    }
}
