# QuarkAI Example Application

This example demonstrates how to build a production-ready AI-powered REST API using QuarkAI with Quarkus.

## Features Demonstrated

- **Multiple AI Providers**: Switch between OpenAI, Anthropic Claude, Google Vertex AI, and Ollama
- **Streaming Responses**: Server-Sent Events (SSE) for real-time token streaming
- **Circuit Breaker**: Fail-fast pattern for resilient AI service calls
- **RAG (Retrieval-Augmented Generation)**: Context-aware responses using vector search
- **Metrics & Observability**: Prometheus metrics and health checks
- **Reactive Programming**: Non-blocking, backpressure-aware with Mutiny

## Quick Start

### 1. Set Environment Variables

```bash
# For OpenAI
export OPENAI_API_KEY="sk-..."

# For Anthropic Claude
export ANTHROPIC_API_KEY="sk-ant-..."

# For Google Vertex AI
export GCP_PROJECT_ID="your-project-id"
export VERTEX_ACCESS_TOKEN="ya29...."

# For Ollama (local - no API key needed)
# Just ensure Ollama is running on http://localhost:11434
```

### 2. Configure Provider

Edit `src/main/resources/application.properties`:

```properties
# Choose your provider
quarkai.provider=openai  # or: anthropic, vertex, ollama
```

### 3. Run the Application

```bash
# Development mode with live reload
./mvnw quarkus:dev

# Production mode
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

## API Endpoints

### Chat Completion

**Single Response:**

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "Explain QuarkAI in one sentence"}
    ],
    "temperature": 0.7,
    "maxTokens": 100
  }'
```

**Streaming Response (SSE):**

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "Write a haiku about reactive programming"}
    ]
  }'
```

### Circuit Breaker Protected

```bash
curl -X POST http://localhost:8080/api/chat/protected \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "Hello!"}
    ]
  }'
```

### RAG Question Answering

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is QuarkAI?",
    "topK": 5,
    "minScore": 0.7
  }'
```

## Monitoring

- **Health Check**: http://localhost:8080/q/health
- **Metrics**: http://localhost:8080/q/metrics
- **Prometheus**: http://localhost:8080/q/metrics/prometheus

## Testing Different Providers

### OpenAI

```properties
quarkai.provider=openai
quarkai.openai.api-key=${OPENAI_API_KEY}
```

Models: `gpt-4o`, `gpt-4o-mini`, `gpt-3.5-turbo`

### Anthropic Claude

```properties
quarkai.provider=anthropic
quarkai.anthropic.api-key=${ANTHROPIC_API_KEY}
```

Models: `claude-sonnet-4-5-20250929`, `claude-3-5-sonnet-20241022`

### Google Vertex AI (Gemini)

```properties
quarkai.provider=vertex
quarkai.vertex.project-id=${GCP_PROJECT_ID}
quarkai.vertex.access-token=${VERTEX_ACCESS_TOKEN}
quarkai.vertex.model=gemini-1.5-flash
```

Models: `gemini-1.5-flash`, `gemini-1.5-pro`

### Ollama (Local)

```bash
# Start Ollama
ollama serve

# Pull a model
ollama pull llama3.2
```

```properties
quarkai.provider=ollama
quarkai.ollama.base-url=http://localhost:11434
```

Models: `llama3.2`, `mistral`, `codellama`

## Production Deployment

### Native Image (GraalVM)

```bash
./mvnw package -Pnative
./target/quarkai-example-1.0.0-SNAPSHOT-runner
```

### Docker

```bash
docker build -f src/main/docker/Dockerfile.jvm -t quarkai-example .
docker run -p 8080:8080 -e OPENAI_API_KEY=$OPENAI_API_KEY quarkai-example
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: quarkai-example
spec:
  replicas: 3
  selector:
    matchLabels:
      app: quarkai-example
  template:
    metadata:
      labels:
        app: quarkai-example
    spec:
      containers:
      - name: app
        image: quarkai-example:latest
        env:
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: ai-secrets
              key: openai-api-key
        ports:
        - containerPort: 8080
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
```

## Architecture

```
┌─────────────────┐
│   REST API      │  ← AiChatResource
└────────┬────────┘
         │
         ├─────► ChatModel ◄──┬─ OpenAiChatModel
         │                    ├─ AnthropicChatModel
         │                    ├─ VertexAiChatModel
         │                    └─ OllamaChatModel
         │
         ├─────► CircuitBreaker ─► ChatModel
         │
         └─────► RagPipeline ◄─┬─ EmbeddingModel
                                ├─ VectorStore
                                └─ ChatModel
```

## Performance Tuning

### Connection Pooling

```properties
# Vert.x WebClient settings (used by all providers)
quarkus.vertx.max-event-loop-execute-time=30S
quarkus.vertx.warning-exception-time=10S
```

### Circuit Breaker Tuning

Modify `AiCircuitBreaker` constants:
- `FAILURE_THRESHOLD`: Number of failures before opening (default: 5)
- `RESET_TIMEOUT_MS`: Time to wait before half-open attempt (default: 10s)
- `REQUEST_TIMEOUT_MS`: Individual request timeout (default: 30s)

### Rate Limiting

Implement custom rate limiting using Quarkus Rate Limiting extension:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-rate-limiting</artifactId>
</dependency>
```

## Troubleshooting

### Provider Connection Issues

Enable debug logging:
```properties
quarkus.log.category."io.quarkiverse.quarkai".level=DEBUG
```

### High Latency

1. Check provider region/location settings
2. Increase timeout: `quarkai.{provider}.timeout-seconds`
3. Monitor metrics: `/q/metrics/prometheus`

### Circuit Breaker Opening

Check failure logs and adjust thresholds in `AiCircuitBreaker`

## License

Apache License 2.0
