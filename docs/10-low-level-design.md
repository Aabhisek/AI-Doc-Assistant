# 10. Low-Level Design (LLD)

## Purpose

The Low-Level Design describes the **internal structure** of the AI Document Assistant. It covers:

- Backend class relationships (controllers, services, repositories, models, DTOs)
- Database schema and Entity-Relationship diagram
- Full API endpoint catalog with request/response contracts
- Sequence diagrams for every major operation
- Frontend component tree and data flow
- Configuration details and error handling flow

---

## 1. Backend Class Diagram

### 1.1 Controllers (HTTP Layer)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              Controller Layer                                    │
│                           (@RestController, @RequestMapping)                     │
│                                                                                  │
│  ┌─────────────────────────┐   ┌─────────────────────────┐                      │
│  │   DocumentController    │   │     ChatController       │                      │
│  │  /api/documents         │   │  /api/chat               │                      │
│  ├─────────────────────────┤   ├─────────────────────────┤                      │
│  │ + upload(file): DocResp │   │ + chat(req): ChatResp   │                      │
│  │ + listAll(): List<Doc>  │   │                         │                      │
│  │ + getById(id): DocResp  │   │  ──────────────────────▶ RagService            │
│  │ + delete(id): void      │   └─────────────────────────┘                      │
│  │                         │                                                     │
│  │  ─────────────────────▶ DocumentService                                       │
│  └─────────────────────────┘                                                    │
│                                                                                  │
│  ┌─────────────────────────┐   ┌─────────────────────────┐                      │
│  │   SummaryController     │   │  ExtractionController   │                      │
│  │  /api/summary           │   │  /api/extract           │                      │
│  ├─────────────────────────┤   ├─────────────────────────┤                      │
│  │ + summarize(id):SumResp │   │ + extract(req): ExtResp │                      │
│  │                         │   │                         │                      │
│  │  ─────────────────────▶ SummaryService                │                      │
│  └─────────────────────────┘   │  ─────────────────────▶ ExtractionService      │
│                                └─────────────────────────┘                      │
│                                                                                  │
│  ┌─────────────────────────┐                                                     │
│  │  ComparisonController   │                                                     │
│  │  /api/compare           │                                                     │
│  ├─────────────────────────┤                                                     │
│  │ + compare(req): CmpResp │                                                     │
│  │  ─────────────────────▶ ComparisonService                                     │
│  └─────────────────────────┘                                                     │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Services (Business Logic Layer)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                               Service Layer (@Service)                           │
│                                                                                  │
│  ┌──────────────────────────────────┐                                            │
│  │         DocumentService          │                                            │
│  ├──────────────────────────────────┤                                            │
│  │ - documentRepository             │                                            │
│  │ - processingService              │                                            │
│  │ - uploadDir: Path                │                                            │
│  ├──────────────────────────────────┤                                            │
│  │ + upload(file): DocumentResponse │──────────────────────────────────────────▶ │
│  │ + findAll(): List<DocResponse>   │     DocumentProcessingService              │
│  │ + findById(id): DocResponse      │  ┌────────────────────────────────────────┐│
│  │ + delete(id): void               │  │  DocumentProcessingService             ││
│  └──────────────────────────────────┘  ├────────────────────────────────────────┤│
│                                        │ - vectorStore: VectorStore             ││
│                                        │ - chunkRepository                      ││
│                                        │ - documentRepository                   ││
│                                        ├────────────────────────────────────────┤│
│                                        │ + ingest(document, filePath): void     ││
│                                        │   1. TikaDocumentReader.get()          ││
│                                        │   2. TokenTextSplitter.apply()         ││
│                                        │   3. vectorStore.add(chunks)           ││
│                                        │   4. chunkRepository.saveAll()         ││
│                                        │   5. doc.setStatus(READY)              ││
│                                        └────────────────────────────────────────┘│
│                                                                                  │
│  ┌──────────────────────────────────┐                                            │
│  │           RagService             │                                            │
│  ├──────────────────────────────────┤                                            │
│  │ - vectorStore: VectorStore       │                                            │
│  │ - chatClient: ChatClient         │                                            │
│  │ - chunkRepository                │                                            │
│  │ - TOP_K = 5                      │                                            │
│  ├──────────────────────────────────┤                                            │
│  │ + answer(question, docId)        │                                            │
│  │   : ChatResponse                 │                                            │
│  │   1. vectorStore.similarity      │                                            │
│  │      Search(query, filter, top5) │                                            │
│  │   2. buildContext(chunks)        │                                            │
│  │   3. chatClient.prompt(rag_prompt│                                            │
│  │   4. resolveCitations(chunkIds)  │                                            │
│  └──────────────────────────────────┘                                            │
│                                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────────┐   │
│  │  SummaryService  │  │ExtractionService │  │    ComparisonService         │   │
│  ├──────────────────┤  ├──────────────────┤  ├──────────────────────────────┤   │
│  │ - vectorStore    │  │ - vectorStore    │  │ - vectorStore                │   │
│  │ - chatClient     │  │ - chatClient     │  │ - chatClient                 │   │
│  ├──────────────────┤  ├──────────────────┤  ├──────────────────────────────┤   │
│  │ + summarize(id)  │  │ + extract(req)   │  │ + compare(docId1, docId2)    │   │
│  │  :SummaryResponse│  │  :ExtrResponse   │  │  :ComparisonResponse         │   │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Repositories (Data Access Layer)

```
┌──────────────────────────────────────────────────────────────────────┐
│                     Repository Layer (Spring Data JPA)               │
│                                                                      │
│  ┌────────────────────────────────────────┐                          │
│  │  DocumentRepository                    │                          │
│  │  extends JpaRepository<Document, Long> │                          │
│  ├────────────────────────────────────────┤                          │
│  │ + findAll(): List<Document>            │   (inherited from JPA)   │
│  │ + findById(id): Optional<Document>     │                          │
│  │ + save(doc): Document                  │                          │
│  │ + delete(doc): void                    │                          │
│  └────────────────────────────────────────┘                          │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  DocumentChunkRepository                                       │  │
│  │  extends JpaRepository<DocumentChunk, Long>                    │  │
│  ├────────────────────────────────────────────────────────────────┤  │
│  │ + findByDocumentId(docId): List<DocumentChunk>                 │  │
│  │ + findByVectorStoreIdIn(ids): List<DocumentChunk>              │  │
│  │ + deleteByDocumentId(docId): void                              │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.4 JPA Entities (Domain Model)

```
┌──────────────────────────────────┐     ┌──────────────────────────────────────┐
│  Document (@Entity)              │     │  DocumentChunk (@Entity)             │
│  table: documents                │     │  table: document_chunks              │
├──────────────────────────────────┤     ├──────────────────────────────────────┤
│ id: Long (PK, auto)              │1    │ id: Long (PK, auto)                  │
│ fileName: String                 │────▶│ documentId: Long (FK → documents.id) │
│ originalFileName: String         │  N  │ content: String                      │
│ fileType: String (pdf/docx/txt)  │     │ chunkIndex: Integer                  │
│ fileSize: Long (bytes)           │     │ vectorStoreId: String (UUID)         │
│ filePath: String                 │     └──────────────────────────────────────┘
│ status: DocumentStatus (enum)    │
│   UPLOADING | PROCESSING         │
│   READY | FAILED                 │
│ uploadedAt: LocalDateTime        │
│ processedAt: LocalDateTime       │
│ errorMessage: String (nullable)  │
└──────────────────────────────────┘
```

### 1.5 DTOs

```
  REQUEST DTOs                          RESPONSE DTOs
  ────────────────                      ─────────────────

  ChatRequest                           ChatResponse
  ├── question: String                  ├── answer: String
  ├── documentId: Long                  └── citations: List<CitationResponse>
  └── conversationId: String (opt)
                                        CitationResponse
  ExtractionRequest                     ├── chunkId: Long
  ├── documentId: Long                  ├── content: String
  └── fields: List<String>              └── similarityScore: Double

  CompareRequest                        DocumentResponse
  ├── documentId1: Long                 ├── id: Long
  └── documentId2: Long                 ├── originalFileName: String
                                        ├── fileType: String
                                        ├── fileSize: Long
                                        ├── status: String
                                        └── uploadedAt: String

                                        SummaryResponse
                                        ├── summary: String
                                        └── keyPoints: List<String>

                                        ExtractionResponse
                                        └── fields: Map<String, String>

                                        ComparisonResponse
                                        ├── similarities: List<String>
                                        ├── differences: List<String>
                                        └── recommendation: String
```

---

## 2. Database Schema (ER Diagram)

```
┌──────────────────────────────────────────────┐
│                  documents                    │
├─────────────────────┬────────────────────────┤
│ id                  │ BIGSERIAL PRIMARY KEY  │
│ file_name           │ VARCHAR(255) NOT NULL  │  ← UUID-prefixed stored filename
│ original_file_name  │ VARCHAR(255) NOT NULL  │  ← Original name shown to user
│ file_type           │ VARCHAR(50)            │
│ file_size           │ BIGINT                 │
│ file_path           │ TEXT                   │
│ status              │ VARCHAR(20)            │  ← UPLOADING|PROCESSING|READY|FAILED
│ uploaded_at         │ TIMESTAMP              │
│ processed_at        │ TIMESTAMP              │
│ error_message       │ TEXT                   │
└─────────────────────┴────────────────────────┘
                         │
                         │ 1 : N
                         ▼
┌──────────────────────────────────────────────┐
│               document_chunks                 │
├─────────────────────┬────────────────────────┤
│ id                  │ BIGSERIAL PRIMARY KEY  │
│ document_id         │ BIGINT FK → documents  │
│ content             │ TEXT NOT NULL          │  ← Raw text of the chunk
│ chunk_index         │ INTEGER                │  ← Position in document
│ vector_store_id     │ VARCHAR(255)           │  ← UUID linking to vector_store
└─────────────────────┴────────────────────────┘
                         │
                         │ 1 : 1  (vector_store_id → id)
                         ▼
┌──────────────────────────────────────────────┐
│           vector_store  (pgvector)            │
├─────────────────────┬────────────────────────┤
│ id                  │ UUID PRIMARY KEY       │
│ content             │ TEXT                   │
│ metadata            │ JSON                   │  ← { "documentId": "42" }
│ embedding           │ VECTOR(1024)           │  ← 1024-dim Jina embedding
└─────────────────────┴────────────────────────┘
```

**Index on vector_store:**
```sql
-- HNSW index for fast approximate nearest-neighbour search
CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);
```

---

## 3. API Endpoint Catalog

### Document Endpoints

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `POST` | `/api/documents/upload` | Upload & ingest a file | `multipart/form-data` → `file` | `201 DocumentResponse` |
| `GET` | `/api/documents` | List all documents | — | `200 List<DocumentResponse>` |
| `GET` | `/api/documents/{id}` | Get single document | path `id` | `200 DocumentResponse` |
| `DELETE` | `/api/documents/{id}` | Delete document + chunks | path `id` | `204 No Content` |

### AI Feature Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `/api/chat` | RAG query against a document | `ChatRequest` | `200 ChatResponse` |
| `POST` | `/api/summary/{id}` | Summarise a document | path `id` | `200 SummaryResponse` |
| `POST` | `/api/extract` | Extract structured fields | `ExtractionRequest` | `200 ExtractionResponse` |
| `POST` | `/api/compare` | Compare two documents | `CompareRequest` | `200 ComparisonResponse` |

### Request / Response Shapes

```
POST /api/documents/upload
  Content-Type: multipart/form-data
  Body: file=<binary>

  Response 201:
  {
    "id": 1,
    "originalFileName": "resume.pdf",
    "fileType": "pdf",
    "fileSize": 102400,
    "status": "READY",
    "uploadedAt": "2025-06-01T10:30:00"
  }

