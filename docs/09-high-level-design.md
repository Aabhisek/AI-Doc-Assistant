# 9. High-Level Design (HLD)

## Purpose

The High-Level Design describes the AI Document Assistant from a **system-wide, architectural perspective**. It answers:

- What are the major components of the system?
- How do they communicate?
- What external services are involved?
- How is the system deployed?
- What are the two primary data flows?

---

## 1. System Context Diagram

Shows the system as a black box, surrounded by the actors and external systems it interacts with.

```
                        ┌─────────────────────────────────────────────────────────┐
                        │               AI Document Assistant System               │
                        │                                                         │
  ┌──────────┐  upload  │  ┌──────────────┐    REST/HTTP   ┌────────────────────┐ │
  │          │─────────▶│  │              │◀──────────────▶│                    │ │
  │   User   │  query   │  │    React     │                │   Spring Boot      │ │
  │(Browser) │◀─────────│  │  Frontend    │                │     Backend        │ │
  │          │  results │  │  (port 3000) │                │    (port 8080)     │ │
  └──────────┘          │  └──────────────┘                └─────────┬──────────┘ │
                        │                                            │            │
                        │                          ┌─────────────────┼──────────┐ │
                        │                          │                 │          │ │
                        │                   ┌──────▼──────┐  ┌──────▼───────┐  │ │
                        │                   │ PostgreSQL  │  │   File       │  │ │
                        │                   │ + pgvector  │  │   System     │  │ │
                        │                   │ (port 5432) │  │ (./uploads/) │  │ │
                        │                   └─────────────┘  └──────────────┘  │ │
                        │                                                       │ │
                        └───────────────────────────────────────────────────────┘ │
                                                                                   │
              External Services (Cloud APIs)                                       │
              ┌────────────────────────────────────────────────────────────────────┘
              │
              │   ┌───────────────────┐        ┌───────────────────────┐
              └──▶│   Groq Cloud LLM  │        │    Jina AI Embedding  │
                  │ llama-3.1-8b-inst │        │  jina-embeddings-v3   │
                  │  (inference API)  │        │   (embedding API)     │
                  └───────────────────┘        └───────────────────────┘
```

**Actors:**
| Actor | Role |
|-------|------|
| User (Browser) | Uploads documents, asks questions, reads responses |
| Groq Cloud | Provides LLM inference (text generation) |
| Jina AI | Provides embedding model (text → 1024-dim vector) |
| PostgreSQL + pgvector | Stores documents, chunks, and vector embeddings |
| File System | Stores raw uploaded files (PDF, DOCX, TXT) |

---

## 2. Component Architecture Diagram

Shows the major internal components and how they are layered.

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND (React + Vite)                               │
│                                                                                    │
│   ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  ┌────────────────────┐    │
│   │DocumentUpload│  │ DocumentList │  │  ChatInput  │  │    ActionBar       │    │
│   │  (dropzone)  │  │  (sidebar)   │  │  + ChatMsg  │  │ (summarize/extract │    │
│   └──────────────┘  └──────────────┘  └─────────────┘  │  /compare)        │    │
│                                                          └────────────────────┘    │
│                        ┌──────────────────────────┐                               │
│                        │     services/api.js       │  (Axios HTTP client)         │
│                        └───────────────────────────┘                              │
└────────────────────────────────────┬───────────────────────────────────────────────┘
                                     │  REST / JSON  (CORS enabled)
                                     │
