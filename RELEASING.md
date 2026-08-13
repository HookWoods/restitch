# Releasing

This project publishes Java 17 artifacts with Maven coordinates under group `io.github.hookwoods.restitch`.

## Release Checklist

1. Confirm the release version and matching tag (for example, `v0.1.0`).
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

- The verified `io.github.hookwoods` Sonatype Central Portal namespace, which authorizes `io.github.hookwoods.restitch`
- A published OpenPGP public key and its private key available only through CI secrets
- Central Portal user-token credentials available only through CI secrets
- Published POM metadata for name, description, URL, license, developers, and SCM
- A release workflow that signs and uploads release artifacts

The build defines Apache 2.0 license metadata, project URL, developer metadata, SCM metadata, signed publications, and Central Portal publishing in `build-logic/src/main/kotlin/aggregation.published-library.gradle.kts`.

The default development version is `0.1.0-SNAPSHOT`. The publish workflow derives a release version only from a stable `vMAJOR.MINOR.PATCH` tag, which prevents a snapshot from being released to Central.

Configure these GitHub Actions secrets before creating the first release tag:

- `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`: Central Portal user-token credentials, not the interactive account password.
- `SIGNING_IN_MEMORY_KEY`: ASCII-armored private OpenPGP key.
- `SIGNING_IN_MEMORY_KEY_ID`: optional signing subkey ID.
- `SIGNING_IN_MEMORY_KEY_PASSWORD`: private-key passphrase.

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

The tag workflow runs `publishAndReleaseToMavenCentral`, waits for Central Portal validation, releases the deployment automatically, then creates or updates the matching GitHub release from the signed tag description. It also updates the four README installation snippets to the tag version after a successful publication. Actual Central publication happens only from CI with repository secrets, not from a developer machine.

The repository must allow the workflow `GITHUB_TOKEN` to write to `main`; if branch protection prevents direct workflow pushes, allow GitHub Actions to bypass that rule.
