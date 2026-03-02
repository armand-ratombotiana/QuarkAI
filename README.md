# QuarkAI

[![Java](https://img.shields.io/badge/java-17+-blue)](https://www.java.com/)
[![Quarkus](https://img.shields.io/badge/quarkus-3.x-red)](https://quarkus.io/)
[![Vert.x](https://img.shields.io/badge/vert.x-5.x-green)](https://vertx.io/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

---

## Overview

**QuarkAI** is a modular, extensible AI framework for Java developers, designed to simplify the integration of **Large Language Models (LLMs), vector databases, RAG pipelines, and conversational AI capabilities** into **Quarkus, Vert.x, and other Java frameworks**.

Inspired by Spring AI, QuarkAI provides **ready-to-use starters, abstractions, and integrations** for developers to build AI-powered applications with minimal boilerplate.

---

## Key Features

* **LLM Integration:** OpenAI, Hugging Face, Anthropic, Vertex AI, and more.
* **Vector Databases:** Chroma, Redis, PostgreSQL (pgvector), Elasticsearch.
* **Retrieval-Augmented Generation (RAG):** Build knowledge retrieval pipelines easily.
* **Conversation Memory:** Track chat history and AI agent context.
* **Framework Integration:**

  * **Quarkus:** CDI-based dependency injection and auto-configured producers.
  * **Vert.x:** Verticles and event bus integration for async pipelines.
* **Async & Reactive Support:** CompletableFuture, Mutiny, Vert.x Futures.
* **Extensible Architecture:** Add new AI providers, vector stores, or pipeline steps effortlessly.

---

## Installation

### Prerequisites

* Java 17 or higher
* Maven or Gradle
* Quarkus 3.x or Vert.x 5.x

### Maven Example

Add the core module and providers to your `pom.xml`:

```xml
<dependency>
  <groupId>com.quarkai</groupId>
  <artifactId>quarkai-core</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>com.quarkai</groupId>
  <artifactId>quarkai-openai</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>com.quarkai</groupId>
  <artifactId>quarkai-vector-chroma</artifactId>
  <version>0.1.0</version>
</dependency>
```

---

## Quick Start (Quarkus Example)

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ChatService {

    @Inject
    AIClient aiClient;

    @Inject
    VectorStore vectorStore;

    public String ask(String question) {
        // Retrieve relevant context from vector store
        List<String> context = vectorStore.query(aiClient.embedText(question), 5);
        // Generate answer with context
        return aiClient.generateText("Answer using context: " + context + "\nQuestion: " + question);
    }
}
```

---

## Quick Start (Vert.x Example)

```java
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import java.util.List;

public class ChatVerticle extends AbstractVerticle {

    private AIClient aiClient;
    private VectorStore vectorStore;

    @Override
    public void start(Promise<Void> startPromise) {
        aiClient = new OpenAIClient("YOUR_API_KEY");
        vectorStore = new ChromaVectorStore("http://localhost:8000");

        vertx.eventBus().consumer("ai.ask", message -> {
            String question = (String) message.body();
            List<String> context = vectorStore.query(aiClient.embedText(question), 5);
            String answer = aiClient.generateText("Answer with context: " + context + "\nQuestion: " + question);
            message.reply(answer);
        });

        startPromise.complete();
    }
}
```

---

## Project Structure

```
quarkai/
├── quarkai-core          # Core abstractions (AIClient, VectorStore, Pipeline)
├── quarkai-openai        # OpenAI provider integration
├── quarkai-huggingface   # Hugging Face provider integration
├── quarkai-vector-*      # Vector store modules (Chroma, Redis, PGVector)
├── quarkai-pipeline      # Pipeline and RAG orchestration
├── quarkai-memory        # Conversation memory management
├── quarkai-starter-quarkus # Quarkus CDI integration
└── quarkai-starter-vertx   # Vert.x Verticle integration
```

---

## Features in Detail

### 1. AI Client Layer

* Provides unified API for LLMs.
* Supports multiple providers via `Strategy` pattern.
* Example: `OpenAIClient`, `HuggingFaceClient`.

### 2. Vector Store Layer

* Standard interface for storing and querying embeddings.
* Supports Chroma, Redis, PostgreSQL (pgvector), Elasticsearch.

### 3. Pipeline / Chain Layer

* Orchestrates AI steps: retrieval, generation, summarization, tool usage.
* Enables **RAG pipelines**.

### 4. Memory Layer

* Stores chat history or session context.
* Supports in-memory, Redis, or database-backed storage.

---

## Contributing

We welcome contributions! Please follow standard GitHub workflow:

1. Fork the repository
2. Create a new branch (`feature/xyz`)
3. Make your changes
4. Submit a pull request

---

## Roadmap

* [ ] Add more LLM providers (Anthropic, Vertex AI)
* [ ] Add more vector store integrations (Pinecone, Milvus)
* [ ] Add agent orchestration modules
* [ ] Add ready-to-use Quarkus & Vert.x sample apps
* [ ] Add async & reactive streaming support

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).

---

## Contact

For questions, feedback, or support: **[hello@quarkai.io](mailto:hello@quarkai.io)**
