package com.leslie.tvhome

import com.leslie.tvhome.tv.BraviaSession
import org.junit.Assert.*
import org.junit.Test

class BraviaSessionTest {
    private var time = 10_000L
    private var failure = 0L
    private var registrations = 0
    private fun session(result: () -> String? = { null }) = BraviaSession(
        { time }, { failure }, { failure = it }, { registrations++; result() })

    @Test fun backgroundReadsIncludingProtectedReadsNeverRegister() {
        val session = session()
        repeat(100) { assertNull(session.cookie(false)) }
        assertEquals(0, registrations)
        assertEquals(0L, failure)
    }

    @Test fun failedRegistrationBacksOffAcrossNewClientInstances() {
        assertNull(session().cookie(true))
        repeat(10) { assertNull(session().cookie(true)) }
        assertEquals(1, registrations)
        time += BraviaSession.RETRY_MS
        assertNull(session().cookie(true))
        assertEquals(2, registrations)
    }

    @Test fun successfulPairedWriteMakesCookieAvailableToReadsWithoutRegisteringAgain() {
        val session = session { "paired-cookie" }
        assertEquals("paired-cookie", session.cookie(true))
        repeat(10) { assertEquals("paired-cookie", session.cookie(false)) }
        assertEquals(1, registrations)
        assertEquals(0L, failure)
    }

    @Test fun transportErrorAlsoPersistsBackoff() {
        assertNull(session { throw IllegalStateException("firmware registration error") }.cookie(true))
        assertNull(session().cookie(true))
        assertEquals(1, registrations)
    }

    @Test fun secondWriteRejectionSuppressesFurtherRegistrationStorms() {
        val session = session { "cookie" }
        session.cookie(true)
        session.invalidate()
        session.cookie(true)
        session.invalidate(backoff = true)
        repeat(100) { assertNull(session.cookie(true)) }
        assertEquals(2, registrations)
    }

    @Test fun expiredCookieDoesNotMakeBackgroundReadsPair() {
        val session = session { "cookie" }
        session.cookie(true)
        time += 8 * 24 * 3_600_000L
        assertNull(session.cookie(false))
        assertEquals(1, registrations)
    }

    @Test fun clockRollbackDoesNotSuppressRegistrationForever() {
        failure = time + 3_600_000
        assertNull(session().cookie(true))
        assertEquals(1, registrations)
    }
}
