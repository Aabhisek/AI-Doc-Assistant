package com.example.aidocassistant.dto.response;

/**
 * A source citation attached to a chat answer.
 * Points the user to the exact chunk of text the LLM used to generate the answer.
 */
public record CitationResponse(
        String documentId,
        String documentName,
        /** First 200 characters of the matched chunk — enough context to be useful. */
        String excerpt,
        /** Cosine similarity score (0–1). Higher = more relevant to the question. */
        double score) {
}
