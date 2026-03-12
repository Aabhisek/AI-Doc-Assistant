# AI Document Assistant

A portfolio project demonstrating **Retrieval-Augmented Generation (RAG)** using cloud AI services.

Upload a PDF, DOCX, or TXT file — then ask questions, generate summaries, extract structured information,
and compare documents, all powered by [Groq](https://groq.com) (LLM) and [Jina AI](https://jina.ai) (embeddings).

---

## What it does

| Feature | Description |
|---|---|
| **Document upload** | Upload PDF, DOCX, or TXT files. Text is extracted and indexed automatically. |
| **Q&A chat** | Ask natural-language questions. The AI searches for relevant sections before answering. |
| **Summarization** | Get a concise summary and key points for any indexed document. |
| **Information extraction** | Extract structured lists: dates, names, skills, action items, requirements, and more. |
| **Document comparison** | Compare two documents side-by-side for similarities and differences. |

---

## Architecture

```
Browser (React + Vite)
        │
        │  HTTP /api/*
        ▼
Spring Boot API (port 8080)
   │                    │
   │                    │ Jina AI (embeddings)
   │                    │ jina-embeddings-v3 → 1024-dim vectors
   │                    │
   │                    │ Groq Cloud (LLM)
   │                    │ llama-3.1-8b-instant → text generation
   │
   ├── PostgreSQL + pgvector  ← document metadata + vector embeddings
   └── Local disk (./uploads) ← uploaded raw files
```

### RAG pipeline (how Q&A works)

1. **Upload** — Apache Tika extracts plain text from PDF / DOCX / TXT
2. **Chunk** — Text is split into ~500-token overlapping chunks
3. **Embed** — Each chunk is embedded via `jina-embeddings-v3` and stored in pgvector (1024 dimensions)
4. **Query** — The user's question is embedded and the top-5 closest chunks are retrieved
5. **Generate** — The retrieved chunks + question are sent to `llama-3.1-8b-instant` on Groq for the final answer

---

## Tech stack

**Backend**
- Java 21 + Spring Boot 3.3
- Spring AI 1.0 (OpenAI-compatible client for Groq + Jina AI, pgvector store, Tika document reader)
- Spring Data JPA + PostgreSQL
- Lombok, dotenv-java

**Frontend**
- React 18 + Vite 6
- Tailwind CSS 3
- `react-dropzone`, `react-markdown`, `lucide-react`, `axios`

**Infrastructure**
- Groq Cloud — free-tier LLM inference (`llama-3.1-8b-instant`)
- Jina AI — free-tier embeddings (`jina-embeddings-v3`, 1024-dim, 1M tokens/month)
- PostgreSQL 16 + pgvector extension
- Docker + Docker Compose

---

## Quick start

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- A free **Groq API key** → [console.groq.com](https://console.groq.com) (no credit card)
- A free **Jina AI API key** → [jina.ai](https://jina.ai) (no credit card)

### 1. Set your API keys

Create a `.env` file in the project root:

```env
GROQ_API_KEY=your_groq_api_key_here
JINA_API_KEY=your_jina_api_key_here
```

### 2. Start the full stack

```bash
docker compose up --build
```

This starts:
- PostgreSQL with pgvector on port `5432`
- Spring Boot backend on port `8080`
- React frontend (nginx) on port `3000`

Open **http://localhost:3000** in your browser.

### 3. Development mode (hot reload)

Run each service separately for faster iteration:

```bash
# Terminal 1 — PostgreSQL only
docker compose up postgres

# Terminal 2 — Spring Boot backend
./mvnw spring-boot:run

# Terminal 3 — React frontend (Vite dev server)
cd frontend
npm install
npm run dev
```

Frontend: http://localhost:5173 (Vite proxies `/api` to port 8080)

---

## Project structure

```
AI-Doc-Assistant/
├── src/main/java/com/example/aidocassistant/
│   ├── AiDocAssistantApplication.java      ← Entry point (loads .env, starts Spring)
│   ├── config/
│   │   ├── AiConfig.java                   ← ChatClient bean + RestClient timeouts
│   │   ├── EmbeddingConfig.java            ← Jina AI EmbeddingModel bean (1024-dim)
│   │   ├── VectorStoreConfig.java          ← PgVectorStore bean (initializeSchema)
│   │   └── WebConfig.java                  ← CORS
│   ├── model/
│   │   ├── Document.java                   ← JPA entity (uploaded file, UUID PK)
│   │   └── DocumentChunk.java              ← JPA entity (text segment)
│   ├── repository/                         ← Spring Data JPA interfaces
│   ├── service/
│   │   ├── DocumentService.java            ← Upload, list, delete + file validation
│   │   ├── DocumentProcessingService.java  ← Ingestion pipeline (Tika → chunk → embed)
│   │   ├── RagService.java                 ← Core RAG Q&A pipeline
│   │   ├── SummaryService.java             ← Summarization
│   │   ├── ExtractionService.java          ← Information extraction
│   │   └── ComparisonService.java          ← Document comparison
│   ├── controller/                         ← REST endpoints
│   ├── dto/                                ← Request/response records
│   └── exception/                          ← Error handling + GlobalExceptionHandler
│
├── frontend/src/
│   ├── App.jsx                             ← Root component + state
│   ├── services/api.js                     ← Axios API client
│   └── components/
│       ├── DocumentUpload.jsx              ← Drag-and-drop upload zone
│       ├── DocumentList.jsx                ← Sidebar document list + status badges
│       ├── ChatInput.jsx                   ← Auto-resizing message input
│       ├── ChatMessage.jsx                 ← Chat bubble + collapsible citations
│       ├── ActionBar.jsx                   ← Summarize / Extract / Compare buttons
│       └── ResultPanel.jsx                 ← Summary / Extraction / Comparison cards
│
├── docs/                                   ← Architecture documentation (8 files)
├── .env                                    ← GROQ_API_KEY, JINA_API_KEY (not committed)
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```

---

## API reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/documents/upload` | Upload a document (`multipart/form-data`, field: `file`) |
| `GET` | `/api/documents` | List all documents |
| `GET` | `/api/documents/{id}` | Get document by ID |
| `DELETE` | `/api/documents/{id}` | Delete document and its embeddings |
| `POST` | `/api/chat` | Q&A: `{ documentId, question }` |
| `POST` | `/api/summary/{documentId}` | Summarize a document |
| `POST` | `/api/extract` | Extract info: `{ documentId, category }` |
| `POST` | `/api/compare` | Compare docs: `{ documentIdA, documentIdB }` |

---

## Configuration

Key settings in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `spring.ai.openai.api-key` | _(from `.env`)_ | Groq API key |
| `spring.ai.openai.base-url` | `https://api.groq.com/openai` | Groq endpoint |
| `spring.ai.openai.chat.options.model` | `llama-3.1-8b-instant` | LLM used for generation |
| `jina.api-key` | _(from `.env`)_ | Jina AI API key |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/ragdb` | PostgreSQL connection |
| `app.upload.dir` | `./uploads` | Directory for uploaded files |

All settings can be overridden via environment variables (see `docker-compose.yml`).

---

## Learning resources

The `docs/` folder contains in-depth explanations of every major concept used in this project:

- `01-system-overview.md` — How all the pieces fit together
- `02-rag-concept.md` — What RAG is and why it matters
- `03-document-processing.md` — Text extraction and chunking
- `04-vector-database.md` — How pgvector stores and searches embeddings
- `05-retrieval-pipeline.md` — The similarity search step-by-step
- `06-llm-integration.md` — Groq (LLM) and Jina AI (embeddings) integration via Spring AI
- `07-design-decisions.md` — Key architectural choices and trade-offs
- `08-scaling-considerations.md` — What would need to change at scale
