import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.publish.PublishingExtension

plugins {
    base
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "io.github.restaggregation"
version = "0.1.0-SNAPSHOT"

subprojects {
    when (path) {
        ":samples:boot3-mvc-sample" -> pluginManager.withPlugin("java") {
            dependencies {
                add("implementation", platform("org.springframework.boot:spring-boot-dependencies:${catalog.findVersion("boot3").get().requiredVersion}"))
            }
        }
        ":samples:boot4-webflux-sample" -> pluginManager.withPlugin("java") {
            dependencies {
                add("implementation", platform("org.springframework.boot:spring-boot-dependencies:${catalog.findVersion("boot4").get().requiredVersion}"))
            }
        }
    }

    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/HookWoods/restitch")
                    credentials {
                        username = System.getenv("MAVEN_USERNAME")
                            ?: System.getenv("GITHUB_ACTOR")
                            ?: ""
                        password = System.getenv("MAVEN_TOKEN")
                            ?: System.getenv("GITHUB_TOKEN")
                            ?: ""
                    }
                }
            }
        }
    }
}
