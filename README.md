# PDFviewer

A lightweight Android PDF viewer built on the platform `PdfRenderer` API.

## Goals

- Android 9+ (API 28+)
- Continuous vertical document scrolling
- Fast fling
- Pinch-to-zoom and double-tap zoom
- Zoomed panning
- Background page rendering
- Priority preloading and bitmap caching
- Memory-aware cache management
- Kotlin and Java compatible View API
- Optional Jetpack Compose adapter
- No bundled native PDF engine

## Android View

```kotlin
val pdfView = findViewById<PdfView>(R.id.pdfView)
pdfView.setDocument(uri)
```

Java:

```java
PdfView pdfView = findViewById(R.id.pdfView);
pdfView.setDocument(uri);
```

Jump to a page (zero-based):

```kotlin
pdfView.goToPage(4)
```

Observe the current page:

```kotlin
pdfView.setOnPageChangedListener { page ->
    // page is zero-based
}
```

Rotate:

```kotlin
pdfView.rotate()
pdfView.rotate(false)
pdfView.resetRotation()
```

## Jetpack Compose

The Compose adapter is a separate artifact so applications that do not use Compose do not pay the Compose dependency cost.

```kotlin
PdfViewer(uri = pdfUri)
```

## Rendering model

The library uses Android's native `android.graphics.pdf.PdfRenderer`. PDF pages are rendered into bitmaps off the UI thread and displayed using Android Canvas transforms during gestures.

No C/C++ PDF rendering engine is bundled.

## Public API

The public surface is intentionally small:

- `PdfView`
- `PdfViewerConfig`
- `PdfViewerState`
- `PdfViewer` (Compose adapter)

Internal rendering, scheduling and caching classes are not part of the supported API.

## Requirements

- Android API 28+
- A readable PDF `Uri` exposed through `ContentResolver`
