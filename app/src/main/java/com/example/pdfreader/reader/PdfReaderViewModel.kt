package com.example.pdfreader.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfreader.data.*
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.pdfreader.pdf.*
import com.example.pdfreader.storage.*
class PdfReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val cache = PdfBitmapCache()
    private val recent = RecentFilesRepository(app)
    private var engine: PdfEngine? = null
    private var sourceFile: File? = null
    private var sourceUri: Uri? = null
    private var fileName: String = "document.pdf"

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()
    private val _annotations = MutableStateFlow<Map<Int, List<PdfAnnotation>>>(emptyMap())
    val annotations: StateFlow<Map<Int, List<PdfAnnotation>>> = _annotations.asStateFlow()

    private val undo = ArrayDeque<PdfAnnotation>()
    private val redo = ArrayDeque<PdfAnnotation>()

    fun open(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine?.close(); cache.clear()
                sourceUri = uri
                sourceFile = PdfStorage.copyToCache(getApplication(), uri)
                fileName = PdfStorage.displayName(getApplication(), uri)
                val e = PdfRendererEngine(sourceFile!!)
                engine = e
                val resume = recent.get().firstOrNull { it.uri == uri.toString() }?.lastPage ?: 0
                _state.value = ReaderState(fileName, e.pageCount, resume.coerceIn(0, e.pageCount - 1))
            }.onFailure { _state.value = ReaderState(error = it.message ?: "Unable to open PDF") }
        }
    }

    suspend fun bitmap(page: Int, width: Int): Bitmap? {
        val e = engine ?: return null
        val key = CacheKey(page, width)
        cache.get(key)?.let { return it }
        return runCatching { e.render(page, width).also { cache.put(key, it) } }.getOrNull()
    }

    fun pageSize(page: Int) = engine?.pageSize(page)

    fun setPage(page: Int) {
        val count = _state.value.pageCount
        if (count == 0) return
        _state.value = _state.value.copy(currentPage = page.coerceIn(0, count - 1))
        sourceUri?.let { recent.put(it.toString(), fileName, _state.value.currentPage) }
    }

    fun setTool(tool: Tool) { _state.value = _state.value.copy(tool = tool) }
    fun setZoom(zoom: Float) { _state.value = _state.value.copy(zoom = zoom.coerceIn(1f, 5f)) }

    fun addAnnotation(annotation: PdfAnnotation) {
        val map = _annotations.value.toMutableMap()
        map[annotation.pageIndex] = map[annotation.pageIndex].orEmpty() + annotation
        _annotations.value = map
        undo.addLast(annotation); redo.clear()
    }

    fun removeLastAnnotation(page: Int) {
        val list = _annotations.value[page].orEmpty()
        if (list.isEmpty()) return
        val removed = list.last()
        val map = _annotations.value.toMutableMap()
        map[page] = list.dropLast(1)
        _annotations.value = map
        undo.removeLastOrNull()
        redo.addLast(removed)
    }

    fun undo() {
        val a = undo.removeLastOrNull() ?: return
        val list = _annotations.value[a.pageIndex].orEmpty()
        if (list.isNotEmpty()) {
            val map = _annotations.value.toMutableMap(); map[a.pageIndex] = list.dropLast(1); _annotations.value = map
            redo.addLast(a)
        }
    }

    fun redo() {
        val a = redo.removeLastOrNull() ?: return
        val map = _annotations.value.toMutableMap(); map[a.pageIndex] = map[a.pageIndex].orEmpty() + a; _annotations.value = map
        undo.addLast(a)
    }

    fun saveTo(output: OutputStream, onDone: (Result<Unit>) -> Unit) {
        val e = engine ?: return onDone(Result.failure(IllegalStateException("No PDF opened")))
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isSaving = true)
            val result = runCatching { PdfExporter(e).export(_annotations.value, output) }
            output.use { }
            _state.value = _state.value.copy(isSaving = false)
            onDone(result)
        }
    }

    fun currentFileName() = fileName
    fun currentUri() = sourceUri

    override fun onCleared() { engine?.close(); cache.clear(); super.onCleared() }
}
