package com.shahaabapps.core.firebase.e2ee

/**
 * Decisions about a parent's shared (per-account) key, `publicKeys/{uid}`, which
 * older app versions still use alongside the per-device keys. Kept free of
 * Android and Firebase so the rules can be unit-tested. Keys are compared by
 * fingerprint.
 */
internal object SharedKeyPolicy {

    /** What the server says the shared key is. */
    sealed interface SharedKey {
        /** The server couldn't be reached; [cachedFp] is the offline copy, if any. */
        data class Offline(val cachedFp: String?) : SharedKey
        /** The parent has no shared key (their last phone signed out). */
        data object Missing : SharedKey
        data class Present(val fp: String) : SharedKey
    }

    /** What Shield does with its copy of the data key sealed for the shared key. */
    enum class ChildAction {
        /** Wrap for the shared key; the pin is already right. */
        WRAP,
        /** Trust the shared key: pin it, then wrap for it. */
        PIN_AND_WRAP,
        /** Leave the shared wrap as it is and check again later. */
        SKIP,
        /** Delete the shared wrap and stop sharing with that key (signed-out phone). */
        DROP,
        /** A key nobody approved: wait for approval on the child's phone. */
        ASK,
    }

    /**
     * @param pinnedFp the shared key this phone trusts, if any.
     * @param revokedFps keys of the parent's phones that signed out.
     * @param sharedWrapFp the key the existing shared wrap was sealed for (server copy).
     * @param approvedDeviceFps keys of the parent's phones this phone has approved.
     * @param listedDeviceFps the parent's phones on the server now (null if unknown).
     */
    fun childAction(
        shared: SharedKey,
        pinnedFp: String?,
        revokedFps: Set<String>,
        sharedWrapFp: String?,
        approvedDeviceFps: Set<String>,
        listedDeviceFps: Set<String>?,
    ): ChildAction = when (shared) {
        // Offline: keep using a key that is already trusted, decide nothing new.
        is SharedKey.Offline -> when {
            shared.cachedFp == null || shared.cachedFp in revokedFps -> ChildAction.SKIP
            pinnedFp != null && pinnedFp != shared.cachedFp -> ChildAction.SKIP
            else -> ChildAction.WRAP
        }
        SharedKey.Missing -> ChildAction.DROP
        is SharedKey.Present -> when {
            // A phone that signed out: refused while it's gone, trusted again once it
            // has signed back in (it is listed again; it was approved before).
            shared.fp in revokedFps -> if (listedDeviceFps?.contains(shared.fp) == true) ChildAction.PIN_AND_WRAP else ChildAction.DROP
            pinnedFp == shared.fp -> ChildAction.WRAP
            // Linked before pinning existed: trust what is there now (first sight).
            pinnedFp == null -> ChildAction.PIN_AND_WRAP
            // This phone already shared with exactly this key: the pin was out of date.
            sharedWrapFp == shared.fp -> ChildAction.PIN_AND_WRAP
            // Handed over to another of the parent's phones that was approved here.
            shared.fp in approvedDeviceFps -> ChildAction.PIN_AND_WRAP
            // Another of the parent's phones overwrote it; its own wrap covers it.
            listedDeviceFps != null && pinnedFp in listedDeviceFps -> ChildAction.SKIP
            else -> ChildAction.ASK
        }
    }

    /**
     * Whether a parent phone's key can be trusted without asking (true), or only if
     * this phone already shared with it (false: check its existing wrap).
     */
    fun deviceTrustedOnSight(
        fp: String,
        pinnedFp: String?,
        hasAnyPin: Boolean,
        sharedPinFp: String?,
        revokedFps: Set<String>,
    ): Boolean = when {
        pinnedFp == fp -> true
        // A phone that was approved, signed out, and signed back in to this account:
        // the same phone key, so no new approval (it got nothing while signed out).
        fp in revokedFps -> true
        pinnedFp == null && (!hasAnyPin || sharedPinFp == fp) -> true
        else -> false
    }

    /** What a Watch Dog phone signing out does with the parent's shared key. */
    sealed interface Handover {
        /** The shared key isn't this phone's (or can't be checked): leave it. */
        data object Leave : Handover
        /** Hand it to another of the parent's phones. */
        data class Replace(val publicKeyB64: String) : Handover
        /** No other phone is left: delete it. */
        data object Delete : Handover
    }

    /**
     * @param sharedKeyB64 the shared key on the server (null when there is none or
     *   the server couldn't be reached; either way there's nothing to hand over).
     * @param others the parent's other phones as (publicKeyB64, updatedAt), or null
     *   when the list couldn't be read.
     */
    fun handover(
        myKeyB64: String,
        sharedKeyB64: String?,
        others: List<Pair<String, Long>>?,
    ): Handover {
        if (sharedKeyB64 != myKeyB64 || others == null) return Handover.Leave
        val next = others.filter { it.first != myKeyB64 }.maxByOrNull { it.second }
        return if (next != null) Handover.Replace(next.first) else Handover.Delete
    }
}