──────────────────────────────────────────────────────

POST /api/chat
  Content-Type: application/json
  Body:
  {
    "question": "What are Alice's technical skills?",
    "documentId": 1
  }

  Response 200:
  {
    "answer": "Alice's technical skills include Python, Java, Spring Boot...",
    "citations": [
      {
        "chunkId": 42,
        "content": "Technical Skills: Python, Java, Spring Boot, React...",
        "similarityScore": 0.92
      }
    ]
  }

──────────────────────────────────────────────────────

POST /api/summary/1
  Response 200:
  {
    "summary": "This document is a resume for Alice Chen, a senior engineer...",
    "keyPoints": [
      "10 years of experience in distributed systems",
      "Led a team of 5 engineers at TechCorp",
      "B.Sc. Computer Science, 2021"
    ]
  }

──────────────────────────────────────────────────────

POST /api/extract
  Body:
  {
    "documentId": 1,
    "fields": ["name", "email", "skills", "experience"]
  }

  Response 200:
  {
    "fields": {
      "name": "Alice Chen",
      "email": "alice@example.com",
      "skills": "Python, Java, React, Spring Boot",
      "experience": "10 years"
    }
  }

──────────────────────────────────────────────────────

POST /api/compare
  Body:
  { "documentId1": 1, "documentId2": 2 }

  Response 200:
  {
    "similarities": ["Both candidates have Java experience", ...],
    "differences": ["Doc1 has 10 yrs experience, Doc2 has 3 yrs", ...],
    "recommendation": "Document 1 is a stronger match for a senior role..."
  }
