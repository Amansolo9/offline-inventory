package com.eaglepoint.task136.media

import androidx.compose.ui.graphics.ImageBitmap

class ImageBitmapLruCache(
    private val maxBytes: Int = 20 * 1024 * 1024,
) {
    private val map = linkedMapOf<String, CacheEntry>()
    private var currentBytes: Int = 0

    fun get(key: String): ImageBitmap? {
        val value = map.remove(key) ?: return null
        map[key] = value
        return value.bitmap
    }

    fun put(key: String, bitmap: ImageBitmap) {
        val size = bitmap.width * bitmap.height * 4
        val existing = map.remove(key)
        if (existing != null) currentBytes -= existing.bytes

        map[key] = CacheEntry(bitmap, size)
        currentBytes += size
        trimToSize(maxBytes)
    }

    private fun trimToSize(targetBytes: Int) {
        while (currentBytes > targetBytes && map.isNotEmpty()) {
            val firstKey = map.keys.first()
            val removed = map.remove(firstKey) ?: continue
            currentBytes -= removed.bytes
        }
    }

    private data class CacheEntry(
        val bitmap: ImageBitmap,
        val bytes: Int,
    )
}
