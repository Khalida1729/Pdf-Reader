package com.example.pdfreader.reader

import android.graphics.Bitmap
import android.graphics.Paint
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pdfreader.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PdfPage(
    pageIndex: Int,
    vm: PdfReaderViewModel,
    annotations: List<PdfAnnotation>,
    tool: Tool,
    zoom: Float,
    onAnnotationTool: (Tool) -> Unit
) {
    var bitmap by remember(pageIndex) {
        mutableStateOf<Bitmap?>(null)
    }

    var aspect by remember(pageIndex) {
        mutableFloatStateOf(1.414f)
    }

    var textDialog by remember {
        mutableStateOf<PdfPoint?>(null)
    }

    var textValue by remember {
        mutableStateOf("")
    }

    var canvasSize by remember(pageIndex) {
        mutableStateOf(IntSize.Zero)
    }

    val livePoints = remember {
        mutableStateListOf<PdfPoint>()
    }

    val state by vm.state.collectAsState()

    /*
     * ------------------------------------------------------------
     * PAGE SIZE
     * ------------------------------------------------------------
     */

    LaunchedEffect(pageIndex) {
        val size = withContext(Dispatchers.IO) {
            vm.pageSize(pageIndex)
        }

        if (size != null && size.x > 0f && size.y > 0f) {
            aspect = size.y / size.x
        }
    }

    /*
     * ------------------------------------------------------------
     * BITMAP LOADING
     *
     * Width is quantized to avoid rendering a new bitmap for
     * every tiny zoom change.
     * ------------------------------------------------------------
     */

    LaunchedEffect(pageIndex, zoom) {

        val safeZoom = zoom.coerceIn(1f, 4f)

        val rawWidth = (1200f * safeZoom).roundToInt()

        val targetWidth = (
                ((rawWidth + 199) / 200) * 200
                ).coerceIn(1200, 4096)

        val renderedBitmap = withContext(Dispatchers.IO) {
            vm.bitmap(
                page = pageIndex,
                width = targetWidth
            )
        }

        if (renderedBitmap != null) {
            bitmap = renderedBitmap
        }
    }

    /*
     * ------------------------------------------------------------
     * EFFECTIVE STROKE WIDTH
     * ------------------------------------------------------------
     */

    val isHighlighter = tool == Tool.HIGHLIGHT

    val defaultStrokeWidth =
        if (isHighlighter) {
            0.025f
        } else {
            0.006f
        }

    val effectiveStrokeWidth =
        if (state.annotationWidth > 0f) {
            state.annotationWidth
        } else {
            defaultStrokeWidth
        }

    /*
     * ------------------------------------------------------------
     * PAGE
     * ------------------------------------------------------------
     */

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / aspect)
            .shadow(1.dp)
            .graphicsLayer {
                scaleX = zoom.coerceIn(1f, 4f)
                scaleY = zoom.coerceIn(1f, 4f)
            }
    ) {

        /*
         * --------------------------------------------------------
         * PDF BITMAP
         * --------------------------------------------------------
         */

        bitmap?.let { b ->
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillWidth
            )
        }

        /*
         * --------------------------------------------------------
         * ANNOTATION CANVAS
         * --------------------------------------------------------
         */

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    canvasSize = it
                }
                .pointerInput(
                    tool,
                    pageIndex,
                    state.annotationColor,
                    state.annotationWidth,
                    state.annotationOpacity
                ) {

                    when (tool) {

                        /*
                         * ------------------------------------------------
                         * PEN + HIGHLIGHTER
                         * ------------------------------------------------
                         */

                        Tool.PEN,
                        Tool.HIGHLIGHT -> {

                            detectDragGestures(

                                onDragStart = { position ->

                                    if (
                                        canvasSize.width <= 0 ||
                                        canvasSize.height <= 0
                                    ) {
                                        return@detectDragGestures
                                    }

                                    livePoints.clear()

                                    livePoints.add(
                                        toPdfPoint(
                                            position,
                                            canvasSize.width.toFloat(),
                                            canvasSize.height.toFloat()
                                        )
                                    )
                                },

                                onDrag = { change, _ ->

                                    if (
                                        canvasSize.width <= 0 ||
                                        canvasSize.height <= 0
                                    ) {
                                        return@detectDragGestures
                                    }

                                    change.consume()

                                    livePoints.add(
                                        toPdfPoint(
                                            change.position,
                                            canvasSize.width.toFloat(),
                                            canvasSize.height.toFloat()
                                        )
                                    )
                                },

                                onDragEnd = {

                                    if (livePoints.size > 1) {

                                        val high =
                                            tool == Tool.HIGHLIGHT

                                        vm.addAnnotation(
                                            InkAnnotation(
                                                pageIndex = pageIndex,

                                                points =
                                                    livePoints.toList(),

                                                color =
                                                    state.annotationColor,

                                                strokeWidth =
                                                    effectiveStrokeWidth,

                                                opacity =
                                                    if (high) {
                                                        state.annotationOpacity
                                                            .coerceAtMost(0.75f)
                                                    } else {
                                                        state.annotationOpacity
                                                    },

                                                isHighlighter = high
                                            )
                                        )
                                    }

                                    livePoints.clear()
                                },

                                onDragCancel = {
                                    livePoints.clear()
                                }
                            )
                        }

                        /*
                         * ------------------------------------------------
                         * TEXT
                         * ------------------------------------------------
                         */

                        Tool.TEXT -> {

                            detectTapGestures { position ->

                                if (
                                    canvasSize.width <= 0 ||
                                    canvasSize.height <= 0
                                ) {
                                    return@detectTapGestures
                                }

                                textDialog =
                                    toPdfPoint(
                                        position,
                                        canvasSize.width.toFloat(),
                                        canvasSize.height.toFloat()
                                    )

                                textValue = ""
                            }
                        }

                        /*
                         * ------------------------------------------------
                         * ERASER
                         *
                         * Current VM API removes the last annotation.
                         * ------------------------------------------------
                         */

                        Tool.ERASER -> {

                            detectTapGestures {
                                vm.removeLastAnnotation(pageIndex)
                            }
                        }

                        /*
                         * ------------------------------------------------
                         * HAND
                         *
                         * Parent scroll/zoom system can handle this.
                         * ------------------------------------------------
                         */

                        Tool.HAND -> Unit
                    }
                }
        ) {

            /*
             * --------------------------------------------------------
             * EXISTING ANNOTATIONS
             * --------------------------------------------------------
             */

            annotations.forEach { annotation ->

                drawAnnotation(
                    annotation = annotation,
                    width = size.width,
                    height = size.height
                )
            }

            /*
             * --------------------------------------------------------
             * LIVE DRAWING PREVIEW
             * --------------------------------------------------------
             */

            if (livePoints.size > 1) {

                val path = Path()

                livePoints.forEachIndexed { index, point ->

                    val x = point.x * size.width
                    val y = point.y * size.height

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                val high =
                    tool == Tool.HIGHLIGHT

                drawPath(
                    path = path,

                    color = Color(
                        state.annotationColor
                    ).copy(
                        alpha =
                            if (high) {
                                state.annotationOpacity
                                    .coerceAtMost(0.75f)
                            } else {
                                state.annotationOpacity
                            }
                    ),

                    style = Stroke(
                        width =
                            size.width *
                                    effectiveStrokeWidth
                    )
                )
            }
        }
    }

    /*
     * ------------------------------------------------------------
     * TEXT DIALOG
     * ------------------------------------------------------------
     */

    textDialog?.let { position ->

        AlertDialog(

            onDismissRequest = {
                textDialog = null
            },

            title = {
                Text("Add text")
            },

            text = {

                OutlinedTextField(
                    value = textValue,

                    onValueChange = {
                        textValue = it
                    },

                    singleLine = false
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        if (textValue.isNotBlank()) {

                            vm.addAnnotation(
                                TextAnnotation(
                                    pageIndex = pageIndex,

                                    position = position,

                                    text = textValue,

                                    color =
                                        state.annotationColor,

                                    textSize = 0.022f
                                )
                            )
                        }

                        textDialog = null
                    }
                ) {
                    Text("Add")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        textDialog = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


/*
 * ==================================================================
 * CONVERT SCREEN/CANVAS POSITION → NORMALIZED PDF POSITION
 * ==================================================================
 */

private fun toPdfPoint(
    p: Offset,
    width: Float,
    height: Float
): PdfPoint {

    if (width <= 0f || height <= 0f) {
        return PdfPoint(0f, 0f)
    }

    return PdfPoint(
        x = (p.x / width)
            .coerceIn(0f, 1f),

        y = (p.y / height)
            .coerceIn(0f, 1f)
    )
}


/*
 * ==================================================================
 * DRAW ANNOTATION
 * ==================================================================
 */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotation(
    annotation: PdfAnnotation,
    width: Float,
    height: Float
) {

    when (annotation) {

        /*
         * ------------------------------------------------------------
         * INK / PEN / HIGHLIGHTER
         * ------------------------------------------------------------
         */

        is InkAnnotation -> {

            if (annotation.points.size < 2) {
                return
            }

            val path = Path()

            annotation.points.forEachIndexed { index, point ->

                val x = point.x * width
                val y = point.y * height

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,

                color = Color(
                    annotation.color
                ).copy(
                    alpha = annotation.opacity
                ),

                style = Stroke(
                    width =
                        annotation.strokeWidth * width
                )
            )
        }

        /*
         * ------------------------------------------------------------
         * TEXT
         * ------------------------------------------------------------
         */

        is TextAnnotation -> {

            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {

                    color = annotation.color.toInt()

                    textSize =
                        annotation.textSize * width

                    isSubpixelText = true
                }

            val startX =
                annotation.position.x * width

            val startY =
                annotation.position.y * height

            val lineHeight =
                paint.fontSpacing

            annotation.text
                .split("\n")
                .forEachIndexed { index, line ->

                    drawContext.canvas.nativeCanvas.drawText(
                        line,

                        startX,

                        startY + index * lineHeight,

                        paint
                    )
                }
        }
    }
}