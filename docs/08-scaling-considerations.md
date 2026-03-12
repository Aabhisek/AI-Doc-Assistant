# 8. Scaling Considerations

## Current State

This project is designed for single-user or small-team use. The AI services (Groq, Jina AI) are cloud-based so there is no local model server to manage. Here's how each component would evolve for production scale.

---

## Scale: 10 Users → Production

### LLM
- **Current**: Groq Cloud (`llama-3.1-8b-instant`) — fast free tier inference
- **Production**: Switch to OpenAI GPT-4o, Anthropic Claude, or a larger Groq model via Spring AI (single config change)
- **Cost control**: Cache frequent summaries, implement rate limiting per user

### Vector Database
- **Current**: pgvector on single PostgreSQL instance
- **Production**: Read replicas for search, HNSW index tuning, consider dedicated Qdrant or Weaviate for >10M vectors
- **pgvector performance**: Handles ~1M vectors on modest hardware (16GB RAM)

### File Storage
- **Current**: Local filesystem (`./uploads/`)
- **Production**: Amazon S3 / Google Cloud Storage
- Change: `DocumentProcessingService` uploads to S3 and stores the S3 key instead of local path

### Database
- **Current**: Single PostgreSQL instance
- **Production**: Managed database (RDS, Cloud SQL) with connection pooling (HikariCP is already configured by Spring Boot)

---

## Scale: Multiple Users

### Multi-tenancy
Add a `userId` column to the `documents` table. Filter all queries by the authenticated user's ID. Vector store metadata already supports arbitrary key-value pairs — add `userId` to chunk metadata for vector-level isolation.

### Authentication
Add Spring Security + JWT:
1. `SecurityConfig` — define `/api/auth/**` as public, others require auth
2. `JwtFilter` — validate token on each request
3. Store user ID in Spring Security context
4. Services read `userId` from context, not from request params

### Asynchronous Processing
Document ingestion is CPU/IO intensive. Move it to an async pipeline:
```java
@Async
public CompletableFuture<Document> ingest(MultipartFile file) { ... }
```
Or use a message queue (RabbitMQ / Kafka) for durable processing:
```
Upload API → Message Queue → Ingestion Worker → DB + pgvector
```

---

## Scale: High Performance

### Embedding Caching
Cache embeddings for identical queries (rare but free wins):
```java
@Cacheable("queryEmbeddings")
public float[] embed(String text) { ... }
```

### Batch Embedding
Instead of embedding chunks one by one, batch them:
```java
vectorStore.add(allChunks);  // Spring AI already batches internally
```

### Hybrid Search
Combine keyword search (PostgreSQL full-text with `tsvector`) and vector search, then re-rank:
```sql
SELECT id, content,
       ts_rank(to_tsvector(content), query) AS keyword_score,
       1 - (embedding <=> $1) AS vector_score
FROM vector_store
WHERE to_tsvector(content) @@ query
   OR embedding <=> $1 < 0.5
ORDER BY (0.5 * keyword_score + 0.5 * vector_score) DESC
LIMIT 5;
```

### Streaming Responses
LLM responses can take 5-30 seconds. Use Server-Sent Events for streaming:
```java
// Spring AI supports streaming:
Flux<String> stream = chatClient.prompt().user(prompt).stream().content();
```

---

## Monitoring & Observability

- Spring Boot Actuator exposes health, metrics endpoints
- Add Micrometer → Prometheus → Grafana for dashboards
- Log each RAG query with: question, retrieved chunk IDs, model latency, token count
- Alert on: slow LLM calls (>30s), high error rates, failed ingestions

---

## Summary

This project demonstrates the **core architecture** of a production RAG system. Each component has a clear upgrade path:

| Component | Current | Production upgrade |
|-----------|---------|-------------------|
| LLM | Groq (`llama-3.1-8b-instant`) | OpenAI GPT-4o / Anthropic Claude |
| Embeddings | Jina AI (`jina-embeddings-v3`) | OpenAI `text-embedding-3-large` |
| Vectors | pgvector | Qdrant / Weaviate |
| Storage | Disk | S3 |
| Auth | None | JWT / OAuth2 |
| Processing | Synchronous | Async / Queue |
| Search | Pure vector | Hybrid BM25+vector |
