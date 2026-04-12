# Quarkus EDDI Extension

[![Build](https://github.com/quarkiverse/quarkus-eddi/actions/workflows/build.yml/badge.svg)](https://github.com/quarkiverse/quarkus-eddi/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.quarkiverse.eddi/quarkus-eddi)](https://search.maven.org/artifact/io.quarkiverse.eddi/quarkus-eddi)

A [Quarkiverse](https://github.com/quarkiverse) extension for integrating the [EDDI](https://github.com/labsai/EDDI) conversational AI platform into Quarkus applications. Built exclusively for **EDDI v6**.

## ✨ Features

| Feature | Description |
|---|---|
| 🚀 **Dev Services** | Auto-starts EDDI + MongoDB during `quarkus dev` — zero config |
| 💬 **Fluent Client** | `@Inject EddiClient eddi;` → `eddi.chat("agent", "Hello!")` |
| ⚡ **SSE Streaming** | `Multi<StreamToken>` with full event types (`token`, `task_start`, `done`, `error`) |
| 🤖 **Managed Agents** | Intent-based conversations — no conversation ID management needed |
| 🔗 **@EddiAgent** | Declaratively wire agents to REST/SSE endpoints at build time |
| 🛠️ **@EddiTool** | Expose CDI methods as MCP tools EDDI can call back |
| 🔐 **API Key Auth** | Auto-propagated Bearer token via `quarkus.eddi.api-key` |
| 💚 **Health Check** | Async readiness probe for EDDI connectivity |

## 📦 Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.eddi</groupId>
    <artifactId>quarkus-eddi</artifactId>
    <version>6.0.0-SNAPSHOT</version>
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

    // Send messages (blocking, 30s timeout)
    ConversationResult result = conv.say("Hello!");
    String reply = result.text();
    List<String> quickReplies = result.quickReplies();

    // Send with context
    ConversationResult withContext = conv.sayWithContext("Book a flight",
        Map.of("userId", Context.of("user-123")));

    // Undo / Redo
    if (conv.isUndoAvailableAsync().await().atMost(Duration.ofSeconds(5))) {
        conv.undo();
    }

    // End the conversation
    conv.end();
}
```

### Reactive API

Every method has an `*Async()` variant returning `Uni<T>`:

```java
eddi.agent("my-agent")
    .startConversationAsync()
    .flatMap(conv -> conv.sayAsync("Hello!"))
    .subscribe().with(result -> log.info(result.text()));
```

### SSE Streaming

```java
// Reactive (Mutiny Multi) — event names are preserved from the server
Multi<StreamToken> tokens = conv.sayStreaming("Tell me a story");
tokens.subscribe().with(token -> {
    if (token.isToken())  System.out.print(token.text());
    if (token.isDone())   System.out.println("\n[Complete]");
    if (token.isError())  System.err.println("Error: " + token.text());
});

// Callback-style
conv.sayStreaming("Explain quantum physics", new StreamListener() {
    @Override public void onToken(String text) { System.out.print(text); }
    @Override public void onComplete(ConversationResult result) { /* done */ }
    @Override public void onError(Throwable error) { log.error("Failed", error); }
});
```

### Managed Agents (Intent-based)

No conversation ID management — EDDI resolves the active conversation by intent + userId:

```java
ManagedConversation mc = eddi.managed("support").userId("user-123").build();
ConversationResult result = mc.say("I need help with my order");
mc.end();
```

### Fluent Agent Setup

```java
// Standard agent
SetupResult result = eddi.setup()
    .name("Customer Support Bot")
    .systemPrompt("You are a helpful support agent for Acme Corp...")
    .provider("openai").model("gpt-4o")
    .apiKey(config.openaiKey())
    .enableBuiltInTools(true)
    .deploy(true)
    .create();

// API agent from OpenAPI spec
SetupResult apiResult = eddi.setupApi()
    .name("API Bot")
    .systemPrompt("You call APIs on behalf of users.")
    .openApiSpec(openApiYaml)
    .deploy(true)
    .create();
```

### Group Discussions (Multi-Agent Debates)

```java
Response result = eddi.group("architect-panel")
    .userId("user-123")
    .discuss("Monolith vs microservices?");
```

### @EddiAgent — Declarative Endpoint Wiring

Annotate a class to auto-generate REST + SSE endpoints that proxy to an EDDI agent:

```java
@EddiAgent(id = "support-bot", path = "/api/support", streaming = true)
public class SupportEndpoint {

    @OnMessage
    public String preProcess(String message) {
        // Optionally transform the message before sending to EDDI
        return message + " [department: engineering]";
    }

    @OnResponse
    public void postProcess(ConversationResult result) {
        metricsService.recordResponse(result);
    }
}
// Generates: POST /api/support              → talk to support-bot
//            POST /api/support/stream        → SSE streaming
//            Query param: ?userId=anonymous  → per-user conversation reuse
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

# API key for authentication
quarkus.eddi.api-key=your-api-key

# Default deployment environment
quarkus.eddi.environment=production

# Dev Services
quarkus.eddi.devservices.enabled=true           # default: true in dev/test
quarkus.eddi.devservices.image=labsai/eddi:6
quarkus.eddi.devservices.mongodb-image=mongo:6.0

# Health check
quarkus.eddi.health.enabled=true
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
    │              EDDI v6 Server                      │
    │  Conversations │ Streaming │ Groups │ MCP Client │
    └─────────────────────────────────────────────────┘
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
