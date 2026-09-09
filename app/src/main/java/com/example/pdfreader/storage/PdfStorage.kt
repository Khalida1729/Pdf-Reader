package com.example.pdfreader.storage

import android.content.Context
import android.net.Uri
import java.io.File

object PdfStorage {
    private const val CACHE_DIR = "pdfs"

    fun copyToCache(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, "opened_${System.currentTimeMillis()}.pdf")

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open PDF" }
                file.outputStream().use { output -> input.copyTo(output) }
            }
            cleanupExcept(dir, file)
            return file
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
    }

    fun cleanup(context: Context, keep: File? = null) {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists()) return
        cleanupExcept(dir, keep)
    }

    private fun cleanupExcept(dir: File, keep: File?) {
        dir.listFiles()
            ?.filter { it.isFile && keep?.canonicalFile != it.canonicalFile }
            ?.forEach { it.delete() }
    }

    fun displayName(context: Context, uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }
}