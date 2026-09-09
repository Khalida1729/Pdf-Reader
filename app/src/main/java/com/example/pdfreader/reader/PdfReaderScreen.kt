package com.example.pdfreader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
//import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
//import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Highlight
//import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdfreader.data.ReaderState
import com.example.pdfreader.data.Tool
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
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

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.currentPage
    )

    var showChrome by remember { mutableStateOf(true) }
    var showJump by remember { mutableStateOf(false) }
    var showStylePanel by remember { mutableStateOf(false) }

    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(showChrome, state.tool) {
        if (
            showChrome &&
            state.tool == Tool.HAND
        ) {
            delay(3000.milliseconds)
            showChrome = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex
        }
            .distinctUntilChanged()
            .collect { page ->
                if (page != state.currentPage) {
                    vm.setPage(page)
                }
            }
    }

    LaunchedEffect(
        listState.isScrollInProgress,
        state.tool
    ) {
        if (
            listState.isScrollInProgress &&
            state.tool == Tool.HAND
        ) {
            showChrome = false
        }
    }

    val transformState =
        rememberTransformableState { _, zoomChange, panChange, _ ->

            val newZoom =
                (zoom * zoomChange)
                    .coerceIn(1f, 5f)

            zoom = newZoom

            if (newZoom > 1.01f) {
                panX += panChange.x
                panY += panChange.y
            } else {
                panX = 0f
                panY = 0f
            }

            vm.setZoom(newZoom)
            showChrome = false
        }

    fun resetZoom() {
        zoom = 1f
        panX = 0f
        panY = 0f

        vm.setZoom(1f)
        showChrome = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {

        /*
         * =====================================================
         * PDF
         * =====================================================
         */

        Box(
            modifier = Modifier
                .fillMaxSize()
                .transformable(
                    state = transformState,
                    enabled = state.tool == Tool.HAND
                )
                .pointerInput(state.tool) {

                    if (state.tool == Tool.HAND) {

                        detectTapGestures {
                            showChrome = !showChrome
                        }
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

                verticalArrangement =
                    Arrangement.spacedBy(10.dp),

                contentPadding =
                    PaddingValues(
                        top =
                            if (showChrome) {
                                82.dp
                            } else {
                                12.dp
                            },

                        bottom =
                            if (
                                showChrome ||
                                state.tool != Tool.HAND
                            ) {
                                110.dp
                            } else {
                                12.dp
                            },

                        start = 6.dp,
                        end = 6.dp
                    )
            ) {

                items(
                    count = state.pageCount,
                    key = { it }
                ) { page ->

                    PdfPage(
                        pageIndex = page,
                        vm = vm,
                        annotations =
                            annotations[page].orEmpty(),
                        tool = state.tool,
                        zoom = zoom,
                        onAnnotationTool = vm::setTool
                    )
                }
            }
        }


        /*
         * =====================================================
         * MODERN TOP BAR
         * =====================================================
         */

        AnimatedVisibility(
            visible = showChrome,
            modifier = Modifier
                .align(Alignment.TopCenter)
        ) {

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(22.dp)
                    ),

                shape =
                    RoundedCornerShape(22.dp),

                color =
                    MaterialTheme.colorScheme.surface,

                tonalElevation = 5.dp
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(
                            horizontal = 6.dp
                        ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    /*
                     * File icon
                     */

                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(13.dp),
                        color =
                            MaterialTheme.colorScheme.primaryContainer
                    ) {

                        Box(
                            contentAlignment =
                                Alignment.Center
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.FileOpen,

                                contentDescription =
                                    "PDF"
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )

                    /*
                     * File name
                     */

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text =
                                state.fileName.ifBlank {
                                    "PDF Reader"
                                },

                            maxLines = 1,

                            style =
                                MaterialTheme.typography.titleSmall,

                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Text(
                            text =
                                if (
                                    state.pageCount > 0
                                ) {
                                    "Page ${state.currentPage + 1} of ${state.pageCount}"
                                } else {
                                    "No pages"
                                },

                            maxLines = 1,

                            style =
                                MaterialTheme.typography.labelSmall,

                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    /*
                     * Page indicator
                     */

                    Surface(
                        shape =
                            RoundedCornerShape(12.dp),

                        color =
                            MaterialTheme.colorScheme
                                .surfaceVariant
                    ) {

                        Text(
                            text =
                                if (
                                    state.pageCount > 0
                                ) {
                                    "${state.currentPage + 1}/${state.pageCount}"
                                } else {
                                    "0/0"
                                },

                            modifier =
                                Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 7.dp
                                ),

                            style =
                                MaterialTheme.typography.labelMedium,

                            fontWeight =
                                FontWeight.Medium
                        )
                    }

                    Spacer(
                        modifier = Modifier.width(4.dp)
                    )

                    TopIconButton(
                        icon = Icons.Default.Save,
                        description = "Save",
                        onClick = onSave
                    )

                    TopIconButton(
                        icon = Icons.Default.IosShare,
                        description = "Share",
                        onClick = onShare
                    )

                    TopIconButton(
                        icon = Icons.Default.FileOpen,
                        description = "Open",
                        onClick = onOpen
                    )

                    IconButton(
                        onClick = {
                            showJump = true
                        }
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.MoreVert,

                            contentDescription =
                                "More"
                        )
                    }
                }
            }
        }


        /*
         * =====================================================
         * SAVING INDICATOR
         * =====================================================
         */

        AnimatedVisibility(
            visible = state.isSaving,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp)
        ) {

            Surface(
                shape =
                    RoundedCornerShape(50),

                color =
                    MaterialTheme.colorScheme
                        .primaryContainer
            ) {

                Row(
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 8.dp
                    ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        "Saving...",
                        style =
                            MaterialTheme.typography.labelMedium
                    )
                }
            }
        }


        /*
         * =====================================================
         * BOTTOM TOOLBAR
         * =====================================================
         */

        AnimatedVisibility(
            visible =
                showChrome ||
                        state.tool != Tool.HAND,

            modifier = Modifier
                .align(Alignment.BottomCenter)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        bottom = 8.dp
                    )
            ) {

                /*
                 * Style panel
                 */

                AnimatedVisibility(
                    visible =
                        showStylePanel &&
                                (
                                        state.tool == Tool.PEN ||
                                                state.tool == Tool.HIGHLIGHT
                                        )
                ) {

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .shadow(
                                8.dp,
                                RoundedCornerShape(20.dp)
                            ),

                        shape =
                            RoundedCornerShape(20.dp),

                        color =
                            MaterialTheme.colorScheme.surface,

                        tonalElevation = 5.dp
                    ) {

                        AnnotationStylePanel(
                            state = state,

                            onColor =
                                vm::setAnnotationColor,

                            onWidth =
                                vm::setAnnotationWidth,

                            onOpacity =
                                vm::setAnnotationOpacity
                        )
                    }
                }


                /*
                 * Main toolbar
                 */

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 12.dp,
                            shape =
                                RoundedCornerShape(24.dp)
                        ),

                    shape =
                        RoundedCornerShape(24.dp),

                    color =
                        MaterialTheme.colorScheme.surface,

                    tonalElevation = 6.dp
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(
                                horizontal = 6.dp
                            ),

                        horizontalArrangement =
                            Arrangement.SpaceEvenly,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        ModernToolButton(
                            label = "Hand",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            tool = Tool.HAND,
                            selected = state.tool
                        ) {
                            vm.setTool(it)
                            showStylePanel = false
                        }

                        ModernToolButton(
                            label = "Pen",
                            icon = Icons.Default.Edit,
                            tool = Tool.PEN,
                            selected = state.tool
                        ) {
                            vm.setTool(it)
                            showChrome = true
                            showStylePanel = true
                        }

                        ModernToolButton(
                            label = "High",
                            icon = Icons.Default.Highlight,
                            tool = Tool.HIGHLIGHT,
                            selected = state.tool
                        ) {
                            vm.setTool(it)
                            showChrome = true
                            showStylePanel = true
                        }

                        ModernToolButton(
                            label = "Text",
                            icon = Icons.Default.TextFields,
                            tool = Tool.TEXT,
                            selected = state.tool
                        ) {
                            vm.setTool(it)
                            showChrome = true
                            showStylePanel = false
                        }

                        ModernToolButton(
                            label = "Erase",
                            icon = Icons.AutoMirrored.Filled.Backspace,
                            tool = Tool.ERASER,
                            selected = state.tool
                        ) {
                            vm.setTool(it)
                            showChrome = true
                            showStylePanel = false
                        }

                        /*
                         * Divider
                         */

                        VerticalDivider(
                            modifier = Modifier
                                .height(34.dp)
                        )

                        /*
                         * Undo
                         */

                        CompactAction(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            label = "Undo",
                            onClick = vm::undo
                        )

                        /*
                         * Redo
                         */

                        CompactAction(
                            icon = Icons.AutoMirrored.Filled.Redo,
                            label = "Redo",
                            onClick = vm::redo
                        )

                        /*
                         * Zoom
                         */

                        CompactAction(
                            icon = Icons.Default.ZoomIn,

                            label =
                                if (
                                    abs(zoom - 1f) < 0.01f
                                ) {
                                    "1×"
                                } else {
                                    "${zoom.formatZoom()}×"
                                },

                            onClick = ::resetZoom
                        )
                    }
                }
            }
        }
    }


    /*
     * =====================================================
     * GO TO PAGE DIALOG
     * =====================================================
     */

    if (showJump) {

        var pageText by remember(
            state.currentPage
        ) {
            mutableStateOf(
                if (state.pageCount > 0) {
                    (state.currentPage + 1).toString()
                } else {
                    ""
                }
            )
        }

        AlertDialog(
            onDismissRequest = {
                showJump = false
            },

            shape =
                RoundedCornerShape(28.dp),

            title = {
                Text(
                    "Go to page",
                    fontWeight =
                        FontWeight.SemiBold
                )
            },

            text = {

                OutlinedTextField(
                    value = pageText,

                    onValueChange = {
                        pageText =
                            it.filter(Char::isDigit)
                    },

                    singleLine = true,

                    shape =
                        RoundedCornerShape(16.dp),

                    label = {
                        Text("Page number")
                    }
                )
            },

            confirmButton = {

                Button(
                    onClick = {

                        val page =
                            pageText
                                .toIntOrNull()
                                ?.minus(1)

                        if (
                            page != null &&
                            state.pageCount > 0
                        ) {

                            val target =
                                page.coerceIn(
                                    0,
                                    state.pageCount - 1
                                )

                            vm.setPage(target)

                            scope.launch {
                                listState.animateScrollToItem(
                                    target
                                )
                            }
                        }

                        showJump = false
                        showChrome = true
                    },

                    shape =
                        RoundedCornerShape(14.dp)
                ) {
                    Text("Go")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        showJump = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


/*
 * =============================================================
 * TOP ICON BUTTON
 * =============================================================
 */

@Composable
private fun TopIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(42.dp)
    ) {

        Icon(
            imageVector = icon,
            contentDescription = description
        )
    }
}


/*
 * =============================================================
 * MODERN TOOL BUTTON
 * =============================================================
 */

@Composable
private fun ModernToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tool: Tool,
    selected: Tool,
    onSelected: (Tool) -> Unit
) {

    val isSelected =
        tool == selected

    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }

    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Column(
        modifier = Modifier
            .width(54.dp)
            .height(62.dp)
            .clip(
                RoundedCornerShape(16.dp)
            )
            .background(containerColor)
            .clickable {
                onSelected(tool)
            },

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(
            modifier = Modifier.height(3.dp)
        )

        Text(
            text = label,
            color = contentColor,

            style =
                MaterialTheme.typography.labelSmall,

            fontWeight =
                if (isSelected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                }
        )
    }
}


