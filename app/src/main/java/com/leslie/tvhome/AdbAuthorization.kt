package com.leslie.tvhome

import dadb.AdbAuthException
import dadb.AdbConnectException
import dadb.AdbConnectionClosedException
import dadb.AdbProtocolException
import dadb.AdbTimeoutException
import java.net.ConnectException
import java.net.SocketTimeoutException

/** No host probing or automatic pairing: describe the stage observed on the existing localhost endpoint. */
internal enum class AdbStage { KEYS, HANDSHAKE, SHELL }
internal enum class AdbFailure(val message: Int) {
    KEYS(R.string.adb_failure_keys), CONNECT(R.string.adb_failure_connect),
    AUTH(R.string.adb_failure_auth), HANDSHAKE_TIMEOUT(R.string.adb_failure_handshake_timeout),
    PROTOCOL(R.string.adb_failure_protocol), CLOSED(R.string.adb_failure_closed),
    SHELL(R.string.adb_failure_shell), UNKNOWN(R.string.adb_failure_unknown);

    companion object {
        fun classify(stage: AdbStage, error: Exception): AdbFailure = when {
            stage == AdbStage.KEYS -> KEYS
            error is AdbConnectException || error is ConnectException -> CONNECT
            error is AdbAuthException -> AUTH
            error is AdbProtocolException -> PROTOCOL
            error is AdbConnectionClosedException -> CLOSED
            error is AdbTimeoutException || error is SocketTimeoutException ->
                if (stage == AdbStage.HANDSHAKE) HANDSHAKE_TIMEOUT else SHELL
            stage == AdbStage.SHELL -> SHELL
            else -> UNKNOWN
        }
    }
}

internal data class AdbAuthorization(val failure: AdbFailure? = null) {
    val authorized get() = failure == null
    fun description() = I18n.text(failure?.message ?: R.string.i18n_homeactivity_055)
}
