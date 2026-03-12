# 1. System Overview

## What This Project Is

The AI Document Assistant is a **Retrieval-Augmented Generation (RAG)** application that lets users:

- Upload PDF, DOCX, or TXT documents
- Ask questions about them in natural language
- Generate summaries
- Extract structured information
- Compare two documents

Think of it as a simplified ChatGPT that has read your documents.

---

## Architecture Diagram

```
┌─────────────┐       ┌──────────────────────────────────────────────┐
│             │       │              Spring Boot Backend              │
│   React     │──────▶│                                              │
│  Frontend   │  REST │  ┌────────────┐   ┌────────────────────────┐ │
│  (port 3000)│       │  │ Controllers│──▶│       Services         │ │
└─────────────┘       │  └────────────┘   └──────────┬─────────────┘ │
                      │                              │                │
                      │               ┌──────────────┴───────────┐   │
                      │               │                          │   │
                      │        ┌──────▼──────┐   ┌──────────────▼─┐ │
                      │        │ PostgreSQL  │   │  AI Services   │ │
                      │        │ + pgvector  │   │                │ │
                      │        │ (port 5432) │   │ Groq Cloud LLM │ │
                      │        └─────────────┘   │ Jina AI Embeds │ │
                      │                          └────────────────┘ │
                      └────────────────────────────────────────────┘
```

---

## Project Structure

```
ai-doc-assistant/
├── src/main/java/com/example/aidocassistant/
│   ├── AiDocAssistantApplication.java      # Entry point (loads .env, starts Spring)
│   ├── config/
│   │   ├── AiConfig.java                   # ChatClient bean + RestClient timeouts
│   │   ├── EmbeddingConfig.java            # Jina AI EmbeddingModel bean (1024-dim)
│   │   ├── VectorStoreConfig.java          # PgVectorStore bean (initializeSchema)
│   │   └── WebConfig.java                  # CORS configuration
│   ├── controller/                         # HTTP layer (REST endpoints)
│   │   ├── DocumentController.java         # upload, list, get, delete
│   │   ├── ChatController.java             # POST /api/chat
│   │   ├── SummaryController.java          # POST /api/summary/{id}
│   │   ├── ExtractionController.java       # POST /api/extract
│   │   └── ComparisonController.java       # POST /api/compare
│   ├── service/                            # Business logic
│   │   ├── DocumentService.java            # File validation, storage, lifecycle
│   │   ├── DocumentProcessingService.java  # Tika → chunk → embed → pgvector
│   │   ├── RagService.java                 # Core RAG pipeline
│   │   ├── SummaryService.java
│   │   ├── ExtractionService.java
│   │   └── ComparisonService.java
│   ├── repository/                         # Database access
│   │   ├── DocumentRepository.java
│   │   └── DocumentChunkRepository.java
│   ├── model/                              # JPA entities
│   │   ├── Document.java
│   │   └── DocumentChunk.java
│   ├── dto/                                # Data transfer objects
│   │   ├── request/
│   │   │   ├── ChatRequest.java
│   │   │   ├── CompareRequest.java
│   │   │   └── ExtractionRequest.java
│   │   └── response/
│   │       ├── ChatResponse.java
│   │       ├── CitationResponse.java
│   │       ├── ComparisonResponse.java
│   │       ├── DocumentResponse.java
│   │       ├── ExtractionResponse.java
│   │       └── SummaryResponse.java
│   └── exception/                          # Error handling
│       ├── ResourceNotFoundException.java
│       ├── DocumentProcessingException.java
│       └── GlobalExceptionHandler.java
├── frontend/                               # React application
│   ├── src/
│   │   ├── App.jsx                         # Main layout + state
│   │   ├── components/
│   │   │   ├── ActionBar.jsx               # Summarize / Extract / Compare buttons
│   │   │   ├── ChatInput.jsx
│   │   │   ├── ChatMessage.jsx             # Markdown rendering + citations
│   │   │   ├── DocumentList.jsx
│   │   │   ├── DocumentUpload.jsx          # react-dropzone upload zone
│   │   │   └── ResultPanel.jsx             # Summary / Extraction / Comparison cards
│   │   └── services/api.js                 # Axios HTTP client
│   ├── Dockerfile.frontend
│   ├── nginx.conf
│   └── package.json
├── docs/                                   # This documentation
├── docker-compose.yml                      # postgres + backend + frontend
├── Dockerfile                              # Backend multi-stage build
└── pom.xml
```

---

## Technology Choices

| Technology       | Role                              | Why                                        |
|------------------|-----------------------------------|--------------------------------------------|
| Spring Boot 3    | Backend framework                 | Industry standard, clean, well-documented  |
| Java 21          | Language                          | Modern features (records, virtual threads) |
| Spring AI 1.0    | LLM + vector store abstraction    | Hides Groq / Jina / pgvector API details   |
| Groq Cloud       | LLM inference                     | Fast, free tier, OpenAI-compatible API     |
| Jina AI          | Embedding model                   | High-quality 1024-dim vectors, free tier   |
| PostgreSQL       | Relational database               | ACID, familiar, widely used                |
| pgvector         | Vector similarity search          | Native Postgres extension — no extra DB    |
| Apache Tika      | Document parsing                  | Supports PDF, DOCX, TXT and 1000+ formats |
| React + Vite     | Frontend                          | Fast, modern, easy to understand           |
| Tailwind CSS     | Styling                           | Utility-first, no CSS files to maintain   |
| Docker Compose   | Local orchestration               | One-command startup for all services       |

---

## Data Flow Summary

1. User uploads a file → `DocumentController` → `DocumentService` → `DocumentProcessingService`
2. Tika parses text → chunks → **Jina AI** generates 1024-dim embeddings → stored in pgvector
3. User asks a question → `ChatController` → `RagService`
4. RagService searches pgvector → retrieves relevant chunks → calls **Groq LLM**
5. LLM generates answer → returned with source citations
