package com.neighborly.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes a picked photo, scales it to max 1600 px and compresses it to a JPEG ≤ 5 MB. */
@Singleton
class ImageCompressor @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun compress(uri: Uri, maxSide: Int = MAX_SIDE, maxBytes: Int = MAX_BYTES): ByteArray =
        withContext(Dispatchers.IO) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h)
                if (longest > maxSide) {
                    val scale = maxSide.toFloat() / longest
                    decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
                }
            }
            var quality = 88
            var bytes: ByteArray
            do {
                bytes = ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.toByteArray()
                }
                quality -= 10
            } while (bytes.size > maxBytes && quality > 20)
            bitmap.recycle()
            if (bytes.size > maxBytes) throw AppError.Validation("Image must be smaller than 5 MB")
            bytes
        }

    companion object {
        const val MAX_SIDE = 1600
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
