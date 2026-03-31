package com.example.aidocassistant.dto.response;

import com.example.aidocassistant.model.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentResponseTest {

    @Test
    void from_mapsAllFieldsFromEntity() {
        Document doc = new Document();
        doc.setId("abc-123");
        doc.setName("report.pdf");
        doc.setFileType("application/pdf");
        doc.setFileSizeBytes(2048L);
        LocalDateTime now = LocalDateTime.of(2026, 3, 12, 10, 0);
        doc.setUploadedAt(now);
        doc.setStatus(Document.DocumentStatus.READY);
        doc.setChunkCount(5);

        DocumentResponse response = DocumentResponse.from(doc);

        assertThat(response.id()).isEqualTo("abc-123");
        assertThat(response.name()).isEqualTo("report.pdf");
        assertThat(response.fileType()).isEqualTo("application/pdf");
        assertThat(response.fileSizeBytes()).isEqualTo(2048L);
        assertThat(response.uploadedAt()).isEqualTo(now);
        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.chunkCount()).isEqualTo(5);
    }

    @Test
    void from_statusIsStringNameOfEnum() {
        Document doc = buildDoc(Document.DocumentStatus.PROCESSING);
        assertThat(DocumentResponse.from(doc).status()).isEqualTo("PROCESSING");
    }

    @Test
    void from_uploading_statusMappedCorrectly() {
        Document doc = buildDoc(Document.DocumentStatus.UPLOADING);
        assertThat(DocumentResponse.from(doc).status()).isEqualTo("UPLOADING");
    }

    @Test
    void from_failed_statusMappedCorrectly() {
        Document doc = buildDoc(Document.DocumentStatus.FAILED);
        assertThat(DocumentResponse.from(doc).status()).isEqualTo("FAILED");
    }

    private Document buildDoc(Document.DocumentStatus status) {
        Document doc = new Document();
        doc.setId("id");
        doc.setName("file.txt");
        doc.setFileType("text/plain");
        doc.setFileSizeBytes(100L);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setStatus(status);
        doc.setChunkCount(0);
        return doc;
    }
}
