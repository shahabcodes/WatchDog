package com.shahaabapps.core.firebase.e2ee

import com.shahaabapps.core.firebase.e2ee.SharedKeyPolicy.ChildAction
import com.shahaabapps.core.firebase.e2ee.SharedKeyPolicy.Handover
import com.shahaabapps.core.firebase.e2ee.SharedKeyPolicy.SharedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedKeyPolicyTest {

    private fun action(
        shared: SharedKey,
        pinned: String? = "A",
        revoked: Set<String> = emptySet(),
        sharedWrap: String? = null,
        approved: Set<String> = emptySet(),
        listed: Set<String>? = null,
    ) = SharedKeyPolicy.childAction(shared, pinned, revoked, sharedWrap, approved, listed)

    // ---- Shield: the shared key ----

    @Test fun trustedKeyIsWrapped() =
        assertEquals(ChildAction.WRAP, action(SharedKey.Present("A")))

    @Test fun firstSightIsPinned() =
        assertEquals(ChildAction.PIN_AND_WRAP, action(SharedKey.Present("A"), pinned = null))

    @Test fun missingKeyIsDropped() =
        assertEquals(ChildAction.DROP, action(SharedKey.Missing))

    @Test fun signedOutPhoneKeyIsDroppedEvenIfPinned() =
        assertEquals(ChildAction.DROP, action(SharedKey.Present("A"), revoked = setOf("A"), listed = emptySet()))

    @Test fun signedOutPhoneThatSignedBackInIsTrustedAgain() =
        assertEquals(ChildAction.PIN_AND_WRAP, action(SharedKey.Present("A"), revoked = setOf("A"), listed = setOf("A")))

    @Test fun handoverToApprovedPhoneIsAcceptedQuietly() =
        assertEquals(ChildAction.PIN_AND_WRAP, action(SharedKey.Present("B"), approved = setOf("B"), listed = setOf("B")))

    @Test fun keyAlreadySharedWithIsRepinned() =
        assertEquals(ChildAction.PIN_AND_WRAP, action(SharedKey.Present("B"), sharedWrap = "B"))

    @Test fun overwrittenByAnotherPhoneWhileTrustedOneIsListedIsSkipped() =
        assertEquals(ChildAction.SKIP, action(SharedKey.Present("B"), listed = setOf("A", "B")))

    @Test fun unknownKeyAsksForApproval() =
        assertEquals(ChildAction.ASK, action(SharedKey.Present("X"), listed = setOf("X")))

    @Test fun unknownKeyWithNoListAsks() =
        assertEquals(ChildAction.ASK, action(SharedKey.Present("X")))

    @Test fun offlineUsesOnlyTheTrustedKey() {
        assertEquals(ChildAction.WRAP, action(SharedKey.Offline("A")))
        assertEquals(ChildAction.SKIP, action(SharedKey.Offline("X")))
        assertEquals(ChildAction.SKIP, action(SharedKey.Offline(null)))
        assertEquals(ChildAction.SKIP, action(SharedKey.Offline("A"), revoked = setOf("A")))
    }

    // ---- Shield: a parent's phone ----

    @Test fun deviceTrust() {
        // Pinned already.
        assertTrue(SharedKeyPolicy.deviceTrustedOnSight("D", "D", true, "A", emptySet()))
        // The parent's first phone.
        assertTrue(SharedKeyPolicy.deviceTrustedOnSight("D", null, false, null, emptySet()))
        // Same key as the trusted shared key.
        assertTrue(SharedKeyPolicy.deviceTrustedOnSight("A", null, true, "A", emptySet()))
        // A new phone needs approving.
        assertFalse(SharedKeyPolicy.deviceTrustedOnSight("D", null, true, "A", emptySet()))
        // A phone that signed out and signed back in (same key) is trusted again.
        assertTrue(SharedKeyPolicy.deviceTrustedOnSight("A", null, true, "A", setOf("A")))
        assertTrue(SharedKeyPolicy.deviceTrustedOnSight("B", null, true, "A", setOf("B")))
    }

    // ---- Watch Dog: signing out ----

    @Test fun handoverGoesToTheMostRecentOtherPhone() =
        assertEquals(
            Handover.Replace("C"),
            SharedKeyPolicy.handover("A", "A", listOf("B" to 1L, "C" to 5L, "A" to 9L)),
        )

    @Test fun lastPhoneDeletesTheSharedKey() =
        assertEquals(Handover.Delete, SharedKeyPolicy.handover("A", "A", emptyList()))

    @Test fun someoneElsesSharedKeyIsLeftAlone() =
        assertEquals(Handover.Leave, SharedKeyPolicy.handover("A", "B", listOf("B" to 1L)))

    @Test fun unknownStateIsLeftAlone() {
        assertEquals(Handover.Leave, SharedKeyPolicy.handover("A", null, emptyList()))
        assertEquals(Handover.Leave, SharedKeyPolicy.handover("A", "A", null))
    }
}
