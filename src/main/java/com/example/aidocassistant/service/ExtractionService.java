package com.example.aidocassistant.service;

import com.example.aidocassistant.dto.response.ExtractionResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.model.DocumentChunk;
import com.example.aidocassistant.repository.DocumentChunkRepository;
import com.example.aidocassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extracts structured lists of items from a document.
 *
 * The category is free-form text (e.g. "skills", "dates", "names", "requirements").
 * The LLM interprets the category and returns a bullet list.
 */
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final ChatClient chatClient;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    // ~3 000 tokens — stays well under Groq free-tier rate limits to avoid truncated responses
    private static final int MAX_INPUT_CHARS = 12_000;

    public ExtractionResponse extract(String documentId, String category) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        String documentText = buildDocumentText(documentId);

        // Ask the model to return only a bullet list — easier to parse reliably
        String prompt = """
                From the document text below, extract all items that belong to the category: "%s"

                Rules:
                - Return ONLY a bullet-point list. Start each item with "- ".
                - One item per line.
                - If nothing is found, write exactly: "- None found"
                - Do not add any introduction, explanation, or conclusion.

                DOCUMENT TEXT:
                %s
                """.formatted(category, documentText);

        String raw = chatClient.prompt(prompt).call().content();
        return new ExtractionResponse(category, parseItems(raw));
    }

    private String buildDocumentText(String documentId) {
        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
        String text = chunks.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.joining("\n\n"));
        return text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;
    }

    private List<String> parseItems(String raw) {
        return Arrays.stream(raw.split("\n"))
                .map(String::trim)
                .filter(line -> line.startsWith("- "))
                .map(line -> line.substring(2).trim())
                .filter(item -> !item.isBlank())
                .toList();
    }
}
