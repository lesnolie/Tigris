package com.leslie.tvhome

import org.junit.Assert.*
import org.junit.Test

class LocalRestrictionPolicyTest {
    @Test fun onlyAsciiFourToEightDigitsAllowed() {
        assertFalse(LocalRestrictionPolicy.validPin("123"))
        assertTrue(LocalRestrictionPolicy.validPin("1234"))
        assertTrue(LocalRestrictionPolicy.validPin("00001234"))
        assertFalse(LocalRestrictionPolicy.validPin("123456789"))
        assertFalse(LocalRestrictionPolicy.validPin("12a4"))
        assertFalse(LocalRestrictionPolicy.validPin("１２３４"))
    }

    @Test fun lockoutAfterFifthWrongAttempt() {
        assertFalse(LocalRestrictionPolicy.temporarilyLock(4))
        assertTrue(LocalRestrictionPolicy.temporarilyLock(5))
    }
}
