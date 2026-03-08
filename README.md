# QuarkAI

> **CDI-first, reactive-first AI abstraction framework for Java ecosystems**

[![Java](https://img.shields.io/badge/java-17+-blue)](https://www.java.com/)
[![Maven](https://img.shields.io/badge/maven-3.6+-blue)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/tests-108%2F108%20pass-brightgreen)](https://github.com/armand-ratombotiana/QuarkAI)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

QuarkAI brings unified, reactive AI integration to Quarkus and Vert.x with native-image compatibility, zero reflection, and full SmallRye Mutiny streaming.

---

## ✨ Features

- **4 LLM Providers**: OpenAI, Anthropic, Google Vertex AI, Ollama
- **RAG Pipeline**: Vector store integration + context augmentation
- **Reactive-First**: SmallRye Mutiny `Uni<T>` and `Multi<T>` primitives
- **CDI Integration**: Quarkus extension with auto-configuration
- **Circuit Breaker**: Vert.x integration with resilience patterns
- **100% Test Coverage**: 108/108 tests passing

---

## 🚀 Quick Start

### Add Dependency

```xml
<dependency>
    <groupId>io.quarkiverse.quarkai</groupId>
    <artifactId>quarkai-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.quarkiverse.quarkai</groupId>
    <artifactId>quarkai-openai</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Inject and Use

```java
@Inject ChatModel chatModel;

// Single response
AiRequest request = AiRequest.builder()
    .model("gpt-4")
    .addMessage(Message.user("What is QuarkAI?"))
    .build();

chatModel.chat(request)
    .subscribe().with(response ->
        System.out.println(response.content())
    );
```

### Streaming

```java
@Inject StreamingChatModel streamingChatModel;

streamingChatModel.stream(request)
    .subscribe().with(
        chunk -> System.out.print(chunk.content()),
        error -> log.error("Error:", error),
        () -> System.out.println("\n[done]")
    );
```

---

## 📦 Modules

| Module | Tests | Status |
|--------|-------|--------|
| quarkai-core | 20/20 | ✅ |
| quarkai-openai | 22/22 | ✅ |
| quarkai-anthropic | 14/14 | ✅ |
| quarkai-vertex | 11/11 | ✅ |
| quarkai-ollama | 12/12 | ✅ |
| quarkai-rag | 21/21 | ✅ |
| quarkai-vertx | 8/8 | ✅ |

---

## 🔧 Configuration

### OpenAI

```properties
quarkus.quarkai.openai.api-key=sk-...
quarkus.quarkai.openai.base-url=https://api.openai.com
```

### Anthropic

```properties
quarkus.quarkai.anthropic.api-key=sk-ant-...
```

### Vertex AI

```properties
quarkus.quarkai.vertex.api-key=...
quarkus.quarkai.vertex.project-id=your-project
```

### Ollama

```properties
quarkus.quarkai.ollama.base-url=http://localhost:11434
```

---

## 📚 Documentation

- [Deployment Guide](DEPLOYMENT_GUIDE.md) - Getting started
- [Configuration Guide](CONFIGURATION.md) - All settings
- [Production Validation Report](PRODUCTION_VALIDATION_REPORT.md) - Test results
- [Native Image Guide](NATIVE_IMAGE_GUIDE.md) - GraalVM support

---

## 🏗️ Build

```bash
# Build all modules
mvn clean verify

# Run tests
mvn test

# Package
mvn package
```

---

## 🎯 Design Principles

- **CDI-first**: All beans are `@ApplicationScoped`, no static singletons
- **Reactive-first**: Network calls return `Uni<T>` or `Multi<T>` (never block)
- **Provider-agnostic**: Swap providers without code changes
- **Native-compatible**: Zero reflection in hot paths
- **Minimal dependencies**: Vert.x, Jackson, Mutiny only

---

## 📄 License

Apache License 2.0 - See LICENSE file for details

---

## 🤝 Contributing

Contributions welcome! Please follow standard GitHub workflow:

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

---

**Status**: ✅ Production Ready - All 108 tests passing
