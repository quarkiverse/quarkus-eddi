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

### Architecture — The Four Pillars

1. **Dev Services** — Auto-start EDDI + MongoDB via Testcontainers in dev/test mode
2. **Typesafe Client** — Hand-crafted REST client interfaces with Mutiny `Uni<T>` / `Multi<T>` return types
3. **@EddiAgent** — Build-time annotation scanning generates JAX-RS endpoints that proxy to EDDI agents
4. **@EddiTool (MCP Bridge)** — Expose CDI methods as MCP tools via generated `quarkus-mcp-server-http` `@Tool` delegate beans

### Key Design Decisions

- **No OpenAPI codegen**: EDDI's server uses `AsyncResponse` / `SseEventSink` which codegen can't handle. Client interfaces are hand-crafted for clean Mutiny types.
- **Client paths mirror EDDI's v6 API**: `/agents` (conversations, SSE streaming, `/agents/managed`), `/administration` (deploy, setup, logs, coordinator), `/groups` (group discussions under `/groups/{groupId}/conversations`, plus the cross-group `/groups/pending-approvals` inbox).
- **MCP dependency matches EDDI**: Uses `quarkus-mcp-server-http:1.13.2` (same artifact/version EDDI 6.4 uses). The bridge references the `@Tool`/`@ToolArg` annotations by name (no compile dependency; `quarkus-mcp-server-http` is optional and must be added by the app), so this must be kept in sync with EDDI's `quarkus-mcp-server.version`.
- **Track EDDI releases together**: keep `quarkus.version`, the MCP version and the Dev Services default image (`labsai/eddi:<x.y.z>` in `EddiDevServicesBuildTimeConfig`) on the same EDDI release. EDDI ≥ 6.3 refuses to boot with OIDC off unless `EDDI_SECURITY_ALLOW_UNAUTHENTICATED`, `EDDI_MCP_ALLOW_UNAUTHENTICATED` and `EDDI_SECRETSTORE_ALLOW_UNAUTHENTICATED` are set — Dev Services sets them.

---

## 2. Module Structure

```
quarkus-eddi/
├── .github/
│   ├── workflows/build.yml       → CI: matrix build (JDK 21, Ubuntu + Windows)
│   ├── workflows/pre-release.yml, release-prepare.yml, release-perform.yml → Quarkiverse release pipeline
│   └── project.yml               → Release version metadata
│
├── runtime/                    → Maven artifact: quarkus-eddi
│   └── io.quarkiverse.eddi/
│       ├── EddiClient.java       → Main CDI facade (@ApplicationScoped)
│       ├── Conversation.java     → Conversation lifecycle wrapper (pkg-private constructor)
│       ├── ManagedConversation.java → Intent-based conversation (no ID mgmt)
│       ├── EddiDefaults.java     → Shared constants (DEFAULT_TIMEOUT, MAX_CONVERSATIONS)
│       ├── EddiHealthCheck.java  → Async readiness probe (EDDI's /q/health/ready)
│       ├── StreamListener.java   → SSE callback interface
│       ├── annotations/          → @EddiAgent, @OnMessage, @OnResponse, @EddiTool, @ToolArg
│       ├── client/               → 9 @RegisterRestClient interfaces + API key filter
│       ├── config/               → EddiConfig (@ConfigMapping quarkus.eddi.*)
│       ├── model/                → ConversationResult, StreamToken, InputData, etc.
│       └── resources/META-INF/quarkus-extension.yaml → Extension catalog metadata
│
└── deployment/                 → Maven artifact: quarkus-eddi-deployment
    └── io.quarkiverse.eddi.deployment/
        ├── EddiProcessor.java                   → Feature/bean registration, REST client defaults (url ← quarkus.eddi.url, read-timeout)
        ├── EddiDevServicesBuildTimeConfig.java   → Build-time Dev Services config (canonical)
        ├── EddiDevServicesProcessor.java         → Docker container management
        ├── EddiAgentAnnotationProcessor.java     → @EddiAgent build-time scan (bounded map, @PreDestroy)
        └── EddiMcpBridgeProcessor.java           → @EddiTool → generated delegate beans with MCP @Tool/@ToolArg
```

