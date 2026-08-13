plugins {
    id("aggregation.java-library")
    `maven-publish`
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(providers.gradleProperty("pomName").orElse(project.name))
                description.set(providers.gradleProperty("pomDescription").orElse("Restitch library module"))
                url.set(providers.gradleProperty("pomUrl").orElse("https://github.com/HookWoods/restitch"))
                licenses {
                    license {
                        name.set(providers.gradleProperty("pomLicenseName").orElse("The Apache License, Version 2.0"))
                        url.set(providers.gradleProperty("pomLicenseUrl").orElse("https://www.apache.org/licenses/LICENSE-2.0.txt"))
                    }
                }
                developers {
                    developer {
                        id.set(providers.gradleProperty("pomDeveloperId").orElse("hookwoods"))
                        name.set(providers.gradleProperty("pomDeveloperName").orElse("HookWoods"))
                    }
                }
                scm {
                    connection.set(providers.gradleProperty("pomScmConnection").orElse("scm:git:git://github.com/HookWoods/restitch.git"))
                    developerConnection.set(providers.gradleProperty("pomScmDeveloperConnection").orElse("scm:git:ssh://git@github.com:HookWoods/restitch.git"))
                    url.set(providers.gradleProperty("pomScmUrl").orElse("https://github.com/HookWoods/restitch"))
                }
            }
        }
    }
}
