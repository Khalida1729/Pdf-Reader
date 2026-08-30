package com.example.pdfreader.reader
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.pdfreader.data.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    var showJump by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }


    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
        panX += panChange.x
        panY += panChange.y
    }


    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { page ->
                vm.setPage(page)
            }
    }
    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PDF Reader", modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                Text("${state.currentPage + 1}/${state.pageCount}")
                IconButton(onClick = { showJump = true }) { Text("Go") }
                IconButton(onClick = onSave) { Text("Save") }
                IconButton(onClick = onShare) { Text("Share") }
                IconButton(onClick = onOpen) { Text("Open") }
            }
        }

        if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())

        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds()
                .transformable(transformState)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom
                    translationX = panX; translationY = panY
                },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((0 until state.pageCount).toList(), key = { it }) { page ->
                    PdfPage(
                        pageIndex = page,
                        vm = vm,
                        annotations = annotations[page].orEmpty(),
                        tool = state.tool,
                        zoom = zoom,
                        onAnnotationTool = { vm.setTool(it) }
                    )
                }
            }
        }

        AnnotationToolbar(
            selected = state.tool,
            onSelected = vm::setTool,
            onUndo = vm::undo,
            onRedo = vm::redo,
            onResetZoom = { zoom = 1f; panX = 0f; panY = 0f }
        )
    }

    if (showJump) {
        var pageText by remember { mutableStateOf((state.currentPage + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(value = pageText, onValueChange = { pageText = it.filter(Char::isDigit) }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    val page = pageText.toIntOrNull()?.minus(1)
                    if (page != null) {
                        vm.setPage(page)
                        kotlinx.coroutines.MainScope().launch { listState.animateScrollToItem(page.coerceIn(0, state.pageCount - 1)); showJump = false }
                    }
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showJump = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AnnotationToolbar(
    selected: Tool,
    onSelected: (Tool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton("Hand", Tool.HAND, selected, onSelected)
            ToolButton("Pen", Tool.PEN, selected, onSelected)
            ToolButton("High", Tool.HIGHLIGHT, selected, onSelected)
            ToolButton("Text", Tool.TEXT, selected, onSelected)
            ToolButton("Erase", Tool.ERASER, selected, onSelected)
            TextButton(onClick = onUndo) { Text("↶") }
            TextButton(onClick = onRedo) { Text("↷") }
            TextButton(onClick = onResetZoom) { Text("1x") }
        }
    }
}

@Composable
private fun ToolButton(label: String, tool: Tool, selected: Tool, onSelected: (Tool) -> Unit) {
    TextButton(onClick = { onSelected(tool) }) { Text(if (tool == selected) "[$label]" else label) }
}

