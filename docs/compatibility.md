# Compatibility

| Library integration | Java | Spring Boot | JSON implementation                 | HTTP model                |
|---------------------|------|-------------|-------------------------------------|---------------------------|
| Boot 3 starter      | 17+  | 3.2+        | Jackson 2 (`com.fasterxml.jackson`) | `RestClient`, `WebClient` |
| Boot 4 starter      | 17+  | 4.x         | Jackson 3 (`tools.jackson`)         | `RestClient`, `WebClient` |

The portable `aggregation-api`, `aggregation-core`, and `aggregation-json-spi` modules do not depend on Spring or a
concrete Jackson package. They can be shared by both integration generations.

Boot 3 uses the application's Jackson 2 `ObjectMapper`. Boot 4 uses the application's Jackson 3 `ObjectMapper`. Boot 4
intentionally does not support a Jackson 2 compatibility path, and Boot 3 must not select Jackson 3 artifacts.

MVC uses synchronous `RestClient` execution. WebFlux keeps `WebClient`, `Mono`, and `Flux` native, including
cancellation and bounded streaming. A future-returning facade is limited to bounded single-object or page results
because a `CompletionStage` cannot represent an unbounded stream.

The versioned module dependencies and both sample configurations are kept separate so an application chooses one Spring
Boot generation and its matching JSON stack.

Release verification runs the full reactor, both starters, both samples, and a publication dry run in CI. Boot 3 and
Boot 4 generation modules remain independent matrix entries. The Boot 3 matrix uses only Jackson 2 dependencies, and the
Boot 4 matrix uses only Jackson 3 dependencies.
