package com.example.aidocassistant.service;

import com.example.aidocassistant.dto.response.ChatResponse;
import com.example.aidocassistant.dto.response.CitationResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements Retrieval-Augmented Generation (RAG) for the Q&A feature.
 *
 * Why RAG instead of just passing the whole document to the LLM?
 *   1. Context window limits — a 50-page PDF may have 50,000+ tokens, far beyond what
 *      most local models can process at once.
 *   2. Relevance — sending 50 pages for a narrow question produces vague answers.
 *      RAG retrieves only the top-5 most relevant passages, giving the LLM focused context.
 *   3. Citations — because we know exactly which chunks were used, we can show the user
 *      the source text that supported the answer.
 */
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final DocumentRepository documentRepository;

    /**
     * RAG pipeline:
     *   1. Retrieve the top-5 chunks most similar to the question (vector search)
     *   2. Assemble those chunks into a context block
     *   3. Send context + question to the LLM with a grounding prompt
     *   4. Return the answer + citations so the user can verify the sources
     */
    public ChatResponse answer(String documentId, String question) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        var docName = documentRepository.findById(documentId).get().getName();

        // Step 1 — Similarity search scoped to this document via a metadata filter.
        // Without the filter we'd get chunks from all uploaded documents.
        var b = new FilterExpressionBuilder();
        List<Document> relevant = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .filterExpression(b.eq("documentId", documentId).build())
                        .build()
        );

        if (relevant.isEmpty()) {
            return new ChatResponse(
                    "I could not find relevant information in this document to answer your question.",
                    List.of()
            );
        }

        // Step 2 — Concatenate chunk texts into a single context block
        String context = relevant.stream()
                .map(Document::getText)
                .reduce("", (a, c) -> a + "\n\n" + c)
                .strip();

        // Step 3 — Ground the LLM in the retrieved context.
        // Explicit instructions prevent the model from "hallucinating" facts not in the text.
        String prompt = """
                You are a helpful assistant answering questions about a document.
                Answer using ONLY the information in the provided context.
                If the context does not contain enough information to answer, say so clearly.
                Do not make up facts or draw on outside knowledge.

                CONTEXT:
                %s

                QUESTION: %s
                """.formatted(context, question);

        String answer = chatClient.prompt(prompt).call().content();

        // Step 4 — One citation per document (highest-scoring chunk wins).
        // Multiple chunks from the same document would show duplicate source cards in the UI.
        List<CitationResponse> citations = relevant.stream()
                .map(chunk -> new CitationResponse(
                        documentId,
                        docName,
                        truncate(chunk.getText(), 200),
                        scoreFrom(chunk)
                ))
                .collect(Collectors.toMap(
                        CitationResponse::documentId,
                        c -> c,
                        (x, y) -> x.score() >= y.score() ? x : y   // keep highest score
                ))
                .values()
                .stream()
                .sorted(Comparator.comparingDouble(CitationResponse::score).reversed())
                .toList();

        return new ChatResponse(answer, citations);
    }

    private double scoreFrom(Document chunk) {
        Object score = chunk.getMetadata().get("distance");
        if (score instanceof Number n) {
            // pgvector returns cosine distance (0 = identical, 2 = opposite).
            // Convert to a similarity score (1 = identical, 0 = unrelated).
            return Math.max(0, 1.0 - n.doubleValue());
        }
        return 0.0;
    }

    private String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…";
    }
}
