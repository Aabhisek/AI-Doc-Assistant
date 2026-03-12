package com.example.aidocassistant;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Document Assistant — entry point.
 *
 * Architecture:
 *   HTTP request → Controller → Service → Repository → PostgreSQL / pgvector
 *                                    ↓
 *                               AI layer → Groq (LLM + embeddings)
 *
 * Core features:
 *   - Document upload (PDF, DOCX, TXT) with automatic text chunking and embedding
 *   - Q&A chat using Retrieval-Augmented Generation (RAG)
 *   - Document summarization, information extraction, and document comparison
 */
@SpringBootApplication
public class AiDocAssistantApplication {

    public static void main(String[] args) {
        Dotenv.configure().ignoreIfMissing().load()
              .entries()
              .forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        SpringApplication.run(AiDocAssistantApplication.class, args);
    }
}
