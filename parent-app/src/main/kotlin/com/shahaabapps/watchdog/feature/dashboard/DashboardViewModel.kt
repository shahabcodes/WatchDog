package com.shahaabapps.watchdog.feature.dashboard

import com.shahaabapps.core.firebase.callSafely

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahaabapps.core.common.AppResult
import com.shahaabapps.core.firebase.auth.AuthRepository
import com.shahaabapps.core.firebase.child.ChildRepository
import com.shahaabapps.core.firebase.child.LinkOutcome
import com.shahaabapps.core.firebase.e2ee.E2eeManager
import com.shahaabapps.core.model.ChildAccount
import com.shahaabapps.core.model.PairingCodec
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed interface LinkState {
    data object Idle : LinkState
    data object Linking : LinkState
    /** The code was accepted; the child's phone must tap Approve. */
    data class AwaitingApproval(val childId: String) : LinkState
    data class Success(val childName: String?) : LinkState
    data class Error(val message: String) : LinkState
}

sealed interface VerifyState {
    data object Idle : VerifyState
    data object Success : VerifyState
    data class Error(val message: String) : VerifyState
}

sealed interface ReconnectState {
    data object Idle : ReconnectState
    data object Working : ReconnectState
    /** Done; [waitMinutes] > 0 when it waits for the other parents first. */
    data class Success(val waitMinutes: Int = 0) : ReconnectState
    data class Error(val message: String) : ReconnectState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val childRepository: ChildRepository,
    private val e2eeManager: E2eeManager,
    syncStatus: com.shahaabapps.core.firebase.SyncStatus,
) : ViewModel() {

    /** Saved changes not yet sent to the server (e.g. made while offline). */
    val syncPending: StateFlow<Boolean> = syncStatus.pending
    val online: StateFlow<Boolean> = syncStatus.online

    init {
        // Publish this parent's E2EE public key so linked children can wrap their
        // data key for it, for every account signed in on this phone (signing out
        // removes it, so a second account signed in later must publish too).
        // Idempotent; safe on every launch.
        viewModelScope.launch {
            authRepository.currentUserId.filterNotNull().distinctUntilChanged().collect { uid ->
                runCatching { e2eeManager.publishParentKey(uid) }
            }
        }
    }

    val children: StateFlow<List<ChildAccount>> = authRepository.currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else childRepository.observeLinkedChildren(uid)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * True until the first children snapshot arrives for the current user, so the
     * UI shows a spinner — not the empty/first-run "add a child" screen — during
     * the brief gap right after sign-in before the children query responds.
     */
    val loading: StateFlow<Boolean> = authRepository.currentUserId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(true)
            else childRepository.observeLinkedChildren(uid).map { false }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    private val _linkState = MutableStateFlow<LinkState>(LinkState.Idle)
    val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    fun onQrScanned(contents: String?) {
        val pairing = PairingCodec.decode(contents)
        if (pairing == null) {
            _linkState.value = LinkState.Error("That QR code isn't a valid pairing code.")
            return
        }
        linkWith(pairing.code, pairing.childPublicKey)
    }

    fun onManualCode(code: String) = linkWith(code.trim())

    fun onImageDecodeFailed() {
        _linkState.value = LinkState.Error("No QR code found in that image.")
    }

    private fun linkWith(code: String, childPublicKey: String? = null) {
        _linkState.value = LinkState.Linking
        viewModelScope.launch {
            _linkState.value = when (val result = childRepository.linkChild(code)) {
                is AppResult.Success -> {
                    val childId = result.data.childId
                    // Pin the child's key captured out-of-band from the QR, so this
                    // link is verified independently of the backend.
                    childPublicKey?.let { runCatching { e2eeManager.pinChildKey(childId, it) } }
                    when (result.data) {
                        is LinkOutcome.Linked -> LinkState.Success(null)
                        is LinkOutcome.AwaitingApproval -> {
                            awaitApproval(childId)
                            LinkState.AwaitingApproval(childId)
                        }
                    }
                }
                is AppResult.Failure -> {
                    val message = result.error.message ?: "Couldn't link this device."
                    // Unverified email: send the link so the parent can verify and retry.
                    if (message.startsWith("Verify your email")) runCatching { authRepository.sendEmailVerification() }
                    LinkState.Error(message)
                }
            }
        }
    }

    /** Flip to Success once the child's phone approves (the child joins our list). */
    private fun awaitApproval(childId: String) {
        viewModelScope.launch {
            val approved = kotlinx.coroutines.withTimeoutOrNull(APPROVAL_WAIT_MS) {
                children.first { list -> list.any { it.childId == childId } }
            }
            val state = _linkState.value
            if (state is LinkState.AwaitingApproval && state.childId == childId) {
                _linkState.value = if (approved != null) {
                    LinkState.Success(null)
                } else {
                    LinkState.Error("The child's phone didn't approve in time. Scan a fresh code to try again.")
                }
            }
        }
    }

    private val _verifyState = MutableStateFlow<VerifyState>(VerifyState.Idle)
    val verifyState: StateFlow<VerifyState> = _verifyState.asStateFlow()

    /** Returns whether [childId]'s key has been pinned (verified out-of-band). */
    fun keyVerified(childId: String): kotlinx.coroutines.flow.Flow<Boolean> =
        e2eeManager.observeChildKeyVerified(childId).map { it != null }

    /**
     * Re-scan flow for an already-linked child: pins the key from the QR without
     * re-linking (the child is no longer in pairing mode). [expectedChildId], when
     * given, guards against scanning the wrong device's code.
     */
    fun verifyKeyFromQr(contents: String?, expectedChildId: String? = null) {
        val pairing = PairingCodec.decode(contents)
        val key = pairing?.childPublicKey
        when {
            pairing == null -> _verifyState.value = VerifyState.Error("That isn't a valid pairing code.")
            expectedChildId != null && pairing.childId != expectedChildId ->
                _verifyState.value = VerifyState.Error("That code is for a different device.")
            key == null -> _verifyState.value =
                VerifyState.Error("This device's app is out of date — update Shield, then try again.")
            else -> viewModelScope.launch {
                runCatching { e2eeManager.pinChildKey(pairing.childId, key) }
                    .onSuccess { _verifyState.value = VerifyState.Success }
                    .onFailure { _verifyState.value = VerifyState.Error("Couldn't verify. Try again.") }
            }
        }
    }

    fun clearVerifyState() { _verifyState.value = VerifyState.Idle }

    private val _reconcileBusy = MutableStateFlow(false)
    val reconcileBusy: StateFlow<Boolean> = _reconcileBusy.asStateFlow()

    /**
     * Manually reconcile the E2EE secure link for [childId]: drop the cached key
     * and ask the child to re-wrap its data key for this device now. Fixes a
     * "can't see location/history" stall after this device's key rotated.
     */
    fun reconcileSecureLink(childId: String) {
        val parentId = authRepository.currentUserIdOrNull() ?: return
        if (_reconcileBusy.value) return
        viewModelScope.launch {
            _reconcileBusy.value = true
            runCatching { e2eeManager.reconcileAsParent(childId, parentId) }
            kotlinx.coroutines.delay(1_500)
            _reconcileBusy.value = false
        }
    }

    /** True once after a 2FA backup-code sign-in (which turned 2FA off). */
    fun consumeBackupCodeSignInNotice(): Boolean = authRepository.consumeBackupCodeSignInNotice()

    fun clearLinkState() {
        _linkState.value = LinkState.Idle
    }

    /** True while signing out (network clean-up), for the "Signing out…" overlay. */
    val signingOut: StateFlow<Boolean> = authRepository.signingOut

    /** Signs the parent out; the app returns to the auth flow. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Unlinks (removes) a child from this parent. */
    fun removeChild(childId: String) {
        viewModelScope.launch { childRepository.unlinkChild(childId) }
    }

    /** The "Parents" dialog for one child: who supervises it, and removals waiting on its phone. */
    data class ParentsDialog(
        val childId: String,
        val childName: String,
        val parents: List<com.shahaabapps.core.firebase.child.ParentInfo>? = null,
        val pendingRemovals: Set<String> = emptySet(),
        val message: String? = null,
        val busy: Boolean = false,
    )

    private val _parentsDialog = MutableStateFlow<ParentsDialog?>(null)
    val parentsDialog: StateFlow<ParentsDialog?> = _parentsDialog.asStateFlow()
    private var parentsJob: kotlinx.coroutines.Job? = null

    val myUid: String? get() = authRepository.currentUserIdOrNull()

    fun openParents(childId: String, childName: String) {
        _parentsDialog.value = ParentsDialog(childId, childName)
        parentsJob?.cancel()
        parentsJob = viewModelScope.launch {
            when (val r = childRepository.linkedParentInfo(childId)) {
                is AppResult.Success -> _parentsDialog.value = _parentsDialog.value?.copy(parents = r.data)
                is AppResult.Failure -> _parentsDialog.value = _parentsDialog.value?.copy(
                    parents = emptyList(), message = r.error.message ?: "Couldn't load the parents.",
                )
            }
            childRepository.observeParentRemovals(childId).collect { list ->
                _parentsDialog.value = _parentsDialog.value?.copy(pendingRemovals = list.map { it.parentId }.toSet())
            }
        }
    }

    fun closeParents() {
        parentsJob?.cancel()
        _parentsDialog.value = null
    }

    fun requestRemoval(parentId: String) {
        val d = _parentsDialog.value ?: return
        _parentsDialog.value = d.copy(busy = true, message = null)
        viewModelScope.launch {
            val r = childRepository.requestParentRemoval(d.childId, parentId)
            _parentsDialog.value = _parentsDialog.value?.copy(
                busy = false,
                message = when (r) {
                    is AppResult.Success -> "Asked ${d.childName}'s phone to approve. Nothing changes until it does."
                    is AppResult.Failure -> r.error.message ?: "Couldn't send the request."
                },
            )
        }
    }

    private val _reconnectState = MutableStateFlow<ReconnectState>(ReconnectState.Idle)
    val reconnectState: StateFlow<ReconnectState> = _reconnectState.asStateFlow()

    /**
     * Restore a reinstalled Shield as the same child: [oldChildId] is the child in
     * the list, [contents] the QR scanned on the reinstalled phone. Encrypted data
     * is bound to the child's id and key, so the reinstalled phone takes over the
     * old record (a one-time sign-in from the server) and this phone hands it the
     * old data key, wrapped for the new install's key from the QR.
     */
    fun reconnectChild(oldChildId: String, contents: String?) {
        val pairing = PairingCodec.decode(contents)
        val newKey = pairing?.childPublicKey
        if (pairing == null || newKey == null) {
            _reconnectState.value = ReconnectState.Error("That QR code isn't a valid pairing code. Scan the QR (not the typed code).")
            return
        }
        val me = authRepository.currentUserIdOrNull() ?: return
        _reconnectState.value = ReconnectState.Working
        viewModelScope.launch {
            // Without the old key the restored phone couldn't read its places and rules.
            val ring = e2eeManager.ringForRestore(oldChildId, me)
            if (ring == null) {
                _reconnectState.value = ReconnectState.Error(
                    "Couldn't read this child's encryption key on this phone. Open the child's page once, then try again.",
                )
                return@launch
            }
            if (!e2eeManager.handRingToRestoredChild(oldChildId, me, newKey, ring)) {
                _reconnectState.value = ReconnectState.Error("Couldn't prepare the restore. Check the internet connection.")
                return@launch
            }
            val data = hashMapOf(
                "oldChildId" to oldChildId,
                "newChildId" to pairing.childId,
                "code" to pairing.code,
            )
            _reconnectState.value = runCatching {
                FirebaseFunctions.getInstance().getHttpsCallable("restoreChild").callSafely(name = "restoreChild", data = data)
            }.fold(
                onSuccess = { r ->
                    val until = ((r.getData() as? Map<*, *>)?.get("pendingUntilMillis") as? Number)?.toLong() ?: 0L
                    val wait = if (until > 0) ((until - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1) else 0
                    ReconnectState.Success(wait)
                },
                onFailure = {
                    runCatching { e2eeManager.cancelRingHandoff(oldChildId, me) }
                    ReconnectState.Error(it.message ?: "Couldn't restore this phone.")
                },
            )
        }
    }

    fun clearReconnectState() { _reconnectState.value = ReconnectState.Idle }

    /** Children with a restore waiting for the other parents (cancellable). */
    val pendingRestores: StateFlow<Set<String>> = children
        .map { list -> list.map { it.childId } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptySet())
            else kotlinx.coroutines.flow.combine(ids.map { id -> observePendingRestore(id) }) { flags ->
                ids.filterIndexed { i, _ -> flags[i] }.toSet()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private fun observePendingRestore(childId: String) = kotlinx.coroutines.flow.callbackFlow {
        val reg = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("pendingRestores").document(childId)
            .addSnapshotListener { snap, err -> trySend(err == null && snap?.exists() == true) }
        awaitClose { reg.remove() }
    }

    fun cancelRestore(childId: String) {
        viewModelScope.launch {
            _reconnectState.value = runCatching {
                FirebaseFunctions.getInstance().getHttpsCallable("cancelRestore")
                    .callSafely(name = "cancelRestore", data = hashMapOf("childId" to childId))
            }.fold(
                onSuccess = { ReconnectState.Idle },
                onFailure = { ReconnectState.Error(it.message ?: "Couldn't cancel the restore.") },
            )
        }
    }

    private companion object {
        const val APPROVAL_WAIT_MS = 15 * 60_000L
    }
}
