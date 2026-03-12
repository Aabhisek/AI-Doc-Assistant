# 12. Scalability — Low-Level Design (LLD)

## Purpose

This document describes the **concrete implementation details** for each scalability upgrade. For every component that changes when scaling, it shows:

- What new classes / config / infrastructure is added
- How the existing code changes
- Exact code patterns to follow

---

## 1. Asynchronous Document Ingestion

### 1.1 Phase A — Spring `@Async` (Tier 1, simplest upgrade)

**New dependency:** none (Spring's built-in `@Async`)

**New config class:**
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ingest-");
        executor.initialize();
        return executor;
    }
}
```

**Change to `DocumentProcessingService`:**
```java
// Before (synchronous — blocks the upload HTTP thread):
public void ingest(Document document, String filePath) { ... }

// After (async — upload returns immediately):
@Async("ingestionExecutor")
public CompletableFuture<Void> ingest(Document document, String filePath) {
    try {
        // ... same Tika → chunk → embed → store logic ...
        document.setStatus(DocumentStatus.READY);
    } catch (Exception e) {
        document.setStatus(DocumentStatus.FAILED);
        document.setErrorMessage(e.getMessage());
    }
    documentRepository.save(document);
    return CompletableFuture.completedFuture(null);
}
```

**Change to `DocumentController`:**
```java
// Before:
@PostMapping("/upload")
public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
    DocumentResponse response = documentService.upload(file);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);  // blocks until READY
}

// After — returns 202 Accepted immediately:
@PostMapping("/upload")
public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
    DocumentResponse response = documentService.uploadAsync(file);  // saves doc, queues ingest
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);  // status: QUEUED
}

// New polling endpoint:
@GetMapping("/{id}/status")
public ResponseEntity<DocumentResponse> getStatus(@PathVariable Long id) {
    return ResponseEntity.ok(documentService.findById(id));
}
```

---

### 1.2 Phase B — RabbitMQ Message Queue (Tier 2)

**New dependency (`pom.xml`):**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**Queue configuration:**
```java
@Configuration
public class RabbitConfig {
    public static final String UPLOAD_QUEUE        = "doc.upload.queue";
    public static final String UPLOAD_DLQ          = "doc.upload.dlq";       // dead-letter queue
    public static final String UPLOAD_EXCHANGE     = "doc.upload.exchange";

    @Bean
    public Queue uploadQueue() {
        return QueueBuilder.durable(UPLOAD_QUEUE)
            .withArgument("x-dead-letter-exchange", UPLOAD_DLQ)
            .withArgument("x-message-ttl", 3_600_000)  // 1 hour max in queue
            .build();
    }

    @Bean
    public DirectExchange uploadExchange() {
        return new DirectExchange(UPLOAD_EXCHANGE);
    }

    @Bean
    public Binding uploadBinding(Queue uploadQueue, DirectExchange uploadExchange) {
        return BindingBuilder.bind(uploadQueue).to(uploadExchange).with(UPLOAD_QUEUE);
    }
}
```

**Message payload:**
```java
public record IngestionMessage(
    Long documentId,
    String filePath,     // S3 key (Tier 1+) or local path (Tier 0)
    String fileName
) implements Serializable {}
```

**Publisher (in `DocumentService`):**
```java
@Autowired RabbitTemplate rabbitTemplate;

public DocumentResponse uploadAsync(MultipartFile file) {
    Document doc = saveToDb(file, DocumentStatus.QUEUED);
    String s3Key = s3Service.upload(file, doc.getId());
    doc.setFilePath(s3Key);
    documentRepository.save(doc);

    rabbitTemplate.convertAndSend(
        RabbitConfig.UPLOAD_EXCHANGE,
        RabbitConfig.UPLOAD_QUEUE,
        new IngestionMessage(doc.getId(), s3Key, doc.getOriginalFileName())
    );
    return DocumentMapper.toResponse(doc);
}
```

**Consumer (Ingestion Worker — separate Spring Boot app or same app with `@RabbitListener`):**
```java
@Component
public class IngestionWorker {

