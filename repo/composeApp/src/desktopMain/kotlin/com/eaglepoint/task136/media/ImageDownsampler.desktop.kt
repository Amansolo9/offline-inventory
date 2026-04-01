package com.eaglepoint.task136.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual class ImageDownsampler {
    actual suspend fun downsample(bytes: ByteArray, maxWidth: Int, maxHeight: Int): ImageBitmap? {
        val skia = Image.makeFromEncoded(bytes)
        return skia.toComposeImageBitmap()
    }
}
