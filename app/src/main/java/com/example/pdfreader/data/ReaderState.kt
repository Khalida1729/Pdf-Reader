package com.example.pdfreader.data

data class ReaderState(
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val tool: Tool = Tool.HAND,
    val zoom: Float = 1f,
    val darkUi: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,

    // Annotation settings. Width is stored relative to page width.
    val annotationColor: Long = 0xFF202020L,
    val annotationWidth: Float = 0.006f,
    val annotationOpacity: Float = 1f
)
