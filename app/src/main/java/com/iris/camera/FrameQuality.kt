package com.iris.camera

import android.graphics.Bitmap
import kotlin.math.abs

data class FrameQuality(
    val brightness: Float,
    val variance: Float,
    val blurScore: Float,
) {
    companion object {
        const val BRIGHTNESS_TOO_DARK = 0.05f
        const val BRIGHTNESS_TOO_BRIGHT = 0.95f
        const val VARIANCE_TOO_LOW = 0.01f
        const val BLUR_TOO_LOW = 0.005f

        fun assess(bitmap: Bitmap): FrameQuality {
            val downsample = downsample(bitmap, target = 64)
            val w = downsample.width
            val h = downsample.height
            val pixels = IntArray(w * h)
            downsample.getPixels(pixels, 0, w, 0, 0, w, h)

            var sum = 0.0
            val luminances = FloatArray(pixels.size)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val l = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                luminances[i] = l
                sum += l
            }
            val mean = (sum / pixels.size).toFloat()

            var varianceAccum = 0.0
            for (l in luminances) {
                val d = l - mean
                varianceAccum += d * d
            }
            val variance = (varianceAccum / pixels.size).toFloat()

            var laplacianAccum = 0.0
            var count = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val c = luminances[y * w + x]
                    val n = luminances[(y - 1) * w + x]
                    val s = luminances[(y + 1) * w + x]
                    val e = luminances[y * w + x + 1]
                    val ww = luminances[y * w + x - 1]
                    val laplacian = abs(4 * c - n - s - e - ww)
                    laplacianAccum += laplacian * laplacian
                    count++
                }
            }
            val blur = if (count > 0) (laplacianAccum / count).toFloat() else 0f

            if (downsample !== bitmap) downsample.recycle()
            return FrameQuality(brightness = mean, variance = variance, blurScore = blur)
        }

        private fun downsample(src: Bitmap, target: Int): Bitmap {
            val longest = maxOf(src.width, src.height)
            // Only downsample bitmaps that are at least 4x the target size; for
            // smaller bitmaps the bilinear filter erases high-frequency detail
            // (stripes, edges) and falsely flattens variance/blur scores.
            if (longest < target * 4) return src
            val scale = target.toFloat() / longest
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(src, w, h, true)
        }
    }
}
