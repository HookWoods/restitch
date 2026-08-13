#!/usr/bin/env bash

set -euo pipefail

workspace="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sample="${1:?sample path is required}"

case "$sample" in
  samples/boot3-mvc-sample|samples/boot4-webflux-sample) ;;
  *)
    printf 'Unsupported sample: %s\n' "$sample" >&2
    exit 2
    ;;
esac

verification_dir="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/restitch-${sample##*/}.XXXXXX")"
settings_file="$verification_dir/settings.gradle.kts"
trap 'rm -rf "$verification_dir"' EXIT
sample_project=":${sample//\//:}"

ln -s "$workspace/build.gradle.kts" "$verification_dir/build.gradle.kts"

cat > "$settings_file" <<EOF
pluginManagement {
    includeBuild("$workspace/build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("$workspace/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "rest-aggregation-sample-verification"

include(
    ":modules",
    ":samples",
    ":modules:aggregation-api",
    ":modules:aggregation-json-spi",
    ":modules:aggregation-core",
    ":modules:aggregation-spring-boot3-autoconfigure",
    ":modules:aggregation-spring-boot3-starter",
    ":modules:aggregation-spring-boot4-autoconfigure",
    ":modules:aggregation-spring-boot4-starter",
)

project(":modules").projectDir = file("$workspace/modules")
project(":samples").projectDir = file("$workspace/samples")
project(":modules:aggregation-api").projectDir = file("$workspace/modules/aggregation-api")
project(":modules:aggregation-json-spi").projectDir = file("$workspace/modules/aggregation-json-spi")
project(":modules:aggregation-core").projectDir = file("$workspace/modules/aggregation-core")
project(":modules:aggregation-spring-boot3-autoconfigure").projectDir = file("$workspace/modules/aggregation-spring-boot3-autoconfigure")
project(":modules:aggregation-spring-boot3-starter").projectDir = file("$workspace/modules/aggregation-spring-boot3-starter")
project(":modules:aggregation-spring-boot4-autoconfigure").projectDir = file("$workspace/modules/aggregation-spring-boot4-autoconfigure")
project(":modules:aggregation-spring-boot4-starter").projectDir = file("$workspace/modules/aggregation-spring-boot4-starter")

include("$sample_project")
project("$sample_project").projectDir = file("$workspace/$sample")
EOF

sample_task="$sample_project:check"
./gradlew --project-dir "$verification_dir" "$sample_task" --no-daemon --console=plain
