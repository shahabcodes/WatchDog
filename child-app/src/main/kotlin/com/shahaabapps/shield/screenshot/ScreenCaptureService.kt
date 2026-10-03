package com.shahaabapps.shield.screenshot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.screenshot.ScreenshotRepository
import com.shahaabapps.core.model.ScreenshotCommand
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Foreground service holding a [MediaProjection]. Listens for pending screenshot
 * commands and fulfils each by capturing the screen, compressing to JPEG and
 * uploading as base64 (kept small enough for a Firestore document).
 */
@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject lateinit var screenshotRepository: ScreenshotRepository
    @Inject lateinit var authRepository: AuthRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var width = 0
    private var height = 0
    private var density = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode == 0 || data == null) {
            stopSelf(); return START_NOT_STICKY
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data).also {
            it.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { teardown() }
            }, null)
        }
        setupCapture()
        listenForRequests()
        return START_STICKY
    }

    private fun setupCapture() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "shield-capture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null,
        )
    }

    private fun listenForRequests() {
        val childId = authRepository.currentUserIdOrNull() ?: run { stopSelf(); return }
        scope.launch {
            screenshotRepository.observePendingRequests(childId).collectLatest { pending ->
                pending.forEach { command -> fulfil(childId, command) }
            }
        }
    }

    private suspend fun fulfil(childId: String, command: ScreenshotCommand) {
        val base64 = runCatching { captureBase64() }.getOrNull()
        val result = if (base64 != null) {
            screenshotRepository.submitResult(childId, command.id, base64)
        } else {
            screenshotRepository.submitFailure(childId, command.id, "Couldn't capture the screen.")
        }
        if (result is AppResult.Failure) { /* best effort */ }
    }

    private suspend fun captureBase64(): String? {
        val reader = imageReader ?: return null
        // Give the mirror a moment to produce a frame.
        var image = reader.acquireLatestImage()
        var attempts = 0
        while (image == null && attempts < 10) {
            delay(100); image = reader.acquireLatestImage(); attempts++
        }
        image ?: return null

        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val full = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888,
        )
        full.copyPixelsFromBuffer(buffer)
        image.close()
        val cropped = Bitmap.createBitmap(full, 0, 0, width, height)

        // Downscale so the base64 JPEG stays under Firestore's 1 MB limit.
        val scaled = downscale(cropped, maxWidth = 720)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 55, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun downscale(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width
        return Bitmap.createScaledBitmap(
            bitmap, maxWidth, (bitmap.height * ratio).toInt(), true,
        )
    }

    private fun startForegroundCompat() {
        val channelId = "screen_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Screen sharing", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Shield")
            .setContentText("Screen sharing is available to parents")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun teardown() {
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        projection = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        teardown()
        scope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 77
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
