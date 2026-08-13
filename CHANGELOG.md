# Changelog

All notable changes to Restitch are documented here.

This project follows the spirit of Keep a Changelog and uses semantic versioning once public releases begin.

## Unreleased

## 0.1.1 - 2026-08-14

### Added

- Public API Javadocs and a GitHub Pages deployment after each successful stable release.

### Changed

- Public API, SPI, core, and Spring extension contracts now include complete Javadocs.
- Pull requests must update the `Unreleased` changelog section before they can pass the release-notes check.

## 0.1.0 - 2026-08-13

### Added

- Portable API module with `@AggregateRef`, aggregate request and response contracts, error modes, and result objects.
- Portable core module for resolver profiles, plan compilation, request-local sessions, limits, batching profiles, and extension points.
- JSON SPI module shared by Spring integrations.
- Spring Boot 3 starter and auto-configuration using Jackson 2, `RestClient`, `WebClient`, and Reactor-native WebFlux support.
- Spring Boot 4 starter and auto-configuration using Jackson 3, `RestClient`, `WebClient`, and Reactor-native WebFlux support.
- MVC and WebFlux samples.
- Documentation for quick starts, configuration, compatibility, error behavior, batch de-duplication benchmark contract, contribution, conduct, security, support, and release guidance.
- Maven Central publishing for the `io.github.hookwoods.restitch` group, using signed stable release tags.
