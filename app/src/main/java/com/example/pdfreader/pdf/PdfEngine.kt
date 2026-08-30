package com.example.pdfreader.pdf

import android.graphics.Bitmap
import android.graphics.PointF

interface PdfEngine : AutoCloseable {
    val pageCount: Int
    fun pageSize(page: Int): PointF
    suspend fun render(page: Int, targetWidth: Int): Bitmap
    override fun close()
}
