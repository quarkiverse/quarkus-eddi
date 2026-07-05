# Quarkus EDDI — The Official Quarkus SDK for EDDI

[![CI](https://github.com/quarkiverse/quarkus-eddi/actions/workflows/build.yml/badge.svg)](https://github.com/quarkiverse/quarkus-eddi/actions/workflows/build.yml) [![Maven Central](https://img.shields.io/maven-central/v/io.quarkiverse.eddi/quarkus-eddi?color=f59e0b)](https://search.maven.org/artifact/io.quarkiverse.eddi/quarkus-eddi) [![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

The official [Quarkiverse](https://github.com/quarkiverse) extension for [**EDDI**](https://github.com/labsai/EDDI) — the enterprise multi-agent orchestration middleware. Add one dependency and get **fluent conversations, SSE streaming, Dev Services, and MCP tool bridging** — all Quarkus-native.

Built for **EDDI v6**. Reactive-first. Typesafe. Zero-config in dev mode.

**Latest version: 6.0.0** · [Website](https://eddi.labs.ai/) · [Documentation](https://docs.labs.ai/) · [EDDI Server](https://github.com/labsai/EDDI) · License: Apache 2.0

---

## 📑 Table of Contents

- [✨ Features](#-features)
- [📦 Installation](#-installation)
- [⚡ Quick Start](#-quick-start)
- [🔗 @EddiAgent — Declarative Endpoints](#-eddiagentkind---declarative-endpoint-wiring-)
- [🛠️ @EddiTool — MCP Tool Bridge](#️-edditool--mcp-tool-bridge)
- [⚙️ Configuration](#️-configuration)
- [🏛️ Architecture](#️-architecture)
- [🧪 Testing](#-testing)
- [📦 The EDDI Ecosystem](#-the-eddi-ecosystem)
- [📜 License](#-license)

---

## ✨ Features

| | Feature | What You Get |
|---|---|---|
| 🚀 | **Dev Services** | Auto-starts EDDI + MongoDB via Testcontainers in `quarkus dev` — zero config |
| 💬 | **Fluent Client API** | `@Inject EddiClient eddi;` → `eddi.chat("agent", "Hello!")` — one-liner conversations |
| ⚡ | **SSE Streaming** | `Multi<StreamToken>` with full event types (`token`, `task_start`, `done`, `error`) |
| 🤖 | **Managed Agents** | Intent-based conversations — no conversation ID management needed |
| 🔗 | **@EddiAgent** 🧪 | Declarative annotation → auto-generated REST/SSE endpoints at build time *(experimental)* |
| 🛠️ | **@EddiTool MCP Bridge** | Expose CDI methods as MCP tools EDDI can call back — transparently bridges to `quarkus-mcp-server-http` |
| 👥 | **Group Discussions** | Multi-agent debates with structured discussion styles |
| ✋ | **HITL (Human-in-the-Loop)** | Pause on tool/rule approval → `resume`/`approve`/`reject`, poll `approval-status`, and an approvals inbox |
| 🔐 | **API Key Auth** | Auto-propagated Bearer token via `quarkus.eddi.api-key` |
| 💚 | **Health Check** | Async readiness probe for EDDI connectivity |

---

## 📦 Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.eddi</groupId>
    <artifactId>quarkus-eddi</artifactId>
    <version>6.0.0</version>
</dependency>
```

> 💡 Check [Maven Central](https://search.maven.org/artifact/io.quarkiverse.eddi/quarkus-eddi) for the latest release version.

That's it. In dev mode, EDDI starts automatically via Dev Services. 🎉

---

## ⚡ Quick Start

### 🗣️ One-liner — ask a question, get an answer

```java
@Inject EddiClient eddi;

public void handleRequest() {
    String answer = eddi.chat("my-agent-id", "What's the weather?");
}
```

### 🔄 Full conversation lifecycle

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

### 🔀 Reactive API

Every method has an `*Async()` variant returning `Uni<T>`:

```java
eddi.agent("my-agent")
    .startConversationAsync()
    .flatMap(conv -> conv.sayAsync("Hello!"))
    .subscribe().with(result -> log.info(result.text()));
```

### 📡 SSE Streaming

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

### 🤖 Managed Agents (Intent-based)

No conversation ID management — EDDI resolves the active conversation by intent + userId:

```java
ManagedConversation mc = eddi.managed("support").userId("user-123").build();
ConversationResult result = mc.say("I need help with my order");
mc.end();
```

### 🧪 Fluent Agent Setup

```java
// Standard agent
SetupResult result = eddi.setup()
    .agentName("Customer Support Bot")
    .systemPrompt("You are a helpful support agent for Acme Corp...")
    .provider("openai").model("gpt-4o")
    .apiKey(config.openaiKey())
    .enableBuiltInTools(true)
    .deploy(true)
    .create();

// API agent from OpenAPI spec
SetupResult apiResult = eddi.setupApi()
    .agentName("API Bot")
    .systemPrompt("You call APIs on behalf of users.")
    .openApiSpec(openApiYaml)
    .deploy(true)
    .create();
```

### 👥 Group Discussions (Multi-Agent Debates)

```java
Response result = eddi.group("architect-panel")
    .userId("user-123")
    .discuss("Monolith vs microservices?");
```

### ✋ Human-in-the-Loop (HITL)

When an agent is configured to gate tool calls (or a behavior rule pauses the turn), the
conversation stops in the `AWAITING_HUMAN` state. The SDK surfaces the pause and lets a
reviewer resume it:

```java
Conversation conv = eddi.agent("support-bot").startConversation();
ConversationResult result = conv.say("Refund order #4711");

if (result.isAwaitingHuman()) {
    // Inspect *why* it paused (no tool arguments are exposed)
    System.out.println("Paused: " + result.hitlPauseType());          // "TOOL_CALL" / "RULE"
    System.out.println("Pending tools: " + result.hitlPendingToolNames());

    // Approve or reject to resume
    conv.approve("Verified with the customer");
    // conv.reject("Not permitted");
    // conv.resume(HitlDecision.ofToolCalls(HitlVerdict.APPROVED, "partial",
    //         Map.of("call-1", ToolCallDecision.reject("unsafe"))));
}
```

> ⚠️ The one-liner `chat()` / `chatFull()` deliberately **do not** end a conversation left in
> `AWAITING_HUMAN` (ending it would cancel the pending approval). Resume it via
> `eddi.agent(id).conversation(result.conversationId())`.

Poll status and build an approvals inbox:

```java
// Poll a single conversation's approval status (summary | full)
Response status = conv.approvalStatus();

// Approvals inbox — conversations (and, cross-group, group discussions) awaiting a human
List<PendingApprovalSummary> pending = eddi.approvals().pending();
List<PendingApprovalSummary> groupPending = eddi.approvals().pendingGroups();

// Group discussions pause too — approve a paused phase
eddi.group("architect-panel").approve(groupConversationId, "Ship it");
```

---

## 🔗 @EddiAgent — Declarative Endpoint Wiring 🧪

> ⚠️ **Experimental:** This feature has a known limitation with RESTEasy Reactive. Generated endpoints may not be fully functional in all scenarios. Tracked for resolution post-6.0.0.

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

---

## 🛠️ @EddiTool — MCP Tool Bridge

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

---

## ⚙️ Configuration

All properties are under the `quarkus.eddi` namespace:

```properties
# 🌐 EDDI server URL (auto-configured by Dev Services in dev mode)
quarkus.eddi.url=http://localhost:7070

# 🔐 API key for authentication
quarkus.eddi.api-key=your-api-key

# 🌍 Default deployment environment
quarkus.eddi.environment=production

# 🚀 Dev Services
quarkus.eddi.devservices.enabled=true           # default: true in dev/test
quarkus.eddi.devservices.image=labsai/eddi:6
quarkus.eddi.devservices.mongodb-image=mongo:6.0

# 💚 Health check
quarkus.eddi.health.enabled=true
```

---

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

---

## 🧪 Testing

The extension ships with a comprehensive test suite:

| | Suite | What It Covers |
|---|---|---|
| ✅ | **Unit Tests** | Model records (incl. HITL models & `ConversationState`), snapshot parsing, API key filter, conversation lifecycle, client facade |
| 🔌 | **WireMock Tests** | Full `EddiClient` flow and the HITL round-trip (`AWAITING_HUMAN` → `resume` → `READY`, approvals inbox) against a simulated EDDI v6 server (`EddiExtensionTest`, `EddiHitlTest`) |
| 🐳 | **Dev Services** | Auto-starts EDDI + MongoDB via **Testcontainers** in dev/test mode |
| 🌐 | **Real-server IT** | `EddiRealServerIT` runs against a locally running EDDI at `localhost:7070`; **auto-skips** when none is reachable |
| 🔄 | **CI Matrix** | JDK 21 × {Ubuntu, Windows} |

---

## 📦 The EDDI Ecosystem

| | Repo | Description |
|---|---|---|
| 🧠 | [**EDDI**](https://github.com/labsai/EDDI) | Core AI orchestration engine (Java 25, Quarkus) |
| 🖥️ | [**EDDI-Manager**](https://github.com/labsai/EDDI-Manager) | Admin dashboard (React 19, Vite, Tailwind) |
| 💬 | [**eddi-chat-ui**](https://github.com/labsai/eddi-chat-ui) | Embeddable chat widget (React, TypeScript) |
| ☸️ | [**EDDI-Operator**](https://github.com/labsai/EDDI-operator) | Kubernetes operator |
| 📦 | [**quarkus-eddi**](https://github.com/quarkiverse/quarkus-eddi) | This repo — Quarkus SDK |
| 🌐 | [**EDDI Website**](https://eddi.labs.ai) | Documentation & marketing site |

---

## 🤝 Contributing

We welcome contributions! Please open an issue or submit a pull request. See the [EDDI Contributing Guide](https://github.com/labsai/EDDI/blob/main/CONTRIBUTING.md) for conventions.

## 🔒 Security

Please report security vulnerabilities privately — see the [EDDI Security Policy](https://github.com/labsai/EDDI/blob/main/SECURITY.md).

---

## 📜 License

Apache License 2.0 — see [LICENSE](LICENSE) for details.

Part of the [EDDI](https://eddi.labs.ai) ecosystem. Developed with ❤️ in Europe.
