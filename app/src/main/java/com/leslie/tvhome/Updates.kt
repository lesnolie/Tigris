package com.leslie.tvhome

import android.annotation.SuppressLint
import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PersistableBundle
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Checks official stable releases; APK identity and signature are verified before installation. */
object Updates {
    private const val TAG = "TigrisUpdate"
    data class Product(val name: String, val repo: String, val pkg: String, val optional: Boolean = false)
    val products get() = listOf(
        Product(I18n.text(R.string.i18n_updates_001), "lesnolie/Tigris", "com.leslie.tvhome"),
        Product(I18n.text(R.string.i18n_updates_002), "weekdayjast/MiracastReceiver", OptionalAppPolicy.MIRACAST_PKG, true),
        Product(I18n.text(R.string.optional_hgtv_name), "pan8664716/HGTV", OptionalAppPolicy.HGTV_PKG, true)
    )
    val executor = Executors.newSingleThreadExecutor()
    private const val CHANNEL = "software_updates"
    private val transferring = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private fun stage(c: Context, p: Product, phase: String, message: String, percent: Int = -1) {
        prefs(c).edit().putString(p.pkg + ".phase", phase).putString(p.pkg + ".status", message)
            .putInt(p.pkg + ".progress", percent).apply()
    }
    fun pending(c: Context, p: Product): Boolean =
        c.packageManager.packageInstaller.mySessions.any { it.appPackageName == p.pkg }

    private fun trackedSession(c: Context, p: Product): PackageInstaller.SessionInfo? {
        val id = prefs(c).getInt(p.pkg + ".session", -1)
        return c.packageManager.packageInstaller.mySessions.firstOrNull {
            it.sessionId == id && it.appPackageName == p.pkg
        }
    }

    internal fun canResume(c: Context, p: Product) = trackedSession(c, p) != null

    /** Called on return to the page; completion is based on the installed version, never on a launched UI. */
    internal fun reconcile(c: Context) {
        for (p in products) {
            val sp = prefs(c)
            val target = sp.getLong(p.pkg + ".target_code", -1).takeIf { it >= 0 }
            val installed = runCatching { c.packageManager.getPackageInfo(p.pkg, 0).longVersionCode }.getOrNull()
            when (UpdateRecovery.action(sp.getString(p.pkg + ".phase", "idle").orEmpty(), p.pkg in transferring,
                installed, target, sp.getInt(p.pkg + ".session", -1), trackedSession(c, p)?.sessionId)) {
                UpdateRecovery.Action.COMPLETE -> {
                    cancelPending(c, p)
                    sp.edit().putString(p.pkg + ".phase", "done")
                        .putString(p.pkg + ".status", I18n.text(R.string.i18n_updates_026))
                        .putBoolean(p.pkg + ".available", false).apply()
                }
                UpdateRecovery.Action.RETRY -> {
                    cancelPending(c, p, keepDownload = true)
                    stage(c, p, "failed", I18n.text(R.string.update_interrupted))
                    sp.edit().putBoolean(p.pkg + ".available", true).apply()
                }
                else -> Unit
            }
        }
    }

    /** Only abandon this app's own sessions for the selected product, on explicit retry. */
    fun cancelPending(c: Context, p: Product, keepDownload: Boolean = false) {
        check(p.pkg !in transferring) { I18n.text(R.string.update_transfer_busy) }
        val installer = c.packageManager.packageInstaller
        val sessions = installer.mySessions.filter { it.appPackageName == p.pkg }
        // Invalidate the callback identity before abandonment can deliver STATUS_FAILURE.
        prefs(c).edit().remove(p.pkg + ".session").remove(p.pkg + ".confirmation")
            .remove(p.pkg + ".target_code")
            .putString(p.pkg + ".phase", "idle").putInt(p.pkg + ".progress", -1)
            .putString(p.pkg + ".status", I18n.text(R.string.update_cancelled)).commit()
        sessions.forEach { info ->
            runCatching { installer.abandonSession(info.sessionId) }.getOrElse { error ->
                if (installer.getSessionInfo(info.sessionId) != null) throw error
            }
        }
        if (!keepDownload) releaseDownload(c, p.pkg)
    }

