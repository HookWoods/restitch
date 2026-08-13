# Contributing To Restitch

Thanks for helping improve Restitch. The project is a Java 17, Gradle Kotlin DSL library for REST DTO aggregation across Spring MVC and WebFlux.

## Start Here

Before opening a pull request:

1. Read `README.md` for the user-facing behavior.
2. Read `docs/configuration.md` for YAML contracts and resolver limits.
3. Read `docs/compatibility.md` before touching Spring Boot or Jackson integration code.
4. Run the focused tests for the module you changed.
5. Run the full verification command before requesting review.

```sh
./gradlew clean check
```

## Development Setup

Requirements:

- Java 17
- The checked-in Gradle wrapper
- No generated build output committed

Useful commands:

```sh
./gradlew clean check
./gradlew publishToMavenLocal
./gradlew :samples:boot3-mvc-sample:check
./gradlew :samples:boot4-webflux-sample:check
```

## Project Rules

- Public packages use `io.github.hookwoods.restitch`.
- Maven coordinates use group `io.github.hookwoods.restitch`.
- Spring Boot 3 modules use Jackson 2 only.
- Spring Boot 4 modules use Jackson 3 only.
- MVC integration uses `RestClient`.
- WebFlux integration stays native to `WebClient`, `Mono`, `Flux`, and Reactor.
- `@AggregateRef` only names a resolver profile; YAML owns routes, JSON Pointers, batching, limits, and policies.
- Resolver hosts must come from named configured clients.
- Inbound headers must only be forwarded when they are explicitly allowlisted.
- Request-local de-duplication is allowed; cross-request caching is not part of the project.

## Pull Requests

Keep pull requests small enough to review confidently. A good pull request includes:

- A clear description of the user-visible change
- Tests for new behavior or regression coverage
- Documentation updates when configuration, usage, or contracts change
- Confirmation that `./gradlew clean check` passes

Do not commit:

- Build output
- IDE workspace state
- Local credentials
- Transcripts, temporary coordination files, or generated planning notes

## Testing Expectations

Prefer focused tests first:

- API contracts: `:modules:aggregation-api:test`
- Core behavior: `:modules:aggregation-core:test`
- Boot 3 integration: `:modules:aggregation-spring-boot3-autoconfigure:test`
- Boot 4 integration: `:modules:aggregation-spring-boot4-autoconfigure:test`
- Samples: `:samples:boot3-mvc-sample:check` and `:samples:boot4-webflux-sample:check`

Then run:

```sh
./gradlew clean check
```

## Documentation

Update `README.md` when the first-run user experience changes. Update files under `docs/` when a configuration field, limit, error mode, compatibility rule, or extension point changes.

Use direct examples. A reader should be able to copy a dependency, annotation, YAML resolver, and aggregator call without piecing together details from multiple places.
