package com.example.AI.Doc.Assistant.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingest(Path path) {

        Resource resource = new FileSystemResource(path);

        TikaDocumentReader reader = new TikaDocumentReader(resource);

        List<Document> documents = reader.get();

        vectorStore.add(documents);
    }
}
