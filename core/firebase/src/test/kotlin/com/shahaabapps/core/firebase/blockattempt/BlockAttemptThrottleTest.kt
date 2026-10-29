package com.shahaabapps.core.firebase.blockattempt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockAttemptThrottleTest {

    private class MapStore : BlockAttemptThrottle.Store {
        val map = HashMap<String, BlockAttemptThrottle.State>()
        override fun get(packageName: String) = map[packageName]
        override fun put(packageName: String, state: BlockAttemptThrottle.State) { map[packageName] = state }
    }

    private val min = 60_000L
    private val throttle = BlockAttemptThrottle(MapStore())

    @Test fun firstAttemptIsSent() = assertEquals(1, throttle.onAttempt("a", 100 * min))

    @Test fun attemptsInsideTheWindowAreHeldAndCounted() {
        assertEquals(1, throttle.onAttempt("a", 100 * min))
        assertNull(throttle.onAttempt("a", 101 * min))
        assertNull(throttle.onAttempt("a", 105 * min))
        assertNull(throttle.onAttempt("a", 109 * min))
        // The next event carries the three held attempts plus this one.
        assertEquals(4, throttle.onAttempt("a", 110 * min))
        assertNull(throttle.onAttempt("a", 111 * min))
    }

    @Test fun appsAreThrottledSeparately() {
        assertEquals(1, throttle.onAttempt("a", 100 * min))
        assertEquals(1, throttle.onAttempt("b", 101 * min))
        assertNull(throttle.onAttempt("a", 102 * min))
    }

    @Test fun failedSendIsRetriedWithItsCount() {
        assertEquals(1, throttle.onAttempt("a", 100 * min))
        assertNull(throttle.onAttempt("a", 101 * min))
        throttle.onSendFailed("a", 1)
        assertEquals(3, throttle.onAttempt("a", 102 * min))
    }

    @Test fun clockMovedBackDoesNotHoldForever() {
        assertEquals(1, throttle.onAttempt("a", 100 * min))
        assertEquals(1, throttle.onAttempt("a", 50 * min))
    }
}
