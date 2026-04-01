package com.eaglepoint.task136.media

import androidx.compose.ui.graphics.ImageBitmap

expect class ImageDownsampler {
    suspend fun downsample(bytes: ByteArray, maxWidth: Int, maxHeight: Int): ImageBitmap?
}
