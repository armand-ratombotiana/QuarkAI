# QuarkAI Framework - Deployment & Getting Started Guide

## Quick Start

### 1. Installation

Add the QuarkAI dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.quarkai</groupId>
    <artifactId>quarkai-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Your Provider

#### OpenAI
```xml
<dependency>
    <groupId>io.quarkiverse.quarkai</groupId>
    <artifactId>quarkai-openai</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Configuration** (application.properties):
```properties
quarkus.quarkai.openai.api-key=${OPENAI_API_KEY}
quarkus.quarkai.openai.base-url=https://api.openai.com
quarkus.quarkai.openai.organization-id=${OPENAI_ORG_ID:}
```

#### Anthropic
```xml
<dependency>
    <groupId>io.quarkiverse.quarkai</groupId>
    <artifactId>quarkai-anthropic</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Configuration**:
```properties
quarkus.quarkai.anthropic.api-key=${ANTHROPIC_API_KEY}
quarkus.quarkai.anthropic.base-url=https://api.anthropic.com
```

#### Vertex AI
```xml
<dependency>
    <groupId>io.quarkiverse.quarkai</groupId>
    <artifactId>quarkai-vertex</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Configuration**:
```properties
quarkus.quarkai.vertex.api-key=${GOOGLE_API_KEY}
quarkus.quarkai.vertex.project-id=${GOOGLE_PROJECT_ID}
quarkus.quarkai.vertex.location=${GOOGLE_LOCATION:us-central1}
```

#### Ollama
```xml
<dependency>
    <groupId>io.quarkiverse.quarkai</groupId>
    <artifactId>quarkai-ollama</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Configuration**:
```properties
quarkus.quarkai.ollama.base-url=http://localhost:11434
```

### 3. Use the Framework

#### Inject and Use

```java
@RestController
public class ChatResource {
    @Inject
    ChatModel chatModel;

    @PostMapping("/chat")
    public Uni<String> chat(@RequestBody String message) {
        AiRequest request = AiRequest.builder()
            .model("gpt-4")
            .addMessage(Message.user(message))
            .build();

        return chatModel.chat(request)
            .map(response -> response.content());
    }
}
```

#### Streaming Responses

```java
@GetMapping("/stream")
public Multi<String> streamChat(@RequestParam String message) {
    AiRequest request = AiRequest.builder()
        .model("gpt-4")
        .addMessage(Message.user(message))
        .build();

    return streamingChatModel.stream(request)
        .map(response -> response.content());
}
```

### 4. RAG Integration

```java
@Inject
RagPipeline ragPipeline;

public Uni<String> ragQuery(String question) {
    return ragPipeline.ask(question)
        .map(response -> response.content());
}
```

### 5. Error Handling

```java
return chatModel.chat(request)
    .onFailure(AiAuthException.class).invoke(ex ->
        // Handle auth failure
    )
    .onFailure(AiRateLimitException.class).invoke(ex ->
        // Handle rate limit
    )
    .onFailure(AiTimeoutException.class).invoke(ex ->
        // Handle timeout
    );
```

---

## Docker Deployment

### Build Docker Image

```dockerfile
FROM quay.io/quarkus/quarkus-micro-image:2.0

COPY target/quarkai-*.jar /app.jar

ENV QUARKUS_HTTP_PORT=8080

EXPOSE 8080

CMD ["java", "-jar", "/app.jar"]
```

### Run Container

```bash
docker run -e OPENAI_API_KEY=$OPENAI_API_KEY \
           -p 8080:8080 \
           quarkai:latest
```

---

## Build from Source

### Clone Repository
```bash
git clone https://github.com/yourusername/QuarkAI.git
cd QuarkAI
```

### Build
```bash
mvn clean verify
```

### Run Tests
```bash
mvn test
```

### Create JAR
```bash
mvn package
```

---

## Supported Versions

- **Java**: 17+
- **Maven**: 3.6+
- **Quarkus**: 3.0+
- **SmallRye Mutiny**: 2.0+

---

## Troubleshooting

### Issue: "No ChatModel bean found"
**Solution**: Ensure correct provider dependency is added and configured.

### Issue: "API key not found"
**Solution**: Set environment variables or add to `application.properties`:
```bash
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
```

### Issue: "Timeout during API call"
**Solution**: Increase timeout in configuration:
```properties
quarkus.quarkai.openai.timeout-seconds=60
```

### Issue: Circuit breaker is open
**Solution**: Check backend service status. Circuit breaker prevents cascading failures.

---

## Performance Tuning

### Connection Pooling
Configure Vert.x connection pool size:
```properties
quarkus.http.client.max-pool-size=100
```

### Streaming Buffer Size
Adjust for large responses:
```properties
quarkus.http.body-limit=10M
```

### Timeout Settings
```properties
quarkus.quarkai.openai.timeout-seconds=30
```

---

## Monitoring

### Health Check
```bash
curl http://localhost:8080/health
```

### Metrics (if enabled)
```bash
curl http://localhost:8080/metrics
```

---

## Support

- Documentation: See README.md
- Issues: GitHub Issues
- Configuration: See CONFIGURATION.md
- Native Image: See NATIVE_IMAGE_GUIDE.md

---

## License

QuarkAI Framework is open source and available under the Apache 2.0 license.
