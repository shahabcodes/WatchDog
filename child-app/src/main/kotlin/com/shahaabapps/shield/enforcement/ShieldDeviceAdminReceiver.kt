package com.shahaabapps.shield.enforcement

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * Device-admin receiver. While Shield is an active device admin, Android disables
 * its Uninstall button — a parent must turn protection off (via PIN) first. When
 * admin is turned off (or on), the new state is reported at once, so the parent's
 * tamper alert doesn't wait for the next heartbeat.
 */
@dagger.hilt.android.AndroidEntryPoint
class ShieldDeviceAdminReceiver : DeviceAdminReceiver() {
    @javax.inject.Inject lateinit var childRepository: com.shahaabapps.core.firebase.child.ChildRepository
    @javax.inject.Inject lateinit var authRepository: com.shahaabapps.core.firebase.auth.AuthRepository

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets Shield be uninstalled. A parent PIN is required to remove protection."

    override fun onDisabled(context: Context, intent: Intent) = report(context)

    override fun onEnabled(context: Context, intent: Intent) = report(context)

    private fun report(context: Context) {
        val childId = authRepository.currentUserIdOrNull() ?: return
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { childRepository.updatePermissions(childId, ChildPermissions.snapshot(context.applicationContext)) }
            pending.finish()
        }
    }
}

/** Helpers for Shield's device-admin state. */
object DeviceAdmin {
    fun component(context: Context): ComponentName =
        ComponentName(context, ShieldDeviceAdminReceiver::class.java)

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    fun isActive(context: Context): Boolean = dpm(context).isAdminActive(component(context))

    fun addIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Lets Watch Dog stop this app from being uninstalled.",
            )
        }

    fun remove(context: Context) {
        runCatching { dpm(context).removeActiveAdmin(component(context)) }
    }
}
