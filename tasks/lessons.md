# QuarkAI – Lessons Learned

> Updated: 2026-03-02

---

## L001 — RagPipeline: Model Field Must Be Non-Null and Non-Empty

**What happened**: `RagPipeline.buildAugmentedRequest` set `model("")` when building the `AiRequest`. Although `AiRequest` now only validates non-null, an empty-string model will be forwarded to providers, causing opaque API errors.

**Fix applied**: The `RagPipeline` now does NOT set a model in `AiRequest`; instead the `ChatModel` implementation is responsible for applying its configured default model. The `AiRequest.Builder` should be updated in future to allow an optional model (null = use provider default).

**Rule**: Never construct `AiRequest` with an empty model string. If model selection is delegated to the provider, pass the provider's configured default explicitly, or refactor `AiRequest` to allow `model=null`.

---

## L002 — Quarkus Extension: Always Use Two-Artifact Structure (deployment + runtime)

**What happened**: The Quarkus extension must be split into deployment and runtime JARs. Failing to do so causes build-time classes to pollute the runtime classpath, which breaks native image compilation.

**Rule**: Every Quarkus extension MUST have a `deployment/` sub-module containing `@BuildStep` classes and a `runtime/` sub-module containing `@Recorder` and CDI beans. The deployment JAR must NEVER be on the end-user runtime classpath.

---

## L003 — Vert.x WebClient: `postAbs()` Requires Full URL Including Scheme

**What happened**: Calling `webClient.postAbs("/path")` silently fails. The method requires the full absolute URL (e.g., `https://api.openai.com/v1/chat/completions`).

**Rule**: Always pass absolute URLs to `postAbs()`. Build them by concatenating `config.baseUrl() + CHAT_PATH` where `baseUrl` is always a full URL including scheme.

---

## L004 — MicroProfile Config: `@ConfigMapping` Requires Quarkus Runtime

**What happened**: `@ConfigMapping` interfaces (e.g., `OpenAiConfig`) cannot be injected in plain JUnit 5 tests without a Quarkus test runner. Tests for mappers must receive config manually or use constructor injection.

**Rule**: When testing mapper classes, pass configuration values directly as constructor/method arguments rather than via `@Inject`. Only use CDI injection in `@QuarkusTest`-annotated integration tests.

---

## L005 — Provider Streaming: SSE vs NDJSON Handling

**What happened**: OpenAI uses `data: {...}\n\n` SSE format; Ollama uses newline-delimited JSON objects (NDJSON). Treating all streaming responses the same would break one of the two.

**Rule**: Document the wire format for each provider's streaming endpoint in the mapper class Javadoc. OpenAI parser must skip non-`data:` lines. Ollama parser splits on `\n` and parses each valid JSON line.

---

## L006 — Always Clone float[] When Storing Embeddings

**What happened**: If the caller mutates the `float[]` passed to `InMemoryVectorStore.add()`, the stored vector would silently become corrupted, producing wrong similarity scores.

**Fix**: `InMemoryVectorStore` calls `vector.clone()` on ingestion. `EmbeddingResult` wraps the list as unmodifiable (though individual arrays are still mutable — a known Java limitation).

**Rule**: Always defensively clone `float[]` inputs in any data store. Consider using immutable value types or `float[]` wrappers if mutable-array bugs become recurrent.

---

## L007 — GraalVM Native: Avoid Class.forName and Reflection

**What happened**: Any use of `Class.forName`, `Method.invoke`, or `Field.setAccessible` requires explicit GraalVM reflect-config.json entries, or it silently fails in native mode.

**Rule**: In all QuarkAI classes, use switch expressions on enums and explicit factory methods instead of reflective dispatch. All provider selection must happen in `QuarkAiProducers` at CDI resolution time, not at runtime via reflection.