```

### Error Responses

```
404 Not Found:
{
  "status": 404,
  "error": "Not Found",
  "message": "Document with id 99 not found",
  "path": "/api/documents/99"
}

400 Bad Request:
{
  "status": 400,
  "error": "Bad Request",
  "message": "Unsupported file type: .exe",
  "path": "/api/documents/upload"
}

500 Internal Server Error:
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "Document processing failed: Tika parse error",
  "path": "/api/documents/upload"
}
```

---

## 4. Sequence Diagrams

### 4.1 Document Upload & Processing

```
  React UI          DocumentController    DocumentService    DocProcessingService    Jina AI     pgvector     DB
     │                     │                    │                    │                  │            │          │
     │  POST /upload       │                    │                    │                  │            │          │
     │────────────────────▶│                    │                    │                  │            │          │
     │                     │  upload(file)      │                    │                  │            │          │
     │                     │───────────────────▶│                    │                  │            │          │
     │                     │                    │ save(doc, UPLOADING)                  │            │          │
     │                     │                    │────────────────────────────────────────────────────────────▶│
     │                     │                    │ write file to ./uploads/              │            │          │
     │                     │                    │───────────────────▶│                  │            │          │
     │                     │                    │ ingest(doc, path)  │                  │            │          │
     │                     │                    │ doc.status=PROCESSING                │            │          │
     │                     │                    │────────────────────────────────────────────────────────────▶│
     │                     │                    │                    │ TikaReader.get() │            │          │
     │                     │                    │                    │────────────────▶ (parse text)│          │
     │                     │                    │                    │◀────────────────│            │          │
     │                     │                    │                    │ TokenTextSplitter.apply()    │          │
     │                     │                    │                    │──────────────────┐           │          │
     │                     │                    │                    │ for each chunk:  │           │          │
     │                     │                    │                    │  POST /v1/embeddings         │          │
     │                     │                    │                    │─────────────────────────────▶│          │
     │                     │                    │                    │  1024-dim vector │           │          │
     │                     │                    │                    │◀─────────────────────────────│          │
     │                     │                    │                    │  vectorStore.add(chunk+vec)  │          │
     │                     │                    │                    │──────────────────────────────────────▶│ │
     │                     │                    │                    │  chunkRepo.saveAll()         │          │
     │                     │                    │                    │────────────────────────────────────────▶│
     │                     │                    │                    │ doc.status=READY │            │          │
     │                     │                    │                    │────────────────────────────────────────▶│
     │                     │  DocumentResponse  │                    │                  │            │          │
     │◀────────────────────│◀───────────────────│                    │                  │            │          │
