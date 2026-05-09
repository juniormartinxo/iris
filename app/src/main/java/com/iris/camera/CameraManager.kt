package com.iris.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.iris.ai.GemmaConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraManager(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
) {

    private var imageCapture: ImageCapture? = null
    private val captureExecutor = Executors.newSingleThreadExecutor()

    suspend fun bind(previewView: PreviewView) =
        suspendCancellableCoroutine<Unit> { cont ->
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    imageCapture = capture
                    if (cont.isActive) cont.resume(Unit)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            }, ContextCompat.getMainExecutor(context))
        }

    suspend fun captureFrame(): Bitmap = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resumeWithException(IllegalStateException("Camera not bound"))
            return@suspendCancellableCoroutine
        }
        capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toRotatedDownsampledBitmap()
                    if (cont.isActive) cont.resume(bitmap)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resumeWithException(exception)
            }
        })
    }

    fun assess(bitmap: Bitmap): FrameQuality = FrameQuality.assess(bitmap)

    fun unbind() {
        imageCapture = null
        captureExecutor.shutdown()
    }

    private fun ImageProxy.toRotatedDownsampledBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val longest = maxOf(raw.width, raw.height)
        val scaled = if (longest > GemmaConfig.IMG_LONGEST_EDGE) {
            val s = GemmaConfig.IMG_LONGEST_EDGE.toFloat() / longest
            Bitmap.createScaledBitmap(
                raw,
                (raw.width * s).toInt().coerceAtLeast(1),
                (raw.height * s).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== raw) raw.recycle() }
        } else raw

        val rotation = imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
            if (rotated !== scaled) scaled.recycle()
            rotated
        } else scaled
    }
}
