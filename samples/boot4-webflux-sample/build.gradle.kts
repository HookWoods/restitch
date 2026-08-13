plugins {
    java
    id("org.springframework.boot") version "4.0.0-M1"
}

group = "fr.hookwood.restitch.samples"
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
    implementation(project(":modules:aggregation-spring-boot4-starter"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