```

### 4.2 Chat / RAG Query

```
  React UI       ChatController    RagService      pgvector      Jina AI     Groq LLM      ChunkRepo
     │                │                │               │             │            │              │
     │ POST /api/chat │                │               │             │            │              │
     │───────────────▶│                │               │             │            │              │
     │                │ answer(q, id)  │               │             │            │              │
     │                │───────────────▶│               │             │            │              │
     │                │                │ embed(question)             │            │              │
     │                │                │────────────────────────────▶│            │              │
     │                │                │ question vector             │            │              │
     │                │                │◀────────────────────────────│            │              │
     │                │                │ similaritySearch(vec, docId, topK=5)     │              │
     │                │                │──────────────▶│             │            │              │
     │                │                │ top 5 chunks  │             │            │              │
     │                │                │◀──────────────│             │            │              │
     │                │                │ buildContext(chunks)        │            │              │
     │                │                │────────────────┐            │            │              │
     │                │                │ buildRagPrompt(ctx, q)      │            │              │
     │                │                │────────────────┐            │            │              │
     │                │                │ chatClient.call(prompt)     │            │              │
     │                │                │────────────────────────────────────────▶│              │
     │                │                │ LLM response   │            │            │              │
     │                │                │◀────────────────────────────────────────│              │
     │                │                │ findByVectorStoreIdIn(chunkIds)          │              │
     │                │                │────────────────────────────────────────────────────────▶│
     │                │                │ citation details            │            │              │
     │                │                │◀────────────────────────────────────────────────────────│
     │                │ ChatResponse   │               │             │            │              │
     │◀───────────────│◀───────────────│               │             │            │              │
