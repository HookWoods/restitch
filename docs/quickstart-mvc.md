# MVC Quick Start

The MVC integration uses `RestClient` through `MvcAggregator`. Add the Boot 3 starter to a Java 17 Spring Boot 3.2 or newer application.

```kotlin
dependencies {
    implementation(project(":modules:aggregation-spring-boot3-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

Declare the relationship in the DTO. The annotation names a resolver profile; it does not contain a URL, pointer, client, or error policy.

```java
public final class Order {
    private String ownerId;

    @AggregateRef("order-owner")
    private User owner;
}
```

Configure the named client and resolver in `application.yml`:

```yaml
aggregation:
  clients:
    identity:
      base-url: https://identity.internal
      timeout: 500ms
      propagate-headers: [Authorization, X-Tenant, X-Correlation-ID]
  resolvers:
    order-owner:
      client: identity
      path: /users/{id}
      source-pointer: /ownerId
      response-pointer: /data/user
      error-mode: NULL_FIELD
```

Inject `MvcAggregator` and hydrate an object that is already available to the controller:

```java
return aggregator.hydrate(new Order(orderId), Order.class);
```

The complete runnable shape is in `samples/boot3-mvc-sample`. Downstream hosts remain fixed by the named client base URI, and only the configured header allowlist is forwarded.
