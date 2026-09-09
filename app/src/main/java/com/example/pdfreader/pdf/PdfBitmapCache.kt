package com.example.pdfreader.pdf

import android.graphics.Bitmap
import android.util.LruCache

data class CacheKey(val page: Int, val width: Int)

/**
 * LruCache size is expressed in the unit returned by sizeOf().
 * Both values below are therefore KB.
 */
class PdfBitmapCache(maxMemoryPercent: Int = 20) {
    private val maxCacheKb = (
            Runtime.getRuntime().maxMemory() / 1024L * maxMemoryPercent / 100L
            ).toInt().coerceAtLeast(1024)

    private val cache = object : LruCache<CacheKey, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int =
            (value.byteCount / 1024L).toInt().coerceAtLeast(1)
    }

    @Synchronized
    fun get(key: CacheKey): Bitmap? = cache.get(key)

    @Synchronized
    fun put(key: CacheKey, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    @Synchronized
    fun clear() = cache.evictAll()
}