```

### 4.3 Document Deletion

```
  React UI     DocumentController    DocumentService    ChunkRepo    VectorStore    DocRepo    FileSystem
     │                │                    │                │             │             │            │
     │ DELETE /api/   │                    │                │             │             │            │
     │ documents/{id} │                    │                │             │             │            │
     │───────────────▶│                    │                │             │             │            │
     │                │ delete(id)         │                │             │             │            │
     │                │───────────────────▶│                │             │             │            │
     │                │                    │ findById(id)   │             │             │            │
     │                │                    │────────────────────────────────────────────────────────▶│
     │                │                    │ chunks = findByDocumentId   │             │            │
     │                │                    │────────────────▶│            │             │            │
     │                │                    │ vectorStore.delete(vecIds)  │             │            │
     │                │                    │──────────────────────────────────────────▶│            │
     │                │                    │ chunkRepo.deleteByDocId     │             │            │
     │                │                    │────────────────▶│            │             │            │
     │                │                    │ docRepo.delete(doc)         │             │            │
     │                │                    │────────────────────────────────────────────────────────▶│
     │                │                    │ Files.delete(filePath)      │             │            │
     │                │                    │────────────────────────────────────────────────────────▶│
     │                │  204 No Content    │                │             │             │            │
     │◀───────────────│◀───────────────────│                │             │             │            │
```

### 4.4 Document Summarisation

```
  React UI     SummaryController    SummaryService    VectorStore    Groq LLM
     │                │                   │                │             │
     │ POST /api/     │                   │                │             │
     │ summary/{id}   │                   │                │             │
     │───────────────▶│                   │                │             │
     │                │ summarize(id)     │                │             │
     │                │──────────────────▶│                │             │
     │                │                   │ similaritySearch            │
     │                │                   │ ("summarize all content",   │
     │                │                   │  docId, topK=10)            │
     │                │                   │───────────────▶│             │
     │                │                   │ top chunks     │             │
     │                │                   │◀───────────────│             │
     │                │                   │ buildSummaryPrompt(chunks)  │
     │                │                   │─────────────────┐           │
     │                │                   │ chatClient.call(prompt)     │
     │                │                   │────────────────────────────▶│
     │                │                   │ summary + key points        │
     │                │                   │◀────────────────────────────│
     │                │                   │ parseSummaryResponse()      │
     │                │                   │─────────────────┐           │
     │                │  SummaryResponse  │                 │           │
     │◀───────────────│◀──────────────────│                 │           │