    @RabbitListener(queues = RabbitConfig.UPLOAD_QUEUE,
                    containerFactory = "ingestionContainerFactory")
    public void handle(IngestionMessage msg) {
        Document doc = documentRepository.findById(msg.documentId())
            .orElseThrow(() -> new ResourceNotFoundException("Document", msg.documentId()));

        doc.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(doc);

        try {
            String localPath = s3Service.downloadTemp(msg.filePath());  // pull from S3
            processingService.ingest(doc, localPath);
        } catch (Exception e) {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            documentRepository.save(doc);
            throw new AmqpRejectAndDontRequeueException("Ingestion failed: " + e.getMessage());
            // ↑ sends to dead-letter queue after max retries
        }
    }
}
```

**Dead-Letter Queue handler:**
```java
@RabbitListener(queues = RabbitConfig.UPLOAD_DLQ)
public void handleFailed(IngestionMessage msg) {
    log.error("Document {} permanently failed ingestion — alerting ops team", msg.documentId());
    // notify via Slack / PagerDuty / email
    alertService.send("Ingestion DLQ: " + msg.documentId());
}
```

---

## 2. Amazon S3 File Storage

**New dependency:**
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.0</version>
</dependency>
```

**S3 Service:**
```java
@Service
public class S3StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}") private String bucket;
    @Value("${aws.s3.region}") private String region;

    // Upload file — returns S3 key
    public String upload(MultipartFile file, Long documentId) {
        String key = "documents/" + documentId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build(),
            RequestBody.fromBytes(file.getBytes())
        );
        return key;
    }

    // Download to temp file for Tika processing
    public Path downloadTemp(String s3Key) throws IOException {
        Path tmp = Files.createTempFile("ingest-", "-" + s3Key.replace("/", "_"));
        s3Client.getObject(
            GetObjectRequest.builder().bucket(bucket).key(s3Key).build(),
            ResponseTransformer.toFile(tmp)
        );
        return tmp;
    }

    // Generate presigned URL for browser download
    public URL presignedDownloadUrl(String s3Key, Duration expires) {
        S3Presigner presigner = S3Presigner.create();
        return presigner.presignGetObject(req ->
            req.signatureDuration(expires)
               .getObjectRequest(get -> get.bucket(bucket).key(s3Key))
        ).url();
    }

    public void delete(String s3Key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(s3Key).build());
    }
}
```

**application.properties additions:**
```properties
aws.s3.bucket=${AWS_S3_BUCKET}
aws.s3.region=${AWS_REGION:us-east-1}
# AWS credentials loaded from EC2 instance role (no hardcoded keys)
```

---

## 3. Redis Caching

**New dependency:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

**Cache configuration:**
```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
            );

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "summaries",          defaultConfig.entryTtl(Duration.ofHours(24)),
            "extractions",        defaultConfig.entryTtl(Duration.ofHours(12)),
            "queryEmbeddings",    defaultConfig.entryTtl(Duration.ofHours(2)),
            "documentList",       defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}
```

**Cache annotations on services:**
```java
// SummaryService — cache summary by document ID
@Cacheable(value = "summaries", key = "#documentId")
public SummaryResponse summarize(Long documentId) { ... }

// Evict when document is deleted
@CacheEvict(value = {"summaries", "extractions"}, key = "#documentId")
public void evictDocumentCaches(Long documentId) { ... }

// DocumentService — cache document list per user
@Cacheable(value = "documentList", key = "#userId")
public List<DocumentResponse> findAll(Long userId) { ... }

@CacheEvict(value = "documentList", key = "#userId")
public void evictDocumentList(Long userId) { ... }
```

**Cache key strategy for RAG queries:**
```java
// In RagService — cache responses for identical question + document combinations
public ChatResponse answer(String question, Long documentId) {
    String cacheKey = documentId + ":" + DigestUtils.sha256Hex(question.toLowerCase().trim());

    // Check Redis manually (question+doc combos use programmatic cache access)
    ChatResponse cached = redisTemplate.opsForValue().get("chat:" + cacheKey);
    if (cached != null) return cached;

    ChatResponse response = executeRagPipeline(question, documentId);
    redisTemplate.opsForValue().set("chat:" + cacheKey, response, Duration.ofHours(6));
    return response;
}
```

**application.properties additions:**
```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.timeout=2000ms
# Use Lettuce pool to avoid blocking on Redis calls
spring.data.redis.lettuce.pool.max-active=10
spring.data.redis.lettuce.pool.max-idle=5
```

---

## 4. JWT Authentication & Multi-Tenancy

**New dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
```

**Security filter chain:**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

**JWT filter:**
```java
@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                Long userId = jwtService.extractUserId(token);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);
    }
}
```

**Multi-tenancy — DB schema change:**
```sql
-- Add userId to documents table
ALTER TABLE documents ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_documents_user_id ON documents(user_id);

