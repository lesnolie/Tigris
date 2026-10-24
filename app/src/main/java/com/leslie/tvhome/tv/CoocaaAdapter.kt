package com.leslie.tvhome.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Skyworth / Coocaa (酷On) firmware. Only inputs are adapted so far, and not yet on a real TV.
 *
 * - Inputs: some firmwares register no TvInputService, so when the standard list is empty this
 *   opens the launcher's own source picker instead of pretending single HDMI ports are reachable
 *   (the same approach as [TclAdapter]).
 * - Brightness, settings and Wi-Fi: standard Android until someone with a Coocaa TV finds the
 *   firmware's own (AGENTS.md §9.2), then override them here like [XiaomiAdapter] does.
 */
class CoocaaAdapter(ctx: Context) : AndroidAdapter(ctx) {
    override fun matches() = isCoocaa() || pickerAvailable()

    override fun inputs() = super.inputs().ifEmpty {
        if (pickerAvailable()) listOf(Input(URI_PREFIX, "Coocaa input", "", false)) else emptyList()
    }

    override fun setInput(i: Input) {
        if (!i.uri.startsWith(URI_PREFIX)) return super.setInput(i)
        if (!pickerAvailable()) { Log.w(TAG, "Coocaa source picker unavailable"); return }
        start(Intent(ACTION_SOURCE_DIALOG).setComponent(PICKER).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    override fun inputSummary(list: List<Input>) =
        if (list.firstOrNull()?.uri == URI_PREFIX) "Coocaa input picker" else super.inputSummary(list)

    /**
     * The launcher package alone isn't enough: an older firmware may lack the picker activity,
     * disable it or keep it private. (Package visibility comes from QUERY_ALL_PACKAGES.)
     */
    private fun pickerAvailable(): Boolean {
        val info = runCatching { ctx.packageManager.getActivityInfo(PICKER, 0) }.getOrNull() ?: return false
        return info.exported && info.enabled && info.applicationInfo?.enabled == true
    }

    companion object {
        private const val URI_PREFIX = "coocaa-input://"
        private const val ACTION_SOURCE_DIALOG = "coocaa.intent.action.SOURCE_DIALOG"
        private val PICKER = ComponentName("com.tianci.movieplatform", "com.ccos.cooui.homepage.tvsource.SourceDialogActivity")

        fun isCoocaa() = listOf(Build.MANUFACTURER, Build.BRAND).any { it.equals("Skyworth", true) || it.equals("Coocaa", true) }
    }
}
