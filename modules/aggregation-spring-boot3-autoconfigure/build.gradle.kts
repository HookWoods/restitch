plugins {
    id("aggregation.published-library")
}

dependencies {
    api(project(":modules:aggregation-api"))
    api(project(":modules:aggregation-core"))
    api(project(":modules:aggregation-json-spi"))
    implementation(libs.jackson2.databind)
    implementation(libs.reactor.core)
    implementation(libs.spring6.web)
    implementation("org.springframework:spring-webmvc:${libs.versions.spring6.get()}")
    implementation(libs.spring6.webflux)
    implementation(libs.spring.boot3.autoconfigure)
    annotationProcessor(libs.spring.boot3.configuration.processor)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.reactor.test)
    testImplementation(libs.spring.boot3.test)
}
