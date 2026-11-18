package com.shahaabapps.shield.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.request.RequestRepository
import com.shahaabapps.core.model.RequestKind
import com.shahaabapps.core.model.TimeRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * "Ask to install" on the install-blocked notification: asks the parent to allow
 * the app. Once they do, [ApkInstallGuard] gives the held APK back.
 */
@AndroidEntryPoint
class InstallAskReceiver : BroadcastReceiver() {

    @Inject lateinit var requestRepository: RequestRepository
    @Inject lateinit var authRepository: AuthRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotBlank() } ?: return
        val label = intent.getStringExtra(EXTRA_LABEL)?.take(100)?.ifBlank { null } ?: pkg
        val childId = authRepository.currentUserIdOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Offline, the write waits in Firestore's queue and goes out later:
                // a timeout still counts as asked.
                val result = runCatching {
                    withTimeoutOrNull(8_000) {
                        requestRepository.create(
                            childId,
                            TimeRequest(kind = RequestKind.ALLOW_INSTALL, appPackage = pkg, appLabel = label),
                        )
                    }
                }
                val sent = result.isSuccess && result.getOrNull() !is AppResult.Failure
                ApkInstallGuard.post(
                    context, pkg,
                    ApkInstallGuard.builder(context)
                        .setContentTitle(if (sent) "Asked your parent" else "Couldn't ask your parent")
                        .setContentText(
                            if (sent) "You'll get a notification here if they allow $label."
                            else "Check your connection and download $label again.",
                        ),
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_LABEL = "label"
    }
}
