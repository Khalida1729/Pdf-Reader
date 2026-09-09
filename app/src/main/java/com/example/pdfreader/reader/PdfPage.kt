package com.example.pdfreader.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.pdfreader.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PdfPage(
    pageIndex: Int,
    vm: PdfReaderViewModel,
    annotations: List<PdfAnnotation>,
    tool: Tool,
    zoom: Float,
    onAnnotationTool: (Tool) -> Unit
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var aspect by remember(pageIndex) { mutableFloatStateOf(1.414f) }
    var textDialog by remember { mutableStateOf<PdfPoint?>(null) }
    var textValue by remember { mutableStateOf("") }

    val livePoints = remember { mutableStateListOf<PdfPoint>() }
    val state by vm.state.collectAsState()

    LaunchedEffect(pageIndex) {
        val size = withContext(Dispatchers.IO) { vm.pageSize(pageIndex) }
        if (size != null && size.x > 0f) {
            aspect = size.y / size.x
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / aspect)
            .shadow(1.dp)
    ) {
        bitmap?.let { b ->
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth
            )
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(tool, pageIndex, state.annotationColor, state.annotationWidth, state.annotationOpacity) {
                    when (tool) {
                        Tool.PEN, Tool.HIGHLIGHT -> detectDragGestures(
                            onDragStart = {
                                livePoints.clear()
                                livePoints.add(
                                    toPdfPoint(
                                        it,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                livePoints.add(
                                    toPdfPoint(
                                        change.position,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                )
                            },
                            onDragEnd = {
                                if (livePoints.size > 1) {
                                    val high = tool == Tool.HIGHLIGHT
                                    val defaultWidth =
                                        if (high) 0.025f else 0.006f

                                    vm.addAnnotation(
                                        InkAnnotation(
                                            pageIndex = pageIndex,
                                            points = livePoints.toList(),
                                            color = state.annotationColor,
                                            strokeWidth =
                                                if (state.annotationWidth > 0f)
                                                    state.annotationWidth
                                                else defaultWidth,
                                            opacity =
                                                if (high)
                                                    state.annotationOpacity.coerceAtMost(0.75f)
                                                else state.annotationOpacity,
                                            isHighlighter = high
                                        )
                                    )
                                }
                                livePoints.clear()
                            }
                        )

                        Tool.TEXT -> detectTapGestures {
                            textDialog = toPdfPoint(
                                it,
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                            textValue = ""
                        }

                        Tool.ERASER -> detectTapGestures {
                            vm.removeLastAnnotation(pageIndex)
                        }

                        Tool.HAND -> Unit
                    }
                }
        ) {
            annotations.forEach {
                drawAnnotation(it, size.width, size.height)
            }

            if (livePoints.size > 1) {
                val path = Path()
                livePoints.forEachIndexed { i, p ->
                    val point = Offset(
                        p.x * size.width,
                        p.y * size.height
                    )
                    if (i == 0) path.moveTo(point.x, point.y)
                    else path.lineTo(point.x, point.y)
                }

                val high = tool == Tool.HIGHLIGHT
                drawPath(
                    path = path,
                    color = Color(state.annotationColor)
                        .copy(
                            alpha = if (high)
                                state.annotationOpacity.coerceAtMost(0.75f)
                            else state.annotationOpacity
                        ),
                    style = Stroke(
                        width = size.width * state.annotationWidth
                    )
                )
            }
        }
    }

    LaunchedEffect(pageIndex, zoom, bitmap) {
        if (bitmap == null) {
            bitmap = vm.bitmap(pageIndex, 1200)
        }
    }

    if (textDialog != null) {
        AlertDialog(
            onDismissRequest = { textDialog = null },
            title = { Text("Add text") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = false
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = textDialog!!
                    if (textValue.isNotBlank()) {
                        vm.addAnnotation(
                            TextAnnotation(
                                pageIndex = pageIndex,
                                position = p,
                                text = textValue,
                                color = state.annotationColor,
                                textSize = 0.022f
                            )
                        )
                    }
                    textDialog = null
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { textDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun toPdfPoint(
    p: Offset,
    width: Float,
    height: Float
): PdfPoint =
    PdfPoint(
        (p.x / width).coerceIn(0f, 1f),
        (p.y / height).coerceIn(0f, 1f)
    )

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotation(
    annotation: PdfAnnotation,
    width: Float,
    height: Float
) {
    when (annotation) {
        is InkAnnotation -> {
            if (annotation.points.size < 2) return

            val path = Path()
            annotation.points.forEachIndexed { i, p ->
                val x = p.x * width
                val y = p.y * height
                if (i == 0) path.moveTo(x, y)
                else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = Color(annotation.color)
                    .copy(alpha = annotation.opacity),
                style = Stroke(
                    width = annotation.strokeWidth * width
                )
            )
        }

        is TextAnnotation -> {
            drawContext.canvas.nativeCanvas.drawText(
                annotation.text,
                annotation.position.x * width,
                annotation.position.y * height,
                android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color = annotation.color.toInt()
                    textSize = annotation.textSize * width
                }
            )
        }
    }
}
