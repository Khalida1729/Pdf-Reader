package com.example.pdfreader.data

data class PdfPoint(val x: Float, val y: Float)

sealed interface PdfAnnotation { val pageIndex: Int }

data class InkAnnotation(
    override val pageIndex: Int,
    val points: List<PdfPoint>,
    val color: Long,
    val strokeWidth: Float,
    val opacity: Float = 1f,
    val isHighlighter: Boolean = false
) : PdfAnnotation

data class TextAnnotation(
    override val pageIndex: Int,
    val position: PdfPoint,
    val text: String,
    val color: Long,
    val textSize: Float
) : PdfAnnotation

enum class Tool { HAND, PEN, HIGHLIGHT, TEXT, ERASER }