-- All document queries now filter by user_id:
-- SELECT * FROM documents WHERE user_id = ? AND id = ?
```

**Multi-tenancy — Repository:**
```java
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // All queries scoped to authenticated user's ID
    List<Document> findAllByUserId(Long userId);
    Optional<Document> findByIdAndUserId(Long id, Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
}
```

**Multi-tenancy — Vector store metadata:**
```java
// In DocumentProcessingService, when injecting metadata into chunks:
chunk.getMetadata().put("documentId", document.getId().toString());
chunk.getMetadata().put("userId", document.getUserId().toString());

// In RagService, filter by both documentId AND userId:
FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
Filter.Expression filter = filterBuilder
    .and(
        filterBuilder.eq("documentId", String.valueOf(documentId)),
        filterBuilder.eq("userId", String.valueOf(userId))
    ).build();
```

---

## 5. Qdrant Vector Database Migration

### Why Qdrant over pgvector at Scale

| Criterion | pgvector | Qdrant |
|-----------|----------|--------|
| Max vectors | ~5M on single node | Billions (distributed) |
| Index types | HNSW only | HNSW + IVFFlat |
| Filtering | SQL predicates | Native payload filtering |
| Horizontal scaling | Limited (PG partitioning) | Native distributed mode |
| Separate tuning | Shared with relational DB | Independent resource allocation |

### Migration Steps

**1. Add Qdrant dependency:**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-qdrant-store-spring-boot-starter</artifactId>
</dependency>
```

**2. Change application.properties:**
```properties
# Remove pgvector config:
# spring.ai.vectorstore.pgvector.*

# Add Qdrant config:
spring.ai.vectorstore.qdrant.host=${QDRANT_HOST:localhost}
spring.ai.vectorstore.qdrant.port=${QDRANT_PORT:6334}
spring.ai.vectorstore.qdrant.collection-name=doc_chunks
spring.ai.vectorstore.qdrant.initialize-schema=true
```

**3. Zero Java service code changes** — Spring AI's `VectorStore` interface is the same. `vectorStore.add()`, `vectorStore.similaritySearch()`, `vectorStore.delete()` all work identically.

**4. Migration script — copy existing pgvector data to Qdrant:**
```java
@Component
public class VectorMigrationRunner implements CommandLineRunner {

    @Autowired DocumentChunkRepository chunkRepository;
    @Autowired EmbeddingModel embeddingModel;
    @Autowired VectorStore qdrantStore;  // new Qdrant VectorStore bean

    @Override
    public void run(String... args) {
        List<DocumentChunk> allChunks = chunkRepository.findAll();

        // Re-embed in batches of 100
        Lists.partition(allChunks, 100).forEach(batch -> {
            List<Document> docs = batch.stream().map(chunk ->
                new Document(chunk.getVectorStoreId(), chunk.getContent(),
                    Map.of("documentId", chunk.getDocumentId().toString()))
            ).toList();
            qdrantStore.add(docs);  // batches embedding calls internally
            log.info("Migrated {} chunks", batch.size());
        });
    }
}
```

---

## 6. Hybrid Search (BM25 + Vector)

Combining keyword and semantic search improves recall, especially for exact-match queries like product names, codes, or dates.

**PostgreSQL full-text search (BM25) side:**
```sql
-- Add tsvector column to document_chunks
ALTER TABLE document_chunks ADD COLUMN content_tsv TSVECTOR
    GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX idx_document_chunks_tsv ON document_chunks USING gin(content_tsv);
```

**Java — Hybrid search service:**
```java
@Service
public class HybridSearchService {

    @Autowired VectorStore vectorStore;
    @Autowired DocumentChunkRepository chunkRepository;  // for BM25 queries

    public List<ScoredChunk> search(String query, Long documentId, int topK) {

        // 1. BM25 keyword search (via native query)
        List<BM25Result> keywordResults = chunkRepository.bm25Search(query, documentId, topK * 2);

        // 2. Vector semantic search
        SearchRequest vectorReq = SearchRequest.builder()
            .query(query)
            .topK(topK * 2)
            .filterExpression("documentId == '" + documentId + "'")
            .build();
        List<Document> vectorResults = vectorStore.similaritySearch(vectorReq);

        // 3. Reciprocal Rank Fusion (RRF) — combine scores
        return reciprocalRankFusion(keywordResults, vectorResults, topK);
    }

    private List<ScoredChunk> reciprocalRankFusion(
            List<BM25Result> bm25, List<Document> vectors, int topK) {

        Map<String, Double> scores = new HashMap<>();
        int k = 60;  // RRF constant (standard value)

        for (int i = 0; i < bm25.size(); i++) {
            String id = bm25.get(i).vectorStoreId();
            scores.merge(id, 1.0 / (k + i + 1), Double::sum);
        }
        for (int i = 0; i < vectors.size(); i++) {
            String id = vectors.get(i).getId();
            scores.merge(id, 1.0 / (k + i + 1), Double::sum);
        }

        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> resolveChunk(e.getKey(), e.getValue()))
            .toList();
    }
}
```

