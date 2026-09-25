package `in`.krishna.pdfviewer

class PdfViewerState {
    var currentPage: Int = -1
        internal set

    var pageCount: Int = 0
        internal set

    var zoom: Float = 1f
        internal set
}
