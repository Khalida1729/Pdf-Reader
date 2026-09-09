package com.example.pdfreader.pdf

import android.graphics.Bitmap
import android.util.LruCache

data class CacheKey(
    val documentId: String,
    val page: Int,
    val width: Int
)

class PdfBitmapCache(
    maxMemoryPercent: Int = 20
) {

    private val maxCacheKb: Int = (
            Runtime.getRuntime().maxMemory() / 1024L *
                    maxMemoryPercent.coerceIn(5, 30) / 100L
            )
        .coerceAtLeast(1024L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

    private val cache = object : LruCache<CacheKey, Bitmap>(maxCacheKb) {

        override fun sizeOf(
            key: CacheKey,
            value: Bitmap
        ): Int {
            return (value.allocationByteCount / 1024L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
    }

    @Synchronized
    fun get(key: CacheKey): Bitmap? {
        return cache.get(key)
    }

    @Synchronized
    fun put(key: CacheKey, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }
}