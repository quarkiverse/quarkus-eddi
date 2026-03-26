# Quarkus EDDI Extension

[![Build](https://github.com/quarkiverse/quarkus-eddi/actions/workflows/build.yml/badge.svg)](https://github.com/quarkiverse/quarkus-eddi/actions/workflows/build.yml)
<!-- [![Maven Central](https://img.shields.io/maven-central/v/io.quarkiverse.eddi/quarkus-eddi)](https://search.maven.org/artifact/io.quarkiverse.eddi/quarkus-eddi) -->

A [Quarkiverse](https://github.com/quarkiverse) extension for integrating the [EDDI](https://github.com/labsai/EDDI) conversational AI platform into Quarkus applications.

## ✨ Features

| Feature | Description |
|---|---|
| 🚀 **Dev Services** | Auto-starts EDDI + MongoDB during `quarkus dev` — zero config |
| 💬 **Fluent Client** | `@Inject EddiClient eddi;` → `eddi.chat("agent", "Hello!")` |
| ⚡ **SSE Streaming** | `Multi<StreamToken>` and callback-based `StreamListener` |
| 🔗 **@EddiAgent** | Declaratively wire agents to REST/SSE endpoints at build time |
| 🛠️ **@EddiTool** | Expose CDI methods as MCP tools EDDI can call back |
| 🖥️ **Dev UI** | Chat with agents in the Quarkus Dev UI |
| 🏗️ **Native Image** | GraalVM native compilation support |

## 📦 Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.eddi</groupId>
    <artifactId>quarkus-eddi</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

That's it. In dev mode, EDDI starts automatically via Dev Services.

## 🚀 Quick Start

### One-liner — ask a question, get an answer

```java
@Inject EddiClient eddi;

public void handleRequest() {
    String answer = eddi.chat("my-agent-id", "What's the weather?");
}
```

### Full conversation lifecycle

```java
@Inject EddiClient eddi;

public void conversation() {
    // Start a conversation
    Conversation conv = eddi.agent("my-agent-id").startConversation();

    // Send messages
    ConversationResult result = conv.say("Hello!");
    String reply = result.text();
    List<String> quickReplies = result.quickReplies();

    // Send with context
    ConversationResult withContext = conv.sayWithContext("Book a flight",
        Map.of("userId", new Context(Context.ContextType.string, "user-123")));

    // End the conversation
    conv.end();
}
```

### SSE Streaming

```java
// Reactive (Mutiny Multi)
Multi<StreamToken> tokens = conv.sayStreaming("Tell me a story");
tokens.subscribe().with(
    token -> System.out.print(token.text()),
    error -> log.error("Stream failed", error)
);

// Callback-style
conv.sayStreaming("Explain quantum physics", new StreamListener() {
    @Override public void onToken(String text) { System.out.print(text); }
    @Override public void onThinking()         { System.out.print("🤔"); }
    @Override public void onDone(ConversationResult result) { /* done */ }
});
```

### Group Discussions (Multi-Agent Debates)

```java
GroupResult result = eddi.group("architect-panel")
    .userId("user-123")
    .discuss("Monolith vs microservices?");

System.out.println(result.synthesis());
```

### One-Command Agent Setup

```java
String agentId = eddi.setup()
    .name("Customer Support Bot")
    .systemPrompt("You are a helpful support agent for Acme Corp...")
    .provider("openai").model("gpt-4o")
    .apiKey(config.openaiKey())
    .enableBuiltInTools(true)
    .deploy()
    .create();
```

### @EddiAgent — Declarative Endpoint Wiring

Annotate a class to auto-generate REST + SSE endpoints that proxy to an EDDI agent:

```java
@EddiAgent(id = "support-bot", path = "/api/support", streaming = true)
public class SupportEndpoint {

    @OnMessage
    public void preProcess(EddiConversation conv, String message) {
        conv.addContext("department", "engineering");
    }

    @OnResponse
    public void postProcess(EddiConversation conv, ConversationResult result) {
        metricsService.recordResponse(conv.agentId(), result);
    }
}
// Generates: POST /api/support       → talk to support-bot
//            POST /api/support/stream → SSE streaming
```

### @EddiTool — MCP Tool Bridge

Expose your business logic as MCP tools that EDDI agents can invoke:

```java
@ApplicationScoped
public class OrderTools {

    @Inject OrderService orderService;

    @EddiTool(description = "Look up order status by order ID")
    public OrderStatus lookupOrder(@ToolArg(description = "The order ID") String orderId) {
        return orderService.find(orderId);
    }
}
// → Auto-registered as MCP tool, EDDI discovers and calls it during conversations
```

## ⚙️ Configuration

All properties are under the `quarkus.eddi` namespace:

```properties
# EDDI server URL (auto-configured by Dev Services in dev mode)
quarkus.eddi.url=http://localhost:7070

# Default deployment environment
quarkus.eddi.environment=production

# Dev Services
quarkus.eddi.devservices.enabled=true           # default: true in dev/test
quarkus.eddi.devservices.image=labsai/eddi:6
quarkus.eddi.devservices.mongodb-image=mongo:6.0
quarkus.eddi.devservices.seed-demo-agent=false

# MCP Tool Bridge
quarkus.eddi.mcp-bridge.enabled=true
quarkus.eddi.mcp-bridge.agents=*               # which agents see your tools

# Timeouts
quarkus.eddi.connect-timeout-ms=5000
quarkus.eddi.read-timeout-ms=30000
```

## 🏛️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Your Quarkus Application                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │  EddiClient   │  │  @EddiAgent  │  │  @EddiTool (MCP)   │    │
│  │  (CDI bean)   │  │  (generated  │  │  (auto-registered  │    │
│  │              │  │   endpoints) │  │   MCP tools)       │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬─────────────┘    │
│         │                 │                  │                   │
│  ───────┼─────────────────┼──────────────────┼───────────────   │
│         │     REST Client │                  │ MCP Server       │
└─────────┼─────────────────┼──────────────────┼───────────────────┘
          │                 │                  │
          ▼                 ▼                  ▼
    ┌─────────────────────────────────────────────────┐
    │              EDDI Server                         │
    │  Conversations │ Streaming │ Groups │ MCP Client │
    └─────────────────────────────────────────────────┘
```

- **EddiClient** → calls EDDI's REST API for conversations, setup, admin
- **@EddiAgent** → build-time generated JAX-RS endpoints that proxy to EDDI
- **@EddiTool** → your CDI methods exposed as MCP tools; EDDI discovers and invokes them

## 📁 Project Structure

```
quarkus-eddi/
├── runtime/        → Runtime CDI beans, REST clients, annotations, models
├── deployment/     → Build-time processors (Dev Services, annotation scanning)
├── integration-tests/  → WireMock + Testcontainers tests
└── docs/           → Antora documentation
```

## 🔗 Related Projects

| Project | Description |
|---|---|
| [EDDI](https://github.com/labsai/EDDI) | The EDDI backend server |
| [EDDI-Manager](https://github.com/labsai/EDDI-Manager) | Admin dashboard UI |
| [eddi-chat-ui](https://github.com/labsai/eddi-chat-ui) | Standalone chat widget |
| [eddi-website](https://github.com/labsai/eddi-website) | Documentation site |

## 📄 License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