```

---

## 5. Configuration Classes

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        Configuration Layer (@Configuration)                │
│                                                                            │
│  ┌──────────────────────────┐                                              │
│  │       AiConfig           │                                              │
│  ├──────────────────────────┤                                              │
│  │ @Bean ChatClient         │  ← Groq Cloud endpoint + model + timeout     │
│  │   model: llama-3.1-8b   │                                              │
│  │   baseUrl: api.groq.com  │                                              │
│  │   readTimeout: 60s       │                                              │
│  └──────────────────────────┘                                              │
│                                                                            │
│  ┌──────────────────────────┐                                              │
│  │     EmbeddingConfig      │                                              │
│  ├──────────────────────────┤                                              │
│  │ @Bean EmbeddingModel     │  ← Jina AI (OpenAI-compatible endpoint)      │
│  │   model: jina-embed-v3   │                                              │
│  │   baseUrl: api.jina.ai   │                                              │
│  │   dimensions: 1024       │                                              │
│  └──────────────────────────┘                                              │
│                                                                            │
│  ┌──────────────────────────┐                                              │
│  │    VectorStoreConfig     │                                              │
│  ├──────────────────────────┤                                              │
│  │ @Bean PgVectorStore      │  ← Schema auto-init, cosine distance         │
│  │   initializeSchema: true │                                              │
│  │   distanceType: COSINE   │                                              │
│  │   dimensions: 1024       │                                              │
│  └──────────────────────────┘                                              │
│                                                                            │
│  ┌──────────────────────────┐                                              │
│  │       WebConfig          │                                              │
│  ├──────────────────────────┤                                              │
│  │ @Bean CORS               │  ← Allow http://localhost:3000               │
│  │   origins: localhost:3000│    Methods: GET, POST, DELETE                │
│  │   allowedHeaders: *      │                                              │
│  └──────────────────────────┘                                              │
└────────────────────────────────────────────────────────────────────────────┘
```

**application.properties key settings:**

```properties
# Disable conflicting auto-configured beans
spring.ai.openai.embedding.enabled=false
spring.ai.openai.chat.enabled=false

# File upload limits
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# Chunking constants
app.chunking.size=500
app.chunking.overlap=50
app.upload.dir=./uploads
```

---

## 6. Frontend Component Tree

```
App.jsx  (root — manages global state)
│
│  State:
│    documents[]          ← all uploaded documents
│    selectedDocumentId   ← currently selected document
│    chatMessages[]       ← chat history
│    activeTab            ← 'chat' | 'summary' | 'extract' | 'compare'
│    resultPanel          ← summary/extraction/comparison result
│
├── DocumentUpload.jsx
│     Props: onUpload(file)
│     Uses: react-dropzone
│     On drop → calls api.uploadDocument(file) → refreshes document list
│
├── DocumentList.jsx
│     Props: documents[], selectedId, onSelect(id), onDelete(id)
│     Displays: filename, status badge, upload time, delete button
│
├── ActionBar.jsx
│     Props: selectedDocumentId, onAction(type)
│     Buttons: Summarize | Extract Fields | Compare
│     Triggers: api.summarize() | api.extract() | api.compare()
│
├── ChatInput.jsx
│     Props: onSubmit(question)
│     State: inputValue
│     On submit → calls App's handleChat() → api.chat()
│
├── ChatMessage.jsx
│     Props: message { role, content, citations }
│     Renders: ReactMarkdown for content
│     Renders: CitationCard for each citation (collapsible)
│
└── ResultPanel.jsx
      Props: result { type, data }
      type='summary'    → Renders summary paragraph + key points list
      type='extraction' → Renders key-value table
      type='compare'    → Renders similarities, differences, recommendation
```

**API service (`services/api.js`):**

```
api.js (Axios instance, baseURL: http://localhost:8080)
│
├── uploadDocument(file)              → POST /api/documents/upload
├── listDocuments()                   → GET  /api/documents
├── deleteDocument(id)                → DELETE /api/documents/{id}
├── chat(question, documentId)        → POST /api/chat
├── summarize(documentId)             → POST /api/summary/{id}
├── extractFields(documentId, fields) → POST /api/extract
└── compareDocuments(id1, id2)        → POST /api/compare
```

