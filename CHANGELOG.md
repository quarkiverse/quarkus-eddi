# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

Verified against **EDDI 6.4.0** (REST contract audit against the server source, plus
`EddiDevServicesIT` against the real `labsai/eddi:6.4.0` container).

### Added — EDDI 6.4 API coverage

- **Group conversation lifecycle** — `followUp`, `continueDiscussionAsync` (+ SSE
  `continueDiscussionStreaming`), `close`, `submitHumanInput` (HUMAN group members),
  attachments on `discuss`, and `read` / `list` / `deleteAsync` on `GroupBuilder`.
- **Admin** — `Conversation.resetState()` (`PATCH /agents/{id}/state`), live log tail
  `logs().stream(...)` and coordinator status stream `coordinator().events()` (SSE).
- **Setup models** — `hitlConfig` (new `HitlConfig` model) and `vaultKeyName` on
  `SetupAgentRequest`; `llmBaseUrl`, `hitlConfig`, `mcpServerUrls`, `maxToolIterations`,
  `vaultKeyName`, `apiAuthHeader` on `CreateApiAgentRequest`;
  `SetupResult.apiKeyVaultReference`. Pre-6.4 record constructors are kept.
- **`Context.secret(...)`** — per-request secret context values, never persisted by EDDI.
- **`PendingApprovalSummary.pendingMemberId`** — the HUMAN member a group discussion waits on.
- **Streaming** — `StreamListener.onToolCall`, `onTaskFailed` and a catch-all `onEvent`;
  `StreamToken.isToolCall()` / `isTaskFailed()`; stream errors arrive as
  `EddiStreamException` carrying EDDI's machine-readable `code()`.
- **`quarkus.eddi.timeout`** (default `5M`) — timeout of all blocking methods (was a
  hard-coded 30 s).
- **Integration tests** — `EddiDevServicesIT` (opt-in, `-Deddi.it.devservices=true`) runs
  against real EDDI + MongoDB from Dev Services: instance id, readiness, admin APIs,
  agent setup + deploy, conversation start/state/end, a real SSE turn and admin state
  reset. `EddiMcpBridgeTest` covers the MCP bridge. `*IT` tests run via failsafe.

### Fixed

- **`@EddiTool` MCP bridge registered no tools** — the `@Tool` / `@ToolArg` annotations
  added by an annotation transformer were never seen by the MCP server. The bridge now
  generates a delegate bean per class with real MCP annotations; the `name` override and
  `@ToolArg(required)` are honoured (both were dropped before). Tool methods must be
  non-private instance methods compiled with `-parameters`.
- **SSE streaming never worked** — every streaming client (`sayStreaming`, group
  discuss/approve/continue streams, log and coordinator tails) declared
  `Multi<InboundSseEvent>`, which the Quarkus REST client cannot produce; streams failed
  with a Jackson `InvalidDefinitionException`. They now use `Multi<SseEvent<String>>`.
  SSE comment frames (e.g. EDDI's log-stream heartbeats) are dropped instead of surfacing
  as empty events.
- **`quarkus.eddi.url` was ignored by the REST clients** — it is now the default for
  `quarkus.rest-client.eddi.url`, so setting it alone is enough.
- **Timeouts** — the EDDI REST clients' read timeout defaults to 300 s (Quarkus default:
  30 s), so group discussions and slow agent turns no longer time out.
- **"Unrecognized configuration key" warnings** for `quarkus.eddi.*` — `EddiConfig` is now a
  proper runtime `@ConfigRoot` and the Dev Services config a build-time one.
- **Dev Services could not start EDDI ≥ 6.3** — the container now receives the
  unauthenticated-mode opt-outs (`EDDI_SECURITY_ALLOW_UNAUTHENTICATED`,
  `EDDI_MCP_ALLOW_UNAUTHENTICATED`, `EDDI_SECRETSTORE_ALLOW_UNAUTHENTICATED`) that EDDI's
  startup guards require when OIDC is off. Default images pinned to `labsai/eddi:6.4.0`
  and `mongo:7.0.14`.
