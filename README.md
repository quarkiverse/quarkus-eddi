# Quarkus EDDI — The Official Quarkus SDK for EDDI

[![CI](https://github.com/quarkiverse/quarkus-eddi/actions/workflows/build.yml/badge.svg)](https://github.com/quarkiverse/quarkus-eddi/actions/workflows/build.yml) [![Maven Central](https://img.shields.io/maven-central/v/io.quarkiverse.eddi/quarkus-eddi?color=f59e0b)](https://search.maven.org/artifact/io.quarkiverse.eddi/quarkus-eddi) [![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

The official [Quarkiverse](https://github.com/quarkiverse) extension for [**EDDI**](https://github.com/labsai/EDDI) — the enterprise multi-agent orchestration middleware. Add one dependency and get **fluent conversations, SSE streaming, Dev Services, and MCP tool bridging** — all Quarkus-native.

Built for **EDDI v6**. Reactive-first. Typesafe. Zero-config in dev mode.

**Current version: 6.0.0-SNAPSHOT** (not yet released to Maven Central) · [Website](https://eddi.labs.ai/) · [Documentation](https://docs.labs.ai/) · [EDDI Server](https://github.com/labsai/EDDI) · License: Apache 2.0

---

## 📑 Table of Contents

- [✨ Features](#-features)
- [📦 Installation](#-installation)
- [⚡ Quick Start](#-quick-start)
- [🔗 @EddiAgent — Declarative Endpoints](#-eddiagent--declarative-endpoint-wiring)
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
| ⚡ | **SSE Streaming** | `Multi<StreamToken>` or `StreamListener` callbacks for every EDDI event (`token`, `task_*`, `tool_call`, `done`, `error`) |
| 🤖 | **Managed Agents** | Intent-based conversations — no conversation ID management needed |
| 🔗 | **@EddiAgent** | Declarative annotation → auto-generated REST/SSE endpoints at build time |
| 🛠️ | **@EddiTool MCP Bridge** | Expose CDI methods as MCP tools EDDI can call back — generates `quarkus-mcp-server-http` tools at build time |
| 👥 | **Group Discussions** | Multi-agent debates — follow-ups, further rounds, human members, streaming |
| ✋ | **HITL (Human-in-the-Loop)** | Pause on tool/rule approval → `resume`/`approve`/`reject`, poll `approval-status`, and an approvals inbox |
| 🔐 | **API Key Auth** | Auto-propagated Bearer token via `quarkus.eddi.api-key` |
| 💚 | **Health Check** | Async readiness probe against EDDI's `/q/health/ready` |

---

## 📦 Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.quarkiverse.eddi</groupId>
    <artifactId>quarkus-eddi</artifactId>
    <version>6.0.0-SNAPSHOT</version>
</dependency>
```

> 💡 No release has been published to Maven Central yet. Until one is, install the
> snapshot locally with `./mvnw install` and depend on `6.0.0-SNAPSHOT`.

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

    // Send messages (blocking, up to quarkus.eddi.timeout — default 5 minutes)
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
    @Override public void onToolCall(String tool) { log.info("calling " + tool); }
    @Override public void onComplete(ConversationResult result) { /* done */ }
    @Override public void onError(Throwable error) {
        // EDDI-reported errors carry a machine-readable code, e.g. "awaiting_approval"
        if (error instanceof EddiStreamException e && e.isAwaitingApproval()) { /* show approval UI */ }
    }
});
```

Also available: `onTaskStart`, `onTaskComplete`, `onTaskFailed`, and `onEvent` for any other event.
When a streamed turn pauses for approval, `onComplete` receives the pause type and pending tool names.

### 🤖 Managed Agents (Intent-based)

No conversation ID management — EDDI resolves the active conversation by intent + userId
(`userId(...)` is required; `build()` throws without it):

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

// Gate every tool call behind a human approval, and reuse a stored LLM key
eddi.setup()
    .agentName("Careful Bot").systemPrompt("...")
    .provider("openai").model("gpt-4o")
    .vaultKeyName("shared-openai")
    .hitlConfig(HitlConfig.gateTools(List.of("*"), "Tool calls need a human OK"))
    .create();
```

### 👥 Group Discussions (Multi-Agent Debates)

```java
var panel = eddi.group("architect-panel").userId("user-123");
Response result = panel.discuss("Monolith vs microservices?");

// Once COMPLETED: ask one member, run another round, or close it for good
panel.followUp(groupConversationId, "Security Reviewer", "What about auth?");
panel.continueDiscussionAsync(groupConversationId, "And for a team of five?")
        .await().indefinitely().close();   // *Async methods are lazy: subscribe or await them
panel.close(groupConversationId);

// Human members: submit a turn when the discussion is AWAITING_HUMAN_INPUT
panel.submitHumanInput(groupConversationId, "alice", "I'd keep the monolith.");
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

## 🔗 @EddiAgent — Declarative Endpoint Wiring

Annotate a class to auto-generate REST + SSE endpoints that proxy to an EDDI agent:

```java
@EddiAgent(id = "support-bot", path = "/api/support")   // streaming = true is the default
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
//            POST /api/support/stream        → SSE streaming (omitted with streaming = false)
//            Query param: ?userId=anonymous  → per-user conversation reuse
```

Both endpoints take a `text/plain` body. Each `userId` gets one EDDI conversation, started with
that userId and reused for later requests (also under concurrent requests); the cache holds up to
10,000 users per agent and evicts the least recently used.

Hooks take and return a `String` (`@OnMessage`) or a `ConversationResult` (`@OnResponse`), or return
`void` to observe only; any other signature fails the build. `@OnResponse` applies to the
non-streaming endpoint.

> 🔒 **Security:** the generated endpoints are unauthenticated and trust the `userId` query
> parameter as given — anyone who knows or guesses a userId can continue that user's
> conversation. Put the endpoint behind your own authentication (e.g. an HTTP permission policy
> for the path) and don't let clients pick arbitrary userIds; if you need the id derived from
> the authenticated principal, call `EddiClient` from your own resource instead.

---

## 🛠️ @EddiTool — MCP Tool Bridge

Expose your business logic as MCP tools that EDDI agents can invoke. The bridge needs the MCP
server extension, which you add yourself (it is an optional dependency of this extension):

```xml
<dependency>
    <groupId>io.quarkiverse.mcp</groupId>
    <artifactId>quarkus-mcp-server-http</artifactId>
    <version>1.13.2</version>
</dependency>
```

```java
@ApplicationScoped
public class OrderTools {

    @Inject OrderService orderService;

    @EddiTool(description = "Look up order status by order ID")
    public String lookupOrder(@ToolArg(description = "The order ID") String orderId) {
        return orderService.find(orderId).toString();
    }

    @EddiTool(name = "list_orders", description = "List a customer's orders")
    public String listOrders(@ToolArg(description = "Customer ID") String customerId,
                             @ToolArg(description = "Filter by status", required = false) String status) {
        return orderService.list(customerId, status).toString();
    }
}
// → Registered as MCP tools "lookupOrder" and "list_orders" on the app's MCP endpoint
```

At build time a delegate bean with real MCP `@Tool` / `@ToolArg` annotations is generated per
class; `name` overrides the tool name (default: method name) and `required` is passed through.
Rules:

- Tool methods must be non-private instance methods; the declaring class becomes a bean
  (`@ApplicationScoped` if it has no scope).
- Compile with `-parameters` (argument names become the MCP argument names) — the build fails otherwise.
- Return and parameter types follow the MCP server's own rules — e.g. `String` works, primitive
  return types such as `int` do not.
- Without `quarkus-mcp-server-http` on the classpath, the build logs a warning and registers nothing.

Point your EDDI agent's MCP server configuration at the app's MCP endpoint to let it call the tools.

---

## ⚙️ Configuration

All properties are under the `quarkus.eddi` namespace:

```properties
# 🌐 EDDI server URL (default http://localhost:7070; set automatically by Dev Services).
# Also the default for quarkus.rest-client.eddi.url, so this one property is enough.
quarkus.eddi.url=https://eddi.example.com

# ⏱️ Timeout of the blocking methods (say(), discuss(), ...). Default: 5M
quarkus.eddi.timeout=5M
# HTTP read timeout of the REST clients, in ms. Default: 300000
quarkus.rest-client.eddi.read-timeout=300000

# 🔐 API key, sent as a Bearer token
quarkus.eddi.api-key=your-api-key

# 🌍 Default deployment environment
quarkus.eddi.environment=production

# 💚 Readiness health check
quarkus.eddi.health.enabled=true

# 🚀 Dev Services (build time; dev and test mode only). Defaults shown.
quarkus.eddi.devservices.enabled=true
quarkus.eddi.devservices.image=labsai/eddi:6.4.0
quarkus.eddi.devservices.mongodb-image=mongo:7.0.14
```

Dev Services start EDDI + MongoDB unless `quarkus.eddi.devservices.enabled=false` or
`quarkus.devservices.enabled=false`, Docker is unavailable, or you set `quarkus.eddi.url` /
`quarkus.rest-client.eddi.url` yourself. The containers are managed by Quarkus and stop with
the application.

Any other `quarkus.rest-client.eddi.*` property (TLS, proxy, connect timeout, ...) applies to all
EDDI REST clients.

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
| 🔌 | **WireMock Tests** | Full `EddiClient` flow, SSE streaming, HITL round-trip, group lifecycle and generated `@EddiAgent` endpoints against a simulated EDDI v6 server (no Docker needed) |
| 🛠️ | **MCP Bridge Test** | `@EddiTool` methods are registered as MCP tools (description, arguments, name override, optional arguments) |
| 🐳 | **Dev Services IT** | `mvn verify -Deddi.it.devservices=true` — boots real EDDI + MongoDB via Testcontainers; checks readiness and admin APIs, sets up and deploys an agent, runs a conversation (incl. a real SSE turn and state reset) |
| 🌐 | **Real-server IT** | `mvn verify -Deddi.it.url=http://localhost:7070` — runs against an EDDI you already have running |
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
