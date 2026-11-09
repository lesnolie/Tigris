package com.leslie.tvhome

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.os.Bundle
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Optional on-launcher PIN restriction (not Android device-owner policy).
 * Enforced at every Tigris launch entrypoint; other launchers/system intents can bypass it.
 * No plaintext PIN, no exportable receiver, bounded attempts with a local lockout.
 */
internal object LocalRestrictionPolicy {
    fun validPin(code: String): Boolean = code.matches(Regex("[0-9]{4,8}"))
    fun temporarilyLock(failures: Int): Boolean = failures >= 5
}

/** Local launcher guard, not OS-wide device-owner enforcement. */
internal object LocalAppRestrictions {
    private const val STORE = "tigris_local_restrictions"
    private const val ITERATIONS = 120_000
    private fun sp(ctx: Context) = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    fun configured(ctx: Context): Boolean = sp(ctx).contains("hash")
    fun blocked(ctx: Context, pkg: String): Boolean =
        pkg != ctx.packageName && sp(ctx).getStringSet("blocked", emptySet())!!.contains(pkg)

    fun setBlocked(ctx: Context, pkg: String, yes: Boolean) {
        if (!configured(ctx) || pkg == ctx.packageName) return
        val saved = sp(ctx).getStringSet("blocked", emptySet())!!.toMutableSet()
        if (yes) saved.add(pkg) else saved.remove(pkg)
        sp(ctx).edit().putStringSet("blocked", saved).apply()
    }

    fun setPin(ctx: Context, code: String): Boolean {
        if (!LocalRestrictionPolicy.validPin(code)) return false
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val hash = derive(code, salt)
        sp(ctx).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("attempts", 0).putLong("locked_until", 0)
            .apply()
        return true
    }

    private fun derive(code: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    @Synchronized fun verify(ctx: Context, code: String): Boolean {
        val settings = sp(ctx)
        if (System.currentTimeMillis() < settings.getLong("locked_until", 0L)) return false
        val salt = settings.getString("salt", null) ?: return false
        val hash = settings.getString("hash", null) ?: return false
        val expected = runCatching { Base64.decode(hash, Base64.NO_WRAP) }.getOrNull() ?: return false
        val decoded = runCatching { Base64.decode(salt, Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = runCatching { derive(code, decoded) }.getOrNull() ?: return false
        val match = MessageDigest.isEqual(expected, actual)
        if (match) settings.edit().putInt("attempts", 0).putLong("locked_until", 0L).apply()
        else {
            val failures = settings.getInt("attempts", 0) + 1
            settings.edit().putInt("attempts", if (LocalRestrictionPolicy.temporarilyLock(failures)) 0 else failures)
                .putLong("locked_until", if (LocalRestrictionPolicy.temporarilyLock(failures)) System.currentTimeMillis() + 60_000 else 0L).apply()
        }
        return match
    }

    fun guardLaunch(activity: Activity, pkg: String, launch: () -> Unit) {
        if (!blocked(activity, pkg)) launch()
        else askPin(activity, launch)
    }

    fun manage(activity: Activity, whenUnlocked: () -> Unit) {
        if (!configured(activity)) {
            createPin(activity, whenUnlocked)
        } else askPin(activity, whenUnlocked)
    }

    private fun digitEntry(activity: Activity, hint: Int): EditText =
        EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFB0BED1.toInt())
            setHint(hint)
            textSize = 19f
        }

    private fun createPin(activity: Activity, done: () -> Unit) {
        val first = digitEntry(activity, R.string.tvos_pin_choose)
        val repeat = digitEntry(activity, R.string.tvos_pin_repeat)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(8), activity.dp(24), 0)
            addView(first)
            addView(repeat)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.tvos_pin_setup_title)
            .setMessage(R.string.tvos_pin_launcher_only)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = first.text.toString()
                if (code != repeat.text.toString() || !setPin(activity, code)) {
                    repeat.error = activity.getString(R.string.tvos_pin_invalid)
                } else { dialog.dismiss(); done() }
            }
        }
        dialog.show()
    }

    private fun askPin(activity: Activity, success: () -> Unit) {
        val field = digitEntry(activity, R.string.tvos_pin_enter)
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.tvos_pin_enter)
            .setMessage(R.string.tvos_pin_launcher_only)
            .setView(field)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = field.text.toString()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                Thread {
                    val valid = verify(activity.applicationContext, entered)
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        if (valid) { dialog.dismiss(); success() }
                        else {
                            field.error = activity.getString(R.string.tvos_pin_wrong)
                            field.text.clear()
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        }
                    }
                }.start()
            }
        }
        dialog.show()
    }
}

/** Manages restrictions within the launcher only, after explicit PIN authorization. */
class LocalAppRestrictionActivity : Activity() {
    private val restrict by lazy { LocalAppRestrictions }
    private val apps by lazy { AppRepo(this).allApps().filter { it.pkg != packageName && it.folder == null && it.input == null }
        .distinctBy { it.pkg }.sortedBy { it.label } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restrict.manage(this) { render() }
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(65), dp(40), dp(65), dp(40))
            setBackgroundColor(0xFF111622.toInt())
        }
        root.addView(TextView(this).apply { text = getString(R.string.tvos_pin_manage_title)
            textSize = 29f; setTextColor(Color.WHITE) })
        root.addView(TextView(this).apply { text = getString(R.string.tvos_pin_launcher_only)
            textSize = 16f; setTextColor(0xFFBBC9D9.toInt()) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (app in apps) {
            val row = TextView(this).apply {
                textSize = 19f; isFocusable = true; isClickable = true
                setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), 0, dp(18), 0)
                background = rounded(0xFF303C50.toInt(), dp(12).toFloat())
                fun update() {
                    text = (if (restrict.blocked(this@LocalAppRestrictionActivity, app.pkg)) "🔒 " else "○ ") + app.label
                }
                update()
                setOnClickListener {
                    restrict.setBlocked(this@LocalAppRestrictionActivity, app.pkg,
                        !restrict.blocked(this@LocalAppRestrictionActivity, app.pkg))
                    update()
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(7) })
        }
        root.addView(ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(15) })
        setContentView(root)
        list.getChildAt(0)?.requestFocus()
    }
}
