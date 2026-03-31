package com.example.aidocassistant.service;

import com.example.aidocassistant.dto.response.ExtractionResponse;
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
class ExtractionServiceTest {

    @Mock private ChatClient chatClient;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentChunkRepository chunkRepository;

    private ExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new ExtractionService(chatClient, documentRepository, chunkRepository);
    }

    // ── extract ───────────────────────────────────────────────────────────────

    @Test
    void extract_returnsParsedBulletItemsFromLlmResponse() {
        stubDocument("doc-1");
        stubChunks("doc-1", "Java, Python, and Kubernetes are mentioned throughout.");

        stubChatClient("""
                - Java
                - Python
                - Kubernetes
                """);

        ExtractionResponse result = extractionService.extract("doc-1", "skills");

        assertThat(result.category()).isEqualTo("skills");
        assertThat(result.items()).containsExactly("Java", "Python", "Kubernetes");
    }

    @Test
    void extract_filtersOutLinesNotStartingWithDash() {
        stubDocument("doc-2");
        stubChunks("doc-2", "Text content.");

        // Mix of intro, bullets, and trailing note
        stubChatClient("""
                Here are the extracted dates:
                - January 2024
                - March 2026
                Note: only two dates were found.
                """);

        ExtractionResponse result = extractionService.extract("doc-2", "dates");

        assertThat(result.items()).containsExactly("January 2024", "March 2026");
    }

    @Test
    void extract_returnsNoneFoundItemWhenLlmFindsNothing() {
        stubDocument("doc-3");
        stubChunks("doc-3", "No relevant content.");

        stubChatClient("- None found");

        ExtractionResponse result = extractionService.extract("doc-3", "phone numbers");

        assertThat(result.items()).containsExactly("None found");
    }

    @Test
    void extract_throwsWhenDocumentNotFound() {
        when(documentRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> extractionService.extract("ghost", "skills"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void extract_capsInputAt12000Chars() {
        stubDocument("doc-4");
        DocumentChunk bigChunk = buildChunk("Y".repeat(15_000), 0);
        when(chunkRepository.findByDocumentIdOrderByChunkIndex("doc-4")).thenReturn(List.of(bigChunk));

        stubChatClient("- Item one\n- Item two");

        ExtractionResponse result = extractionService.extract("doc-4", "names");

        assertThat(result.items()).containsExactly("Item one", "Item two");
    }

    @Test
    void extract_ignoresBlankItemsAfterStripping() {
        stubDocument("doc-5");
        stubChunks("doc-5", "Content.");

        // One bullet that is just whitespace after stripping the "- "
        stubChatClient("- Valid item\n-   \n- Another item");

        ExtractionResponse result = extractionService.extract("doc-5", "requirements");

        assertThat(result.items()).containsExactly("Valid item", "Another item");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubDocument(String id) {
        Document doc = new Document();
        doc.setId(id);
        doc.setName("test.pdf");
        doc.setFileType("application/pdf");
        doc.setStoredFileName("stored.pdf");
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
