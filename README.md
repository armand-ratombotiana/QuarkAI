# QuarkAI

> **CDI-first, reactive-first AI abstraction framework for Java ecosystems**

QuarkAI brings [Spring AI](https://spring.io/projects/spring-ai)-style ergonomics to Quarkus, Vert.x, and Micronaut — with native-image compatibility, zero reflection, and full SmallRye Mutiny streaming out of the box.

---

## Quickstart

```java
@Inject ChatModel chatModel;

// Single response
chatModel.chat(AiRequest.builder()
        .model("gpt-4o")
        .addMessage(Message.user("What is QuarkAI?"))
        .build())
    .subscribe().with(r -> System.out.println(r.content()));

// Streaming
chatModel.stream(request)
    .subscribe().with(
        chunk -> System.out.print(chunk.content()),
        err   -> log.error("fail", err),
        ()    -> System.out.println("\n[done]")
    );
```

### Swap providers with one property

```properties
# openai | anthropic | vertex | ollama
quarkai.provider=anthropic
quarkai.anthropic.api-key=sk-ant-...
quarkai.anthropic.model=claude-3-5-sonnet-20241022
```

---

## Module Structure

| Module | Purpose |
|---|---|
| `quarkai-core` | Interfaces (`ChatModel`, `StreamingChatModel`, `EmbeddingModel`) + DTOs |
| `quarkai-openai` | OpenAI provider (GPT-4o, embeddings, streaming) |
| `quarkai-anthropic` | Anthropic Claude provider |
| `quarkai-vertex` | Google Vertex AI (Gemini) provider |
| `quarkai-ollama` | Local Ollama provider |
| `quarkai-quarkus-extension` | Quarkus extension — `@Inject ChatModel`, DevServices |
| `quarkai-vertx` | Vert.x EventBus bridge + circuit breaker |
| `quarkai-rag` | Vector store abstraction + RAG pipeline |

---

## Build

```bash
# Build all modules
mvn clean package

# Run tests
mvn test

# Native image (extension module)
mvn package -Pnative -pl quarkai-quarkus-extension/runtime
```

---

## Configuration Reference

### Core

| Property | Default | Description |
|---|---|---|
| `quarkai.provider` | `openai` | Active provider |

### OpenAI

| Property | Default | Description |
|---|---|---|
| `quarkai.openai.api-key` | *(required)* | OpenAI API key |
| `quarkai.openai.model` | `gpt-4o` | Default model |
| `quarkai.openai.base-url` | `https://api.openai.com/v1` | API base URL |
| `quarkai.openai.timeout-seconds` | `30` | Request timeout |

### Anthropic

| Property | Default | Description |
|---|---|---|
| `quarkai.anthropic.api-key` | *(required)* | Anthropic API key |
| `quarkai.anthropic.model` | `claude-3-5-sonnet-20241022` | Default model |

### Vertex AI

| Property | Default | Description |
|---|---|---|
| `quarkai.vertex.project-id` | *(required)* | GCP project ID |
| `quarkai.vertex.location` | `us-central1` | GCP region |
| `quarkai.vertex.model` | `gemini-1.5-pro` | Default model |
| `quarkai.vertex.access-token` | *(required)* | OAuth2 access token |

### Ollama

| Property | Default | Description |
|---|---|---|
| `quarkai.ollama.base-url` | `http://localhost:11434` | Ollama API URL |
| `quarkai.ollama.model` | `llama3.2` | Default model |

---

## RAG Pipeline

```java
@Inject RagPipeline rag;

// Index documents
embeddingModel.embed(List.of("QuarkAI is a reactive AI framework for Java."))
    .flatMap(result -> vectorStore.add("doc1", result.firstEmbedding(), "...", Map.of()))
    .subscribe().with(...);

// Query
rag.ask("What is QuarkAI?")
   .subscribe().with(r -> System.out.println(r.content()));
```

---

## Design Principles

- **CDI-first** — all beans are `@ApplicationScoped`, no static singletons
- **Reactive-first** — all network calls return `Uni<T>` or `Multi<T>` (never block)
- **Provider-agnostic** — swap providers without changing application code  
- **Native-image compatible** — zero reflection in hot paths
- **Minimal dependencies** — Vert.x WebClient + Jackson + Mutiny only

---

## License

Apache-2.0
