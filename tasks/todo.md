# QuarkAI – Task Tracker

## Status Legend
- `[ ]` Not started
- `[/]` In progress
- `[x]` Complete

---

## Phase 0 — Architecture & Planning

- [x] Define module structure and deliverable layout
- [x] Write architectural decisions
- [x] Write acceptance criteria

### Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Build tool | Maven (multi-module) | Native Quarkus / Java ecosystem standard |
| Core reactive lib | SmallRye Mutiny | Quarkus native, Vert.x compatible |
| HTTP client | Vert.x WebClient | Non-blocking, native-image friendly |
| CDI runtime | ArC (Quarkus CDI) | Zero-reflection, build-time bean discovery |
| Serialization | Jackson (via quarkus-rest-jackson) | Broad ecosystem support |
| Testing | JUnit 5 + RestAssured + Mockito | Standard Quarkus stack |
| Config | MicroProfile Config (MP Config) | Portable across Quarkus / Micronaut |
| Observability | Micrometer + OpenTelemetry | Production standard |
| Native image | GraalVM via Quarkus native | Required per spec |

### Acceptance Criteria

- `@Inject ChatModel chatModel;` works in any CDI context
- `chatModel.stream(request).subscribe().with(...)` works without blocking
- Provider can be swapped via `application.properties` with zero code change
- All modules build cleanly (`mvn clean package`)
- Core module has ≥90% test coverage
- No static state, no blocking calls in reactive paths
- Native image builds succeed for `quarkai-quarkus-extension`

---

## Phase 1 — Root POM & Module Skeletons

- [x] Root `pom.xml` (multi-module Maven)
- [x] `quarkai-core/pom.xml`
- [x] `quarkai-openai/pom.xml`
- [x] `quarkai-anthropic/pom.xml`
- [x] `quarkai-vertex/pom.xml`
- [x] `quarkai-ollama/pom.xml`
- [x] `quarkai-quarkus-extension/pom.xml`
- [x] `quarkai-vertx/pom.xml`
- [x] `quarkai-rag/pom.xml`

---

## Phase 2 — quarkai-core

### Model Interfaces
- [x] `AiModel.java`
- [x] `ChatModel.java`
- [x] `EmbeddingModel.java`
- [x] `StreamingChatModel.java`

### DTOs (Immutable, Builders)
- [x] `AiRequest.java` (with builder)
- [x] `AiResponse.java` (with builder)
- [x] `Message.java` (role + content)
- [x] `TokenUsage.java`
- [x] `EmbeddingResult.java`

### Exceptions
- [x] `AiException.java`
- [x] `AiAuthException.java`
- [x] `AiRateLimitException.java`
- [x] `AiTimeoutException.java`

### Unit Tests
- [x] `AiRequestTest.java`
- [x] `AiResponseTest.java`
- [x] `TokenUsageTest.java`

---

## Phase 3 — quarkai-openai

- [x] `OpenAiChatModel.java` (implements ChatModel + StreamingChatModel)
- [x] `OpenAiEmbeddingModel.java` (implements EmbeddingModel)
- [x] `OpenAiConfig.java` (MicroProfile Config mapping)
- [x] `OpenAiRequestMapper.java`
- [x] `OpenAiResponseMapper.java`
- [x] `OpenAiErrorMapper.java`
- [x] Unit tests for mappers

---

## Phase 4 — quarkai-anthropic

- [x] `AnthropicChatModel.java`
- [x] `AnthropicConfig.java`
- [x] `AnthropicRequestMapper.java`
- [x] `AnthropicResponseMapper.java`
- [x] `AnthropicErrorMapper.java`

---

## Phase 5 — quarkai-vertex

- [x] `VertexAiChatModel.java`
- [x] `VertexAiConfig.java`
- [x] Mappers

---

## Phase 6 — quarkai-ollama

- [x] `OllamaChatModel.java`
- [x] `OllamaConfig.java`
- [x] Mappers

---

## Phase 7 — quarkai-quarkus-extension

- [x] Extension descriptor `quarkus-extension.yaml`
- [x] `QuarkAiProcessor.java` (deployment / build-time processor)
- [x] `QuarkAiRecorder.java` (runtime recorder)
- [x] `QuarkAiConfig.java` (config root)
- [x] CDI producer for `ChatModel`
- [x] CDI producer for `EmbeddingModel`
- [x] DevServices shell

---

## Phase 8 — quarkai-vertx

- [x] `VertxAiClient.java` (EventBus bridge)
- [x] `AiCircuitBreaker.java`
- [x] Backpressure-safe streaming adapter

---

## Phase 9 — quarkai-rag

- [x] `VectorStore.java` interface
- [x] `InMemoryVectorStore.java`
- [x] `PgVectorStore.java` (Postgres pgvector)
- [x] `RagPipeline.java`
- [x] `EmbeddingStoreRetriever.java`
- [x] Unit tests for InMemoryVectorStore

---

## Phase 10 — Observability

- [x] `AiMetrics.java` (Micrometer counters/histograms)
- [x] `AiMeterBinder.java`
- [x] Structured logging MDC helpers

---

## Phase 11 — Verification

- [ ] `mvn clean package` — all modules build
- [ ] Unit tests pass (core, openai mappers, rag)
- [ ] Review public API surface
- [ ] Behavior diff: test CDI injection path
- [ ] Native image smoke test command documented

---

## Review

> To be completed after Phase 11.

---
