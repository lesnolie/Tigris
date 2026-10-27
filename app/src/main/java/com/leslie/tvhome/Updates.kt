package com.leslie.tvhome

import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.PersistableBundle
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Checks official stable releases; APK identity and signature are verified before installation. */
object Updates {
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

    /** Only abandon this app's own sessions for the selected product, on explicit retry. */
    fun cancelPending(c: Context, p: Product) {
        check(p.pkg !in transferring) { I18n.text(R.string.update_transfer_busy) }
        val installer = c.packageManager.packageInstaller
        installer.mySessions.filter { it.appPackageName == p.pkg }.forEach { installer.abandonSession(it.sessionId) }
        prefs(c).edit().remove(p.pkg + ".session").remove(p.pkg + ".confirmation")
            .putString(p.pkg + ".phase", "idle").putInt(p.pkg + ".progress", -1)
            .putString(p.pkg + ".status", I18n.text(R.string.update_cancelled)).apply()
    }

    fun confirm(c: Context, p: Product): Boolean {
        val uri = prefs(c).getString(p.pkg + ".confirmation", null) ?: return false
        return runCatching {
            check(pending(c, p))
            c.startActivity(Intent.parseUri(uri, 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
    fun prefs(c: Context) = c.getSharedPreferences("software_updates", Context.MODE_PRIVATE)
    fun installed(c: Context, p: Product) = runCatching { c.packageManager.getPackageInfo(p.pkg, 0).versionName ?: "0.0" }.getOrNull()
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
            val status = when { current == null -> I18n.text(R.string.i18n_updates_004, version); update -> "$current → $version"; else -> I18n.text(R.string.i18n_updates_005, current, version) }
            // An external upgrade can leave the old self-install session behind.
            // Installed current-or-newer software no longer needs that pending session.
            if (!update && p.pkg !in transferring && pending(c, p)) cancelPending(c, p)
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
            prefs(c).edit().putString(p.pkg + ".status", status).putBoolean(p.pkg + ".available", false).apply()
            "${p.name}：$status"
        }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun install(c: Context, p: Product) {
        check(transferring.add(p.pkg)) { I18n.text(R.string.update_transfer_busy) }
        try { performInstall(c, p) }
        catch (e: Exception) {
            stage(c, p, if (pending(c, p)) "pending" else "failed", e.message ?: I18n.text(R.string.i18n_updates_024))
            throw e
        } finally { transferring.remove(p.pkg) }
    }
    private fun performInstall(c: Context, p: Product) {
        check(c.packageManager.canRequestPackageInstalls()) { I18n.text(R.string.i18n_updates_008) }
        val sp = prefs(c)
        if (pending(c, p)) {
            if (UpdateActivity.visible && confirm(c, p)) return
            error(I18n.text(R.string.update_pending_recovery))
        }
        sp.edit().remove(p.pkg + ".session").remove(p.pkg + ".confirmation").apply()
        check(sp.getBoolean(p.pkg + ".available", false)) { I18n.text(R.string.i18n_updates_010) }
        val releaseDigest = sp.getString(p.pkg + ".digest", "").orEmpty()
        check(OptionalAppPolicy.validDigest(p.pkg, releaseDigest)) { I18n.text(R.string.optional_missing_digest) }
        val file = File(c.cacheDir, p.pkg + "-update.apk")
        try {
            val url = sp.getString(p.pkg + ".url", "")!!
            check(url.startsWith("https://github.com/${p.repo}/releases/download/"))
            val size = sp.getLong(p.pkg + ".size", 0)
            check(size in 1..250_000_000L) { I18n.text(R.string.i18n_updates_011) }
            stage(c, p, "download", I18n.text(R.string.update_download_percent, 0), 0)
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
            stage(c, p, "verify", I18n.text(R.string.update_verifying))
            val digest = releaseDigest
            if (digest.startsWith("sha256:")) {
                val md = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input -> val bytes = ByteArray(65536)
                    while (true) { val n = input.read(bytes); if (n < 0) break; md.update(bytes, 0, n) } }
                check(hex(md.digest()) == digest.removePrefix("sha256:")) { I18n.text(R.string.i18n_updates_014) }
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
            val installer = pm.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(p.pkg)
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            stage(c, p, "stage", I18n.text(R.string.update_staging))
            val id = installer.createSession(params)
            sp.edit().putInt(p.pkg + ".session", id).apply()
            try {
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                    val intent = Intent(c, UpdateResult::class.java).putExtra("product", p.pkg)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(c, id, intent, flags)
                    stage(c, p, "pending", I18n.text(R.string.i18n_updates_022))
                    session.commit(pending.intentSender)
                }
            } catch (e: Exception) { installer.abandonSession(id); sp.edit().remove(p.pkg + ".session").apply(); throw e }
        } finally { file.delete() }
    }
    fun notify(c: Context, p: Product, message: String, action: Intent? = null) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, I18n.text(R.string.i18n_updates_023), NotificationManager.IMPORTANCE_DEFAULT))
        val intent = action ?: Intent(c, UpdateActivity::class.java)
        val pending = PendingIntent.getActivity(c, p.pkg.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(p.pkg.hashCode(), Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_toggles)
            .setContentTitle(p.name).setContentText(message).setContentIntent(pending).setAutoCancel(true).build())
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
                        runCatching { Updates.install(this, p) }.onFailure { Updates.notify(this, p, it.message ?: I18n.text(R.string.i18n_updates_024)) }
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
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val expected = Updates.prefs(context).getInt(p.pkg + ".session", -1)
        // A cancelled session may deliver its result after the replacement session starts.
        if (sessionId < 0 || sessionId != expected) return
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Updates.prefs(context).edit().putString(p.pkg + ".confirmation", confirmation.toUri(0))
                .putString(p.pkg + ".phase", "pending").putInt(p.pkg + ".progress", -1)
                .putString(p.pkg + ".status", I18n.text(R.string.update_pending_recovery)).apply()
            Updates.notify(context, p, I18n.text(R.string.i18n_updates_025), confirmation)
            if (UpdateActivity.visible) runCatching { context.startActivity(confirmation) }
        } else {
            val message = if (status == PackageInstaller.STATUS_SUCCESS) I18n.text(R.string.i18n_updates_026) else I18n.text(R.string.i18n_updates_027, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
            Updates.prefs(context).edit().remove(p.pkg + ".session").remove(p.pkg + ".confirmation")
                .putString(p.pkg + ".phase", if (status == PackageInstaller.STATUS_SUCCESS) "done" else "failed")
                .putInt(p.pkg + ".progress", -1).putString(p.pkg + ".status", message)
                .putBoolean(p.pkg + ".available", status != PackageInstaller.STATUS_SUCCESS).apply()
            Updates.notify(context, p, message)
        }
    }
}
