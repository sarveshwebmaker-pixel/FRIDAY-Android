package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.actions.ActionResult
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Screen Vision Capture Manager using MediaProjection.
 * Safely captures a downscaled snapshot of current screen content for AI visual analysis.
 */
object ScreenVisionManager {

    private const val TAG = "ScreenVisionManager"
    var mediaProjection: MediaProjection? = null
    var resultCode: Int = Activity.RESULT_CANCELED
    var resultData: Intent? = null

    fun isProjectionAvailable(): Boolean = resultData != null

    fun setProjectionData(code: Int, data: Intent) {
        resultCode = code
        resultData = data
    }

    fun captureScreen(context: Context, onComplete: (Bitmap?) -> Unit) {
        val data = resultData
        if (data == null) {
            onComplete(null)
            return
        }

        try {
            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            val projection = mpManager?.getMediaProjection(resultCode, data) ?: run {
                onComplete(null)
                return
            }

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels / 2
            val height = metrics.heightPixels / 2
            val density = metrics.densityDpi

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val virtualDisplay = projection.createVirtualDisplay(
                "FridayScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                Handler(Looper.getMainLooper())
            )

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = imageReader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()
                        virtualDisplay?.release()
                        projection.stop()
                        onComplete(bitmap)
                    } else {
                        virtualDisplay?.release()
                        projection.stop()
                        onComplete(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring screen image", e)
                    virtualDisplay?.release()
                    projection.stop()
                    onComplete(null)
                }
            }, 300)
        } catch (e: Exception) {
            Log.e(TAG, "Failed screen capture", e)
            onComplete(null)
        }
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
