# Configuration

Configuration is organized below `aggregation`. A client is named once and resolver profiles refer to that name. `AggregateRef` only names the profile on a DTO field.

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
      batch:
        path: /users
        query-parameter: ids
        items-pointer: /data/users
        item-key-pointer: /id
        max-size: 100
  limits:
    max-concurrency: 16
```

## Pointers and routes

`source-pointer` is evaluated against the source object before the request. Its value supplies `{id}` in the configured path. `response-pointer` is evaluated against the downstream JSON response, and the selected value becomes the annotated field. Both values use JSON Pointer syntax, such as `/ownerId` and `/data/user`.

The client base URI is fixed configuration. Path templates may use the source identifier and declared variables only; data from a request cannot select a host. Resolver profiles do not infer `content`, `pageable`, or `identifier` fields.

## Batching

When `batch` is present, missing identifiers are sent through one bounded request up to `max-size`. `items-pointer` selects the returned collection and `item-key-pointer` selects each item key. Without a batch definition, the session still de-duplicates equivalent in-flight lookups, but each distinct key uses its own request.

## Headers and limits

Inbound headers are never forwarded by default. `propagate-headers` is an explicit allowlist for that named client. Credentials and cookies not listed there remain local to the incoming request.

Set limits for concurrency, request count, response bytes, object size, buffered root items, pending identifiers, session entries, session bytes, batch size, and stream prefetch according to the application workload. Limits apply within one aggregation session; completed values are not a cross-request cache.

## Extension beans

Optional named beans can customize irregular integrations without changing the portable contracts:

- `AggregationResponseExtractor` selects a response value when JSON Pointers are insufficient.
- `AggregationRequestCustomizer` returns safe outbound headers or request metadata.
- `AggregationResolver` resolves a configured relation from a non-HTTP source.
- `AggregationErrorMapper` maps downstream failures to `AggregationError`.
- `AggregationObserver` receives resolution lifecycle events.

Defaults are used only when an application has not supplied the corresponding bean.
