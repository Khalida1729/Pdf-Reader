package com.example.pdfreader.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.pdfreader.data.*

/**
 * Exports a flattened, annotated PDF. The source page is rasterized, so the output is not
 * an editable vector/text PDF. This is intentional for the first production-safe version.
 */
class PdfExporter(private val engine: PdfEngine) {
    suspend fun export(annotations: Map<Int, List<PdfAnnotation>>, output: OutputStream) = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            for (pageIndex in 0 until engine.pageCount) {
                val pageSize = engine.pageSize(pageIndex)
                val width = pageSize.x.toInt().coerceIn(720, 1800)
                val bitmap = engine.render(pageIndex, width)
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                drawAnnotations(canvas, bitmap.width.toFloat(), bitmap.height.toFloat(), annotations[pageIndex].orEmpty())
                document.finishPage(page)
                bitmap.recycle()
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun drawAnnotations(canvas: Canvas, width: Float, height: Float, items: List<PdfAnnotation>) {
        items.forEach { annotation ->
            when (annotation) {
                is InkAnnotation -> {
                    if (annotation.points.size < 2) return@forEach
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = annotation.color.toInt()
                        alpha = (annotation.opacity.coerceIn(0f, 1f) * 255).toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = annotation.strokeWidth * width
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    val path = Path()
                    annotation.points.forEachIndexed { i, p ->
                        val x = p.x * width; val y = p.y * height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, paint)
                }
                is TextAnnotation -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = annotation.color.toInt()
                        textSize = annotation.textSize * width
                        style = Paint.Style.FILL
                    }
                    canvas.drawText(annotation.text, annotation.position.x * width, annotation.position.y * height, paint)
                }
            }
        }
    }
}
