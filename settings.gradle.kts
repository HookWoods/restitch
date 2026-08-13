pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "restitch"

include(
    ":modules:aggregation-api",
    ":modules:aggregation-json-spi",
    ":modules:aggregation-core",
    ":modules:aggregation-spring-boot3-autoconfigure",
    ":modules:aggregation-spring-boot3-starter",
    ":modules:aggregation-spring-boot4-autoconfigure",
    ":modules:aggregation-spring-boot4-starter",
)
