package com.shahaabapps.core.firebase.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordRuleTest {

    @Test fun meetsTheRule() {
        assertNull(PasswordRule.problem("abcdefgh12"))
        assertNull(PasswordRule.problem("Correct horse 9 battery"))
    }

    @Test fun tooShort() = assertEquals("Use at least 10 characters.", PasswordRule.problem("abc12"))

    @Test fun needsLowercase() = assertEquals("Add a lowercase letter.", PasswordRule.problem("ABCDEFGH12"))

    @Test fun needsNumber() = assertEquals("Add a number.", PasswordRule.problem("abcdefghij"))

    @Test fun mapsServerRefusal() {
        assertNotNull(PasswordRule.friendlyError(Exception("PASSWORD_DOES_NOT_MEET_REQUIREMENTS : Missing password requirements")))
        assertNotNull(PasswordRule.friendlyError(Exception("wrapped", Exception("WEAK_PASSWORD"))))
        assertNull(PasswordRule.friendlyError(Exception("network error")))
    }
}
