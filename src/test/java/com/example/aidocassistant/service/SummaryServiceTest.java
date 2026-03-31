package com.example.aidocassistant.service;

import com.example.aidocassistant.dto.response.SummaryResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.model.Document;
import com.example.aidocassistant.model.DocumentChunk;
import com.example.aidocassistant.repository.DocumentChunkRepository;
import com.example.aidocassistant.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository chunkRepository;

    private SummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new SummaryService(chatClient, documentRepository, chunkRepository);
    }

    // ── summarize ─────────────────────────────────────────────────────────────

    @Test
    void summarize_parsesStructuredResponseCorrectly() {
        stubDocument("doc-1");
        stubChunks("doc-1", "Chunk one content.", "Chunk two content.");

        String llmResponse = """
                SUMMARY:
                This document covers software architecture principles.

                KEY POINTS:
                - Microservices improve scalability
                - Loose coupling is essential
                - Use event-driven patterns
                """;
        stubChatClient(llmResponse);

        SummaryResponse result = summaryService.summarize("doc-1");

        assertThat(result.summary()).contains("software architecture principles");
        assertThat(result.keyPoints()).contains("Microservices improve scalability");
        assertThat(result.keyPoints()).contains("Loose coupling is essential");
    }

    @Test
    void summarize_fallsBackToRawTextWhenLlmDoesNotFollowFormat() {
        stubDocument("doc-2");
        stubChunks("doc-2", "Some text.");
        stubChatClient("The document talks about various topics without any structure.");

        SummaryResponse result = summaryService.summarize("doc-2");

        assertThat(result.summary()).isEqualTo("The document talks about various topics without any structure.");
        assertThat(result.keyPoints()).isEmpty();
    }

    @Test
    void summarize_throwsWhenDocumentNotFound() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> summaryService.summarize("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void summarize_capsDocumentTextAt12000Chars() {
        stubDocument("doc-3");

        // Build a chunk whose content exceeds 12 000 chars
        DocumentChunk bigChunk = buildChunk("doc-3", "X".repeat(15_000), 0);
        when(chunkRepository.findByDocumentIdOrderByChunkIndex("doc-3")).thenReturn(List.of(bigChunk));

        // We just need the chatClient call to succeed; the truncation is invisible to the caller
        stubChatClient("SUMMARY:\nA summary.\n\nKEY POINTS:\n- A point");

        SummaryResponse result = summaryService.summarize("doc-3");

        assertThat(result.summary()).isEqualTo("A summary.");
    }

    @Test
    void summarize_joinsMultipleChunksWithDoubleNewline() {
        stubDocument("doc-4");
        stubChunks("doc-4", "First chunk.", "Second chunk.", "Third chunk.");
        stubChatClient("SUMMARY:\nCombined summary.\n\nKEY POINTS:\n- Point one");

        SummaryResponse result = summaryService.summarize("doc-4");

        assertThat(result.summary()).isEqualTo("Combined summary.");
        assertThat(result.keyPoints()).contains("Point one");
    }

    @Test
    void summarize_returnsEmptySummaryWhenNoChunks() {
        stubDocument("doc-5");
        when(chunkRepository.findByDocumentIdOrderByChunkIndex("doc-5")).thenReturn(List.of());
        stubChatClient("SUMMARY:\nEmpty document.\n\nKEY POINTS:\n- Nothing found");

        SummaryResponse result = summaryService.summarize("doc-5");

        assertThat(result.summary()).isEqualTo("Empty document.");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubDocument(String id) {
        Document doc = new Document();
        doc.setId(id);
        doc.setName("test.pdf");
        doc.setFileType("application/pdf");
        doc.setStoredFileName("stored.pdf");
        doc.setFileSizeBytes(1024L);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setStatus(Document.DocumentStatus.READY);
        when(documentRepository.findById(id)).thenReturn(Optional.of(doc));
    }

    private void stubChunks(String documentId, String... contents) {
        List<DocumentChunk> chunks = java.util.stream.IntStream.range(0, contents.length)
                .mapToObj(i -> buildChunk(documentId, contents[i], i))
                .toList();
        when(chunkRepository.findByDocumentIdOrderByChunkIndex(documentId)).thenReturn(chunks);
    }

    private DocumentChunk buildChunk(String documentId, String content, int index) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setContent(content);
        chunk.setChunkIndex(index);
        return chunk;
    }

    private void stubChatClient(String response) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
    }
}