┌────────────────────────────────────▼───────────────────────────────────────────────┐
│                           BACKEND (Spring Boot 3 / Java 21)                        │
│                                                                                    │
│  ┌─────────────────────────────── HTTP Layer ──────────────────────────────────┐  │
│  │  DocumentController   ChatController   SummaryController                    │  │
│  │  ExtractionController ComparisonController                                  │  │
│  └──────────────────────────────────┬──────────────────────────────────────────┘  │
│                                     │                                              │
│  ┌─────────────────────────── Service Layer ───────────────────────────────────┐  │
│  │  DocumentService    DocumentProcessingService    RagService                  │  │
│  │  SummaryService     ExtractionService            ComparisonService           │  │
│  └────────────┬──────────────────────────┬──────────────────────────────────────┘  │
│               │                          │                                          │
│  ┌────────────▼────────────┐  ┌──────────▼────────────────────────────────────┐  │
│  │    Data Access Layer    │  │          AI / Integration Layer               │  │
│  │                         │  │                                               │  │
│  │  DocumentRepository     │  │  VectorStore (pgvector via Spring AI)         │  │
│  │  DocumentChunkRepository│  │  EmbeddingModel (Jina AI via Spring AI)       │  │
│  │  (Spring Data JPA)      │  │  ChatClient (Groq Cloud via Spring AI)        │  │
│  └────────────┬────────────┘  └────────────────────────┬──────────────────────┘  │
│               │                                         │                          │
└───────────────┼─────────────────────────────────────────┼──────────────────────────┘
                │                                         │
  ┌─────────────▼──────────────┐          ┌──────────────▼────────────────────────┐
  │      PostgreSQL             │          │           Cloud APIs                  │
  │  ┌────────────────────┐    │          │  ┌──────────────┐  ┌───────────────┐  │
  │  │  documents table   │    │          │  │  Groq Cloud  │  │   Jina AI     │  │
  │  │  document_chunks   │    │          │  │  (LLM)       │  │  (Embeddings) │  │
  │  │  vector_store      │    │          │  └──────────────┘  └───────────────┘  │
  │  │  (pgvector)        │    │          └───────────────────────────────────────┘
  │  └────────────────────┘    │
  └────────────────────────────┘
```

---

## 3. Deployment Architecture (Docker Compose)

All three services are containerised and orchestrated by Docker Compose. They share a private Docker bridge network.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           docker-compose.yml                             │
│                         (Docker Bridge Network)                          │
│                                                                          │
│  ┌─────────────────────┐   ┌───────────────────────┐   ┌─────────────┐  │
│  │   frontend          │   │      backend           │   │  postgres   │  │
│  │  (Nginx + React)    │   │  (Spring Boot JAR)     │   │  + pgvector │  │
│  │                     │   │                        │   │             │  │
│  │  Port: 3000 (host)  │   │  Port: 8080 (host)     │   │  Port: 5432 │  │
│  │  → :80 (container)  │   │  → :8080 (container)   │   │  (internal) │  │
│  │                     │   │                        │   │             │  │
│  │  Dockerfile.frontend│   │  Dockerfile            │   │  Image:     │  │
│  │  (multi-stage build)│   │  (multi-stage build)   │   │  pgvector/  │  │
│  │                     │   │                        │   │  pgvector   │  │
│  │  depends_on:backend │   │  depends_on: postgres  │   │             │  │
│  └─────────────────────┘   └───────────────────────┘   └─────────────┘  │
│                                                                          │
│  Volumes:                                                                │
│    postgres_data → /var/lib/postgresql/data  (persistent DB)            │
│    ./uploads → /app/uploads                  (raw uploaded files)       │
│                                                                          │
│  Environment Variables (from .env):                                     │
│    GROQ_API_KEY, JINA_API_KEY, DB_PASSWORD                              │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                          Host Machine (localhost)
                          :3000  → React UI
                          :8080  → Spring Boot API
```

---

## 4. Primary Data Flows

### Flow A — Document Upload & Ingestion

