# Support

Restitch is maintained as an open-source library.

## Questions

Use GitHub Discussions if they are enabled. Otherwise, open an issue with the `question` template.

Good questions include:

- Your Spring Boot generation
- Whether you use MVC or WebFlux
- The relevant `aggregation.*` YAML
- A small DTO example
- The expected output and actual output

## Bugs

Open a bug report when behavior is unexpected or a documented guarantee is not met.

Include:

- Restitch version
- Java version
- Spring Boot version
- Jackson generation
- Full stack trace when available
- A minimal reproduction or sample request flow

## Feature Requests

Open a feature request when the current model cannot express a real use case.

Please describe the use case before proposing an API. Restitch intentionally keeps resolver behavior in YAML and keeps `@AggregateRef` small, so new public contracts need a clear reason.
