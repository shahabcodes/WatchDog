package com.shahaabapps.core.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether changes saved in the app have reached the server yet.
 *
 * Firestore saves offline straight away and sends the changes when the phone is
 * back online, so a parent's edit may not have reached the child's phone yet.
 * This watches for unsent writes (all of them, whichever screen made them) and
 * for connectivity, so the UI can say "waiting to sync".
 */
@Singleton
class SyncStatus @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(isOnlineNow())
    /** The phone has a working internet connection. */
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _pending = MutableStateFlow(false)
    /** Some saved changes haven't reached the server yet. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    init {
        runCatching {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
                override fun onLost(network: Network) { _online.value = isOnlineNow() }
            })
        }
        scope.launch {
            while (true) {
                // waitForPendingWrites() completes at once when nothing is unsent,
                // otherwise when everything written so far is acknowledged.
                val task = runCatching { firestore.waitForPendingWrites() }.getOrNull()
                if (task == null) {
                    delay(POLL_MS)
                    continue
                }
                val doneQuickly = withTimeoutOrNull(QUICK_MS) { runCatching { task.await() } } != null
                if (doneQuickly) {
                    _pending.value = false
                } else {
                    _pending.value = true
                    runCatching { task.await() }
                    _pending.value = false
                }
                delay(POLL_MS)
            }
        }
    }

    private fun isOnlineNow(): Boolean = runCatching {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    private companion object {
        const val QUICK_MS = 1_500L
        const val POLL_MS = 1_000L
    }
}