    fun confirm(c: Context, p: Product): Boolean {
        return try {
            val info = trackedSession(c, p) ?: error(I18n.text(R.string.update_interrupted))
            // Android drops parsed APK state for ordinary sessions across reboot. Its documented
            // recovery is commit() again, which revalidates and returns a NEW confirmation Intent.
            // Replaying the old Activity Intent can only open an unusable confirmation page.
            prefs(c).edit().remove(p.pkg + ".confirmation").commit()
            stage(c, p, "pending", I18n.text(R.string.update_resuming))
            c.packageManager.packageInstaller.openSession(info.sessionId).use { session ->
                session.commit(resultReceiver(c, p, info.sessionId))
            }
            Log.i(TAG, "session recommitted id=${info.sessionId} pkg=${p.pkg}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not resume ${p.pkg}", e)
            // Keep the verified APK for the explicit force-retry operation.
            stage(c, p, "failed", I18n.text(R.string.update_interrupted))
            false
        }
    }

    private fun resultReceiver(c: Context, p: Product, id: Int): IntentSender {
        val intent = Intent(c, UpdateResult::class.java).putExtra("product", p.pkg)
            .putExtra("expected_session", id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(c, id, intent, flags).intentSender
    }
    fun prefs(c: Context) = c.getSharedPreferences("software_updates", Context.MODE_PRIVATE)
    fun installed(c: Context, p: Product) = runCatching { c.packageManager.getPackageInfo(p.pkg, 0).versionName ?: "0.0" }.getOrNull()

    /**
     * MiracastReceiver 1.2.13 and older shipped as [OptionalAppPolicy.MIRACAST_LEGACY_PKG].
     * v1.2.14 renamed the package, so Android cannot upgrade the old install in place; the
     * update center offers an explicit uninstall instead of a download that can never pass the
     * package-identity check.
     */
    fun legacyCastVersion(c: Context): String? = runCatching {
        c.packageManager.getPackageInfo(OptionalAppPolicy.MIRACAST_LEGACY_PKG, 0).versionName
    }.getOrNull()

    /** Preserve a complete, previously checked GitHub release across transient network failures. */
    internal fun hasCachedOffer(c: Context, p: Product): Boolean {
        val sp = prefs(c)
        return UpdateOfferCache.usable(sp.getBoolean(p.pkg + ".available", false),
            sp.getString(p.pkg + ".version", null), sp.getString(p.pkg + ".url", null),
            sp.getString(p.pkg + ".digest", null), sp.getLong(p.pkg + ".size", 0L),
            p.repo, installed(c, p))
    }
    fun schedule(c: Context) {
        val scheduler = c.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(24001) != null) return
        scheduler.schedule(JobInfo.Builder(24001, ComponentName(c, UpdateJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(6 * 60 * 60 * 1000L).build())
    }
    private fun connection(address: String): HttpURLConnection {
        require(address.startsWith("https://"))
        return (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("User-Agent", "Tigris-Updater")
        }
    }
    fun check(c: Context): List<String> = products.map { p ->
        try {
            val con = connection("https://api.github.com/repos/${p.repo}/releases/latest")
            val release = try { con.inputStream.bufferedReader().use { JSONObject(it.readText()) } } finally { con.disconnect() }
            check(!release.optBoolean("draft") && !release.optBoolean("prerelease"))
            val version = release.getString("tag_name")
            val current = installed(c, p)
            val update = current == null || UpdatePolicy.newer(version, current)
            val assets = release.getJSONArray("assets")
            val home = c.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(c.packageName), 0).isNotEmpty()
            val items = (0 until assets.length()).map { assets.getJSONObject(it) }
            val asset = if (p.pkg == c.packageName) {
                // New releases carry the version in their filename; preserve legacy release support.
                val preferred = ReleaseAssetNames.select(version, !home, items.map { it.getString("name") })
                items.firstOrNull { it.getString("name") == preferred }
            } else {
                val selected = OptionalAppPolicy.selectApk(p.pkg, version, items.map { it.getString("name") })
                items.firstOrNull { it.getString("name") == selected }
            } ?: error(I18n.text(R.string.i18n_updates_003))
            val url = asset.getString("browser_download_url")
            check(url.startsWith("https://github.com/${p.repo}/releases/download/"))
            val status = when { p.pkg == OptionalAppPolicy.MIRACAST_PKG && legacyCastVersion(c) != null && current == null -> I18n.text(R.string.i18n_updates_029, legacyCastVersion(c)); p.pkg == OptionalAppPolicy.MIRACAST_PKG && legacyCastVersion(c) != null -> I18n.text(R.string.i18n_updates_030, legacyCastVersion(c)); current == null -> I18n.text(R.string.i18n_updates_004, version); update -> "$current → $version"; else -> I18n.text(R.string.i18n_updates_005, current, version) }
            // An external upgrade can leave the old self-install session behind.
            // Installed current-or-newer software no longer needs that pending session.
            if (!update && p.pkg !in transferring) {
                if (pending(c, p) || prefs(c).getString(p.pkg + ".phase", "") == "pending") cancelPending(c, p)
                discardSideload(c, p.pkg)
                releaseDownload(c, p.pkg)
            }
            prefs(c).edit().putString(p.pkg + ".status", status).putBoolean(p.pkg + ".available", update)
                .putString(p.pkg + ".url", url).putString(p.pkg + ".version", version)
                .putString(p.pkg + ".digest", asset.optString("digest"))
                .putLong(p.pkg + ".size", asset.getLong("size")).apply()
            if (update && current != null) {
                val key = p.pkg + ".notified"
                if (prefs(c).getString(key, "") != version) {
                    notify(c, p, I18n.text(R.string.i18n_updates_006, version))
                    prefs(c).edit().putString(key, version).apply()
                }
            }
            "${p.name}：$status"
        } catch (e: Exception) {
            val status = I18n.text(R.string.i18n_updates_007, e.message ?: I18n.text(R.string.i18n_updates_028))
            val cached = hasCachedOffer(c, p)
            val shown = if (cached) "$status · ${I18n.text(R.string.update_cached_offer_available)}" else status
            prefs(c).edit().putString(p.pkg + ".status", shown)
                .putBoolean(p.pkg + ".available", cached).apply()
            "${p.name}：$shown"
        }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun install(c: Context, p: Product) {
        check(transferring.add(p.pkg)) { I18n.text(R.string.update_transfer_busy) }
        try { performInstall(c, p) }
        catch (e: Exception) {
            val message = e.message ?: I18n.text(R.string.i18n_updates_024)
            // A missing confirmation page is a durable sideload instruction, not a stuck session.
            val phase = if (e is SideloadRequired || !pending(c, p)) "failed" else "pending"
            stage(c, p, phase, message)
            if (e is SideloadRequired) notify(c, p, message)
            throw e
        } finally { transferring.remove(p.pkg) }
    }
    @SuppressLint("ApplySharedPref") // commit before abandonSession, so its failure callback cannot restore the old session id
    private fun performInstall(c: Context, p: Product) {
        val sp = prefs(c)
        // Synthetic resolver probes can return false while the OS can open its confirmation.
        // Always let PackageInstaller return the real system confirmation Intent.
        check(c.packageManager.canRequestPackageInstalls()) { I18n.text(R.string.i18n_updates_008) }
        if (pending(c, p)) {
            if (UpdateActivity.visible && confirm(c, p)) return
            error(I18n.text(R.string.update_pending_recovery))
        }
        sp.edit().remove(p.pkg + ".session").remove(p.pkg + ".confirmation").remove(p.pkg + ".target_code").apply()
        check(sp.getBoolean(p.pkg + ".available", false)) { I18n.text(R.string.i18n_updates_010) }
        val releaseDigest = sp.getString(p.pkg + ".digest", "").orEmpty()
        check(OptionalAppPolicy.validDigest(p.pkg, releaseDigest)) { I18n.text(R.string.optional_missing_digest) }
        val file = downloadCache(c, p.pkg)
        var submitted = false
        var verified = false
        try {
            val url = sp.getString(p.pkg + ".url", "")!!
            check(url.startsWith("https://github.com/${p.repo}/releases/download/"))
            val size = sp.getLong(p.pkg + ".size", 0)
            check(size in 1..250_000_000L) { I18n.text(R.string.i18n_updates_011) }
            stage(c, p, "download", I18n.text(R.string.update_download_percent, 0), 0)
            val cached = file.isFile && file.length() == size && releaseDigest.startsWith("sha256:") &&
                sha256(file).equals(releaseDigest.removePrefix("sha256:"), ignoreCase = true)
            if (!cached) {
                val con = connection(url)
                try {
                    con.inputStream.use { input -> file.outputStream().use { out ->
                        try {
                            UpdateDownload.copy(input, out, size) { percent ->
                                stage(c, p, "download", I18n.text(R.string.update_download_percent, percent), percent)
                            }
                        } catch (e: IllegalStateException) {
                            error(I18n.text(R.string.update_download_failed))
                        }
                    } }
                } finally { con.disconnect() }
            } else Log.i(TAG, "reusing verified download pkg=${p.pkg}")
            stage(c, p, "verify", I18n.text(R.string.update_verifying))
            val digest = releaseDigest
            if (digest.startsWith("sha256:")) {
                check(sha256(file) == digest.removePrefix("sha256:")) { I18n.text(R.string.i18n_updates_014) }
            }
            val pm = c.packageManager
            val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: error(I18n.text(R.string.i18n_updates_015))
            check(archive.packageName == p.pkg) { I18n.text(R.string.i18n_updates_016) }
            check((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { I18n.text(R.string.i18n_updates_017) }
            val old = runCatching { pm.getPackageInfo(p.pkg, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
            if (old != null) check(archive.longVersionCode > old.longVersionCode) { I18n.text(R.string.i18n_updates_018) }

            // Android 9 / 10 can return PackageInfo for an APK on disk without populating
            // signingInfo. On those releases, explicitly reparse with GET_SIGNATURES; merely
            // checking archive.signatures without that second query leaves the same bug.
            // Newer Android must not silently downgrade to legacy certificate extraction.
            fun certs(info: android.content.pm.PackageInfo?): Set<String> {
                val signers = info?.signingInfo?.apkContentsSigners?.takeIf { it.isNotEmpty() }
                    ?: info?.signatures?.takeIf { it.isNotEmpty() }
                return signers?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet().orEmpty()
            }
            val legacyAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
            @Suppress("DEPRECATION")
            val signatures = UpdateSignerPolicy.withLegacyFallback(certs(archive), legacyAllowed) {
                val legacy = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
                // Never trust a fallback PackageInfo that disagrees on APK identity.
                if (legacy?.packageName == archive.packageName) certs(legacy) else emptySet()
            }
            check(signatures.isNotEmpty()) { I18n.text(R.string.i18n_updates_019) }
            if (old != null) {
                @Suppress("DEPRECATION")
                val installedSignatures = UpdateSignerPolicy.withLegacyFallback(certs(old), legacyAllowed) {
                    certs(runCatching { pm.getPackageInfo(p.pkg, PackageManager.GET_SIGNATURES) }.getOrNull())
                }
                check(UpdateSignerPolicy.sameSigner(signatures, installedSignatures)) { I18n.text(R.string.i18n_updates_020) }
            } else {
                val trusted = OptionalAppPolicy.trustedCertificate(p.pkg)
                check(trusted != null && UpdateSignerPolicy.matchesPinned(signatures, p.optional, trusted)) {
                    I18n.text(R.string.i18n_updates_021)
                }
            }
            verified = true
            val installer = pm.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(p.pkg)
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            stage(c, p, "stage", I18n.text(R.string.update_staging))
            val id = installer.createSession(params)
            sp.edit().putInt(p.pkg + ".session", id)
                .putLong(p.pkg + ".target_code", archive.longVersionCode).commit()
            try {
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                    stage(c, p, "pending", I18n.text(R.string.i18n_updates_022))
                    session.commit(resultReceiver(c, p, id))
                    Log.i(TAG, "session committed id=$id pkg=${p.pkg}")
                    // Retain the verified APK until installation succeeds or the user cancels.
                    submitted = true
                }
            } catch (e: Exception) { installer.abandonSession(id); sp.edit().remove(p.pkg + ".session").apply(); throw e }
        } finally { if (!submitted && !verified) file.delete() }
    }
    fun notify(c: Context, p: Product, message: String, action: Intent? = null) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, I18n.text(R.string.i18n_updates_023), NotificationManager.IMPORTANCE_DEFAULT))
        val intent = action ?: Intent(c, UpdateActivity::class.java)
        val pending = PendingIntent.getActivity(c, p.pkg.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_toggles)
            .setContentTitle(p.name).setContentText(message).setContentIntent(pending).setAutoCancel(true)
        if (message.contains("adb install -r")) builder.setStyle(Notification.BigTextStyle().bigText(message))
        nm.notify(p.pkg.hashCode(), builder.build())
    }

    private fun downloadCache(c: Context, pkg: String) = File(c.filesDir, "updates").let { dir ->
        check(dir.isDirectory || dir.mkdirs()) { I18n.text(R.string.update_no_install_ui_missing_copy) }
        File(dir, "$pkg-update.apk")
    }

    internal fun releaseDownload(c: Context, pkg: String) {
        runCatching { downloadCache(c, pkg).delete() }
        runCatching { File(c.cacheDir, "$pkg-update.apk").delete() }
    }

    internal fun discardSideload(c: Context, pkg: String) {
        val path = prefs(c).getString(pkg + ".sideload", null) ?: return
        runCatching { File(path).delete() }
        prefs(c).edit().remove(pkg + ".sideload").apply()
    }

    private fun sideloadMessage(path: String) =
        I18n.text(R.string.update_no_install_ui, path, InstallConfirmation.adbCommand(path))

    /** App-specific external files. No WRITE_EXTERNAL_STORAGE on SDK 28+. */
    private fun sideloadDir(c: Context): File {
        c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { return File(it, InstallConfirmation.DIRECTORY) }
        c.getExternalFilesDir(null)?.let { return File(it, "Download/${InstallConfirmation.DIRECTORY}") }
        error(I18n.text(R.string.update_no_install_ui_missing_copy))
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(65536)
            while (true) { val n = input.read(bytes); if (n < 0) break; md.update(bytes, 0, n) }
        }
        return hex(md.digest())
    }

    private fun publishVerifiedApk(c: Context, p: Product, source: File, digest: String): File {
        val version = prefs(c).getString(p.pkg + ".version", null)?.takeIf { it.isNotBlank() } ?: "release"
        val dir = sideloadDir(c)
        if (!dir.isDirectory && !dir.mkdirs()) error(I18n.text(R.string.update_no_install_ui_missing_copy))
        val name = InstallConfirmation.fileName(p.pkg, version)
        dir.listFiles()?.filter { InstallConfirmation.previousApk(it.name, p.pkg, name) }?.forEach { it.delete() }
        val dest = File(dir, name)
        val partial = File(dir, "$name.partial")
        try {
            source.inputStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            check(partial.length() == source.length()) { I18n.text(R.string.i18n_updates_014) }
            if (digest.startsWith("sha256:")) {
                check(sha256(partial) == digest.removePrefix("sha256:")) { I18n.text(R.string.i18n_updates_014) }
            }
            if (dest.exists() && !dest.delete()) error(I18n.text(R.string.update_no_install_ui_missing_copy))
            if (!partial.renameTo(dest)) error(I18n.text(R.string.update_no_install_ui_missing_copy))
        } catch (e: Exception) {
            partial.delete()
            if (e is IllegalStateException) throw e
            throw IllegalStateException(I18n.text(R.string.update_no_install_ui_missing_copy), e)
        }
        prefs(c).edit().putString(p.pkg + ".sideload", dest.absolutePath).apply()
        return dest
    }

    /**
     * Session commit asked for a confirmation page that cannot open.
     * Abandon the session, keep a verified APK when the download is still on disk,
     * and replace the status with the sideload instruction.
     */
    @SuppressLint("ApplySharedPref") // commit before abandonSession, so its failure callback cannot overwrite the sideload status
    internal fun reportMissingInstallUi(c: Context, p: Product) {
        val app = c.applicationContext
        val id = prefs(app).getInt(p.pkg + ".session", -1)
        Log.w(TAG, "pending confirmation intent cannot start pkg=${p.pkg} session=$id")
        val existing = prefs(app).getString(p.pkg + ".sideload", null)?.takeIf { File(it).isFile }
        val immediate = if (existing != null) sideloadMessage(existing) else I18n.text(R.string.update_no_install_ui_saving)
        // Clear the expected session before abandon so a failure callback cannot overwrite this status.
        prefs(app).edit().remove(p.pkg + ".session").remove(p.pkg + ".confirmation")
            .putString(p.pkg + ".phase", "failed").putInt(p.pkg + ".progress", -1)
            .putString(p.pkg + ".status", immediate).putBoolean(p.pkg + ".available", true).commit()
        if (id >= 0) runCatching { app.packageManager.packageInstaller.abandonSession(id) }
        if (existing != null) {
            releaseDownload(app, p.pkg)
            notify(app, p, immediate)
            return
        }
        executor.execute {
            val cache = downloadCache(app, p.pkg)
            val message = try {
                if (!cache.isFile) I18n.text(R.string.update_no_install_ui_missing_copy)
                else sideloadMessage(publishVerifiedApk(app, p, cache, prefs(app).getString(p.pkg + ".digest", "").orEmpty()).absolutePath)
            } catch (e: IllegalStateException) {
                e.message ?: I18n.text(R.string.update_no_install_ui_missing_copy)
            } finally {
                cache.delete()
            }
            prefs(app).edit().putString(p.pkg + ".phase", "failed").putInt(p.pkg + ".progress", -1)
                .putString(p.pkg + ".status", message).putBoolean(p.pkg + ".available", true).apply()
            Log.w(TAG, "sideload status: $message")
            notify(app, p, message)
        }
    }
}

class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Updates.executor.execute {
            try {
                Updates.check(this)
                if (Updates.prefs(this).getBoolean("auto", false)) for (p in Updates.products) {
                    if (OptionalAppPolicy.mayAutoUpdate(p.pkg, Updates.installed(this, p) != null) &&
                        Updates.installed(this, p) != null &&
                        Updates.prefs(this).getBoolean(p.pkg + ".available", false)) {
                        runCatching { Updates.install(this, p) }.onFailure {
                            if (it !is SideloadRequired) Updates.notify(this, p, it.message ?: I18n.text(R.string.i18n_updates_024))
                        }
                    }
                }
            } finally { jobFinished(params, false) }
        }
        return true
    }
    override fun onStopJob(params: JobParameters) = true
}

