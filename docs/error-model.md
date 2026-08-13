# Error Model

The global error mode can be overridden on an individual resolver profile. The four modes describe what happens when a relation cannot be resolved.

## `FAIL_FAST`

The aggregation fails and the root result is not returned. Use this when the related resource is required for a valid response.

## `NULL_FIELD`

The annotated target field is set to `null`, and the root object continues. This is useful for optional relationships.

## `KEEP_SOURCE_ID`

The source identifier remains in the object when the DTO model can represent it. No unresolved related value is invented.

## `RESULT`

The call returns `AggregationResult<T>`, containing the root value and field-level errors. Select this mode through the request or resolver configuration; the portable request contract represents it with `ErrorMode.RESULT`.

```java
AggregateRequest<Order> request = new AggregateRequest<>(
        "order-root", Map.of(), Order.class, ErrorMode.RESULT);
```

Each `AggregationError` includes the resolver name, target pointer, safe category, and correlation identifier. Downstream credentials, unrestricted URLs, and raw sensitive bodies are excluded from errors.

Timeouts, response-size violations, invalid pointers, request-limit violations, and downstream status failures are reported through the same policy path. A cancelled reactive subscription cancels pending HTTP work and root parsing.
