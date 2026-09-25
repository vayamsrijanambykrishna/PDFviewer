plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "in.krishna.pdfviewer.compose"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    implementation(project(":pdfviewer"))
    implementation("androidx.compose.ui:ui")
}
