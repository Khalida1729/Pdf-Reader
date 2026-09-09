package com.example.pdfreader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PdfReaderScreen(
    vm: PdfReaderViewModel,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val annotations by vm.annotations.collectAsStateWithLifecycle()

    val listState = rememberLazyListState(state.currentPage)
    var showChrome by remember { mutableStateOf(true) }
    var showJump by remember { mutableStateOf(false) }
    var showStylePanel by remember { mutableStateOf(false) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()

    // Normal reading mode: controls disappear after 3 seconds.
    LaunchedEffect(showChrome, state.tool) {
        if (showChrome && state.tool == com.example.pdfreader.data.Tool.HAND) {
            delay(3000.milliseconds)
            showChrome = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { page -> vm.setPage(page) }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && state.tool == com.example.pdfreader.data.Tool.HAND) {
            showChrome = false
        }
    }

    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
        panX += panChange.x
        panY += panChange.y
        vm.setZoom(zoom)
        showChrome = false
    }

    Box(Modifier.fillMaxSize()) {

        // PDF surface
        Box(
            Modifier
                .fillMaxSize()
                .transformable(transformState)
                .pointerInput(state.tool) {
                    if (state.tool == com.example.pdfreader.data.Tool.HAND) {
                        detectTapGestures(
                            onTap = {
                                showChrome = !showChrome
                                if (showChrome) {
                                    // The LaunchedEffect above will start the 3s timer.
                                }
                            }
                        )
                    }
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = panX
                        translationY = panY
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    top = if (showChrome) 72.dp else 8.dp,
                    bottom = if (showChrome || state.tool != com.example.pdfreader.data.Tool.HAND) 92.dp else 8.dp
                )
            ) {
                items((0 until state.pageCount).toList(), key = { it }) { page ->
                    PdfPage(
                        pageIndex = page,
                        vm = vm,
                        annotations = annotations[page].orEmpty(),
                        tool = state.tool,
                        zoom = zoom,
                        onAnnotationTool = vm::setTool
                    )
                }
            }
        }

        // Top controls
        AnimatedVisibility(
            visible = showChrome,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        state.fileName.ifBlank { "PDF Reader" },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "${state.currentPage + 1}/${state.pageCount}",
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    SmallAction("Go") { showJump = true }
                    SmallAction("Save") { onSave() }
                    SmallAction("Share") { onShare() }
                    SmallAction("Open") { onOpen() }
                }
            }
        }

        if (state.isSaving) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }

        // Bottom annotation toolbar. It remains visible while annotating.
        AnimatedVisibility(
            visible = showChrome || state.tool != com.example.pdfreader.data.Tool.HAND,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (showStylePanel &&
                    (state.tool == com.example.pdfreader.data.Tool.PEN ||
                     state.tool == com.example.pdfreader.data.Tool.HIGHLIGHT)
                ) {
                    AnnotationStylePanel(
                        state = state,
                        onColor = vm::setAnnotationColor,
                        onWidth = vm::setAnnotationWidth,
                        onOpacity = vm::setAnnotationOpacity
                    )
                }

                Surface(tonalElevation = 5.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolButton("Hand", com.example.pdfreader.data.Tool.HAND, state.tool, vm::setTool)
                        ToolButton("Pen", com.example.pdfreader.data.Tool.PEN, state.tool, {
                            vm.setTool(it)
                            showChrome = true
                            showStylePanel = true
                        })
                        ToolButton("High", com.example.pdfreader.data.Tool.HIGHLIGHT, state.tool, {
                            vm.setTool(it)
                            showChrome = true
                            showStylePanel = true
                        })
                        ToolButton("Text", com.example.pdfreader.data.Tool.TEXT, state.tool, {
                            vm.setTool(it)
                            showChrome = true
                            showStylePanel = false
                        })
                        ToolButton("Erase", com.example.pdfreader.data.Tool.ERASER, state.tool, {
                            vm.setTool(it)
                            showChrome = true
                            showStylePanel = false
                        })
                        TextButton(onClick = vm::undo) { Text("↶") }
                        TextButton(onClick = vm::redo) { Text("↷") }
                        TextButton(onClick = {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                            vm.setZoom(1f)
                        }) { Text("1×") }
                    }
                }
            }
        }
    }

    if (showJump) {
        var pageText by remember { mutableStateOf((state.currentPage + 1).toString()) }

        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { pageText = it.filter(Char::isDigit) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val page = pageText.toIntOrNull()?.minus(1)
                    if (page != null && state.pageCount > 0) {
                        val target = page.coerceIn(0, state.pageCount - 1)
                        vm.setPage(target)
                        scope.launch { listState.animateScrollToItem(target) }
                    }
                    showJump = false
                    showChrome = true
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showJump = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun ToolButton(
    label: String,
    tool: com.example.pdfreader.data.Tool,
    selected: com.example.pdfreader.data.Tool,
    onSelected: (com.example.pdfreader.data.Tool) -> Unit
) {
    val selectedColor =
        if (tool == selected) MaterialTheme.colorScheme.primaryContainer
        else Color.Transparent

    TextButton(
        onClick = { onSelected(tool) },
        modifier = Modifier
            .height(52.dp)
            .background(selectedColor, RoundedCornerShape(12.dp))
    ) {
        Text(if (tool == selected) "✓ $label" else label)
    }
}

@Composable
private fun AnnotationStylePanel(
    state: com.example.pdfreader.data.ReaderState,
    onColor: (Long) -> Unit,
    onWidth: (Float) -> Unit,
    onOpacity: (Float) -> Unit
) {
    val colors = listOf(
        0xFF202020L, // black
        0xFFE53935L, // red
        0xFF1E88E5L, // blue
        0xFF43A047L, // green
        0xFFFFA000L, // orange
        0xFF8E24AAL, // purple
        0xFFFFD600L  // yellow
    )

    val widths = if (state.tool == com.example.pdfreader.data.Tool.HIGHLIGHT) {
        listOf(0.012f, 0.020f, 0.030f, 0.045f)
    } else {
        listOf(0.0025f, 0.004f, 0.006f, 0.010f, 0.016f)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            if (state.tool == com.example.pdfreader.data.Tool.HIGHLIGHT)
                "Highlighter"
            else
                "Pen",
            style = MaterialTheme.typography.labelLarge
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEach { color ->
                val c = Color(color)
                Surface(
                    modifier = Modifier
                        .size(30.dp),
                    shape = RoundedCornerShape(50),
                    color = c,
                    tonalElevation = if (color == state.annotationColor) 6.dp else 0.dp,
                    onClick = { onColor(color) }
                ) {}
            }

            TextButton(onClick = {
                // Toggle between common transparent and full opacity via quick preset.
                onOpacity(if (state.annotationOpacity < 0.95f) 1f else 0.45f)
            }) {
                Text("${(state.annotationOpacity * 100).toInt()}%")
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            widths.forEach { width ->
                TextButton(onClick = { onWidth(width) }) {
                    Text(
                        when {
                            width <= 0.003f -> "1"
                            width <= 0.004f -> "2"
                            width <= 0.006f -> "3"
                            width <= 0.010f -> "5"
                            width <= 0.016f -> "8"
                            width <= 0.020f -> "Thin"
                            width <= 0.030f -> "Med"
                            else -> "Thick"
                        }
                    )
                }
            }
        }
    }
}
