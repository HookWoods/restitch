dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

gradle.beforeProject {
    buildscript.configurations.matching { it.name == "classpath" }.configureEach {
        exclude(group = "com.google.code.gson", module = "gson")
    }
}

rootProject.name = "aggregation-build-logic"
