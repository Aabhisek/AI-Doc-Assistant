package com.example.aidocassistant.service;

import com.example.aidocassistant.dto.response.ComparisonResponse;
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
class ComparisonServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository chunkRepository;

    private ComparisonService comparisonService;

    @BeforeEach
    void setUp() {
        comparisonService = new ComparisonService(chatClient, documentRepository, chunkRepository);
    }

    // ── compare ───────────────────────────────────────────────────────────────

    @Test
    void compare_parsesStructuredResponseCorrectly() {
        stubDocument("doc-a", "Policy v1.pdf");
        stubDocument("doc-b", "Policy v2.pdf");
        stubChunks("doc-a", "Policy v1 covers basic compliance.");
        stubChunks("doc-b", "Policy v2 introduces GDPR clauses.");

        stubChatClient("""
                SIMILARITIES:
                - Both cover data retention policies
                - Both require user consent

                DIFFERENCES:
                - v2 adds GDPR compliance requirements
                - v1 lacks international regulation coverage
                """);

        ComparisonResponse result = comparisonService.compare("doc-a", "doc-b");

        assertThat(result.similarities()).contains("Both cover data retention policies");
        assertThat(result.differences()).contains("v2 adds GDPR compliance requirements");
    }

    @Test
    void compare_fallsBackToRawTextWhenFormatIsMissing() {
        stubDocument("doc-a", "a.pdf");
        stubDocument("doc-b", "b.pdf");
        stubChunks("doc-a", "Content A.");
        stubChunks("doc-b", "Content B.");

        stubChatClient("The two documents are quite similar in their approach.");

        ComparisonResponse result = comparisonService.compare("doc-a", "doc-b");

        assertThat(result.similarities()).isEqualTo("The two documents are quite similar in their approach.");
        assertThat(result.differences()).isEmpty();
    }

    @Test
    void compare_throwsWhenDocumentANotFound() {
        when(documentRepository.findById("missing-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comparisonService.compare("missing-a", "doc-b"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing-a");
    }

    @Test
    void compare_throwsWhenDocumentBNotFound() {
        stubDocument("doc-a", "a.pdf");
        when(documentRepository.findById("missing-b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comparisonService.compare("doc-a", "missing-b"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing-b");
    }

    @Test
    void compare_capsEachDocumentAt6000Chars() {
        stubDocument("doc-a", "big-a.pdf");
        stubDocument("doc-b", "big-b.pdf");

        DocumentChunk bigA = buildChunk("A".repeat(8_000), 0);
        DocumentChunk bigB = buildChunk("B".repeat(8_000), 0);
        when(chunkRepository.findByDocumentIdOrderByChunkIndex("doc-a")).thenReturn(List.of(bigA));
        when(chunkRepository.findByDocumentIdOrderByChunkIndex("doc-b")).thenReturn(List.of(bigB));

        stubChatClient("SIMILARITIES:\n- Both are long\n\nDIFFERENCES:\n- Content differs");

        ComparisonResponse result = comparisonService.compare("doc-a", "doc-b");

        assertThat(result.similarities()).contains("Both are long");
        assertThat(result.differences()).contains("Content differs");
    }

    @Test
    void compare_handlesEmptyChunksGracefully() {
        stubDocument("doc-a", "empty-a.pdf");
        stubDocument("doc-b", "empty-b.pdf");
        when(chunkRepository.findByDocumentIdOrderByChunkIndex("doc-a")).thenReturn(List.of());
        when(chunkRepository.findByDocumentIdOrderByChunkIndex("doc-b")).thenReturn(List.of());

        stubChatClient("SIMILARITIES:\n- Both are empty\n\nDIFFERENCES:\n- None");

        ComparisonResponse result = comparisonService.compare("doc-a", "doc-b");

        assertThat(result.similarities()).contains("Both are empty");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubDocument(String id, String name) {
        Document doc = new Document();
        doc.setId(id);
        doc.setName(name);
        doc.setFileType("application/pdf");
        doc.setStoredFileName("stored_" + name);
        doc.setFileSizeBytes(512L);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setStatus(Document.DocumentStatus.READY);
        when(documentRepository.findById(id)).thenReturn(Optional.of(doc));
    }

    private void stubChunks(String documentId, String... contents) {
        List<DocumentChunk> chunks = java.util.stream.IntStream.range(0, contents.length)
                .mapToObj(i -> buildChunk(contents[i], i))
                .toList();
        when(chunkRepository.findByDocumentIdOrderByChunkIndex(documentId)).thenReturn(chunks);
    }

    private DocumentChunk buildChunk(String content, int index) {
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
