plugins {
    id("com.android.library")
        id("maven-publish")
    id("signing")
}

android {
    namespace = "in.krishna.pdfviewer"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

val versionName = project.version.toString()

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "pdfviewer"
            version = versionName

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("PDFviewer")
                description.set("Lightweight Android PDF viewer built on Android PdfRenderer.")
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


dependencies {
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
