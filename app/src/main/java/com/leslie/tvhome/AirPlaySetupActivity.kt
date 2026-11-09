package com.leslie.tvhome

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Optional, independent GNU GPLv3 AirPlay receiver integration.
 * Tigris never bundles native receiver code, APKs, GPL libraries or unknown binaries.
 * Explicitly open the trusted upstream project for user-directed installation.
 */
class AirPlaySetupActivity : Activity() {
    companion object {
        const val RECEIVER_PKG = "io.github.jqssun.airplay"
        const val RELEASE_PAGE = "https://github.com/jqssun/android-airplay-server/releases/latest"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val launch = packageManager.getLaunchIntentForPackage(RECEIVER_PKG)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF10151F.toInt())
            setPadding(dp(56), dp(31), dp(56), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        layout.addView(TextView(this).apply {
            text = getString(R.string.tvos_airplay_title)
            textSize = 29f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(TextView(this).apply {
            text = getString(if (launch != null) R.string.tvos_airplay_ready
                else R.string.tvos_airplay_install)
            textSize = 18f;setTextColor(0xFFE0EAF8.toInt());gravity=Gravity.CENTER
        }, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(14) })
        layout.addView(ImageView(this).apply {
            setImageBitmap(PhoneServer.qr(RELEASE_PAGE, 5))
            contentDescription = RELEASE_PAGE
        }, LinearLayout.LayoutParams(dp(195), dp(195)).apply { topMargin = dp(24) })
        layout.addView(TextView(this).apply {
            text = RELEASE_PAGE
            textSize = 13f;setTextColor(0xFFB6C7DB.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(12) })
        val action = TextView(this).apply {
            text = getString(if (launch != null) R.string.tvos_airplay_open
                else R.string.tvos_airplay_upstream)
            textSize = 19f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
            background = rounded(0xFF325E88.toInt(), dp(14).toFloat())
            isFocusable=true;isClickable=true
            setOnClickListener {
                val target = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE))
                try { startActivity(target) }
                catch (_: Exception) {
                    android.widget.Toast.makeText(this@AirPlaySetupActivity,
                        R.string.tvos_airplay_no_browser,
                        android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        layout.addView(action, LinearLayout.LayoutParams(dp(355),dp(56)).apply{topMargin=dp(16)})
        layout.addView(TextView(this).apply {
            text = getString(R.string.tvos_airplay_limitations)
            textSize=14f;setTextColor(0xFFB1BED1.toInt());gravity=Gravity.CENTER
        }, LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(21)})
        setContentView(layout)
        action.requestFocus()
    }
}
