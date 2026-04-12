# Changelog

All notable changes to this project will be documented in this file.

## [6.0.0] — 2026-04-12

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
