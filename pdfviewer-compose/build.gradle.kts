plugins {
    id("com.android.library")
        id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "in.krishna.pdfviewer.compose"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(project(":pdfviewer"))
    implementation(libs.compose.ui)
}

val versionName = project.version.toString()

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "pdfviewer-compose"
            version = versionName

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("PDFviewer Compose")
                description.set("Jetpack Compose adapter for the lightweight PDFviewer Android library.")
                url.set("https://github.com/vayamsrijanambykrishna/PDFviewer")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("vayamsrijanambykrishna")
                        name.set("Vayam Srijanam by Krishna")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/vayamsrijanambykrishna/PDFviewer.git")
                    developerConnection.set("scm:git:ssh://git@github.com/vayamsrijanambykrishna/PDFviewer.git")
                    url.set("https://github.com/vayamsrijanambykrishna/PDFviewer")
                }
            }
        }
    }
}

val repositoryUrl = System.getenv("MAVEN_REPOSITORY_URL")
if (!repositoryUrl.isNullOrBlank()) {
    publishing {
        repositories {
            maven {
                name = "Remote"
                url = uri(repositoryUrl)
                credentials {
                    username = System.getenv("MAVEN_USERNAME")
                    password = System.getenv("MAVEN_PASSWORD")
                }
            }
        }
    }
}

val signingKey = System.getenv("SIGNING_KEY")
val signingPassword = System.getenv("SIGNING_PASSWORD")
if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
    signing {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["release"])
    }
}