/*
 * =============================================================
 * COMPACT ACTION
 * =============================================================
 */

@Composable
private fun CompactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .width(48.dp)
            .height(58.dp)
            .clip(
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(21.dp)
        )

        Spacer(
            modifier = Modifier.height(2.dp)
        )

        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall
        )
    }
}


/*
 * =============================================================
 * ANNOTATION STYLE PANEL
 * =============================================================
 */

@Composable
private fun AnnotationStylePanel(
    state: ReaderState,
    onColor: (Long) -> Unit,
    onWidth: (Float) -> Unit,
    onOpacity: (Float) -> Unit
) {

    val colors = listOf(
        0xFF202020L,
        0xFFE53935L,
        0xFF1E88E5L,
        0xFF43A047L,
        0xFFFFA000L,
        0xFF8E24AAL,
        0xFFFFD600L
    )

    val widths =
        if (
            state.tool == Tool.HIGHLIGHT
        ) {

            listOf(
                0.012f,
                0.020f,
                0.030f,
                0.045f
            )

        } else {

            listOf(
                0.0025f,
                0.004f,
                0.006f,
                0.010f,
                0.016f
            )
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 14.dp,
                vertical = 12.dp
            )
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    if (
                        state.tool ==
                        Tool.HIGHLIGHT
                    ) {
                        "Highlighter"
                    } else {
                        "Pen"
                    },

                modifier =
                    Modifier.weight(1f),

                style =
                    MaterialTheme.typography.titleSmall,

                fontWeight =
                    FontWeight.SemiBold
            )

            Surface(
                shape =
                    RoundedCornerShape(10.dp),

                color =
                    MaterialTheme.colorScheme
                        .surfaceVariant
            ) {

                Text(
                    "${(state.annotationOpacity * 100).toInt()}%",

                    modifier =
                        Modifier.padding(
                            horizontal = 9.dp,
                            vertical = 5.dp
                        ),

                    style =
                        MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            colors.forEach { color ->

                val selected =
                    color == state.annotationColor

                Surface(
                    modifier = Modifier
                        .size(
                            if (selected) {
                                34.dp
                            } else {
                                30.dp
                            }
                        )
                        .border(
                            width =
                                if (selected) {
                                    2.dp
                                } else {
                                    0.dp
                                },

                            color =
                                if (selected) {
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                } else {
                                    Color.Transparent
                                },

                            shape = CircleShape
                        ),

                    shape = CircleShape,

                    color = Color(color),

                    onClick = {
                        onColor(color)
                    }
                ) {}
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceEvenly,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            widths.forEachIndexed { index, width ->

                val label =
                    when (index) {
                        0 -> "1"
                        1 -> "2"
                        2 -> "3"
                        3 -> "5"
                        else -> "8"
                    }

                FilterChip(
                    selected =
                        abs(
                            state.annotationWidth -
                                    width
                        ) < 0.0001f,

                    onClick = {
                        onWidth(width)
                    },

                    label = {
                        Text(label)
                    },

                    shape =
                        RoundedCornerShape(12.dp)
                )
            }

            FilterChip(
                selected = false,

                onClick = {

                    onOpacity(
                        if (
                            state.annotationOpacity < 0.95f
                        ) {
                            1f
                        } else {
                            0.45f
                        }
                    )
                },

                label = {
                    Text("Opacity")
                },

                shape =
                    RoundedCornerShape(12.dp)
            )
        }
    }
}


/*
 * =============================================================
 * ZOOM FORMAT
 * =============================================================
 */

private fun Float.formatZoom(): String {
    return if (this % 1f == 0f) {
        this.toInt().toString()
    } else {
        "%.1f".format(this)
    }
}