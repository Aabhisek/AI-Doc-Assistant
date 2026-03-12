# 3. Document Processing Pipeline

## Overview

When a user uploads a file, we run it through a processing pipeline before any AI queries can be performed.

```
File Upload
    │
    ▼
Save to disk
    │
    ▼
Parse text (Apache Tika)
    │
    ▼
Split into chunks (TokenTextSplitter)
    │
    ▼
Generate embeddings per chunk (Jina AI)
    │
    ▼
Store embeddings in pgvector
    │
    ▼
Save chunk metadata in PostgreSQL
    │
    ▼
Mark document as READY
```

---

## Step 1: File Storage

We save the raw file to a local directory (`./uploads/`) with a UUID prefix to avoid filename collisions.

Why save the file? We might want to re-process it later with a different chunking strategy, or allow users to download their original documents.

---

## Step 2: Text Parsing with Apache Tika

Apache Tika is a content analysis toolkit that extracts text from:
- PDF files
- DOCX, DOC files
- Excel, PowerPoint
- HTML, XML
- And hundreds more formats

In code:

```java
DocumentReader reader = new TikaDocumentReader(new FileSystemResource(filePath));
List<Document> parsedDocs = reader.get();
```

Spring AI wraps Tika in a `DocumentReader` interface — the same interface used for web pages, databases, etc. This abstraction means we can swap parsers without changing downstream code.

---

## Step 3: Text Chunking

An LLM has a context window limit (typically 2048–128k tokens). If we send the whole document, we:
1. May exceed the context window
2. Force the model to attend to irrelevant text, reducing quality

Instead, we split the document into small overlapping windows called **chunks**.

Configuration in this project:
```
CHUNK_SIZE    = 500 tokens
CHUNK_OVERLAP = 50 tokens
```

Why overlap?
If a sentence spans the boundary of two chunks, overlap ensures neither chunk loses it entirely.

Example:
```
Chunk 1: "...Alice is a senior engineer with 10 years of experience in distributed systems..."
Chunk 2: "...experience in distributed systems. She led the migration of..."
                                              ↑ overlap section
```

---

## Step 4: Embedding Generation

Each chunk is passed through the `jina-embeddings-v3` model via the Jina AI API. The model produces a **1024-dimensional** vector for each chunk.

Spring AI handles this transparently via the `VectorStore.add()` method:

```java
vectorStore.add(chunks);  // embeds each chunk and stores the vectors
```

The embedding model must be the **same** at indexing time and query time. If you switch models, you must re-embed all documents.

The `EmbeddingConfig` bean manually constructs an `OpenAiEmbeddingModel` pointed at Jina AI's root URL (`https://api.jina.ai`) — Spring AI appends `/v1/embeddings` internally. `spring.ai.openai.embedding.enabled=false` is set in `application.properties` to prevent the default auto-configured embedding bean from conflicting.

---

## Step 5: Chunk Metadata

We store chunk metadata in the relational `document_chunks` table:

| Field          | Purpose                                      |
|----------------|----------------------------------------------|
| `id`           | Primary key                                  |
| `document_id`  | Links chunk to its parent document            |
| `content`      | The actual text of the chunk                 |
| `chunk_index`  | Position in document (for ordered display)   |
| `vector_store_id` | ID in pgvector's `vector_store` table     |

The `vector_store_id` is critical: when pgvector returns search results, we use this ID to look up the original chunk text and display it as a citation.

---

## Document Status Lifecycle

```
UPLOADING → PROCESSING → READY
                      ↘ FAILED
```

The upload API call (`POST /api/documents/upload`) **blocks synchronously** until ingestion finishes — the response is only returned once the document reaches `READY` (or `FAILED`). The frontend does not need to poll; `DocumentService.upload()` calls `processingService.ingest()` directly before returning the result.
