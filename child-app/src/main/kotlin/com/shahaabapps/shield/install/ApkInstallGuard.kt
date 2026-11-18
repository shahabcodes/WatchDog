package com.shahaabapps.shield.install

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.installguard.InstallGuardRepository
import com.shahaabapps.core.model.InstallGuard
import com.shahaabapps.shield.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Enforces the parent's sideload policy ([InstallGuard]) by watching the device's
 * download folders for new `.apk` files, reading each one's TRUE package name from
 * its manifest ([PackageManager.getPackageArchiveInfo]), and quarantining (renaming
 * so it can't be installed) any APK whose package the policy blocks.
 *
 * Event-driven (inotify via [FileObserver]) — idle cost is ~zero; it only does work
 * when an APK actually appears. Covers the common "download then install/update"
 * path; it cannot see streamed/adb installs (those have no readable file).
 */
class ApkInstallGuard(
    context: Context,
    private val scope: CoroutineScope,
    private val installGuardRepository: InstallGuardRepository,
    private val authRepository: AuthRepository,
) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager

    @Volatile private var policy = InstallGuard()
    private var observers: List<FileObserver> = emptyList()

    fun start() {
        val childId = authRepository.currentUserIdOrNull() ?: return
        scope.launch {
            installGuardRepository.observe(childId).collectLatest { guard ->
                policy = guard
                refreshWatchers()
                // A parent who allowed an app (or turned the guard off) frees the
                // APKs held back so far.
                launch(Dispatchers.IO) { releaseAllowed() }
            }
        }
    }

    fun stop() {
        observers.forEach { runCatching { it.stopWatching() } }
        observers = emptyList()
    }

    /** (Re)start inotify watchers only while the guard is on and storage is readable. */
    private fun refreshWatchers() {
        stop()
        if (!policy.enabled || !hasStorageAccess()) return
        observers = watchDirs().mapNotNull { dir -> runCatching { observerFor(dir).also { it.startWatching() } }.getOrNull() }
    }

    private fun watchDirs(): List<File> = buildList {
        runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }
            .getOrNull()?.let { if (it.isDirectory) add(it) }
    }

    private fun observerFor(dir: File): FileObserver {
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) = handle(dir, path)
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) = handle(dir, path)
            }
        }
    }

    private fun handle(dir: File, path: String?) {
        val name = path ?: return
        if (!name.endsWith(".apk", ignoreCase = true)) return
        val file = File(dir, name)
        // Parse off the observer thread; the file is complete on CLOSE_WRITE/MOVED_TO.
        scope.launch(Dispatchers.IO) { inspect(file) }
    }

    private fun inspect(file: File) {
        if (!file.exists()) return
        val info = runCatching { pm.getPackageArchiveInfo(file.absolutePath, 0) }.getOrNull() ?: return
        val pkg = info.packageName ?: return
        // Never block Shield's own APK — the parent must always be able to
        // install/update the protection app itself.
        if (pkg == appContext.packageName) return
        if (!policy.blocks(pkg)) return
        val label = labelOf(info, file) ?: pkg
        if (quarantine(file)) {
            appContext.getSharedPreferences(HELD_PREFS, Context.MODE_PRIVATE).edit()
                .putString(File(file.parentFile, file.name + BLOCKED_SUFFIX).absolutePath, "$pkg\n$label")
                .apply()
            notifyBlocked(label, pkg, canAsk = true)
        } else {
            notifyBlocked(label, pkg, canAsk = false)
        }
    }

    /** Gives back the held APKs the policy now allows, and tells the child. */
    private fun releaseAllowed() {
        if (!hasStorageAccess()) return
        // Each held file's package, noted when it was held back (a renamed APK
        // isn't reliably readable as one).
        val prefs = appContext.getSharedPreferences(HELD_PREFS, Context.MODE_PRIVATE)
        prefs.all.forEach { (path, value) ->
            val held = File(path)
            val (pkg, label) = (value as? String)?.split('\n', limit = 2)?.takeIf { it.size == 2 }
                ?: run { prefs.edit().remove(path).apply(); return@forEach }
            if (!held.exists()) { prefs.edit().remove(path).apply(); return@forEach }
            if (policy.blocks(pkg)) return@forEach
            val target = File(held.parentFile, held.name.removeSuffix(BLOCKED_SUFFIX))
            if (target.exists() || !runCatching { held.renameTo(target) }.getOrDefault(false)) return@forEach
            prefs.edit().remove(path).apply()
            notifyAllowed(label, pkg)
        }
    }

    private fun labelOf(info: android.content.pm.PackageInfo, file: File): String? = runCatching {
        info.applicationInfo?.let { ai ->
            ai.sourceDir = file.absolutePath
            ai.publicSourceDir = file.absolutePath
            pm.getApplicationLabel(ai).toString()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Rename the APK so the installer can't open it; delete as a fallback. */
    private fun quarantine(file: File): Boolean {
        val target = File(file.parentFile, file.name + BLOCKED_SUFFIX)
        runCatching { target.delete() } // an older copy held back under the same name
        val renamed = runCatching { file.renameTo(target) }.getOrDefault(false)
        if (!renamed) runCatching { file.delete() }
        return renamed
    }

    private fun notifyBlocked(label: String, pkg: String, canAsk: Boolean) {
        val ask = PendingIntent.getBroadcast(
            appContext,
            pkg.hashCode(),
            Intent(appContext, InstallAskReceiver::class.java)
                .putExtra(InstallAskReceiver.EXTRA_PACKAGE, pkg)
                .putExtra(InstallAskReceiver.EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "$label can't be installed from outside the Play Store." +
            if (canAsk) " Ask your parent to allow it." else ""
        post(
            appContext, pkg,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle("Install blocked")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .apply { if (canAsk) addAction(0, "Ask to install", ask) },
        )
    }

    private fun notifyAllowed(label: String, pkg: String) {
        val downloads = PendingIntent.getActivity(
            appContext,
            pkg.hashCode(),
            Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            appContext, pkg,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle("You can install $label")
                .setContentText("Your parent allowed it. Open it from Downloads to install.")
                .setContentIntent(downloads),
        )
    }

    companion object {
        private const val CHANNEL_ID = "install_guard"
        private const val BLOCKED_SUFFIX = ".blocked_by_shield"
        private const val HELD_PREFS = "install_guard_held"

        /** Posts (or replaces) the install-guard notification for [pkg]. */
        internal fun post(context: Context, pkg: String, builder: NotificationCompat.Builder) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ensureChannel(context)
            val n = builder
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(pkg.hashCode(), n) }
        }

        internal fun builder(context: Context) = NotificationCompat.Builder(context, CHANNEL_ID)

        private fun ensureChannel(appContext: Context) {
        val mgr = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Install guard", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Tells you when a blocked app install is stopped." },
            )
        }
    }
    }

    private fun hasStorageAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
}
