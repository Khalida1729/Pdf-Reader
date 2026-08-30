package com.example.pdfreader.pdf

import android.graphics.Bitmap
import android.util.LruCache

data class CacheKey(val page: Int, val width: Int)

class PdfBitmapCache(maxMemoryPercent: Int = 20) {
    private val cache = object : LruCache<CacheKey, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 1024L * maxMemoryPercent / 100).toInt().coerceAtLeast(16)
    ) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.byteCount / 1024
    }

    @Synchronized fun get(key: CacheKey): Bitmap? = cache.get(key)
    @Synchronized fun put(key: CacheKey, bitmap: Bitmap) { cache.put(key, bitmap) }
    @Synchronized fun clear() = cache.evictAll()
}
