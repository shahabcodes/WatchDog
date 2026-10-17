package com.shahaabapps.shield.install

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
        val label = runCatching {
            info.applicationInfo?.let { ai ->
                ai.sourceDir = file.absolutePath
                ai.publicSourceDir = file.absolutePath
                pm.getApplicationLabel(ai).toString()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: pkg
        quarantine(file)
        notifyBlocked(label, pkg)
    }

    /** Rename the APK so the installer can't open it; delete as a fallback. */
    private fun quarantine(file: File) {
        val target = File(file.parentFile, file.name + BLOCKED_SUFFIX)
        val renamed = runCatching { file.renameTo(target) }.getOrDefault(false)
        if (!renamed) runCatching { file.delete() }
    }

    private fun notifyBlocked(label: String, pkg: String) {
        if (ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        ensureChannel()
        val n = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Install blocked")
            .setContentText("“$label” can't be installed from an unknown source (blocked by your family).")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(pkg.hashCode(), n) }
    }

    private fun ensureChannel() {
        val mgr = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Install guard", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Tells you when a blocked app install is stopped." },
            )
        }
    }

    private fun hasStorageAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val CHANNEL_ID = "install_guard"
        const val BLOCKED_SUFFIX = ".blocked_by_shield"
    }
}
