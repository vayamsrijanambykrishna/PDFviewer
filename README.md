
# Compose PDF Viewer 🚀

[![Release](https://jitpack.io/v/vayamsrijanambykrishna/PDFviewer.svg)](https://jitpack.io/#vayamsrijanambykrishna/PDFviewer)
[![API](https://img.shields.io/badge/API-28%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=28)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A lightweight (~70 KB runtime), 120Hz gesture-smooth, and theme-adaptive PDF Viewer library engineered purely in **Jetpack Compose** using Android's native dual-pipeline `PdfRenderer`. 

Say goodbye to heavy WebViews and laggy legacy XML wrappers!

## ✨ Features

* **100% Jetpack Compose:** Built from the ground up using Compose Foundation. No legacy `AndroidView` interop.
* **Dual-Thread Rendering:** VIP priority thread for active viewport rendering, and a background dispatcher for caching & thumbnails.
* **Interactive 3D Carousel:** A floating, transparent 3D thumbnail carousel with dynamic rotation physics and ultra-thin crisp borders.
* **Smart Auto-Hide TopBar:** Features a right-to-left marquee title and auto-hides smoothly when you zoom in or fast-scroll.
* **Flawless Zoom & Pan:** Zero layout-shifts or jumping. Intelligent dynamic edge-padding ensures the top of page 1 and the bottom of the last page are never clipped during deep zooms.
* **Two Viewing Modes:** Seamlessly switch between **Continuous Vertical Scroll** and **Horizontal Swipe Pager**.
* **Highly Customizable:** Full control over UI slots, sizes, and colors via `PdfViewerConfig` and `PdfViewerColors`.

---

## 📦 Installation

**Step 1.** Add the JitPack repository to your root `settings.gradle.kts` file:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://jitpack.io") } // Add this line
    }
}
```

Step 2. Add the dependency to your app-level build.gradle.kts:
```dependencies {
    implementation("com.github.vayamsrijanambykrishna:PDFviewer:v1.0.0")
}
```

🚀 Quick Start
Using the PDF Viewer is incredibly simple. Just pass the Uri of your PDF file:
```
import `in`.krishna.pdfviewer.ui.PdfViewer
import `in`.krishna.pdfviewer.model.rememberPdfViewerState

@Composable
fun MyPdfScreen(pdfUri: Uri) {
    val viewerState = rememberPdfViewerState()

    PdfViewer(
        uri = pdfUri,
        state = viewerState,
        onBackClick = { 
            // Handle your back navigation here
        },
        modifier = Modifier.fillMaxSize()
    )
}
```

🎨 Advanced Customization
You can easily theme the viewer to match your app's branding using PdfViewerDefaults.colors() and configure behaviors via PdfViewerConfig.
```
PdfViewer(
    uri = pdfUri,
    title = "Advanced Kotlin Coroutines.pdf", // Custom title for Marquee
    colors = PdfViewerDefaults.colors(
        backgroundColor = Color(0xFFF5F5F5),
        primaryAccentColor = Color(0xFF6200EA),
        topBarBackgroundColor = Color.White,
        carouselSelectedBorderColor = Color(0xFF6200EA)
    ),
    config = PdfViewerConfig(
        maxZoomScale = 5.0f,
        enableFastScrollbar = true,
        autoHideTopBarOnZoom = true
    ),
    carouselConfig = CarouselConfig(
        enabled = true,
        showPageIndicator = true
    ),
    modifier = Modifier.fillMaxSize()
)
```
# 🤝 Contribution
Pull requests are welcome! If you find a bug or have a feature request, please open an issue.
📄 License
This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
