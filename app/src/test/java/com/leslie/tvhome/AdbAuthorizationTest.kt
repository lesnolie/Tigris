package com.leslie.tvhome

import dadb.*
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.*
import org.junit.Test

class AdbAuthorizationTest {
    @Test fun closedPortIsDifferentFromAuthenticationFailure() {
        assertEquals(AdbFailure.CONNECT, AdbFailure.classify(AdbStage.HANDSHAKE, AdbConnectException("refused")))
        assertEquals(AdbFailure.AUTH, AdbFailure.classify(AdbStage.HANDSHAKE, AdbAuthException("rejected")))
    }
    @Test fun timeoutStageDoesNotAssumeAuthorizationWasRejected() {
        assertEquals(AdbFailure.HANDSHAKE_TIMEOUT, AdbFailure.classify(AdbStage.HANDSHAKE, AdbTimeoutException("timeout")))
        assertEquals(AdbFailure.SHELL, AdbFailure.classify(AdbStage.SHELL, SocketTimeoutException()))
    }
    @Test fun protocolMismatchAndClosedConnectionAreReportedSeparately() {
        assertEquals(AdbFailure.PROTOCOL, AdbFailure.classify(AdbStage.HANDSHAKE, AdbProtocolException("bad packet")))
        assertEquals(AdbFailure.CLOSED, AdbFailure.classify(AdbStage.HANDSHAKE, AdbConnectionClosedException("closed")))
    }
    @Test fun keyPreparationFailureCannotBeMistakenForNetworkFailure() {
        assertEquals(AdbFailure.KEYS, AdbFailure.classify(AdbStage.KEYS, IOException("private key path")))
    }
    @Test fun unknownHandshakeAndFailedShellRemainDifferent() {
        assertEquals(AdbFailure.UNKNOWN, AdbFailure.classify(AdbStage.HANDSHAKE, IOException("vendor response")))
        assertEquals(AdbFailure.SHELL, AdbFailure.classify(AdbStage.SHELL, IOException("vendor response")))
    }
}
