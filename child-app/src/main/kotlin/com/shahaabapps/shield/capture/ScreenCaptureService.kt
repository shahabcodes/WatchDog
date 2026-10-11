package com.shahaabapps.shield.capture

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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.screenshot.ScreenshotRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Holds a [MediaProjection] session (after one-time user consent) and, when the
 * parent requests a screenshot, captures the current screen and uploads it.
 *
 * Android shows a mandatory screen-capture indicator while this runs — there is no
 * way to hide it. Capture uses an on-demand VirtualDisplay + ImageReader so no
 * continuous mirroring happens between requests.
 */
@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject lateinit var screenshotRepository: ScreenshotRepository
    @Inject lateinit var authRepository: AuthRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var projection: MediaProjection? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastNonce = 0L
    private var observing = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForegroundCompat()
        } catch (t: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || data == null) {
            // No fresh consent token (e.g. restarted by the system) — can't project.
            stopSelf()
            return START_NOT_STICKY
        }

        if (projection == null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = runCatching { mpm.getMediaProjection(resultCode, data) }.getOrNull()
            if (proj == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            handlerThread = HandlerThread("screen-capture").also { it.start() }
            handler = Handler(handlerThread!!.looper)
            proj.registerCallback(projectionCallback, handler)
            projection = proj
            running = true
            startObserving()
        }
        return START_STICKY
    }

    private fun startObserving() {
        if (observing) return
        val childId = authRepository.currentUserIdOrNull() ?: return
        observing = true
        scope.launch {
            var first = true
            screenshotRepository.observeCaptureRequests(childId).collectLatest { nonce ->
                if (first) {
                    // Adopt the current request as the baseline so enabling capture
                    // doesn't replay the last-ever request.
                    first = false
                    lastNonce = nonce
                    return@collectLatest
                }
                if (nonce > lastNonce) {
                    lastNonce = nonce
                    if (nonce > 0) captureAndUpload(childId)
                }
            }
        }
    }

    private suspend fun captureAndUpload(childId: String) {
        val png = runCatching { capturePng() }.getOrNull() ?: return
        screenshotRepository.uploadScreenshot(childId, png)
    }

    /** Captures one frame via a short-lived VirtualDisplay and returns a PNG. */
    private suspend fun capturePng(): ByteArray? {
        val proj = projection ?: return null
        val metrics = screenMetrics()
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        if (width <= 0 || height <= 0) return null

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val display: VirtualDisplay = proj.createVirtualDisplay(
            "shield-capture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler,
        )

        return try {
            val bitmap = withTimeoutOrNull(4_000) { awaitFrame(reader, width, height) }
            bitmap?.let { downscaleToPng(it) }
        } finally {
            runCatching { display.release() }
            runCatching { reader.close() }
        }
    }

    private suspend fun awaitFrame(reader: ImageReader, width: Int, height: Int): Bitmap? {
        // Poll for the first available frame (mirroring takes a moment to render).
        repeat(40) {
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bmp = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888,
                    )
                    bmp.copyPixelsFromBuffer(buffer)
                    return Bitmap.createBitmap(bmp, 0, 0, width, height)
                } finally {
                    image.close()
                }
            }
            delay(100)
        }
        return null
    }

    private fun downscaleToPng(bitmap: Bitmap): ByteArray {
        val maxW = 900
        val scaled = if (bitmap.width > maxW) {
            val ratio = maxW.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(bitmap, maxW, (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    @Suppress("DEPRECATION")
    private fun screenMetrics(): DisplayMetrics {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun startForegroundCompat() {
        val channelId = "screen_capture"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(channelId, "Screen capture", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Shield")
            .setContentText("Screen capture is available to parents")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        running = false
        runCatching { projection?.unregisterCallback(projectionCallback) }
        runCatching { projection?.stop() }
        projection = null
        runCatching { handlerThread?.quitSafely() }
        scope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 73
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "com.shahaabapps.shield.action.STOP_CAPTURE"

        /** True while a projection session is live (for UI status). */
        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
