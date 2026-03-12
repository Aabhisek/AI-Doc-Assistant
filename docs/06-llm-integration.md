# 6. LLM Integration

## Overview

This project uses two separate AI services and **Spring AI** as the abstraction layer:

| Responsibility | Service | Model |
|----------------|---------|-------|
| Chat / text generation | **Groq Cloud** | `llama-3.1-8b-instant` |
| Embedding generation | **Jina AI** | `jina-embeddings-v3` (1024-dim) |

Both services expose OpenAI-compatible REST APIs, so Spring AI's OpenAI client can talk to both without any custom HTTP code.

---

## Groq Cloud (LLM)

[Groq](https://groq.com) is a cloud inference provider that runs open-source models (LLaMA, Mixtral, Gemma) on custom LPU hardware — typically 10–100× faster than CPU inference.

Key properties:
- **Free tier** with generous rate limits — no credit card required
- **OpenAI-compatible API** at `https://api.groq.com/openai`
- Model used: `llama-3.1-8b-instant` — fast, capable, 128k context window

Spring Boot configuration:
```properties
spring.ai.openai.api-key=${GROQ_API_KEY}
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=${GROQ_CHAT_MODEL:llama-3.1-8b-instant}
spring.ai.openai.chat.options.temperature=0.3
spring.ai.openai.chat.options.max-tokens=1024
```

---

## Jina AI (Embeddings)

[Jina AI](https://jina.ai) provides high-quality embedding models with an OpenAI-compatible endpoint.

Key properties:
- **Free tier** available
- Model: `jina-embeddings-v3` — produces 1024-dimensional vectors
- API base URL: `https://api.jina.ai` (Spring AI appends `/v1/embeddings` automatically)

Because Spring AI's OpenAI autoconfiguration would create a conflicting embedding bean, we disable it and create ours manually:

```properties
# Disable Spring AI's default OpenAI embedding auto-configuration
spring.ai.openai.embedding.enabled=false

# Jina API key (loaded from .env)
jina.api-key=${JINA_API_KEY}
```

`EmbeddingConfig.java` manually builds the bean:
```java
@Bean
public EmbeddingModel embeddingModel(@Value("${jina.api-key}") String jinaApiKey) {
    var jinaApi = OpenAiApi.builder()
            .baseUrl("https://api.jina.ai")   // Jina's OpenAI-compatible root URL
            .apiKey(jinaApiKey)
            .build();
    return new OpenAiEmbeddingModel(
            jinaApi,
            MetadataMode.EMBED,
            OpenAiEmbeddingOptions.builder()
                    .model("jina-embeddings-v3")
                    .build()
    );
}
```

---

## Spring AI ChatClient

The `ChatClient` is the main interface for sending prompts and getting responses.

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder.build();
}

// Usage in services — the full prompt is passed as a single string:
String answer = chatClient.prompt(fullPromptString).call().content();
```

Spring AI translates this into a Groq API call:

```http
POST https://api.groq.com/openai/v1/chat/completions
{
  "model": "llama-3.1-8b-instant",
  "messages": [{"role": "user", "content": "..."}],
  "temperature": 0.3,
  "max_tokens": 1024
}
```

LLM calls can take up to several seconds for long documents. `AiConfig.java` sets generous timeouts on the underlying `RestClient`:
- Connect timeout: 30 seconds
- Read timeout: 180 seconds

---

## Prompt Engineering

Different tasks use different prompt templates:

### Chat (RAG)
```
You are a helpful assistant answering questions about a document.
Answer using ONLY the information in the provided context.
If the context does not contain enough information to answer, say so clearly.
Do not make up facts or draw on outside knowledge.

CONTEXT:
[top-5 chunks joined by double newlines]

QUESTION: [user question]
```

### Summary
```
Summarize the following document. Structure your response exactly as shown:

SUMMARY:
[2–4 sentence summary of the main content and purpose]

KEY POINTS:
[Bullet-point list of the 3–5 most important points, each starting with "- "]

DOCUMENT TEXT:
[document chunks joined, capped at 12 000 chars]
```
Text is capped at 12,000 characters (~3k tokens) before being sent.

### Extraction
```
From the document text below, extract all items that belong to the category: "[category]"

Rules:
- Return ONLY a bullet-point list. Start each item with "- ".
- One item per line.
- If nothing is found, write exactly: "- None found"
- Do not add any introduction, explanation, or conclusion.

DOCUMENT TEXT:
[document chunks joined, capped at 12 000 chars]
```
Preset categories offered in the UI: Key dates, Names and people, Skills and technologies, Action items, Requirements, Financial figures, Locations.

### Comparison
```
Compare the two documents below and respond using this exact format:

SIMILARITIES:
[Bullet-point list of key similarities, each starting with "- "]

DIFFERENCES:
[Bullet-point list of key differences, each starting with "- "]

DOCUMENT A — [Document A name]:
[text A, capped at 6 000 chars]

DOCUMENT B — [Document B name]:
[text B, capped at 6 000 chars]
```
Each document is capped at 6,000 characters (~1,500 tokens) before sending. The actual filenames are injected at runtime so the LLM can reference documents by name.

---

## Structured Output Parsing

We use lightweight response parsing (searching for section markers like `SUMMARY:`, `KEY POINTS:`) rather than JSON schema validation. This is intentionally simple and easy to debug.

For a production system, Spring AI's `BeanOutputConverter` can coerce LLM output directly into Java objects:

```java
var converter = new BeanOutputConverter<>(SummaryResult.class);
String format = converter.getFormat();
// include format in prompt...
SummaryResult result = converter.convert(rawResponse);
```

---

## Configuration Reference

All AI parameters are in `application.properties`:

```properties
# Groq LLM
spring.ai.openai.api-key=${GROQ_API_KEY:your-groq-api-key-here}
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=${GROQ_CHAT_MODEL:llama-3.1-8b-instant}
spring.ai.openai.chat.options.temperature=0.3
spring.ai.openai.chat.options.max-tokens=1024
spring.ai.openai.embedding.enabled=false   # Jina AI handles embeddings

# Jina AI Embeddings
jina.api-key=${JINA_API_KEY:your-jina-api-key-here}

# Long LLM call timeouts
spring.mvc.async.request-timeout=300000
server.tomcat.connection-timeout=300000
```

**Temperature**: Controls randomness. `0.3` = mostly deterministic (good for Q&A and extraction). Higher values produce more creative text.

**max-tokens**: Maximum tokens in the LLM response. `1024` is sufficient for most answers; summaries may use more.

---

## Swapping the LLM

Because Spring AI abstracts the LLM behind `ChatClient`, you can swap providers without changing any Java service code.

### Switch to a different Groq model
```properties
spring.ai.openai.chat.options.model=llama-3.3-70b-versatile
```

### Switch to OpenAI GPT
1. Keep `spring-ai-starter-model-openai` in `pom.xml` (already there)
2. Change config:
```properties
spring.ai.openai.api-key=sk-...
spring.ai.openai.base-url=https://api.openai.com
spring.ai.openai.chat.options.model=gpt-4o-mini
```

### Switch to Ollama (local/offline)
1. Replace `spring-ai-starter-model-openai` with `spring-ai-starter-model-ollama` in `pom.xml`
2. Change config:
```properties
spring.ai.ollama.chat.model=llama3.2
spring.ai.ollama.base-url=http://localhost:11434
```

No Java code changes needed in any case.
