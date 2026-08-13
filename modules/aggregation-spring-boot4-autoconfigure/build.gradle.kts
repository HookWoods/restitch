plugins {
    id("aggregation.published-library")
}

dependencies {
    api(project(":modules:aggregation-api"))
    api(project(":modules:aggregation-core"))
    api(project(":modules:aggregation-json-spi"))
    implementation(libs.jackson3.databind)
    implementation(libs.reactor.core)
    implementation(libs.spring7.web)
    implementation(libs.spring7.webflux)
    implementation("org.springframework:spring-aop:7.0.0-M7")
    implementation(libs.spring.boot4.autoconfigure)
    annotationProcessor(libs.spring.boot4.configuration.processor)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.reactor.test)
    testImplementation(libs.spring.boot4.test)
}
