package com.leslie.tvhome

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display

/**
 * Prefer sharp assets on capable 4K TVs while checking bitmap memory headroom and
 * hardware decoding capability independently. Users can override automatic quality.
 */
internal object MediaQuality {
    const val AUTO = "auto"
    const val COMPAT = "compat"
    const val UHD = "uhd"

    fun displayIsUhd(ctx: Context): Boolean {
        val manager = ctx.getSystemService(DisplayManager::class.java)
        val mode = manager?.getDisplay(Display.DEFAULT_DISPLAY)?.mode
        return mode != null && maxOf(mode.physicalWidth, mode.physicalHeight) >= 3840 &&
            minOf(mode.physicalWidth, mode.physicalHeight) >= 2160
    }

    /**
     * Bitmap decoding and cross-fading consume application heap, unlike hardware-decoded video.
     * Allow 2 GB-class TVs when the heap and available system memory have enough headroom;
     * remain conservative on genuinely low-RAM or memory-pressured devices.
     */
    internal fun hasUhdWallpaperBudget(
        lowRam: Boolean, lowMemory: Boolean, totalBytes: Long, availableBytes: Long,
        heapMb: Int
    ): Boolean = !lowRam && !lowMemory &&
        totalBytes >= 1536L * 1024 * 1024 &&
        availableBytes >= 320L * 1024 * 1024 &&
        heapMb >= 192

    private fun enoughMemoryForWallpapers(ctx: Context): Boolean = runCatching {
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return@runCatching false
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        hasUhdWallpaperBudget(am.isLowRamDevice, info.lowMemory, info.totalMem,
            info.availMem, am.memoryClass)
    }.getOrDefault(false)

    fun uhdWallpapers(ctx: Context): Boolean = when (Prefs(ctx).wallQuality) {
        UHD -> true
        COMPAT -> false
        else -> displayIsUhd(ctx) && enoughMemoryForWallpapers(ctx)
    }

    private fun isHardware(codec: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) codec.isHardwareAccelerated
        else !codec.name.contains("google", true) && !codec.name.contains("software", true) &&
            !codec.name.startsWith("c2.android", true)

    fun hevcDecoderPresent(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            // Some vendor codecs throw while reporting capabilities: skip only the broken one.
            runCatching {
                !codec.isEncoder && isHardware(codec) &&
                    codec.supportedTypes.any { it.equals("video/hevc", true) }
            }.getOrDefault(false)
        }
    }.getOrDefault(false)

    fun canDecode4kHevc(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            runCatching {
                !codec.isEncoder && isHardware(codec) && codec.supportedTypes.any { mime ->
                    mime.equals("video/hevc", true) &&
                        codec.getCapabilitiesForType(mime).videoCapabilities
                            .areSizeAndRateSupported(3840, 2160, 30.0)
                }
            }.getOrDefault(false)
        }
    }.getOrDefault(false)

    /** HEVC is hardware decoded: the 3 GB bitmap policy must not disable 4K video. */
    fun uhdAerial(ctx: Context): Boolean = when (Prefs(ctx).aerialQuality) {
        COMPAT -> false
        // Explicit 4K preference works even when a vendor reports a 1080p UI display mode.
        UHD -> canDecode4kHevc()
        else -> displayIsUhd(ctx) && canDecode4kHevc()
    }

    /** The 4K address is only a candidate: not all Apple's clips have a UHD HEVC file. */
    fun aerialCandidates(avc: String, hevc: String?, want4k: Boolean, allowHevc: Boolean): List<String> =
        buildList {
            if (allowHevc && !hevc.isNullOrBlank()) {
                if (want4k && hevc.endsWith("_2K_HEVC.mov")) add(hevc.removeSuffix("_2K_HEVC.mov") + "_4K_HEVC.mov")
                add(hevc)
            }
            add(avc)
        }.distinct()
}