**BM25 native query in repository:**
```java
@Query(value = """
    SELECT dc.vector_store_id, dc.content,
           ts_rank(dc.content_tsv, plainto_tsquery('english', :query)) AS score
    FROM document_chunks dc
    WHERE dc.document_id = :docId
      AND dc.content_tsv @@ plainto_tsquery('english', :query)
    ORDER BY score DESC
    LIMIT :limit
    """, nativeQuery = true)
List<BM25Result> bm25Search(@Param("query") String query,
                             @Param("docId") Long docId,
                             @Param("limit") int limit);
```

---

## 7. SSE Streaming Responses

LLM calls block for seconds. Server-Sent Events (SSE) stream tokens as they arrive.

**Controller change:**
```java
// Before — waits for full response:
@PostMapping("/chat")
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
    return ResponseEntity.ok(ragService.answer(request.question(), request.documentId()));
}

// After — streams tokens via SSE:
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
    return ragService.answerStream(request.question(), request.documentId())
        .map(token -> ServerSentEvent.<String>builder()
            .event("token")
            .data(token)
            .build()
        )
        .concatWith(Flux.just(
            ServerSentEvent.<String>builder().event("done").data("").build()
        ));
}
```

**RagService streaming method:**
```java
public Flux<String> answerStream(String question, Long documentId) {
    // Retrieval is synchronous (fast: ~100ms)
    List<Document> chunks = retrieveChunks(question, documentId);
    String context = buildContext(chunks);
    String prompt = buildRagPrompt(context, question);

    // Streaming LLM call via Spring AI
    return chatClient.prompt(prompt)
        .stream()
        .content();  // returns Flux<String> of tokens
}
```

**Frontend — React SSE consumer:**
```javascript
async function streamChat(question, documentId, onToken, onDone) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, documentId })
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const lines = decoder.decode(value).split('\n');
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const token = line.slice(5).trim();
        onToken(token);  // append to message in real time
      }
      if (line.includes('event: done')) {
        onDone();
        return;
      }
    }
  }
}
```

---

## 8. Circuit Breaker for External AI APIs

Prevents cascade failures when Groq or Jina AI is slow/down.

**New dependency:**
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
```

**application.properties:**
```properties
# Circuit breaker for LLM calls
resilience4j.circuitbreaker.instances.llm.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.llm.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.llm.sliding-window-size=10
resilience4j.circuitbreaker.instances.llm.permitted-calls-in-half-open-state=3

# Retry for embedding calls
resilience4j.retry.instances.embedding.max-attempts=3
resilience4j.retry.instances.embedding.wait-duration=1s
resilience4j.retry.instances.embedding.enable-exponential-backoff=true
resilience4j.retry.instances.embedding.exponential-backoff-multiplier=2
```

**Apply to service methods:**
```java
@Service
public class RagService {

    // Circuit breaker wraps Groq LLM call
    @CircuitBreaker(name = "llm", fallbackMethod = "llmFallback")
    @TimeLimiter(name = "llm")
    @Retry(name = "llm")
    public ChatResponse answer(String question, Long documentId) {
        // ... normal RAG pipeline ...
    }

    // Fallback when LLM is down / circuit is open
    public ChatResponse llmFallback(String question, Long documentId, Exception ex) {
        log.warn("LLM circuit open or timeout for question: {}", question, ex);
        return new ChatResponse(
            "The AI service is temporarily unavailable. Please try again in a moment.",
            List.of()
        );
    }
}

@Service
public class DocumentProcessingService {

    // Retry wraps Jina AI embedding call
    @Retry(name = "embedding", fallbackMethod = "embeddingFallback")
    public void embedAndStore(List<Document> chunks) {
        vectorStore.add(chunks);
    }

    public void embeddingFallback(List<Document> chunks, Exception ex) {
        throw new DocumentProcessingException("Embedding service unavailable after retries: " + ex.getMessage());
    }
}
```

---

## 9. Rate Limiting

Prevents abuse and enforces per-user quotas on expensive LLM calls.

**New dependency:**
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>
```

