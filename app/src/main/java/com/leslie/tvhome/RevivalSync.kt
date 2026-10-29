package com.leslie.tvhome

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Brings the home guard back on low-RAM Android 9, where the notification listener never binds.
 *
 * Adapted from Aerial (https://github.com/pengchujin/aerial-tv), MIT License,
 * Copyright (c) 2026 pengchujin. The sync writes nothing: Android runs it after a settings
 * change, including the write that drops this app's accessibility service.
 */
object RevivalSync {
    private const val TAG = "TigrisGuard"
    private const val AUTHORITY = "settings"

    fun ensure(context: Context) {
        if (!Guard.isUserUnlocked(context)) return
        runCatching {
            val accountType = context.packageName
            val accounts = AccountManager.get(context)
            val account = accounts.getAccountsByType(accountType).firstOrNull()
                ?: Account("Tigris", accountType).also {
                    if (!accounts.addAccountExplicitly(it, null, null)) return
                    Log.i(TAG, "added the account that lets the sync framework revive Tigris")
                }
            if (ContentResolver.getIsSyncable(account, AUTHORITY) <= 0) {
                ContentResolver.setIsSyncable(account, AUTHORITY, 1)
            }
            if (!ContentResolver.getSyncAutomatically(account, AUTHORITY)) {
                ContentResolver.setSyncAutomatically(account, AUTHORITY, true)
            }
        }.onFailure { Log.w(TAG, "could not set up the sync revival", it) }
    }
}

class RevivalSyncService : Service() {
    private val adapter by lazy { Adapter(applicationContext) }

    override fun onBind(intent: Intent): IBinder = adapter.syncAdapterBinder

    private class Adapter(context: Context) : AbstractThreadedSyncAdapter(context, false) {
        override fun onPerformSync(
            account: Account,
            extras: Bundle,
            authority: String,
            provider: ContentProviderClient,
            result: SyncResult,
        ) {
            if (!Guard.isUserUnlocked(context)) return
            Guard.ensureAdb(context)
            if (HomeGuardService.isRunning || !Guard.ensure(context)) return
            Log.i("TigrisGuard", "revived by the sync framework")
            Guard.noteRevival(context)
            Handler(Looper.getMainLooper()).postDelayed({ Guard.rebindIfStalled(context) }, 5_000)
        }
    }
}

/** The account type [RevivalSync] needs. It cannot sign in to anything. */
class RevivalAccountService : Service() {
    private val authenticator by lazy { Authenticator(this) }

    override fun onBind(intent: Intent): IBinder = authenticator.iBinder

    private class Authenticator(context: Context) : AbstractAccountAuthenticator(context) {
        override fun editProperties(response: AccountAuthenticatorResponse, accountType: String) = null
        override fun addAccount(
            response: AccountAuthenticatorResponse,
            accountType: String,
            authTokenType: String?,
            requiredFeatures: Array<out String>?,
            options: Bundle,
        ) = null
        override fun confirmCredentials(response: AccountAuthenticatorResponse, account: Account, options: Bundle?) = null
        override fun getAuthToken(
            response: AccountAuthenticatorResponse,
            account: Account,
            authTokenType: String,
            options: Bundle,
        ) = null
        override fun getAuthTokenLabel(authTokenType: String) = null
        override fun updateCredentials(
            response: AccountAuthenticatorResponse,
            account: Account,
            authTokenType: String?,
            options: Bundle?,
        ) = null
        override fun hasFeatures(response: AccountAuthenticatorResponse, account: Account, features: Array<out String>) =
            Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
    }
}
