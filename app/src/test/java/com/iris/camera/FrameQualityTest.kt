package com.iris.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FrameQualityTest {

    private fun solidBitmap(color: Int, w: Int = 100, h: Int = 100): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    private fun stripedBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, 100f, 100f, paint)
        paint.color = Color.WHITE
        for (y in 0 until 100 step 4) {
            canvas.drawRect(0f, y.toFloat(), 100f, (y + 2).toFloat(), paint)
        }
        return bmp
    }

    @Test
    fun blackBitmapIsTooDark() {
        val q = FrameQuality.assess(solidBitmap(Color.BLACK))
        assertTrue("brightness should be low: ${q.brightness}", q.brightness < 0.05f)
    }

    @Test
    fun whiteBitmapIsTooBright() {
        val q = FrameQuality.assess(solidBitmap(Color.WHITE))
        assertTrue("brightness should be high: ${q.brightness}", q.brightness > 0.95f)
    }

    @Test
    fun solidColorBitmapHasLowVariance() {
        val q = FrameQuality.assess(solidBitmap(Color.argb(255, 128, 128, 128)))
        assertTrue("variance should be near zero: ${q.variance}", q.variance < 0.01f)
    }

    @Test
    fun stripedBitmapHasHighVariance() {
        val q = FrameQuality.assess(stripedBitmap())
        assertTrue("variance should be substantial: ${q.variance}", q.variance > 0.05f)
    }

    @Test
    fun stripedBitmapHasNonZeroBlurScore() {
        val q = FrameQuality.assess(stripedBitmap())
        assertTrue("blurScore should be positive: ${q.blurScore}", q.blurScore > 0.0f)
    }
}
