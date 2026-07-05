# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

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

- MCP bridge dependency bumped to `quarkus-mcp-server-http:1.13.0` to match EDDI v6.
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
