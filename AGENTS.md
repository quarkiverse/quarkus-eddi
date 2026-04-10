# Quarkus EDDI Extension — AI Agent Instructions

> **This file is automatically loaded by AI coding assistants. Follow ALL rules below.**

## 1. Project Context

**quarkus-eddi** is a [Quarkiverse](https://github.com/quarkiverse) extension that provides a Quarkus-native SDK for the [EDDI](https://github.com/labsai/EDDI) conversational AI platform.

### Ecosystem

| Repo | Tech | Purpose |
|---|---|---|
| **[EDDI](https://github.com/labsai/EDDI)** | Java 25, Quarkus, MongoDB | Backend engine, REST API, lifecycle pipeline |
| **quarkus-eddi** (this repo) | Java 21, Quarkus Extension | Quarkus SDK for consuming EDDI |
| **[EDDI-Manager](https://github.com/labsai/EDDI-Manager)** | React 19, Vite | Admin dashboard |
| **[eddi-chat-ui](https://github.com/labsai/eddi-chat-ui)** | React, TypeScript | Chat widget |

### Architecture — The Five Pillars

1. **Dev Services** — Auto-start EDDI + MongoDB via Testcontainers in dev/test mode
2. **Typesafe Client** — Hand-crafted REST client interfaces with Mutiny `Uni<T>` / `Multi<T>` return types
3. **Dev UI** — Chat with agents, browse conversations in Quarkus Dev UI
4. **@EddiAgent** — Build-time annotation scanning generates JAX-RS endpoints that proxy to EDDI agents
5. **@EddiTool (MCP Bridge)** — Expose CDI methods as MCP tools that EDDI can discover and invoke

### Key Design Decisions

- **No OpenAPI codegen**: EDDI's server uses `AsyncResponse` / `SseEventSink` which codegen can't handle. Client interfaces are hand-crafted for clean Mutiny types.
- **Client paths mirror EDDI's v6 API**: All under `/agents` (conversations), `/administration` (deploy), `/groups/{groupId}/conversations` (group discussions).
- **MCP dependency matches EDDI**: Uses `quarkus-mcp-server-http:1.11.0` (same artifact EDDI uses).

---

## 2. Module Structure

```
quarkus-eddi/
├── .github/
│   ├── workflows/build.yml       → CI: matrix build (JDK 21, Ubuntu + Windows)
│   ├── workflows/release.yml     → Quarkiverse release pipeline
│   └── project.yml               → Release version metadata
│
├── runtime/                    → Maven artifact: quarkus-eddi
│   └── io.quarkiverse.eddi/
│       ├── EddiClient.java       → Main CDI facade (@ApplicationScoped)
│       ├── Conversation.java     → Conversation lifecycle wrapper
│       ├── ManagedConversation.java → Intent-based conversation (no ID mgmt)
│       ├── EddiHealthCheck.java  → Async readiness probe
│       ├── StreamListener.java   → SSE callback interface
│       ├── annotations/          → @EddiAgent, @OnMessage, @OnResponse, @EddiTool, @ToolArg
│       ├── client/               → 8 @RegisterRestClient interfaces + API key filter
│       ├── config/               → EddiConfig (@ConfigMapping quarkus.eddi.*)
│       └── model/                → ConversationResult, StreamToken, InputData, etc.
│
└── deployment/                 → Maven artifact: quarkus-eddi-deployment
    └── io.quarkiverse.eddi.deployment/
        ├── EddiProcessor.java                   → Feature registration
        ├── EddiDevServicesBuildTimeConfig.java   → Build-time Dev Services config
        ├── EddiDevServicesProcessor.java         → Docker container management
        ├── EddiAgentAnnotationProcessor.java     → @EddiAgent build-time scan
        └── EddiMcpBridgeProcessor.java           → @EddiTool build-time scan
```

---

## 3. Development Guidelines

### Building

```bash
mvn compile -DskipTests          # Compile check
mvn test                         # Run tests
mvn verify                       # Full verification including integration tests
```

### REST Client Interfaces

The 8 REST client interfaces under `client/` map to EDDI's actual JAX-RS interfaces:

| SDK Interface | EDDI Interface | Base Path |
|---|---|---|
| `EddiAgentRestClient` | `IRestAgentEngine` | `/agents` |
| `EddiSetupRestClient` | `IRestAgentSetup` | `/administration/agents` |
| `EddiGroupRestClient` | `IRestGroupConversation` | `/groups` |
| `EddiAdminRestClient` | `IRestAgentAdministration` | `/administration` |
| `EddiStreamingRestClient` | `IRestAgentEngine` (SSE) | `/agents` |
| `EddiManagedRestClient` | `IRestManagedConversation` | `/managed` |
| `EddiLogRestClient` | `IRestLogAdministration` | `/administration/logs` |
| `EddiCoordinatorRestClient` | `IRestCoordinatorAdministration` | `/administration/coordinator` |

Additionally, `EddiApiKeyFilter` is a `@Provider` that auto-injects `quarkus.eddi.api-key` as a Bearer token on all client requests.

When EDDI's API changes, update these interfaces accordingly.

### Conventions

- **Quarkiverse parent**: Root POM inherits `io.quarkiverse:quarkiverse-parent`
- **Group ID**: `io.quarkiverse.eddi`
- **Config prefix**: `quarkus.eddi.*`
- **Feature name**: `eddi` (registered in `EddiProcessor`)
- **REST client config key**: `eddi` (all 8 interfaces share this key)
- **Java version**: 21 (extension consumer minimum)

### Commit Conventions

```
feat(runtime): add streaming support
fix(devservices): correct MongoDB connection string
chore(deployment): update Quarkus BOM version
test(it): add WireMock conversation test
docs: update configuration reference
```

### Adding a New REST Endpoint

1. Check EDDI's JAX-RS interface for exact path, params, and return type
2. Add method to the appropriate `*RestClient.java` interface
3. Use `Uni<T>` return types (not blocking)
4. Add a facade method in `EddiClient.java` if it improves DX
5. Update the CI OpenAPI sync check if applicable

---

## 4. Key Files

| File | Purpose |
|---|---|
| `runtime/pom.xml` | Runtime dependencies (REST client, SSE, MCP) |
| `deployment/pom.xml` | Build-time dependencies (Testcontainers, core-deployment) |
| `EddiConfig.java` | All `quarkus.eddi.*` configuration |
| `EddiClient.java` | Main fluent API facade |
| `EddiHealthCheck.java` | Async readiness health check |
| `EddiDevServicesProcessor.java` | Docker container lifecycle |
| `EddiDevServicesBuildTimeConfig.java` | Build-time config for Dev Services |
| `EddiAgentAnnotationProcessor.java` | @EddiAgent endpoint generation |
| `EddiMcpBridgeProcessor.java` | @EddiTool MCP registration |
| `.github/workflows/build.yml` | CI — matrix build, format check |
| `.github/workflows/release.yml` | Quarkiverse release pipeline |
| `.github/project.yml` | Release version metadata |
