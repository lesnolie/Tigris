package com.leslie.tvhome.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * TCL / FFalcon China firmware (tcl_mt5879_cn and relatives).
 *
 * - Inputs: the firmware registers no TvInputService at all (`dumpsys tv_input` has an empty
 *   inputMap), so `TvInputManager.tvInputList` is empty and the standard passthrough URI has no
 *   handler. LiveTV owns the HDMI ports through its own Unified Source stack, so switching means
 *   opening LiveTV's source picker.
 * - Brightness: TCL ignores the standard `screen_brightness` key and has no sysfs backlight node;
 *   the backlight lives behind `com.tcl.tv.pq` / TPictureManager, so the slider stays hidden and
 *   the backlight is reached through TCL's settings app ([settingsIntent]).
 * - Settings: nothing answers `android.settings.SETTINGS`; TCL's settings app opens with ACTION_MAIN.
 */
class TclAdapter(ctx: Context) : AndroidAdapter(ctx) {
    override fun matches() = isTcl() || installed(LIVE_TV)

    /**
     * One entry, not four: the firmware exposes no way to address a port directly, so claiming a
     * per-port list would be a lie. This opens LiveTV, which shows its own picker
     * (TV / HDMI1(ARC/eARC) / HDMI2-4 / AV / 投屏) for the user to choose from.
     */
    override fun inputs() = super.inputs().ifEmpty {
        if (installed(LIVE_TV)) listOf(Input(URI_PREFIX, "TCL input", "", false)) else emptyList()
    }

    override fun setInput(i: Input) {
        if (i.uri.startsWith(URI_PREFIX)) start(Intent(Intent.ACTION_VIEW).setComponent(ComponentName(LIVE_TV, "$LIVE_TV.TVActivity")))
        else super.setInput(i)
    }

    override fun inputSummary(list: List<Input>) =
        if (list.firstOrNull()?.uri == URI_PREFIX) "TCL input picker" else super.inputSummary(list)

    override fun settingsIntent() =
        if (installed(SETTINGS_PKG)) Intent(Intent.ACTION_MAIN).setComponent(ComponentName(SETTINGS_PKG, "$SETTINGS_PKG.ui.MainActivity"))
        else super.settingsIntent()

    companion object {
        private const val URI_PREFIX = "tcl-input://"
        private const val SETTINGS_PKG = "com.tcl.settings"
        private const val LIVE_TV = "com.tcl.tv"

        fun isTcl() = Build.MANUFACTURER.equals("TCL", true) || Build.BRAND.equals("FFALCON", true)
    }
}
