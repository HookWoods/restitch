# Releasing

This project publishes Java 17 artifacts with Maven coordinates under group `fr.hookwood.restitch`.

## Release Checklist

1. Confirm the version in `build.gradle.kts`.
2. Update `CHANGELOG.md`.
3. Run the full verification suite.
4. Publish locally and inspect generated POM metadata.
5. Create a signed release tag.
6. Publish to the target Maven repository.
7. Create a GitHub release from the tag.

```sh
./gradlew clean check
./gradlew publishToMavenLocal
```

## Maven Central Preparation

To publish to Maven Central, the project needs:

- A Sonatype Central Portal namespace for `fr.hookwood.restitch`
- Signing keys available only as local or CI secrets
- Central Portal credentials available only as local or CI secrets
- Published POM metadata for name, description, URL, license, developers, and SCM
- A release workflow that signs and uploads release artifacts

The build already defines Apache 2.0 license metadata, project URL, developer metadata, and SCM metadata in `build-logic/src/main/kotlin/aggregation.published-library.gradle.kts`.

## Versioning

Use semantic versioning after the first public release:

- Patch versions for bug fixes and documentation-only corrections
- Minor versions for backwards-compatible features
- Major versions for breaking public API or configuration changes

Before `1.0.0`, keep compatibility notes explicit in `CHANGELOG.md` because early adopters still need predictable upgrades.

## Release Command Shape

Local verification:

```sh
./gradlew clean check publishToMavenLocal
```

Tag creation:

```sh
git tag -s v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0
```

Actual Central publication should happen from CI with repository secrets, not from a developer machine.
