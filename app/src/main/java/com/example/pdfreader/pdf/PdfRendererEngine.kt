package com.example.pdfreader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PdfRenderer is not thread-safe. Every renderer/page operation is serialized
 * through one dedicated thread.
 */
class PdfRendererEngine(file: File) : PdfEngine {
    private val descriptor = ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_READ_ONLY
    )
    private val renderer = PdfRenderer(descriptor)
    private val dispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "PdfRendererEngine").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    private val closed = AtomicBoolean(false)
    private val cachedPageCount = renderer.pageCount

    override val pageCount: Int
        get() = cachedPageCount

    override fun pageSize(page: Int): PointF = runBlocking {
        withContext(dispatcher) {
            checkOpen()
            require(page in 0 until cachedPageCount)
            renderer.openPage(page).use { p ->
                PointF(p.width.toFloat(), p.height.toFloat())
            }
        }
    }

    override suspend fun render(page: Int, targetWidth: Int): Bitmap =
        withContext(dispatcher) {
            checkOpen()
            require(page in 0 until cachedPageCount)

            renderer.openPage(page).use { p ->
                val ratio = p.height.toFloat() / p.width.toFloat()
                val width = targetWidth.coerceIn(360, 4096)
                val height = (width * ratio).toInt().coerceAtLeast(1)
                val bitmap = createBitmap(width, height)
                bitmap.eraseColor(Color.WHITE)
                p.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                bitmap
            }
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        runBlocking {
            withContext(dispatcher) {
                renderer.close()
                descriptor.close()
            }
        }
        dispatcher.close()
    }

    private fun checkOpen() {
        check(!closed.get()) { "PDF renderer is closed" }
    }
}