---

## 3. Development Guidelines

### Building

```bash
mvn compile -DskipTests          # Compile check
mvn test                         # Run tests
mvn verify                       # + *IT via failsafe (opt-in: -Deddi.it.devservices=true | -Deddi.it.url=...)
```

> Windows: if WireMock tests fail with `Unable to establish loopback connection`, the checkout path is too long for Java's Unix-domain loopback socket under `target/`. Build from a shorter path or add `"-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp"`.

### REST Client Interfaces

The 9 REST client interfaces under `client/` map to EDDI's actual JAX-RS interfaces (verified against EDDI 6.4.0):

| SDK Interface | EDDI Interface | Base Path |
|---|---|---|
| `EddiAgentRestClient` | `IRestAgentEngine` | `/agents` (incl. HITL: `/{id}/resume`, `/{id}/approval-status`, `/pending-approvals`, `/{id}/cancel`) |
| `EddiSetupRestClient` | `IRestAgentSetup` | `/administration/agents` |
| `EddiGroupRestClient` | `IRestGroupConversation` | `/groups` (root — per-group routes carry the `/{groupId}/conversations` prefix so the cross-group `/groups/pending-approvals` inbox is reachable) |
| `EddiAdminRestClient` | `IRestAgentAdministration` | `/administration` |
| `EddiStreamingRestClient` | `IRestAgentEngineStreaming` | `/agents/{id}/stream` (SSE) |
| `EddiManagedRestClient` | `IRestAgentManagement` | `/agents/managed` |
| `EddiLogRestClient` | `IRestLogAdmin` | `/administration/logs` (incl. `/stream` SSE) |
| `EddiCoordinatorRestClient` | `IRestCoordinatorAdmin` | `/administration/coordinator` (incl. `/stream` SSE) |
| `EddiHealthRestClient` | SmallRye Health | `/q/health/ready` (public; used by `EddiHealthCheck`) |

Additionally, `EddiApiKeyFilter` adds `quarkus.eddi.api-key` as a Bearer token. It is not a global `@Provider`: each client registers it with `@RegisterProvider(EddiApiKeyFilter.class)` — every client except `EddiHealthRestClient` (the readiness endpoint is public). New clients must register it too.

When EDDI's API changes, update these interfaces accordingly.

### Conventions

- **Quarkiverse parent**: Root POM inherits `io.quarkiverse:quarkiverse-parent`
- **Group ID**: `io.quarkiverse.eddi`
- **Config prefix**: `quarkus.eddi.*`
- **Feature name**: `eddi` (registered in `EddiProcessor`)
- **REST client config key**: `eddi` (all 9 interfaces share this key)
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
| `deployment/pom.xml` | Build-time dependencies (Testcontainers, MCP deployment, core-deployment) |
| `EddiConfig.java` | All `quarkus.eddi.*` runtime configuration |
| `EddiClient.java` | Main fluent API facade (chat auto-ends conversations) |
| `EddiDefaults.java` | Shared constants (DEFAULT_TIMEOUT fallback — `quarkus.eddi.timeout` wins in the facade; MAX_CONVERSATIONS_PER_AGENT) |
| `EddiHealthCheck.java` | Async readiness probe against EDDI's public `/q/health/ready` |
| `EddiDevServicesProcessor.java` | Docker container lifecycle |
| `EddiDevServicesBuildTimeConfig.java` | Build-time config for Dev Services (canonical source) |
| `EddiAgentAnnotationProcessor.java` | @EddiAgent endpoint generation (bounded map, @PreDestroy) |
| `EddiMcpBridgeProcessor.java` | @EddiTool → generated `<Class>_EddiMcpTools` delegate beans carrying MCP `@Tool`/`@ToolArg` |
| `CHANGELOG.md` | Release notes |
| `.github/workflows/build.yml` | CI — matrix build (JDK 21, Ubuntu + Windows) |
| `.github/workflows/release-prepare.yml`, `release-perform.yml` | Quarkiverse release pipeline (shared Quarkiverse reusable workflows) |
| `.github/project.yml` | Release version metadata |
