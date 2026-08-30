package com.example.pdfreader.data

data class ReaderState(
    val fileName: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val tool: Tool = Tool.HAND,
    val zoom: Float = 1f,
    val darkUi: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)