class UpdateResult : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = Updates.products.find { it.pkg == intent.getStringExtra("product") } ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,
            intent.getIntExtra("expected_session", -1))
        val expected = Updates.prefs(context).getInt(p.pkg + ".session", -1)
        val legacy = intent.getIntExtra(PackageInstaller.EXTRA_LEGACY_STATUS, Int.MIN_VALUE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val blocker = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)
        Log.i("TigrisUpdate", "installer callback pkg=${p.pkg} session=$sessionId expected=$expected " +
            "status=$status legacy=$legacy blocker=$blocker detail=$detail foreground=${UpdateActivity.visible}")
        // A cancelled session may deliver its result after the replacement session starts.
        if (sessionId < 0 || sessionId != expected) {
            Log.w("TigrisUpdate", "ignoring stale installer callback session=$sessionId expected=$expected")
            return
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (InstallConfirmation.missingSystemIntent(confirmation != null)) {
                Log.w("TigrisUpdate", "no system confirmation intent session=$sessionId")
                Updates.reportMissingInstallUi(context, p)
                return
            }
            // The OS-supplied Intent is authoritative; never probe a synthetic resolver.
            // Starting it from a BroadcastReceiver/application context is fragile on OEM TVs.
            // Foreground activity handoff also avoids background activity launch restrictions.
            Updates.prefs(context).edit().putString(p.pkg + ".confirmation", confirmation!!.toUri(0))
                .putString(p.pkg + ".phase", "pending").putInt(p.pkg + ".progress", -1)
                .putString(p.pkg + ".status", I18n.text(R.string.update_pending_recovery)).apply()
            val handedOff = UpdateActivity.launchSystemConfirmation(p, sessionId, confirmation)
            Log.i("TigrisUpdate", "confirmation delivered session=$sessionId foreground=${UpdateActivity.visible} handedOff=$handedOff")
            // Without a resumed update Activity, leave the session intact. The user can
            // return to the page and press Continue; that obtains a fresh OS Intent.
            // Notifications can outlive a reboot too: route through the page's recommit path.
            Updates.notify(context, p, I18n.text(R.string.i18n_updates_025))
        } else {
            if (status == PackageInstaller.STATUS_SUCCESS) Updates.releaseDownload(context, p.pkg)
            if (status == PackageInstaller.STATUS_SUCCESS) Updates.discardSideload(context, p.pkg)
            val message = if (status == PackageInstaller.STATUS_SUCCESS) I18n.text(R.string.i18n_updates_026)
                else I18n.text(R.string.i18n_updates_027,
                    "${detail ?: "unknown"} (status=$status, legacy=$legacy${if (blocker != null) ", blockedBy=$blocker" else ""})")
            Updates.prefs(context).edit().remove(p.pkg + ".session").remove(p.pkg + ".confirmation")
                .putString(p.pkg + ".phase", if (status == PackageInstaller.STATUS_SUCCESS) "done" else "failed")
                .putInt(p.pkg + ".progress", -1).putString(p.pkg + ".status", message)
                .putBoolean(p.pkg + ".available", status != PackageInstaller.STATUS_SUCCESS).apply()
            Updates.notify(context, p, message)
        }
    }
}
