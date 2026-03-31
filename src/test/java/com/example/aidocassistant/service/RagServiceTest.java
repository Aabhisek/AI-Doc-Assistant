package com.example.aidocassistant.service;

import com.example.aidocassistant.dto.response.ChatResponse;
import com.example.aidocassistant.exception.ResourceNotFoundException;
import com.example.aidocassistant.model.Document;
import com.example.aidocassistant.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock private VectorStore vectorStore;
    @Mock private ChatClient chatClient;
    @Mock private DocumentRepository documentRepository;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(vectorStore, chatClient, documentRepository);
    }

    // ── answer ────────────────────────────────────────────────────────────────

    @Test
    void answer_returnsAnswerAndCitationsWhenChunksAreFound() {
        Document jpaDoc = buildJpaDocument("doc-1", "technical-spec.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(jpaDoc));

        org.springframework.ai.document.Document chunk = new org.springframework.ai.document.Document(
                "The system uses microservices architecture.", Map.of("documentId", "doc-1", "distance", 0.15));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

        stubChatClient("Microservices architecture is used for scalability.");

        ChatResponse response = ragService.answer("doc-1", "What architecture is used?");

        assertThat(response.answer()).isEqualTo("Microservices architecture is used for scalability.");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).documentId()).isEqualTo("doc-1");
        assertThat(response.citations().get(0).documentName()).isEqualTo("technical-spec.pdf");
        assertThat(response.citations().get(0).score()).isEqualTo(0.85, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void answer_returnsNoInfoResponseWhenVectorSearchIsEmpty() {
        Document jpaDoc = buildJpaDocument("doc-2", "empty-doc.pdf");
        when(documentRepository.findById("doc-2")).thenReturn(Optional.of(jpaDoc));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ChatResponse response = ragService.answer("doc-2", "What is the main topic?");

        assertThat(response.answer()).contains("could not find relevant information");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void answer_throwsResourceNotFoundWhenDocumentDoesNotExist() {
        when(documentRepository.findById("no-such")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ragService.answer("no-such", "any question"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no-such");
    }

    @Test
    void answer_deduplicatesCitationsKeepingHighestScore() {
        Document jpaDoc = buildJpaDocument("doc-3", "report.pdf");
        when(documentRepository.findById("doc-3")).thenReturn(Optional.of(jpaDoc));

        // Two chunks from the same documentId — only the higher-scoring one should appear
        org.springframework.ai.document.Document lowScore = new org.springframework.ai.document.Document(
                "Low relevance text.", Map.of("documentId", "doc-3", "distance", 0.5));
        org.springframework.ai.document.Document highScore = new org.springframework.ai.document.Document(
                "High relevance text.", Map.of("documentId", "doc-3", "distance", 0.1));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(lowScore, highScore));

        stubChatClient("Some answer.");

        ChatResponse response = ragService.answer("doc-3", "test question");

        // One citation per document, highest score wins (distance 0.1 → score 0.9)
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).score()).isGreaterThan(0.8);
    }

    @Test
    void answer_truncatesCitationExcerptToTwoHundredChars() {
        Document jpaDoc = buildJpaDocument("doc-4", "big.pdf");
        when(documentRepository.findById("doc-4")).thenReturn(Optional.of(jpaDoc));

        String longText = "A".repeat(300);
        org.springframework.ai.document.Document chunk = new org.springframework.ai.document.Document(
                longText, Map.of("documentId", "doc-4", "distance", 0.2));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

        stubChatClient("Answer.");

        ChatResponse response = ragService.answer("doc-4", "question");

        // excerpt must be truncated with ellipsis
        String excerpt = response.citations().get(0).excerpt();
        assertThat(excerpt).hasSizeLessThanOrEqualTo(201); // 200 chars + "…"
        assertThat(excerpt).endsWith("…");
    }

    @Test
    void answer_scoreIsZeroWhenDistanceMetadataIsMissing() {
        Document jpaDoc = buildJpaDocument("doc-5", "nodist.pdf");
        when(documentRepository.findById("doc-5")).thenReturn(Optional.of(jpaDoc));

        // No "distance" key in metadata
        org.springframework.ai.document.Document chunk = new org.springframework.ai.document.Document(
                "Some content.", Map.of("documentId", "doc-5"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

        stubChatClient("Answer without distance.");

        ChatResponse response = ragService.answer("doc-5", "question");

        assertThat(response.citations().get(0).score()).isEqualTo(0.0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubChatClient(String answer) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(answer);
    }

    private Document buildJpaDocument(String id, String name) {
        Document doc = new Document();
        doc.setId(id);
        doc.setName(name);
        doc.setFileType("application/pdf");
        doc.setStoredFileName("stored_" + name);
        doc.setFileSizeBytes(1024L);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setStatus(Document.DocumentStatus.READY);
        doc.setChunkCount(3);
        return doc;
    }
}
