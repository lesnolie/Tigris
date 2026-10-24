package com.leslie.tvhome.tv

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import android.util.Log

/** An HDMI input. [uri] is whatever the adapter that listed it needs to switch to it; it is also kept for inputs pinned to the home grid. */
data class Input(val uri: String, val title: String, val label: String, val connected: Boolean) {
    val name get() = if (label.isNotEmpty()) "$title · $label" else title
}

/** A slider's value and range. [target] is Sony's audio output ("speaker", "headphone"); empty elsewhere. */
data class Level(val value: Int, val min: Int, val max: Int, val target: String = "")

/** What the Control Center needs from a TV. [TvControls] picks one adapter per TV. */
interface TvAdapter {
    /** Whether this adapter is for the TV it runs on. Must be quick: a [android.os.Build] or PackageManager check, no network. */
    fun matches(): Boolean

    /** The backlight; null hides the slider. */
    fun brightness(): Level?

    /** Called with values in the range [brightness] reported. */
    fun setBrightness(v: Int): Boolean

    fun volume(): Level?

    /** [target] is the [Level.target] that [volume] reported. */
    fun setVolume(target: String, v: Int): Boolean

    fun inputs(): List<Input>

    /** Uri of the input being shown, or null on the home screen / in apps / when the TV can't tell. */
    fun currentInput(): String?

    /** Also called for inputs pinned to the home grid, so it must accept every uri [inputs] has ever returned on this TV. */
    fun setInput(i: Input)

    /** Subtitle of the Control Center's input button when [currentInput] matches none of [list]. */
    fun inputSummary(list: List<Input>): String

    /** The TV's own settings app. */
    fun settingsIntent(): Intent

    fun wifiIntent(): Intent
}

/**
 * Standard Android: TvInputFramework HDMI inputs, STREAM_MUSIC volume, the platform settings
 * intents, and no brightness (Android has no backlight API). Brand adapters extend this and
 * override only what their firmware does differently.
 */
open class AndroidAdapter(protected val ctx: Context) : TvAdapter {
    private val audio = ctx.getSystemService(AudioManager::class.java)

    override fun matches() = true

    override fun brightness(): Level? = null

    override fun setBrightness(v: Int) = false

    override fun volume(): Level? {
        val s = AudioManager.STREAM_MUSIC
        return Level(audio.getStreamVolume(s), audio.getStreamMinVolume(s), audio.getStreamMaxVolume(s))
    }

    override fun setVolume(target: String, v: Int): Boolean {
        // TCL only pushes the new value to the hardware while the system volume bar is shown,
        // so FLAG_SHOW_UI is required there too; it is harmless elsewhere.
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI)
        return true
    }

    /** HDMI ports from the TV input framework; devices behind a port (CEC) are left out. */
    override fun inputs(): List<Input> {
        val tim = ctx.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return try {
            tim.tvInputList.filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !hidden(it) }.map {
                val title = it.loadLabel(ctx).toString()
                Input(
                    inputUri(it),
                    title,
                    it.loadCustomLabel(ctx)?.toString()?.takeIf { l -> l != title }.orEmpty(),
                    tim.getInputState(it.id) == TvInputManager.INPUT_STATE_CONNECTED,
                )
            }.sortedBy { it.title }
        } catch (e: Exception) {
            Log.w(TAG, "inputs failed", e)
            emptyList()
        }
    }

    /** An HDMI input the firmware registers with no connector behind it. */
    protected open fun hidden(input: TvInputInfo) = false

    /** The uri [setInput] gets back for this input: by default its passthrough channel. */
    protected open fun inputUri(input: TvInputInfo): String = TvContract.buildChannelUriForPassthroughInput(input.id).toString()

    /** Standard Android can't tell which input is showing. */
    override fun currentInput(): String? = null

    /** Android's way to show an HDMI input: open its passthrough channel in the TV's own player. */
    override fun setInput(i: Input) {
        start(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(i.uri)))
    }

    override fun inputSummary(list: List<Input>) =
        if (list.isEmpty()) "Can't read the TV's inputs" else "${list.size} HDMI inputs"

    override fun settingsIntent() = Intent(Settings.ACTION_SETTINGS)

    override fun wifiIntent() = Intent(Settings.ACTION_WIFI_SETTINGS)

    // ---- helpers for brand adapters ------------------------------------------------

    protected fun installed(pkg: String) =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** PackageManager checks an explicit component; Intent.resolveActivity would return it unchecked. */
    protected fun resolvable(i: Intent): Intent? = if (ctx.packageManager.resolveActivity(i, 0) != null) i else null

    protected fun start(i: Intent): Boolean = runCatching {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrElse { Log.w(TAG, "cannot start $i", it); false }

    companion object {
        const val TAG = "TvControls"
    }
}
