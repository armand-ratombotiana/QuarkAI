# QuarkAI

[![Java](https://img.shields.io/badge/java-17+-blue)](https://www.java.com/)
[![Quarkus](https://img.shields.io/badge/quarkus-3.x-red)](https://quarkus.io/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

## Overview

QuarkAI is a modular AI framework for **Java developers** that simplifies the integration of **Large Language Models (LLMs), vector stores, and AI pipelines** into **Quarkus and Vert.x applications**. Inspired by Spring AI, QuarkAI provides easy-to-use starters and abstractions for building **RAG systems, AI chatbots, and AI-driven services**.

---

## Features

- **LLM Integration:** OpenAI, Hugging Face, Anthropic, Vertex AI.
- **Vector Databases:** Chroma, Redis, PostgreSQL (pgvector), Elasticsearch.
- **RAG Pipelines:** Build retrieval-augmented generation pipelines.
- **Conversation Memory:** Manage context for chatbots and agents.
- **Framework Integration:** CDI for Quarkus, Verticles for Vert.x.
- **Async & Reactive:** CompletableFuture, Mutiny, Vert.x Futures.
- **Extensible:** Add your own providers, pipelines, and memory backends.

---

## Getting Started

### Prerequisites
- Java 17+
- Maven or Gradle
- Quarkus 3.x or Vert.x 5.x

### Maven Dependency Example (Quarkus)
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