```
 User                  Frontend             Backend               External Services
  │                       │                    │                         │
  │  Drag & drop file     │                    │                         │
  │──────────────────────▶│                    │                         │
  │                       │  POST /api/documents/upload (multipart)      │
  │                       │───────────────────▶│                         │
  │                       │                    │  Save file to ./uploads/│
  │                       │                    │─────────┐               │
  │                       │                    │         │               │
  │                       │                    │  Parse text (Apache Tika)
  │                       │                    │─────────┐               │
  │                       │                    │         │               │
  │                       │                    │  Chunk text (500 tok)   │
  │                       │                    │─────────┐               │
  │                       │                    │         │  POST /v1/embeddings
  │                       │                    │─────────┼──────────────▶│ Jina AI
  │                       │                    │         │  1024-dim vecs│
  │                       │                    │◀────────┼───────────────│
  │                       │                    │  Store vectors (pgvector)
  │                       │                    │─────────┐               │
  │                       │                    │  Mark doc READY         │
  │                       │                    │─────────┐               │
  │                       │  200 DocumentResponse        │               │
  │                       │◀───────────────────│         │               │
  │  Document appears in list                  │         │               │
  │◀──────────────────────│                    │         │               │
```

### Flow B — Chat / RAG Query

```
 User                  Frontend             Backend               External Services
  │                       │                    │                         │
  │  Type question        │                    │                         │
  │──────────────────────▶│                    │                         │
  │                       │  POST /api/chat    │                         │
  │                       │  { question, docId}│                         │
  │                       │───────────────────▶│                         │
  │                       │                    │  POST /v1/embeddings    │
  │                       │                    │─────────────────────────▶ Jina AI
  │                       │                    │  question vector        │
  │                       │                    │◀─────────────────────────│
  │                       │                    │  pgvector cosine search  │
  │                       │                    │  (top-5 chunks by docId)│
  │                       │                    │─────────┐               │
  │                       │                    │  Build RAG prompt       │
  │                       │                    │─────────┐               │
  │                       │                    │  POST /openai/v1/chat/completions
  │                       │                    │─────────────────────────▶ Groq LLM
  │                       │                    │  Generated answer       │
  │                       │                    │◀─────────────────────────│
  │                       │                    │  Attach citations       │
  │                       │  ChatResponse      │  (cross-ref vector IDs) │
  │                       │◀───────────────────│                         │
  │  Answer + citations   │                    │                         │
  │◀──────────────────────│                    │                         │
```

---

## 5. Technology Stack Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                         │
│  React 18 + Vite   │  Tailwind CSS   │  react-dropzone         │
│  react-markdown    │  Axios          │  Nginx (Docker serve)   │
├─────────────────────────────────────────────────────────────────┤
│                      APPLICATION LAYER                          │
│  Spring Boot 3     │  Java 21        │  Spring AI 1.0          │
│  Spring Data JPA   │  Spring Web     │  Apache Tika            │
├─────────────────────────────────────────────────────────────────┤
│                        DATA LAYER                               │
│  PostgreSQL 16     │  pgvector ext   │  Local File System      │
├─────────────────────────────────────────────────────────────────┤
│                   EXTERNAL AI SERVICES                          │
│  Groq Cloud (LLM)  │  Jina AI (Embeddings)                     │
│  llama-3.1-8b-instant  │  jina-embeddings-v3 (1024-dim)        │
├─────────────────────────────────────────────────────────────────┤
│                   INFRASTRUCTURE / DEVOPS                       │
│  Docker            │  Docker Compose │  Multi-stage Dockerfiles│
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Non-Functional Characteristics

| Concern | Approach |
|---------|----------|
| **Scalability** | Stateless backend; DB and file system can be externalised for horizontal scaling |
| **Latency** | Groq LPU hardware provides fast LLM inference; pgvector HNSW index for fast ANN search |
| **Reliability** | Document status lifecycle (UPLOADING → PROCESSING → READY / FAILED) prevents serving corrupt data |
| **Maintainability** | Clean 3-layer architecture; DTOs decouple API from DB schema |
| **Security** | CORS restricted to frontend origin; API keys stored in `.env`, not in source code |
| **Portability** | Docker Compose ensures identical environment on any machine; cloud APIs work anywhere |
| **Extensibility** | Spring AI abstraction allows swapping LLM/embedding providers with config-only changes |
