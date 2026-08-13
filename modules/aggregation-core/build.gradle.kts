plugins {
    id("aggregation.published-library")
}

dependencies {
    api(project(":modules:aggregation-api"))
    api(project(":modules:aggregation-json-spi"))
}
