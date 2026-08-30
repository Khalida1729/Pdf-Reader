package com.example.pdfreader.storage

import android.content.Context
import android.net.Uri
import java.io.File

object PdfStorage {
    fun copyToCache(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val file = File(dir, "opened_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open PDF" }
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    fun displayName(context: Context, uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    }
}
