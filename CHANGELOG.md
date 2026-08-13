# Changelog

All notable changes to Restitch are documented here.

This project follows the spirit of Keep a Changelog and uses semantic versioning once public releases begin.

## Unreleased

### Added

- Open-source project documentation, including contribution, conduct, security, support, and release guidance.
- Public Maven group and Java package namespace `io.github.hookwoods.restitch`.

## 0.1.0 - Unreleased

### Added

- Portable API module with `@AggregateRef`, aggregate request and response contracts, error modes, and result objects.
- Portable core module for resolver profiles, plan compilation, request-local sessions, limits, batching profiles, and extension points.
- JSON SPI module shared by Spring integrations.
- Spring Boot 3 starter and auto-configuration using Jackson 2, `RestClient`, `WebClient`, and Reactor-native WebFlux support.
- Spring Boot 4 starter and auto-configuration using Jackson 3, `RestClient`, `WebClient`, and Reactor-native WebFlux support.
- MVC and WebFlux samples.
- Documentation for quick starts, configuration, compatibility, error behavior, and the batch de-duplication benchmark contract.
