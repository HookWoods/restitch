# WebFlux Quick Start

The WebFlux integration uses `WebClient`, `Mono`, and `Flux` natively through `ReactiveAggregator`. Add the Boot 4 starter to a Java 17 Spring Boot 4 application.

```kotlin
dependencies {
    implementation(project(":modules:aggregation-spring-boot4-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}
```

Use the same profile-only annotation and YAML-owned route configuration as the MVC integration:

```java
@AggregateRef("order-owner")
private User owner;
```

```yaml
aggregation:
  clients:
    identity:
      base-url: https://identity.internal
      propagate-headers: [Authorization, X-Tenant, X-Correlation-ID]
  resolvers:
    order-owner:
      client: identity
      path: /users/{id}
      source-pointer: /ownerId
      response-pointer: /data/user
      error-mode: NULL_FIELD
```

Return the native `Mono` from the handler:

```java
return aggregator.hydrate(new Order(orderId), Order.class);
```

For root collections, use the streaming API and retain its `Flux` without collecting it into a list. The complete handler example is in `samples/boot4-webflux-sample`.
