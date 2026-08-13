<div align="center">

# Restitch

### Declarative cross-service DTO hydration for Spring Boot REST BFFs.

[![Java 17](https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.2%2B-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.x-13C100?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Jackson 2 and 3](https://img.shields.io/badge/Jackson-2%20%7C%203-2D6CDF?style=for-the-badge)](#compatibility)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.hookwoods.restitch/aggregation-spring-boot3-starter?style=for-the-badge&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.hookwoods.restitch/aggregation-spring-boot3-starter)
[![Gradle Kotlin DSL](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)

[Quick Start](#quick-start) |
[API Reference](https://hookwoods.github.io/restitch/) |
[Configuration](#configuration) |
[Features](#features) |
[Examples](#examples) |
[Compatibility](#compatibility)

</div>

---

## The Problem

REST BFFs often need to return DTOs that combine a root resource with data owned by other services. A service stores
those references as IDs, not database foreign keys. The usual fix is to scatter extra HTTP calls across controllers,
handlers, services, and DTO mapping code. That works at first, then repeated IDs trigger duplicate requests, headers leak
too easily, and WebFlux streams quietly turn into collected lists.

Restitch keeps that work in one place. DTO fields name a resolver profile with `@AggregateRef`, and YAML decides which
configured client to call, which JSON Pointer selects the source ID, which response value is assigned, and how failures
behave.

GraphQL federation or a gateway can solve this composition problem too. Restitch is for teams that intentionally keep
REST endpoints at the BFF boundary and still want de-duplication, batching, limits, and header discipline without
changing their external API model.

---

## Quick Start

> [!IMPORTANT]
> Restitch performs outbound HTTP calls only to named clients in `aggregation.clients`.
> Inbound headers are not forwarded by default. Add names to `propagate-headers` to forward them.
> To remove Restitch, delete the starter dependency, the `aggregation.*` YAML block, and the `@AggregateRef` annotations.

### 1. Choose your starter

Use exactly one starter. The Spring Boot generation determines the JSON stack used by Restitch.

| Your application uses | Add this starter | JSON stack |
| --- | --- | --- |
| Spring Boot 3.2+ | `aggregation-spring-boot3-starter` | Jackson 2 |
| Spring Boot 4.x | `aggregation-spring-boot4-starter` | Jackson 3 |

### 2. Install it

#### Gradle (Kotlin DSL)

Maven Central is included in most Gradle Spring Boot projects. If it is not, add `mavenCentral()` to `repositories`.

**Spring Boot 3.2+**

```kotlin
dependencies {
    implementation("io.github.hookwoods.restitch:aggregation-spring-boot3-starter:0.1.1")
}
```

**Spring Boot 4.x**

```kotlin
dependencies {
    implementation("io.github.hookwoods.restitch:aggregation-spring-boot4-starter:0.1.1")
}
```

#### Maven

Maven Central is used by default. Add one dependency to your `pom.xml`.

**Spring Boot 3.2+**

```xml
<dependency>
    <groupId>io.github.hookwoods.restitch</groupId>
    <artifactId>aggregation-spring-boot3-starter</artifactId>
    <version>0.1.1</version>
</dependency>
```

**Spring Boot 4.x**

```xml
<dependency>
    <groupId>io.github.hookwoods.restitch</groupId>
    <artifactId>aggregation-spring-boot4-starter</artifactId>
    <version>0.1.1</version>
</dependency>
```

#### Building Restitch from this repository

Use the starter matching the Spring Boot generation you are working on:

```kotlin
dependencies {
    implementation(project(":modules:aggregation-spring-boot3-starter"))
    // Or: implementation(project(":modules:aggregation-spring-boot4-starter"))
}
```

### 3. Mark the field to hydrate

```java
import io.github.hookwoods.restitch.api.AggregateRef;

public final class Order {
    private String ownerId;

    @AggregateRef("order-owner")
    private User owner;

    // getters and setters
}
```

`@AggregateRef("order-owner")` only names a resolver profile. It does not contain a URL, host, header policy, JSON
path, or error rule.

### 4. Configure the resolver

```yaml
aggregation:
  clients:
    identity:
      base-url: https://identity.internal
      timeout: 500ms
      propagate-headers:
        - Authorization
        - X-Tenant
        - X-Correlation-ID
  resolvers:
    order-owner:
      client: identity
      path: /users/{id}
      source-pointer: /ownerId
      response-pointer: /data/user
      error-mode: NULL_FIELD
```

For an `Order` with `ownerId = "42"`, Restitch calls `https://identity.internal/users/42`, selects `/data/user` from
the downstream response, and assigns that value to `owner`.

### 5. Hydrate in MVC or WebFlux

Spring MVC uses `MvcAggregator` and `RestClient`:

```java
import io.github.hookwoods.restitch.boot3.MvcAggregator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class OrderController {
    private final MvcAggregator aggregator;

    public OrderController(MvcAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @GetMapping("/orders/{orderId}")
    public Order getOrder(@PathVariable String orderId) {
        return aggregator.hydrate(new Order(orderId), Order.class);
    }
}
```

Spring WebFlux uses `ReactiveAggregator`, `WebClient`, `Mono`, and `Flux` natively:

```java
import io.github.hookwoods.restitch.boot4.ReactiveAggregator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public final class OrderHandler {
    private final ReactiveAggregator aggregator;

    public OrderHandler(ReactiveAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @GetMapping("/orders/{orderId}")
    public Mono<Order> getOrder(@PathVariable String orderId) {
        return aggregator.hydrate(new Order(orderId), Order.class);
    }
}
```

---

## See It Work

Input object:

```json
{
  "ownerId": "42",
  "owner": null
}
```

Downstream `GET /users/42` response:

```json
{
  "data": {
    "user": {
      "id": "42",
      "name": "Ada Lovelace"
    }
  }
}
```

Hydrated response:

```json
{
  "ownerId": "42",
  "owner": {
    "id": "42",
    "name": "Ada Lovelace"
  }
}
```

With batching enabled, 100 root objects containing only 10 unique owner IDs resolve through one bounded batch request
instead of 10 distinct relationship requests.

---

## Features

| Area | What Restitch provides |
|------|------------------------|
| Declarative references | `@AggregateRef` on DTO fields; YAML owns resolver behavior. |
| Spring MVC | Synchronous hydration through `MvcAggregator` and Spring `RestClient`. |
| Spring WebFlux | Native `Mono` and `Flux` hydration through `ReactiveAggregator` and `WebClient`. |
| Spring Boot 3 | Jackson 2 integration through `com.fasterxml.jackson`. |
| Spring Boot 4 | Jackson 3 integration through `tools.jackson`. |
| Session de-duplication | Repeated IDs in one root request share the same in-memory `AggregationSession`. |
| Batching | Optional resolver batch profile converts many IDs into one bounded downstream request. |
| Streaming | Root collections stream with bounded buffering, prefetch, and cancellation behavior. |
| Error policy | Resolver-level `FAIL_FAST`, `NULL_FIELD`, `KEEP_SOURCE_ID`, and `RESULT` modes. |
| Header safety | Only allowlisted inbound headers are forwarded to configured clients. |
| Host safety | Resolver paths resolve against named client base URIs, not request-controlled hosts. |
| Resource limits | Bounds for requests, concurrency, depth, bytes, buffered roots, pending IDs, batches, and sessions. |
| Extension beans | Custom response extraction, request customization, resolver backends, errors, and observer hooks. |

---

## Configuration

The full configuration lives below `aggregation`.

```yaml
aggregation:
  clients:
    identity:
      base-url: https://identity.internal
      timeout: 500ms
      propagate-headers:
        - Authorization
        - X-Tenant
        - X-Correlation-ID

  resolvers:
    order-owner:
      client: identity
      path: /users/{id}
      source-pointer: /ownerId
      response-pointer: /data/user
      error-mode: NULL_FIELD
      batch:
        path: /users
        query-parameter: ids
        items-pointer: /data/users
        item-key-pointer: /id
        max-size: 100

  limits:
    max-depth: 8
    max-requests: 256
    max-concurrency: 16
    max-response-bytes: 10485760
    max-object-bytes: 1048576
    max-buffered-items: 256
    max-session-entries: 1024
    max-session-bytes: 16777216
    max-pending-ids: 10000
    stream-prefetch: 32
    max-batch-size: 100
    batch-flush-window: 10ms
```

<details>
<summary><b>Resolver fields</b></summary>

| Field | Meaning |
|-------|---------|
| `client` | Name of a configured client under `aggregation.clients`. |
| `path` | Relative path template. `{id}` is replaced with the value selected by `source-pointer`. |
| `source-pointer` | JSON Pointer evaluated against the current source object. |
| `response-pointer` | JSON Pointer evaluated against the downstream response. |
| `error-mode` | Resolver failure behavior. Defaults to `FAIL_FAST`. |
| `batch` | Optional batch profile for resolving many IDs through one request. |

Paths must be relative and resolve under the configured client host. Request data cannot select a new host.

</details>

<details>
<summary><b>Batch fields</b></summary>

| Field | Meaning |
|-------|---------|
| `path` | Batch endpoint path, such as `/users`. |
| `query-parameter` | Query parameter that receives comma-joined IDs, such as `ids`. |
| `items-pointer` | JSON Pointer to the returned collection. |
| `item-key-pointer` | JSON Pointer inside each returned item used to match IDs. |
| `max-size` | Maximum IDs per batch request. |

Without `batch`, Restitch still de-duplicates repeated in-flight lookups inside a request. With `batch`, distinct IDs
can resolve through one bounded downstream call.

</details>

<details>
<summary><b>Default limits</b></summary>

| Limit | Default |
|-------|--------:|
| `max-depth` | 8 |
| `max-requests` | 256 |
| `max-concurrency` | 16 |
| `max-response-bytes` | 10485760 |
| `max-object-bytes` | 1048576 |
| `max-buffered-items` | 256 |
| `max-session-entries` | 1024 |
| `max-session-bytes` | 16777216 |
| `max-pending-ids` | 10000 |
| `stream-prefetch` | 32 |
| `max-batch-size` | 100 |
| `batch-flush-window` | 10ms |

Every root request creates one in-memory aggregation session. The session is cleared when the request finishes; Restitch
does not provide cross-request caching.

</details>

---

## Examples

### MVC sample

```sh
./gradlew :samples:boot3-mvc-sample:check
```

Read the runnable shape in `samples/boot3-mvc-sample`.

### WebFlux sample

```sh
./gradlew :samples:boot4-webflux-sample:check
```

Read the runnable shape in `samples/boot4-webflux-sample`.

### Annotation-driven responses

Controller methods can also be annotated with `@AggregateResponse` so the integration intercepts compatible return
types. Field hydration still comes from `@AggregateRef` on the DTO.

```java
import io.github.hookwoods.restitch.api.AggregateResponse;

@AggregateResponse("order-root")
@GetMapping("/orders/{orderId}")
public Order getOrder(@PathVariable String orderId) {
    return new Order(orderId);
}
```

The generated response uses the same resolver profiles and YAML-owned policies as the direct aggregator calls.

---

## Error Handling

Resolver profiles can choose one of four modes.

| Mode | Behavior |
|------|----------|
| `FAIL_FAST` | Stop aggregation and fail the root result. Use this for required relationships. |
| `NULL_FIELD` | Set the target field to `null` and keep the root object. |
| `KEEP_SOURCE_ID` | Keep the source identifier when the DTO can represent it. |
| `RESULT` | Return `AggregationResult<T>` with the value plus field-level `AggregationError` entries. |

```java
import io.github.hookwoods.restitch.api.AggregateRequest;
import io.github.hookwoods.restitch.api.ErrorMode;
import java.util.Map;

AggregateRequest<Order> request = new AggregateRequest<>(
        "order-root",
        Map.of(),
        Order.class,
        ErrorMode.RESULT);
```

Each `AggregationError` contains the resolver name, target pointer, safe category, and correlation ID. Raw response
bodies, unrestricted URLs, cookies, and credentials are not placed in errors.

---

## Extension Points

Define a bean when a downstream system needs custom behavior.

| Bean | Use it when |
|------|-------------|
| `AggregationResponseExtractor` | A JSON Pointer is not enough to select the response value. |
| `AggregationRequestCustomizer` | Outbound requests need safe derived headers or request metadata. |
| `AggregationResolver` | A relation comes from a non-HTTP source. |
| `AggregationErrorMapper` | Your API needs custom safe error categories. |
| `AggregationObserver` | You want lifecycle events for metrics or tracing. |

Defaults are registered only when your application has not supplied the corresponding bean.

---

## Compatibility

| Module | Runtime | JSON stack | HTTP stack |
|--------|---------|------------|------------|
| `aggregation-spring-boot3-starter` | Java 17, Spring Boot 3.2+ | Jackson 2 | `RestClient`, `WebClient` |
| `aggregation-spring-boot4-starter` | Java 17, Spring Boot 4.x | Jackson 3 | `RestClient`, `WebClient` |
| `aggregation-api` | Java 17 | None | None |
| `aggregation-core` | Java 17 | Portable JSON SPI | None |
| `aggregation-json-spi` | Java 17 | Interface only | None |

Boot 3 and Boot 4 modules are intentionally separate. Boot 3 uses Jackson 2 only; Boot 4 uses Jackson 3 only.

---

## Trust And Safety

| Concern | Restitch behavior |
|---------|-------------------|
| Network calls | Calls only named configured clients. |
| Host selection | Resolver paths are relative to configured base URIs. |
| Header forwarding | No inbound headers are forwarded unless allowlisted per client. |
| Cookies | Cookies are not forwarded unless you explicitly allowlist `Cookie`. |
| Caching | Request-local in-memory session only; no Redis, Caffeine, or cross-request cache. |
| WebFlux cancellation | Cancelling a reactive subscription stops parsing and pending HTTP work. |
| Large responses | Response bytes, object bytes, root buffering, session bytes, and pending IDs are bounded. |

---

## Build And Verify

```sh
./gradlew clean check
./gradlew publishToMavenLocal
```

CI verifies the full reactor, publication task graph, both sample projects, and both Spring generation modules.

Release publication runs from a signed stable release tag in GitHub Actions. The workflow signs artifacts and publishes them to Maven Central; see [RELEASING.md](RELEASING.md).

---

## Benchmark Contract

`benchmarks/batch-deduplication/README.md` documents the expected relationship request counts for 100 already-loaded
root objects with 10 unique related IDs.

| Configuration | Expected relationship requests |
|---------------|-------------------------------:|
| No batch profile | 10 |
| Batch profile enabled | 1 |

The repository currently documents the benchmark contract but does not include an executable benchmark project.

---

## Project Layout

```text
modules/
  aggregation-api/                         Portable annotations and result contracts
  aggregation-json-spi/                    JSON abstraction used by core code
  aggregation-core/                        Resolver profiles, planning, limits, sessions
  aggregation-spring-boot3-autoconfigure/  Boot 3 integration with Jackson 2
  aggregation-spring-boot3-starter/        Boot 3 starter
  aggregation-spring-boot4-autoconfigure/  Boot 4 integration with Jackson 3
  aggregation-spring-boot4-starter/        Boot 4 starter
samples/
  boot3-mvc-sample/
  boot4-webflux-sample/
docs/
  compatibility.md
  configuration.md
  error-model.md
  quickstart-mvc.md
  quickstart-webflux.md
```

---

## FAQ

### Does Restitch replace GraphQL?

No. Restitch keeps REST as the external API shape and hydrates related DTO fields inside your Spring application.

### Does `@AggregateRef` decide where to call?

No. The annotation only names a resolver profile. YAML owns clients, paths, pointers, batching, limits, and error modes.

### Does Restitch cache downstream responses?

Only inside one aggregation request. The `AggregationSession` de-duplicates repeated IDs and is cleared when the root
request ends.

### Can WebFlux aggregation collect the whole stream?

Root collections are streamed with bounded buffering and prefetch. Cancellation stops root parsing and pending HTTP
work.

### Where should I start reading?

Start with `docs/quickstart-mvc.md` or `docs/quickstart-webflux.md`, then read `docs/configuration.md` when you add a
second resolver.

---

## License

Restitch is available under the Apache License, Version 2.0. See `LICENSE`.
