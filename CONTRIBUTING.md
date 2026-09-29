# Contributing to Quarkus EDDI

Thank you for your interest in contributing to Quarkus EDDI! This document provides guidelines and instructions for contributing.

## Prerequisites

- **JDK 21** or later
- **Maven 3.9+** (or use the included `mvnw` wrapper)
- **Docker** — only for Dev Services and the opt-in `EddiDevServicesIT`; the regular test suite uses WireMock and needs no Docker

## Building

```bash
# Compile check
./mvnw compile -DskipTests

# Unit tests + WireMock-based Quarkus tests (no Docker needed)
./mvnw test

# Also runs *IT tests via failsafe; they are skipped unless opted in:
./mvnw verify -Deddi.it.devservices=true          # real EDDI + MongoDB via Dev Services (Docker)
./mvnw verify -Deddi.it.url=http://localhost:7070  # an EDDI server you already run
```

## Project Structure

```
quarkus-eddi/
├── runtime/       → Consumer-facing artifact (CDI beans, REST clients, models)
└── deployment/    → Build-time processing (Gizmo codegen, Dev Services, processors)
```

- **`runtime/`** — Code that runs at application runtime. Contains the `EddiClient` facade, REST client interfaces, model records, annotations, and configuration.
- **`deployment/`** — Code that runs at Quarkus build time. Contains build step processors for feature registration, Dev Services, `@EddiAgent` endpoint generation, and `@EddiTool` MCP bridging.

## Code Style

- Java sources are formatted and imports sorted automatically by every build (formatter + impsort from quarkiverse-parent); CI builds with `-Dno-format`, which fails on unformatted code, so build locally before pushing
- Use `Uni<T>` return types for all reactive methods
- Provide blocking convenience methods with `await().atMost(DEFAULT_TIMEOUT)`
- Use Java records for immutable data types
- Add comprehensive Javadoc for all public APIs

## Commit Messages

Follow the conventional commits format:

```
feat(runtime): add streaming support
fix(devservices): correct MongoDB connection string
chore(deployment): update Quarkus BOM version
test(it): add WireMock conversation test
docs: update configuration reference
```

## Adding a New REST Endpoint

1. Check EDDI's JAX-RS interface for exact path, params, and return type
2. Add method to the appropriate `*RestClient.java` interface in `runtime/`
3. Use `Uni<T>` return types (not blocking)
4. Add a facade method in `EddiClient.java` if it improves DX
5. Add WireMock stubs in `deployment/src/test/.../WireMockEddiResource.java`
6. Add a test in the matching `deployment/src/test` class (`EddiExtensionTest`, `EddiGroupTest`, `EddiHitlTest`, `EddiStreamingTest`, ...), and to `EddiDevServicesIT` if it should be verified against a real server

## Testing

| Test Type | Location | Description |
|---|---|---|
| Unit tests | `runtime/src/test/` | Pure logic tests (no CDI context) |
| Quarkus tests | `deployment/src/test/` (`*Test`) | Full Quarkus boot against WireMock (`WireMockEddiResource`), incl. `@EddiAgent` and the MCP bridge |
| Dev Services IT | `deployment/src/test/EddiDevServicesIT` | Opt-in: `-Deddi.it.devservices=true`; real EDDI + MongoDB containers |
| Real server IT | `deployment/src/test/EddiRealServerIT` | Opt-in: `-Deddi.it.url=<EDDI base URL>` |

## Pull Requests

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes with appropriate tests
4. Ensure `./mvnw verify` passes
5. Submit a pull request with a clear description

## Reporting Issues

Please report issues on the [GitHub Issues](https://github.com/quarkiverse/quarkus-eddi/issues) page with:
- A clear description of the problem
- Steps to reproduce
- Expected vs actual behavior
- Quarkus and JDK versions

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
