package com.example.mydeskrobot.data.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

object JpegImageScaler {

    fun scaleJpeg(
        jpegBytes: ByteArray,
        maxSidePx: Int = 1024,
        jpegQuality: Int = 80,
    ): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return jpegBytes

        val sampleSize = calculateInSampleSize(width, height, maxSidePx)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions)
            ?: return jpegBytes

        val longest = max(decoded.width, decoded.height)
        val scaled = if (longest > maxSidePx) {
            val scale = maxSidePx.toFloat() / longest
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
        scaled.recycle()
        return output.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSidePx: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        while (longest / inSampleSize > maxSidePx * 2) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
