plugins {
    id("com.android.library") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

allprojects {
    group = "in.krishna"
    version = providers.gradleProperty("VERSION_NAME").orElse("0.8.2").get()
}
