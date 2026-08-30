package com.example.pdfreader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.core.graphics.createBitmap

class PdfRendererEngine(file: File) : PdfEngine {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val mutex = Mutex()
    override val pageCount: Int get() = renderer.pageCount

    override fun pageSize(page: Int): PointF {
        synchronized(renderer) {
            val p = renderer.openPage(page)
            val size = PointF(p.width.toFloat(), p.height.toFloat())
            p.close()
            return size
        }
    }

    override suspend fun render(page: Int, targetWidth: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
        require(page in 0 until pageCount)
        val p = renderer.openPage(page)
        val ratio = p.height.toFloat() / p.width.toFloat()
        val width = targetWidth.coerceIn(360, 4096)
        val height = (width * ratio).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        bitmap.eraseColor(Color.WHITE)
        p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        p.close()
        bitmap
        }
    }

    override fun close() {
        renderer.close()
        descriptor.close()
    }
}
