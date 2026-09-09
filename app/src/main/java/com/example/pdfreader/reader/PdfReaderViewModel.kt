package com.example.pdfreader.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfreader.data.*
import com.example.pdfreader.pdf.*
import com.example.pdfreader.storage.*
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PdfReaderViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val cache = PdfBitmapCache()

    private val recent =
        RecentFilesRepository(app)

    private var engine: PdfEngine? = null

    private var sourceFile: java.io.File? = null

    private var sourceUri: Uri? = null

    private var fileName: String = "document.pdf"

    private var openJob: Job? = null

    private var saveJob: Job? = null

    /*
     * Changes whenever a new document is opened.
     *
     * Used to prevent old bitmap requests from putting
     * results into the cache after another PDF is opened.
     */
    private var documentGeneration = 0L

    private val _state =
        MutableStateFlow(ReaderState())

    val state: StateFlow<ReaderState> =
        _state.asStateFlow()

    private val _annotations =
        MutableStateFlow<Map<Int, List<PdfAnnotation>>>(
            emptyMap()
        )

    val annotations:
            StateFlow<Map<Int, List<PdfAnnotation>>> =
        _annotations.asStateFlow()

    private val undo =
        ArrayDeque<PdfAnnotation>()

    private val redo =
        ArrayDeque<PdfAnnotation>()


    // ============================================================
    // OPEN PDF
    // ============================================================

    fun open(uri: Uri) {

        openJob?.cancel()

        openJob = viewModelScope.launch(Dispatchers.IO) {

            val generation = ++documentGeneration

            runCatching {

                /*
                 * Close the previous engine first.
                 */
                val oldEngine = engine
                engine = null

                oldEngine?.close()

                /*
                 * New document means new cache/session.
                 */
                cache.clear()

                undo.clear()
                redo.clear()

                _annotations.value = emptyMap()

                sourceUri = uri

                /*
                 * Copy selected PDF into app cache.
                 */
                val file =
                    PdfStorage.copyToCache(
                        getApplication(),
                        uri
                    )

                sourceFile = file

                fileName =
                    PdfStorage.displayName(
                        getApplication(),
                        uri
                    )

                /*
                 * Create renderer for the new PDF.
                 */
                val newEngine =
                    PdfRendererEngine(file)

                /*
                 * If another open() started while this PDF
                 * was being prepared, don't install this engine.
                 */
                if (generation != documentGeneration) {
                    newEngine.close()
                    return@runCatching
                }

                engine = newEngine

                val resume =
                    recent.get()
                        .firstOrNull {
                            it.uri == uri.toString()
                        }
                        ?.lastPage ?: 0

                val safePage =
                    resume.coerceIn(
                        0,
                        (newEngine.pageCount - 1)
                            .coerceAtLeast(0)
                    )

                _state.value =
                    ReaderState(
                        fileName = fileName,
                        pageCount = newEngine.pageCount,
                        currentPage = safePage
                    )

            }.onFailure { error ->

                /*
                 * Never convert coroutine cancellation into
                 * a fake "Unable to open PDF" error.
                 */
                if (error is CancellationException) {
                    throw error
                }

                _state.value =
                    ReaderState(
                        error =
                            error.message
                                ?: "Unable to open PDF"
                    )
            }
        }
    }


    // ============================================================
    // BITMAP
    // ============================================================

    suspend fun bitmap(
        page: Int,
        width: Int
    ): Bitmap? {

        val e =
            engine ?: return null

        val uri =
            sourceUri ?: return null

        val generation =
            documentGeneration

        /*
         * Cache is document-specific.
         */
        val key =
            CacheKey(
                documentId = uri.toString(),
                page = page,
                width = width
            )

        /*
         * Cache hit.
         */
        cache.get(key)?.let {
            return it
        }

        /*
         * Render.
         */
        val rendered =
            runCatching {

                e.render(
                    page = page,
                    targetWidth = width
                )

            }.getOrElse { error ->

                if (error is CancellationException) {
                    throw error
                }

                return null
            }

        /*
         * Don't cache a bitmap belonging to an old PDF.
         *
         * Example:
         *
         * PDF A starts rendering
         * ↓
         * user opens PDF B
         * ↓
         * PDF A finishes rendering
         *
         * This prevents A's bitmap from being inserted
         * into the current document cache/session.
         */
        if (
            generation == documentGeneration &&
            sourceUri == uri &&
            engine === e
        ) {
            cache.put(
                key,
                rendered
            )
        }

        return rendered
    }


    // ============================================================
    // PAGE SIZE
    // ============================================================

    suspend fun pageSize(page: Int) =
        engine?.let { currentEngine ->

            runCatching {
                currentEngine.pageSize(page)
            }.getOrNull()
        }


    // ============================================================
    // PAGE
    // ============================================================

    fun setPage(page: Int) {

        val count =
            _state.value.pageCount

        if (count <= 0) {
            return
        }

        val safePage =
            page.coerceIn(
                0,
                count - 1
            )

        _state.value =
            _state.value.copy(
                currentPage = safePage
            )

        sourceUri?.let { uri ->

            recent.put(
                uri.toString(),
                fileName,
                safePage
            )
        }
    }


    // ============================================================
    // TOOLS
    // ============================================================

    fun setTool(tool: Tool) {

        _state.value =
            _state.value.copy(
                tool = tool
            )
    }


    // ============================================================
    // ANNOTATION COLOR
    // ============================================================

    fun setAnnotationColor(
        color: Long
    ) {

        _state.value =
            _state.value.copy(
                annotationColor = color
            )
    }


    // ============================================================
    // ANNOTATION WIDTH
    // ============================================================

    fun setAnnotationWidth(
        width: Float
    ) {

        _state.value =
            _state.value.copy(
                annotationWidth =
                    width.coerceAtLeast(0f)
            )
    }


    // ============================================================
    // ANNOTATION OPACITY
    // ============================================================

    fun setAnnotationOpacity(
        opacity: Float
    ) {

        _state.value =
            _state.value.copy(
                annotationOpacity =
                    opacity.coerceIn(
                        0.05f,
                        1f
                    )
            )
    }


    // ============================================================
    // ZOOM
    // ============================================================

    fun setZoom(
        zoom: Float
    ) {

        _state.value =
            _state.value.copy(
                zoom =
                    zoom.coerceIn(
                        1f,
                        5f
                    )
            )
    }


    // ============================================================
    // ADD ANNOTATION
    // ============================================================

    fun addAnnotation(
        annotation: PdfAnnotation
    ) {

        val map =
            _annotations.value.toMutableMap()

        map[annotation.pageIndex] =
            map[annotation.pageIndex]
                .orEmpty() + annotation

        _annotations.value = map

        undo.addLast(annotation)

        /*
         * New action invalidates redo history.
         */
        redo.clear()
    }


    // ============================================================
    // REMOVE LAST ANNOTATION
    // ============================================================

    fun removeLastAnnotation(
        page: Int
    ) {

        val list =
            _annotations.value[page]
                .orEmpty()

        if (list.isEmpty()) {
            return
        }

        val removed =
            list.last()

        val map =
            _annotations.value.toMutableMap()

        val remaining =
            list.dropLast(1)

        if (remaining.isEmpty()) {
            map.remove(page)
        } else {
            map[page] = remaining
        }

        _annotations.value = map

        /*
         * Only remove from undo stack if the removed
         * annotation is actually its last item.
         */
        if (
            undo.isNotEmpty() &&
            undo.last() == removed
        ) {
            undo.removeLast()
        }

        redo.addLast(removed)
    }


    // ============================================================
    // UNDO
    // ============================================================

    fun undo() {

        val annotation =
            undo.removeLastOrNull()
                ?: return

        val list =
            _annotations.value[
                annotation.pageIndex
            ].orEmpty()

        if (list.isEmpty()) {
            return
        }

        /*
         * Make sure we're removing the same
         * annotation that was last added.
         */
        if (list.last() != annotation) {
            undo.addLast(annotation)
            return
        }

        val map =
            _annotations.value.toMutableMap()

        val remaining =
            list.dropLast(1)

        if (remaining.isEmpty()) {
            map.remove(annotation.pageIndex)
        } else {
            map[annotation.pageIndex] =
                remaining
        }

        _annotations.value = map

        redo.addLast(annotation)
    }


    // ============================================================
    // REDO
    // ============================================================

    fun redo() {

        val annotation =
            redo.removeLastOrNull()
                ?: return

        val map =
            _annotations.value.toMutableMap()

        map[annotation.pageIndex] =
            map[annotation.pageIndex]
                .orEmpty() + annotation

        _annotations.value = map

        undo.addLast(annotation)
    }


    // ============================================================
    // SAVE PDF
    // ============================================================

    fun saveTo(
        output: OutputStream,
        onDone: (Result<Unit>) -> Unit
    ) {

        /*
         * Don't allow multiple saves at the same time.
         */
        if (saveJob?.isActive == true) {
            runCatching {
                output.close()
            }

            onDone(
                Result.failure(
                    IllegalStateException(
                        "A save operation is already running"
                    )
                )
            )

            return
        }

        /*
         * Capture the current engine.
         *
         * The exporter works with this exact engine.
         */
        val currentEngine =
            engine

        if (currentEngine == null) {

            runCatching {
                output.close()
            }

            onDone(
                Result.failure(
                    IllegalStateException(
                        "No PDF opened"
                    )
                )
            )

            return
        }

        /*
         * Capture annotations so the export uses
         * one consistent snapshot.
         */
        val annotationSnapshot =
            _annotations.value.toMap()

        saveJob =
            viewModelScope.launch(Dispatchers.IO) {

                _state.value =
                    _state.value.copy(
                        isSaving = true,
                        error = null
                    )

                val result =
                    runCatching {

                        PdfExporter(
                            currentEngine
                        ).export(
                            annotationSnapshot,
                            output
                        )
                    }.onFailure { error ->

                        if (
                            error is CancellationException
                        ) {
                            throw error
                        }
                    }

                runCatching {
                    output.close()
                }

                _state.value =
                    _state.value.copy(
                        isSaving = false
                    )

                onDone(result)
            }
    }


    // ============================================================
    // CURRENT FILE
    // ============================================================

    fun currentFileName(): String =
        fileName


    fun currentUri(): Uri? =
        sourceUri


    // ============================================================
    // CLEANUP
    // ============================================================

    override fun onCleared() {

        openJob?.cancel()
        saveJob?.cancel()

        /*
         * Prevent new bitmap results from being accepted.
         */
        documentGeneration++

        val currentEngine =
            engine

        engine = null

        currentEngine?.close()

        cache.clear()

        super.onCleared()
    }
}