# 5. Retrieval Pipeline

## Overview

The retrieval pipeline is the "R" in RAG. Its job is to find the most relevant text from the vector database for a given user query.

---

## Step-by-Step

### Step 1: Embed the Query

The user's question is embedded using the same model used to embed the document chunks.

**This is critical.** If you embed chunks with `jina-embeddings-v3` but embed queries with a different model, the vectors are in incompatible spaces and similarity search returns nonsense.

```java
// This happens implicitly inside VectorStore.similaritySearch()
// Spring AI calls the EmbeddingModel (Jina AI) to embed the query
```

### Step 2: Filter + Search

We search only within the selected document by filtering on `documentId` metadata.

```java
SearchRequest request = SearchRequest.builder()
    .query(question)          // user's question
    .topK(TOP_K)              // retrieve top 5 most similar chunks
    .filterExpression(filter) // WHERE documentId = ?
    .build();

List<Document> chunks = vectorStore.similaritySearch(request);
```

### Step 3: Build Context

We concatenate the retrieved chunks into a single context string, joined by double newlines:

```java
String context = relevant.stream()
        .map(Document::getText)
        .reduce("", (a, c) -> a + "\n\n" + c)
        .strip();
```

This produces a plain block like:

```
Alice Chen is a senior software engineer with 10 years of experience...

Technical Skills: Python, Java, Spring Boot, React, PostgreSQL...

Alice led a team of 5 engineers at TechCorp, shipping 3 major features...
```

No separator markers — the LLM is instructed to treat the whole block as its evidence base.

### Step 4: Prompt Construction

We use a carefully designed prompt that:
1. Defines the assistant's role
2. Provides the retrieved context
3. Asks the LLM to answer only from that context
4. Handles the "I don't know" case explicitly

```
You are a helpful assistant answering questions about a document.
Answer using ONLY the information in the provided context.
If the context does not contain enough information to answer, say so clearly.
Do not make up facts or draw on outside knowledge.

CONTEXT:
[retrieved chunks joined by double newlines]

QUESTION: What are Alice's technical skills?
```

### Step 5: Citations

After the LLM responds, we attach citations by cross-referencing the vector IDs of retrieved chunks back to our `document_chunks` table. pgvector returns a cosine *distance* in the chunk's `distance` metadata key; we convert it to a similarity score via `1.0 - distance` before surfacing it in the `CitationResponse`.

---

## Why top-k = 5?

Retrieving more chunks provides more context but:
- Increases prompt token count (slower, costlier)
- Introduces potentially irrelevant text that confuses the LLM
- 5 chunks at 500 tokens each = 2500 tokens of context — well within `llama-3.1-8b-instant`'s 128k context window

A production system would tune this based on document characteristics and LLM context window size.

---

## Similarity Score Threshold

Spring AI supports a `similarityThreshold` option on `SearchRequest`:

```java
SearchRequest.builder()
    .similarityThreshold(0.5)  // only return chunks with cosine similarity > 0.5
    ...
```

We don't use this in the basic implementation, but it's useful to avoid returning irrelevant chunks when the query is off-topic.

---

## Keyword vs. Semantic Search

| Keyword Search (BM25)     | Semantic Search (vector)        |
|---------------------------|---------------------------------|
| Matches exact words       | Matches meaning and concepts    |
| Fast, deterministic       | Slower, probabilistic           |
| Fails on synonyms         | Handles synonyms naturally      |
| "CV" ≠ "resume"           | "CV" ≈ "resume"                |

This project uses pure semantic search. A hybrid approach (BM25 + vector) generally performs better for diverse query types and is worth exploring for a production system.
