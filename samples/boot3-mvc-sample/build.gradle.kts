plugins {
    java
    id("org.springframework.boot") version "3.5.4"
}

group = "io.github.restaggregation.samples"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:aggregation-spring-boot3-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
