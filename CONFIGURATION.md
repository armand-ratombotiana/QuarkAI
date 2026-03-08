# QuarkAI Configuration Guide

Complete reference for configuring QuarkAI with all supported AI providers.

## Table of Contents

1. [Provider Selection](#provider-selection)
2. [OpenAI Configuration](#openai-configuration)
3. [Anthropic Claude Configuration](#anthropic-claude-configuration)
4. [Google Vertex AI Configuration](#google-vertex-ai-configuration)
5. [Ollama Configuration](#ollama-configuration)
6. [RAG Configuration](#rag-configuration)
7. [Circuit Breaker Configuration](#circuit-breaker-configuration)
8. [Environment Variables](#environment-variables)
9. [Production Best Practices](#production-best-practices)

---

## Provider Selection

QuarkAI uses a single configuration property to switch between AI providers at runtime:

```properties
# Choose one: openai, anthropic, vertex, ollama
quarkai.provider=openai
```

All providers implement the same `ChatModel` and `StreamingChatModel` interfaces, allowing seamless switching without code changes.

---

## OpenAI Configuration

### Basic Setup

```properties
quarkai.provider=openai
quarkai.openai.api-key=${OPENAI_API_KEY}
quarkai.openai.base-url=https://api.openai.com/v1
quarkai.openai.timeout-seconds=30
```

### Full Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkai.openai.api-key` | String | *required* | OpenAI API key (starts with `sk-...`) |
| `quarkai.openai.base-url` | String | `https://api.openai.com/v1` | API base URL (for Azure OpenAI or proxies) |
| `quarkai.openai.organization-id` | String | *(optional)* | Organization ID for team accounts |
| `quarkai.openai.timeout-seconds` | Long | `30` | HTTP request timeout in seconds |

### Supported Models

**Chat Models:**
- `gpt-4o` (recommended)
- `gpt-4o-mini`
- `gpt-4-turbo`
- `gpt-3.5-turbo`

**Embedding Models:**
- `text-embedding-3-small`
- `text-embedding-3-large`
- `text-embedding-ada-002`

### Azure OpenAI

```properties
quarkai.provider=openai
quarkai.openai.api-key=${AZURE_OPENAI_API_KEY}
quarkai.openai.base-url=https://{your-resource}.openai.azure.com/openai/deployments/{deployment-name}
```

### Example Usage

```java
@Inject ChatModel chatModel;

AiRequest request = AiRequest.builder()
    .model("gpt-4o")
    .addMessage(Message.system("You are a helpful assistant."))
    .addMessage(Message.user("Explain microservices"))
    .temperature(0.7)
    .maxTokens(1000)
    .build();

chatModel.chat(request)
    .subscribe().with(response -> {
        System.out.println(response.content());
        System.out.println("Tokens used: " + response.usage().get().totalTokens());
    });
```

---

## Anthropic Claude Configuration

### Basic Setup

```properties
quarkai.provider=anthropic
quarkai.anthropic.api-key=${ANTHROPIC_API_KEY}
quarkai.anthropic.base-url=https://api.anthropic.com/v1
quarkai.anthropic.api-version=2023-06-01
quarkai.anthropic.timeout-seconds=30
```

### Full Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkai.anthropic.api-key` | String | *required* | Anthropic API key (starts with `sk-ant-...`) |
| `quarkai.anthropic.base-url` | String | `https://api.anthropic.com/v1` | API base URL |
| `quarkai.anthropic.api-version` | String | `2023-06-01` | Anthropic API version header |
| `quarkai.anthropic.timeout-seconds` | Long | `30` | HTTP request timeout in seconds |

### Supported Models

- `claude-sonnet-4-5-20250929` (latest, recommended)
- `claude-3-5-sonnet-20241022`
- `claude-3-opus-20240229`
- `claude-3-haiku-20240307`

### Protocol Differences

Anthropic uses a different message protocol than OpenAI:

1. **System Messages**: Hoisted to a top-level `system` field (not in messages array)
2. **Authentication**: Uses `x-api-key` header instead of `Authorization: Bearer`
3. **API Version**: Requires `anthropic-version` header
4. **Streaming**: Uses event types (`content_block_delta`, `message_stop`) instead of SSE `data:` prefixes

QuarkAI handles all these differences internally.

### Example Usage

```java
AiRequest request = AiRequest.builder()
    .model("claude-sonnet-4-5-20250929")
    .addMessage(Message.system("You are a coding assistant."))
    .addMessage(Message.user("Write a Python function to sort a list"))
    .temperature(0.5)
    .maxTokens(2048)
    .build();

chatModel.chat(request)
    .subscribe().with(response -> {
        System.out.println(response.content());
    });
```

---

## Google Vertex AI Configuration

### Basic Setup

```properties
quarkai.provider=vertex
quarkai.vertex.project-id=${GCP_PROJECT_ID}
quarkai.vertex.location=us-central1
quarkai.vertex.model=gemini-1.5-flash
quarkai.vertex.access-token=${VERTEX_ACCESS_TOKEN}
quarkai.vertex.timeout-seconds=30
```

### Full Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkai.vertex.project-id` | String | *required* | Google Cloud project ID |
| `quarkai.vertex.location` | String | *required* | GCP region (e.g., `us-central1`) |
| `quarkai.vertex.model` | String | *required* | Gemini model name |
| `quarkai.vertex.access-token` | String | *required* | OAuth2 access token (from `gcloud auth print-access-token`) |
| `quarkai.vertex.timeout-seconds` | Long | `30` | HTTP request timeout in seconds |

### Supported Models

- `gemini-1.5-flash` (fast, cost-effective)
- `gemini-1.5-pro` (advanced reasoning)
- `gemini-1.0-pro` (legacy)

### Authentication

Vertex AI requires OAuth2 authentication. Generate a token:

```bash
# Install gcloud CLI
gcloud auth login

# Get access token
export VERTEX_ACCESS_TOKEN=$(gcloud auth print-access-token)
```

For production, use **Service Account** authentication:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json
gcloud auth application-default print-access-token
```

### Protocol Differences

1. **Dynamic URLs**: Endpoint includes project ID and location
2. **System Instructions**: Uses `systemInstruction` field instead of system messages
3. **Role Mapping**: `USER` → `user`, `ASSISTANT` → `model`
4. **Streaming**: Returns JSON array of response objects

### Example Usage

```java
AiRequest request = AiRequest.builder()
    .model("gemini-1.5-flash")
    .addMessage(Message.system("You are a data analysis expert."))
    .addMessage(Message.user("Analyze this dataset: ..."))
    .temperature(0.4)
    .maxTokens(4096)
    .build();

chatModel.chat(request).subscribe().with(response -> {
    System.out.println(response.content());
});
```

---

## Ollama Configuration

### Basic Setup

```properties
quarkai.provider=ollama
quarkai.ollama.base-url=http://localhost:11434
quarkai.ollama.timeout-seconds=60
```

### Full Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkai.ollama.base-url` | String | `http://localhost:11434` | Ollama server URL |
| `quarkai.ollama.timeout-seconds` | Long | `60` | HTTP request timeout (longer for local inference) |

### Installation

```bash
# macOS/Linux
curl https://ollama.ai/install.sh | sh

# Windows (WSL2)
curl https://ollama.ai/install.sh | sh

# Start Ollama
ollama serve

# Pull a model
ollama pull llama3.2
ollama pull mistral
ollama pull codellama
```

### Supported Models

Any model available in the [Ollama library](https://ollama.ai/library):

- `llama3.2` (Meta's latest)
- `mistral` (Mistral AI)
- `codellama` (code generation)
- `gemma:7b` (Google)
- `phi3` (Microsoft)

### Protocol Details

1. **No Authentication**: Local server, no API keys required
2. **NDJSON Streaming**: Newline-delimited JSON (not SSE)
3. **Options Field**: Uses `options` object for temperature, max_tokens

### Example Usage

```java
AiRequest request = AiRequest.builder()
    .model("llama3.2")
    .addMessage(Message.user("Write a bash script to backup files"))
    .temperature(0.7)
    .maxTokens(1024)
    .build();

chatModel.chat(request).subscribe().with(response -> {
    System.out.println(response.content());
});
```

---

## RAG Configuration

### Vector Store Setup

QuarkAI supports two vector store implementations:

#### In-Memory Vector Store

```java
@Inject InMemoryVectorStore vectorStore;

// Add documents
vectorStore.add("doc1", embedding, "QuarkAI is a reactive AI framework", Map.of())
    .await().indefinitely();

// Search
vectorStore.search(queryEmbedding, 5, 0.7)
    .subscribe().with(matches -> {
        matches.forEach(match ->
            System.out.println(match.text() + " (score: " + match.score() + ")")
        );
    });
```

#### PostgreSQL pgvector

```properties
# Database configuration
quarkus.datasource.quarkai.db-kind=postgresql
quarkus.datasource.quarkai.username=postgres
quarkus.datasource.quarkai.password=postgres
quarkus.datasource.quarkai.reactive.url=postgresql://localhost:5432/quarkai
```

**Database Setup:**

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS quarkai_embeddings (
    id       TEXT PRIMARY KEY,
    text     TEXT NOT NULL,
    vector   vector(1536),
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX ON quarkai_embeddings USING ivfflat (vector vector_cosine_ops);
```

### RAG Pipeline Usage

```java
@Inject RagPipeline ragPipeline;

// Ask a question with context retrieval
ragPipeline.ask("What is QuarkAI?", 5, 0.7)
    .subscribe().with(response -> {
        System.out.println(response.content());
    });

// Custom model
ragPipeline.withModel("gpt-4o")
    .ask("Explain reactive programming")
    .subscribe().with(System.out::println);
```

### Parameters

- **topK**: Number of similar documents to retrieve (default: 5)
- **minScore**: Minimum cosine similarity score (0.0–1.0, default: 0.7)

---

## Circuit Breaker Configuration

Circuit breaker is configured programmatically in `AiCircuitBreaker`:

```java
@ApplicationScoped
public class AiCircuitBreaker {
    private static final int    FAILURE_THRESHOLD  = 5;      // failures before opening
    private static final long   RESET_TIMEOUT_MS   = 10_000L; // 10 seconds
    private static final long   REQUEST_TIMEOUT_MS = 30_000L; // 30 seconds
}
```

### Usage

```java
@Inject AiCircuitBreaker breaker;

breaker.chat(request)
    .onFailure().recoverWithItem(fallbackResponse)
    .subscribe().with(response -> {
        System.out.println(response.content());
    });
```

### States

1. **Closed**: Normal operation
2. **Open**: Fails fast after threshold exceeded
3. **Half-Open**: Attempting recovery after timeout

---

## Environment Variables

Recommended environment variable configuration:

```bash
# OpenAI
export OPENAI_API_KEY="sk-..."

# Anthropic
export ANTHROPIC_API_KEY="sk-ant-..."

# Vertex AI
export GCP_PROJECT_ID="my-project"
export VERTEX_ACCESS_TOKEN=$(gcloud auth print-access-token)

# Database (for PgVectorStore)
export DB_PASSWORD="..."
```

Reference in `application.properties`:

```properties
quarkai.openai.api-key=${OPENAI_API_KEY}
quarkai.anthropic.api-key=${ANTHROPIC_API_KEY}
quarkai.vertex.project-id=${GCP_PROJECT_ID}
```

---

## Production Best Practices

### 1. Never Hardcode API Keys

```properties
# ❌ BAD
quarkai.openai.api-key=sk-abcd1234...

# ✅ GOOD
quarkai.openai.api-key=${OPENAI_API_KEY}
```

### 2. Use Appropriate Timeouts

```properties
# Fast models (GPT-3.5, Gemini Flash)
quarkai.{provider}.timeout-seconds=15

# Large models (GPT-4, Claude Opus)
quarkai.{provider}.timeout-seconds=60

# Local models (Ollama)
quarkai.ollama.timeout-seconds=120
```

### 3. Monitor Metrics

```properties
quarkus.micrometer.export.prometheus.enabled=true
```

Key metrics:
- `quarkai.requests.total` (counter by provider, model, outcome)
- `quarkai.tokens.prompt` (distribution summary)
- `quarkai.tokens.completion` (distribution summary)
- `quarkai.latency` (timer with p50, p95, p99)
- `quarkai.errors.total` (counter by provider, error type)

### 4. Health Checks

Implement custom health checks:

```java
@ApplicationScoped
public class AiHealthCheck implements HealthCheck {
    @Inject ChatModel chatModel;

    @Override
    public HealthCheckResponse call() {
        try {
            chatModel.chat(AiRequest.builder()
                .model("gpt-3.5-turbo")
                .addMessage(Message.user("test"))
                .maxTokens(5)
                .build())
                .await().atMost(Duration.ofSeconds(5));

            return HealthCheckResponse.up("ai-provider");
        } catch (Exception e) {
            return HealthCheckResponse.down("ai-provider");
        }
    }
}
```

### 5. Rate Limiting

Implement application-level rate limiting to avoid provider quota exhaustion:

```java
@RegisterForReflection
public class RateLimitInterceptor {
    private final RateLimiter rateLimiter =
        RateLimiter.create(10.0); // 10 requests/second

    @AroundInvoke
    public Object rateLimit(InvocationContext ctx) throws Exception {
        rateLimiter.acquire();
        return ctx.proceed();
    }
}
```

### 6. Logging & Debugging

```properties
# Production
quarkus.log.level=INFO
quarkus.log.category."io.quarkiverse.quarkai".level=INFO

# Development/Troubleshooting
quarkus.log.level=DEBUG
quarkus.log.category."io.quarkiverse.quarkai".level=TRACE
```

### 7. Cost Optimization

```java
// Use cheaper models for simple tasks
AiRequest simpleRequest = AiRequest.builder()
    .model("gpt-3.5-turbo") // instead of gpt-4o
    .maxTokens(100)          // limit response length
    .build();

// Cache responses
@CacheResult(cacheName = "ai-responses")
public Uni<AiResponse> getCachedResponse(String prompt) {
    return chatModel.chat(AiRequest.builder()
        .model("gpt-4o")
        .addMessage(Message.user(prompt))
        .build());
}
```

---

## Migration Between Providers

Switch providers with zero code changes:

**From OpenAI to Anthropic:**

```diff
- quarkai.provider=openai
- quarkai.openai.api-key=${OPENAI_API_KEY}
+ quarkai.provider=anthropic
+ quarkai.anthropic.api-key=${ANTHROPIC_API_KEY}
```

**Model equivalents:**

| OpenAI | Anthropic | Vertex AI | Ollama |
|--------|-----------|-----------|--------|
| gpt-4o | claude-sonnet-4-5 | gemini-1.5-pro | llama3.2 |
| gpt-4o-mini | claude-haiku | gemini-1.5-flash | mistral |
| gpt-3.5-turbo | claude-haiku | gemini-1.0-pro | phi3 |

---

## Troubleshooting

### Authentication Errors (401/403)

**OpenAI:**
```bash
# Verify API key
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

**Anthropic:**
```bash
curl https://api.anthropic.com/v1/messages \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01"
```

**Vertex AI:**
```bash
# Refresh token
export VERTEX_ACCESS_TOKEN=$(gcloud auth print-access-token)
```

### Rate Limiting (429)

Implement exponential backoff:

```java
chatModel.chat(request)
    .onFailure(AiRateLimitException.class)
    .retry().withBackOff(Duration.ofSeconds(1), Duration.ofMinutes(1))
    .atMost(3)
    .subscribe().with(response -> {...});
```

### Timeout Issues

Increase timeout for large requests:

```properties
quarkai.{provider}.timeout-seconds=120
```

### Connection Refused (Ollama)

```bash
# Check if Ollama is running
curl http://localhost:11434/api/tags

# Start Ollama
ollama serve
```

---

## Additional Resources

- [QuarkAI GitHub](https://github.com/quarkiverse/quarkai)
- [Example Application](./quarkai-example/README.md)
- [OpenAI API Docs](https://platform.openai.com/docs)
- [Anthropic API Docs](https://docs.anthropic.com)
- [Vertex AI Docs](https://cloud.google.com/vertex-ai/docs)
- [Ollama Documentation](https://ollama.ai/docs)