**Rate limiter filter:**
```java
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    // Per-user: 20 AI requests/minute, 200/hour
    private static final Bandwidth MINUTE_LIMIT  = Bandwidth.classic(20,  Refill.greedy(20,  Duration.ofMinutes(1)));
    private static final Bandwidth HOUR_LIMIT    = Bandwidth.classic(200, Refill.greedy(200, Duration.ofHours(1)));

    @Autowired RedisTemplate<String, byte[]> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        // Only rate-limit AI endpoints
        String path = req.getRequestURI();
        if (!isAiEndpoint(path)) { chain.doFilter(req, res); return; }

        Long userId = extractUserId(req);
        String bucketKey = "rate:" + userId;

        Bucket bucket = buildOrLoadBucket(bucketKey);

        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("""
                {"status":429,"error":"Too Many Requests",
                 "message":"Rate limit exceeded. Max 20 AI requests per minute."}
                """);
        }
    }

    private boolean isAiEndpoint(String path) {
        return path.startsWith("/api/chat") || path.startsWith("/api/summary")
            || path.startsWith("/api/extract") || path.startsWith("/api/compare");
    }
}
```

---

## 10. Kubernetes Deployment Specs

### Backend Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ai-doc-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: ai-doc-backend
  template:
    metadata:
      labels:
        app: ai-doc-backend
    spec:
      containers:
      - name: backend
        image: ai-doc-assistant/backend:latest
        ports:
        - containerPort: 8080
        env:
        - name: GROQ_API_KEY
          valueFrom:
            secretKeyRef:
              name: ai-doc-secrets
              key: groq-api-key
        - name: JINA_API_KEY
          valueFrom:
            secretKeyRef:
              name: ai-doc-secrets
              key: jina-api-key
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: ai-doc-config
              key: db-url
        - name: REDIS_HOST
          valueFrom:
            configMapKeyRef:
              name: ai-doc-config
              key: redis-host
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 15
```

### Horizontal Pod Autoscaler
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ai-doc-backend-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ai-doc-backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "500"
```

### Ingestion Worker — Queue-depth autoscaling
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ingestion-worker-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ingestion-worker
  minReplicas: 1
  maxReplicas: 8
  metrics:
  - type: External
    external:
      metric:
        name: rabbitmq_queue_messages_ready   # scraped via Prometheus RabbitMQ exporter
        selector:
          matchLabels:
            queue: doc.upload.queue
      target:
        type: AverageValue
        averageValue: "10"    # 1 worker per 10 queued messages
```

---

## 11. Observability Stack

### Metrics (Prometheus + Grafana)

**Dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**Custom metrics in services:**
```java
@Service
public class RagService {

    private final Counter ragQueryCounter;
    private final Timer llmLatencyTimer;
    private final Timer embeddingLatencyTimer;

    public RagService(MeterRegistry registry) {
        this.ragQueryCounter = Counter.builder("rag.queries.total")
            .description("Total RAG queries processed")
            .register(registry);

        this.llmLatencyTimer = Timer.builder("llm.call.duration")
            .description("LLM response time")
            .percentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.embeddingLatencyTimer = Timer.builder("embedding.call.duration")
            .description("Embedding API response time")
            .register(registry);
    }

    public ChatResponse answer(String question, Long documentId) {
        ragQueryCounter.increment();

        List<Document> chunks = embeddingLatencyTimer.record(
            () -> retrieveChunks(question, documentId)
        );

        return llmLatencyTimer.record(
            () -> callLlm(chunks, question)
        );
    }
}
```

**Grafana dashboard panels:**
```
┌────────────────────────────┐  ┌────────────────────────────┐
│  RAG Queries / min         │  │  LLM Latency p50/p95/p99   │
│  [line chart]              │  │  [histogram]               │
└────────────────────────────┘  └────────────────────────────┘
┌────────────────────────────┐  ┌────────────────────────────┐
│  Failed Ingestions (DLQ)   │  │  Cache Hit Rate (Redis)    │
│  [counter + alert rule]    │  │  [gauge]                   │
└────────────────────────────┘  └────────────────────────────┘
┌────────────────────────────┐  ┌────────────────────────────┐
│  Circuit Breaker State     │  │  Active Backend Pods       │
│  (open / half-open / close)│  │  [HPA status]              │
└────────────────────────────┘  └────────────────────────────┘
```

**application.properties for observability:**
```properties
# Expose all actuator endpoints
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.health.show-details=always
management.endpoint.health.probes.enabled=true