---

## 7. Error Handling Architecture

```
HTTP Request
     │
     ▼
Controller
     │
     ├──[throws ResourceNotFoundException]─────────────────────▶┐
     │                                                           │
     ├──[throws DocumentProcessingException]───────────────────▶│
     │                                                           │
     ├──[throws MethodArgumentNotValidException]────────────────▶│
     │                                                           ▼
     │                                           GlobalExceptionHandler
     │                                           (@RestControllerAdvice)
     │                                           │
     │                                           ├── handleNotFound() → 404
     │                                           ├── handleProcessing() → 500
     │                                           ├── handleValidation() → 400
     │                                           └── handleGeneral() → 500
     │                                                     │
     │                                                     ▼
     │                                           ErrorResponse { status, error,
     │                                                           message, path }
     ▼
Controller returns normally → 200/201/204
```

**Exception classes:**

```
Exception
└── RuntimeException
    ├── ResourceNotFoundException        ← 404 — document or chunk not found
    └── DocumentProcessingException      ← 500 — Tika parse, embedding, or DB error
```

---

## 8. Document Processing Detail (Ingest Pipeline)

```
DocumentProcessingService.ingest(document, filePath)
│
│  1. doc.setStatus(PROCESSING) → DB save
│
│  2. TikaDocumentReader(filePath).get()
│        └── Returns: List<Document> (Spring AI Document, not our entity)
│            Each Document has: text content, metadata (source, filename)
│
│  3. TokenTextSplitter(chunkSize=500, overlap=50).apply(parsedDocs)
│        └── Returns: List<Document> (smaller chunks)
│            Each chunk has: partial text, inherited metadata + chunk_index
│
│  4. Inject documentId into each chunk's metadata:
│        chunk.getMetadata().put("documentId", document.getId().toString())
│
│  5. vectorStore.add(chunks)
│        └── Spring AI:
│            a. For each chunk → calls EmbeddingModel (Jina AI) → 1024-dim vector
│            b. Inserts (id UUID, content, metadata JSON, embedding VECTOR)
│               into vector_store table via pgvector
│
│  6. Build DocumentChunk entities from chunks:
│        chunk.getId() → vectorStoreId
│        Persist via documentChunkRepository.saveAll()
│
│  7. doc.setStatus(READY)
│     doc.setProcessedAt(now())
│     documentRepository.save(doc)
│
│  [on any exception]
│     doc.setStatus(FAILED)
│     doc.setErrorMessage(e.getMessage())
│     documentRepository.save(doc)
│     throw DocumentProcessingException
```

---

## 9. RAG Prompt Templates

### Chat Prompt
```
You are a helpful assistant answering questions about a document.
Answer using ONLY the information in the provided context.
If the context does not contain enough information to answer, say so clearly.
Do not make up facts or draw on outside knowledge.

CONTEXT:
{retrieved_chunks_joined_by_double_newlines}

QUESTION: {user_question}
```

### Summary Prompt
```
You are a document summarisation assistant.
Based on the following document content, provide:
1. A concise summary (2-3 paragraphs)
2. A list of 5-7 key points

Format your response as:
SUMMARY:
[summary text]

KEY POINTS:
- [point 1]
- [point 2]
...

DOCUMENT CONTENT:
{retrieved_chunks}
```

### Extraction Prompt
```
You are a structured data extraction assistant.
Extract the following fields from the document content.
If a field is not found, respond with "Not found".

FIELDS TO EXTRACT: {field_list}

Respond in this format:
FIELD_NAME: extracted value

DOCUMENT CONTENT:
{retrieved_chunks}
```

### Comparison Prompt
```
You are a document comparison assistant.
Compare the two documents below and identify:
1. Key similarities
2. Key differences
3. A recommendation

Format your response as:
SIMILARITIES:
- [similarity 1]

DIFFERENCES:
- [difference 1]

RECOMMENDATION:
[recommendation text]

DOCUMENT 1:
{doc1_chunks}

DOCUMENT 2:
{doc2_chunks}
```
