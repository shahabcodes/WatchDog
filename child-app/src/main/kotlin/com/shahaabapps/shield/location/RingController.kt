package com.shahaabapps.shield.location

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays a loud alarm and shows a full-screen notification when the parent rings
 * the device. Survives being backgrounded because it is driven by the
 * (foreground) [GeofenceSyncService].
 */
@Singleton
class RingController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var player: MediaPlayer? = null
    private var lastMessage: String = ""
    private var mediaSession: MediaSession? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    fun start(message: String) {
        lastMessage = message
        ensureChannel()
        showNotification(message)
        startAlarm()
        cancelSnoozeAlarm()
        // Capture hardware buttons even when no ring UI is in the foreground:
        // volume keys (via a MediaSession) and power (via screen-off) stop the ring.
        startVolumeCapture()
        registerScreenOff()
        // Try to bring the full-screen alarm up directly (works when allowed);
        // otherwise the notification's full-screen intent handles it.
        runCatching { context.startActivity(RingActivity.intent(context, message)) }
    }

    /** Stop the ring the same way the UI would (also clears the Firestore ring). */
    private fun dismissViaService() {
        val intent = Intent(context, GeofenceSyncService::class.java)
            .setAction(GeofenceSyncService.ACTION_RING_DISMISS)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    private fun startVolumeCapture() {
        runCatching {
            val session = MediaSession(context, "shield_ring")
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build(),
            )
            session.setPlaybackToRemote(
                object : VolumeProvider(VOLUME_CONTROL_RELATIVE, 100, 50) {
                    override fun onAdjustVolume(direction: Int) { dismissViaService() }
                    override fun onSetVolumeTo(volume: Int) { dismissViaService() }
                },
            )
            session.isActive = true
            mediaSession = session
        }
    }

    private fun registerScreenOff() {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_SCREEN_OFF) dismissViaService()
            }
        }
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
        screenOffReceiver = receiver
    }

    private fun releaseButtonCapture() {
        runCatching { mediaSession?.isActive = false }
        runCatching { mediaSession?.release() }
        mediaSession = null
        screenOffReceiver?.let { r -> runCatching { context.unregisterReceiver(r) } }
        screenOffReceiver = null
    }

    /** Stop the alarm now, then ring again after [SNOOZE_MINUTES] minutes. */
    fun snooze() {
        stopAlarmOnly()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, snoozePendingIntent())
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, snoozePendingIntent())
            }
        }
    }

    /** Fully stop: silence the alarm, clear the notification, cancel any snooze. */
    fun stop() {
        stopAlarmOnly()
        cancelSnoozeAlarm()
        notificationManager().cancel(RING_NOTIFICATION_ID)
    }

    fun lastMessage(): String = lastMessage

    private fun startAlarm() {
        stopAlarmOnly()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                prepare()
                start()
            }
        }
        // Raise alarm volume so the ring is audible.
        runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
        vibrate()
    }

    private fun stopAlarmOnly() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { vibrator()?.cancel() }
        releaseButtonCapture()
    }

    private fun vibrate() {
        val vibrator = vibrator() ?: return
        val pattern = longArrayOf(0, 800, 500)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun showNotification(message: String) {
        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            RingActivity.intent(context, message),
            pendingFlags(),
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Your family is trying to reach you")
            .setContentText(message.ifBlank { "Tap to open Shield" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.ifBlank { "Tap to open Shield" }))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, "Snooze ${SNOOZE_MINUTES}m", servicePendingIntent(GeofenceSyncService.ACTION_RING_SNOOZE, 1))
            .addAction(0, "Dismiss", servicePendingIntent(GeofenceSyncService.ACTION_RING_DISMISS, 2))
        runCatching { notificationManager().notify(RING_NOTIFICATION_ID, builder.build()) }
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, GeofenceSyncService::class.java).setAction(action)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, requestCode, intent, pendingFlags())
        } else {
            PendingIntent.getService(context, requestCode, intent, pendingFlags())
        }
    }

    private fun snoozePendingIntent(): PendingIntent =
        servicePendingIntent(GeofenceSyncService.ACTION_RING_RESUME, 3)

    private fun cancelSnoozeAlarm() {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(snoozePendingIntent())
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = notificationManager()
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Ring device",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Alerts when a parent rings this device"
                    setSound(null, null) // sound handled by the alarm player
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun notificationManager() =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun pendingFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    private companion object {
        const val CHANNEL_ID = "ring_device"
        const val RING_NOTIFICATION_ID = 43
        const val SNOOZE_MINUTES = 5
    }
}
