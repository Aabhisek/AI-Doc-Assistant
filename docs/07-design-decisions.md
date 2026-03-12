# 7. System Design Decisions

## Why Spring Boot?

- Industry standard for Java backend services
- Excellent ecosystem (JPA, Security, Validation, Testing)
- Spring AI is a first-class Spring project — natural integration
- Production-ready features out of the box (health checks, metrics, actuator)

## Why Java 21?

Java 21 is an LTS release with meaningful improvements:
- Virtual threads (Project Loom) — simplifies async code
- Records — immutable value objects with less boilerplate
- Pattern matching — cleaner instanceof checks
- Sequenced collections — deterministic ordering

## Why Groq + Jina AI Instead of Ollama?

| Criterion       | Ollama (local)              | Groq + Jina AI (cloud)         |
|-----------------|-----------------------------|--------------------------------|
| Cost            | Free (but needs hardware)   | Free tier — no credit card     |
| Setup           | Install Ollama + pull models | API key from website           |
| Latency         | Slow on CPU, fast on GPU    | Very fast (Groq LPU hardware)  |
| Model quality   | Good (llama3, mistral)      | Same open-source models        |
| Offline support | Yes                         | No (requires internet)         |
| Portability     | Machine-specific            | Works anywhere (incl. Docker)  |

For a portfolio / demo project: Groq's free tier provides fast inference without requiring dedicated hardware. Jina AI's free embedding API removes the need to run a local embedding server.

Both services use the OpenAI-compatible API format, so Spring AI's `spring-ai-starter-model-openai` dependency handles both with minimal configuration.

## Why PostgreSQL for Vectors Instead of Pinecone/Qdrant?

Dedicated vector databases offer better performance at very large scale (billions of vectors). However:

- PostgreSQL + pgvector handles millions of vectors well
- No extra infrastructure to manage
- Full SQL power for relational + vector hybrid queries
- Easier for interviews to explain ("it's just Postgres")
- For this project's scale, pgvector is the right choice

## Why Not Add Authentication?

Authentication (JWT, OAuth) adds complexity without demonstrating RAG or AI capabilities. The portfolio value is in the AI architecture, not auth flows. If needed, Spring Security + JWT can be added in an afternoon.

## Why Clean Architecture (Controller → Service → Repository)?

```
Controller     — HTTP concerns only. No business logic.
Service        — Business logic. No HTTP, no SQL.
Repository     — Database concerns only. No business logic.
```

This separation:
- Makes each layer independently testable
- Makes the code self-documenting (clear responsibilities)
- Allows swapping implementations (e.g., different DB) without touching business logic

## Why DTOs Instead of Exposing Entities Directly?

Exposing JPA entities as API responses:
- Leaks internal DB structure to clients
- Breaks API when DB schema changes
- Can trigger N+1 lazy loading in JSON serialisation
- Allows clients to send data that modifies entity relationships

DTOs (Data Transfer Objects) are clean API contracts independent of the DB schema.

## Why Simple Prompt Parsing Instead of JSON Schema?

Structured JSON output from LLMs requires careful prompt engineering and can break with small model version changes. Simple section-based parsing (searching for "SUMMARY:", "KEY POINTS:") is:
- Easier to debug
- More tolerant of model variations
- Sufficient for a portfolio project

Production systems should use `BeanOutputConverter` for type-safe structured output.

## Chunking Strategy

`TokenTextSplitter` with 500 token chunks and 50 token overlap was chosen because:
- 500 tokens ≈ 375 words — enough for meaningful context
- Small enough to keep retrieval precise (not too much irrelevant text)
- 10% overlap prevents answers from being split across chunk boundaries

For resume/CV documents: shorter chunks (200-300 tokens) work better.
For legal documents: longer chunks (800-1000 tokens) preserve more legal context.
