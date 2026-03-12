package com.example.aidocassistant.service;

import com.example.aidocassistant.dto.response.SummaryResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.model.DocumentChunk;
import com.example.aidocassistant.repository.DocumentChunkRepository;
import com.example.aidocassistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final ChatClient chatClient;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    // ~3 000 tokens — stays well under Groq free-tier rate limits to avoid truncated responses
    private static final int MAX_INPUT_CHARS = 12_000;

    /**
     * Summarizes a document by feeding its text to the LLM.
     * We cap the input to avoid overflowing the model's context window.
     */
    public SummaryResponse summarize(String documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        String documentText = buildDocumentText(documentId);

        String prompt = """
                Summarize the following document. Structure your response exactly as shown:

                SUMMARY:
                [2–4 sentence summary of the main content and purpose]

                KEY POINTS:
                [Bullet-point list of the 3–5 most important points, each starting with "- "]

                DOCUMENT TEXT:
                %s
                """.formatted(documentText);

        String raw = chatClient.prompt(prompt).call().content();
        return parseSummary(raw);
    }

    private String buildDocumentText(String documentId) {
        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
        String text = chunks.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.joining("\n\n"));
        return text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;
    }

    private SummaryResponse parseSummary(String raw) {
        if (raw.contains("SUMMARY:") && raw.contains("KEY POINTS:")) {
            int summaryStart = raw.indexOf("SUMMARY:") + "SUMMARY:".length();
            int keyStart = raw.indexOf("KEY POINTS:");
            String summary = raw.substring(summaryStart, keyStart).strip();
            String keyPoints = raw.substring(keyStart + "KEY POINTS:".length()).strip();
            return new SummaryResponse(summary, keyPoints);
        }
        // Model didn't follow the format — return the whole response as a plain summary
        return new SummaryResponse(raw.strip(), "");
    }
}