- **Dev Services lifecycle** — moved to Quarkus' owned Dev Services API: honours
  `quarkus.devservices.enabled=false`, is skipped when Docker is unavailable or when
  `quarkus.eddi.url` / `quarkus.rest-client.eddi.url` is set (an unset `${VAR}` in those
  no longer fails the build), and Quarkus handles reuse across reloads and shutdown.
- **Readiness check** probes EDDI's public `/q/health/ready` instead of the admin-only
  coordinator status, which reported DOWN for non-admin API keys.
- **`@EddiAgent` endpoints now work** — generated resources are registered through
  Quarkus REST's `GeneratedJaxRsResourceBuildItem` and responses are written as JSON.
  Unsupported `@OnMessage` / `@OnResponse` signatures now fail the build.
- **`@EddiAgent` streaming default** — `/stream` is generated for the default
  `streaming = true` (previously only when set explicitly); it no longer blocks the event
  loop when starting a conversation.
- **`@EddiAgent` conversations** are started with the caller's `userId` (previously it was
  only used as the local cache key), and each user gets exactly one conversation even under concurrent first requests.
- **Streaming task events** — `onTaskStart` / `onTaskComplete` receive the parsed
  `taskId`, `taskType`, `index` / `durationMs` instead of the raw JSON and event name.
- **Streamed HITL pauses** — the `done` event of an `AWAITING_HUMAN` turn is completed
  with the pause type and pending tool names (the SSE payload omits them).
- **`EddiDevServicesIT` silently ran against WireMock** instead of the Dev Services
  container; it now talks to the real EDDI server.

### Changed

- Removed `quarkus.eddi.mcp-bridge.enabled` / `quarkus.eddi.mcp-bridge.agents`: they were never read. The bridge is active whenever `quarkus-mcp-server-http` is present; which agents use the tools is configured in EDDI.
- Quarkus 3.17.7 → 3.39.5 (same 3.39 line as EDDI 6.4, which pins 3.39.4),
  quarkiverse-parent 18 → 24, `quarkus-mcp-server-http` → 1.13.2 (as in EDDI 6.4),
  Testcontainers 2 (`testcontainers-mongodb`), WireMock 3.13.2, GitHub Actions
  `checkout@v6`, `setup-java@v5`, `ghaction-import-gpg@v7`.
- Compiled with `release 21` (quarkiverse-parent 24 would otherwise force release 17).
- The runtime now depends on `quarkus-rest-jackson` (was `quarkus-rest`) and
  `quarkus-devservices`.
- **`ManagedConversation`**: `build()` now requires a `userId`.
- Removed the Dev Services options `quarkus.eddi.devservices.shared` and `service-name`
  (they had no effect) and the never-implemented `seed-demo-agent`.
- `EddiRealServerIT` is opt-in via `-Deddi.it.url=...` instead of auto-detecting `:7070`.
- Release workflows switched to the shared Quarkiverse prepare/perform workflows (the old
  OSSRH `release.yml` no longer worked with quarkiverse-parent 24).

### Added — HITL (Human-in-the-Loop) support

Aligns the SDK with EDDI v6's Human-in-the-Loop framework, in which a conversation
pauses in the `AWAITING_HUMAN` state pending a human approval decision.

- **`ConversationState.AWAITING_HUMAN`** — the missing sixth server constant (its absence
  previously crashed any status read of a paused conversation).
- **HITL request models** — `HitlDecision`, `HitlVerdict`, `ToolCallDecision`,
  `GroupApprovalRequest`, and the `PendingApprovalSummary` inbox model.
- **Single-agent HITL endpoints** on `EddiAgentRestClient` — `resume`, `approval-status`,
  `pending-approvals`, and `cancel`.
