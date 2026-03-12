# 2. RAG — Retrieval-Augmented Generation

## What Is RAG?

RAG is a technique that combines **information retrieval** with **language model generation**.

Instead of relying on the LLM's memorised training data, RAG:

1. Retrieves relevant documents/passages at query time
2. Injects them as context into the prompt
3. Asks the LLM to generate an answer based on that context

The result: the LLM always answers from **your actual documents**, not from fuzzy memory.

---

## Why Not Just Fine-Tune?

| Approach       | Cost       | Update speed | Transparency | Hallucination risk |
|----------------|------------|--------------|--------------|-------------------|
| Fine-tuning    | Very high  | Days/weeks   | Low          | High              |
| RAG            | Low        | Instant      | High         | Low               |

Fine-tuning bakes knowledge into the model weights — expensive, slow, opaque.
RAG keeps knowledge external — cheap, updatable, and each answer is traceable to a source.

---

## The RAG Pipeline (in this project)

```
User question: "What skills are listed in this resume?"
         │
         ▼
┌─────────────────────┐
│  Embed the question  │  ← Jina AI (jina-embeddings-v3)
│  → vector [0.12, ...]│     1024-dimensional vector
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  pgvector similarity │  ← cosine distance search
│  search (top-5)      │  ← filtered to document ID
└─────────┬───────────┘
          │
          ▼  Chunks with highest similarity scores:
          │   "Python, Java, React, Spring Boot..."
          │   "Led a team of 5 engineers..."
          │   "B.Sc. Computer Science, 2021..."
          │
          ▼
┌─────────────────────┐
│   Build RAG prompt   │
│   with context       │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│   Groq Cloud LLM     │  ← llama-3.1-8b-instant
│   generates answer   │
└─────────┬───────────┘
          │
          ▼
 Answer: "The resume lists the following skills:
          Python, Java, React, Spring Boot..."
 Citations: [chunk 3, chunk 7]
```

---

## Why Cosine Similarity?

An embedding is a list of numbers (e.g., 1024 floats) representing the **meaning** of a text.
Two texts with similar meanings have embeddings that point in the **same direction** in vector space.

Cosine similarity measures the angle between two vectors:
- 1.0 = identical direction (same meaning)
- 0.0 = perpendicular (unrelated)
- -1.0 = opposite directions (opposite meaning)

We use cosine because document length doesn't affect direction — only meaning matters.

---

## Key Interview Points

- RAG separates **retrieval** (vector search) from **generation** (LLM)
- Embeddings encode semantic meaning, not just keywords
- The LLM never needs to "know" the document — it reasons over retrieved text
- Citations are possible because we track which chunks were used
- Filtering by `documentId` ensures answers come from the selected document only