# Structured logging for Loki ingestion
logging.structured.format.console=ecs

# Trace sampling (Jaeger)
management.tracing.sampling.probability=0.1  # 10% of requests
management.zipkin.tracing.endpoint=http://jaeger:9411/api/v2/spans
```

---

## 12. Database Connection Pooling (HikariCP Tuning)

Spring Boot uses HikariCP by default. At scale, pool sizing matters.

```properties
# For backend pods each handling ~50 concurrent requests:
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000       # 30s — fail fast if DB overloaded
spring.datasource.hikari.idle-timeout=600000            # 10min
spring.datasource.hikari.max-lifetime=1800000           # 30min — recycle connections
spring.datasource.hikari.leak-detection-threshold=60000 # warn if connection held > 60s
spring.datasource.hikari.pool-name=AiDocHikariPool

# Read replica routing — route SELECT to replica
spring.datasource.read.url=jdbc:postgresql://${DB_READ_HOST}:5432/aidocdb
spring.datasource.read.hikari.maximum-pool-size=30      # replicas serve more reads
```

**Read/write routing:**
```java
@Configuration
public class DataSourceRoutingConfig extends AbstractRoutingDataSource {

    enum DataSourceType { PRIMARY, READ_REPLICA }

    private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();

    public static void useReadReplica()  { CONTEXT.set(DataSourceType.READ_REPLICA); }
    public static void usePrimary()      { CONTEXT.set(DataSourceType.PRIMARY); }
    public static void clear()           { CONTEXT.remove(); }

    @Override
    protected Object determineCurrentLookupKey() {
        return CONTEXT.get() != null ? CONTEXT.get() : DataSourceType.PRIMARY;
    }
}

// AOP interceptor — auto-route @Transactional(readOnly=true) to replica
@Aspect @Component
public class DataSourceRoutingAspect {
    @Around("@annotation(transactional)")
    public Object route(ProceedingJoinPoint jp, Transactional transactional) throws Throwable {
        if (transactional.readOnly()) {
            DataSourceRoutingConfig.useReadReplica();
        }
        try {
            return jp.proceed();
        } finally {
            DataSourceRoutingConfig.clear();
        }
    }
}
```

---

## 13. Summary — Code Changes per Scaling Tier

```
Tier 1 Additions (10–100 users)
─────────────────────────────────────────────────────────────────────────
  + AsyncConfig.java              (@EnableAsync, thread pool)
  + JwtFilter.java                (JWT validation on every request)
  + SecurityConfig.java           (Spring Security filter chain)
  + JwtService.java               (sign / verify / extract claims)
  + S3StorageService.java         (upload, download, presign, delete)
  + DocumentService changes       (call S3 instead of local FS)
  + Document entity               (+ userId column)
  + DocumentRepository            (+ userId-scoped queries)
  + RagService / services         (+ userId in vector metadata filter)
  + application.properties        (+ AWS / JWT settings)

Tier 2 Additions (100–10K users)
─────────────────────────────────────────────────────────────────────────
  + RabbitConfig.java             (queues, exchanges, bindings)
  + IngestionWorker.java          (@RabbitListener consumer)
  + IngestionMessage.java         (serialisable message payload)
  + CacheConfig.java              (@EnableCaching, Redis TTLs)
  + Cache annotations             (@Cacheable / @CacheEvict on services)
  + HybridSearchService.java      (BM25 + vector RRF fusion)
  + Native BM25 query             (DocumentChunkRepository)
  + ChatController changes        (+ /chat/stream SSE endpoint)
  + RagService.answerStream()     (Flux<String> streaming)
  + application.properties        (+ Redis, RabbitMQ, Qdrant settings)
  + Qdrant VectorStore bean       (swap from pgvector)

Tier 3 Additions (10K–100K users)
─────────────────────────────────────────────────────────────────────────
  + RateLimitFilter.java          (Bucket4j per-user limits)
  + Circuit breaker annotations   (@CircuitBreaker, @Retry, @TimeLimiter)
  + Custom Micrometer metrics     (counters, timers in services)
  + K8s manifests                 (Deployment, Service, HPA, Ingress)
  + HikariCP read routing         (DataSourceRoutingConfig + Aspect)
  + Prometheus scrape config      (spring actuator /prometheus endpoint)
  + Grafana dashboards            (JSON dashboard definitions)
  + Jaeger tracing                (micrometer-tracing-bridge-otel)
```