- **Group HITL endpoints** on `EddiGroupRestClient` — `approve` (+ SSE `approve/stream`),
  `approval-status`, per-group and cross-group `pending-approvals`, and `cancel`. The client
  now roots at `/groups` so the cross-group inbox route is reachable.
- **Group SSE streaming** — `discuss/stream` and `approve/stream` (`Multi<StreamToken>`).
- **Facade DX** — `Conversation.isAwaitingHuman()/resume()/approve()/reject()/approvalStatus()/cancel()`;
  `EddiClient.approvals()`; group approve/status/inbox on `GroupBuilder`.
- **`ConversationResult`** now surfaces HITL pause metadata (`hitlPauseType`,
  `hitlPausedAt`, `hitlPendingToolNames`, `undoAvailable`, `redoAvailable`) and
  `isAwaitingHuman()`.

### Fixed

- **Agent response / quick-reply parsing** — `ConversationResult.text()` and
  `quickReplies()` now correctly read the server's `TextOutputItem` (`text`) and
  `QuickReply` (`value`) object shapes. The previous `List<String>` cast produced garbage
  or threw `ClassCastException` on any real response.
- **Streaming completion** — the `done` SSE event is now parsed into a structured
  `ConversationResult` (text, quick replies, HITL metadata) instead of surfacing raw JSON.
- **`chat()` / `chatFull()` / `chatAsync()`** no longer auto-end a conversation left in
  `AWAITING_HUMAN` (which would cancel the pending approval).
- **`EddiLogRestClient.getHistoryLogs`** now returns typed `List<LogEntry>` instead of an
  untyped map.

### Changed

- Removed the `StreamToken.isProgress()` helper — the server never emits a `progress` event.
- Corrected the SDK-to-EDDI interface mapping table in `AGENTS.md`.

## [6.0.0] — 2026-04-15

### Added
- **Fluent Client API** — `EddiClient` CDI bean with `chat()`, `agent()`, `managed()`, `group()`, `setup()`, `admin()`, `logs()`, `coordinator()`
- **SSE Streaming** — `Multi<StreamToken>` and `StreamListener` callback API with cancellation support
- **Managed Agents** — Intent-based conversations via `ManagedConversation` (no conversation ID management)
- **@EddiAgent** — Declarative annotation that generates JAX-RS endpoints at build time with `@OnMessage`/`@OnResponse` hooks
- **@EddiTool MCP Bridge** — Expose CDI methods as MCP tools via `@EddiTool`/`@ToolArg` annotations; transparently bridges to `quarkus-mcp-server-http`
- **Dev Services** — Auto-start EDDI v6 + MongoDB via Testcontainers in dev/test mode
- **Health Check** — Async readiness probe for EDDI connectivity
- **API Key Auth** — Auto-propagated Bearer token via `quarkus.eddi.api-key`
- **Agent Setup** — Fluent builders for standard and API (OpenAPI) agent creation
- **Group Discussions** — Multi-agent debate API
- **Admin/Logs/Coordinator** — Full administration API coverage
- Extension metadata (`quarkus-extension.yaml`) for Quarkiverse catalog

### Fixed
- `chat()` / `chatFull()` no longer leak server-side conversations (auto-end after response)
- `@EddiAgent` generated endpoints use bounded conversation cache with `@PreDestroy` cleanup
- `@EddiAgent` streaming endpoint now invokes `@OnMessage` hooks (parity with non-streaming)
- `StreamListener.onComplete()` receives parsed `ConversationResult` instead of `null`
- `extractIdFromUri()` validates input and handles edge cases
- Health check respects `EddiConfig` timeouts and API key auth
- Config duplication between build-time and runtime resolved

### Changed
- Version synced to **6.0.0** to match EDDI v6
- `Conversation` constructor is now package-private (use `EddiClient.agent().startConversation()`)
- `DEFAULT_TIMEOUT` consolidated into `EddiDefaults` shared constant
