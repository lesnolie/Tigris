package com.leslie.tvhome

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The clock screensaver's sounds, synthesised here rather than shipped as recordings:
 * a tick per style and the buzz of neon tubes striking up. Pure functions; 16-bit mono PCM.
 */
object ClockSynth {
    const val RATE = 44_100

    enum class Sound(val file: String) {
        TICK_SOFT("tick_soft"), FLAP("flap"), TICK_DIGITAL("tick_digital"), TICK_NEON("tick_neon"),
        NEON_STRIKE("neon_strike"), NEON_FLICK("neon_flick")
    }

    fun render(sound: Sound): ShortArray = when (sound) {
        Sound.TICK_SOFT -> tickSoft()
        Sound.FLAP -> flap()
        Sound.TICK_DIGITAL -> tickDigital()
        Sound.TICK_NEON -> tickNeon()
        Sound.NEON_STRIKE -> neonBuzz(strike = true)
        Sound.NEON_FLICK -> neonBuzz(strike = false)
    }

    private fun buffer(seconds: Double) = DoubleArray((RATE * seconds).toInt())

    private fun pcm(x: DoubleArray, gain: Double): ShortArray {
        val peak = x.maxOf { kotlin.math.abs(it) }.takeIf { it > 0 } ?: 1.0
        return ShortArray(x.size) { (x[it] / peak * gain * Short.MAX_VALUE).toInt().toShort() }
    }

    /** A clock movement: a bright click over a short wooden body. */
    private fun tickSoft(): ShortArray {
        val x = buffer(0.06)
        val r = Random(7)
        for (i in x.indices) {
            val t = i.toDouble() / RATE
            x[i] = (r.nextDouble() * 2 - 1) * exp(-t / 0.0015) * 0.6 +
                sin(2 * PI * 3200 * t) * exp(-t / 0.006) * 0.5 +
                sin(2 * PI * 950 * t) * exp(-t / 0.014) * 0.6
        }
        return pcm(x, 0.55)
    }

    /** A flap falling against the one below: two soft knocks of filtered noise. */
    private fun flap(): ShortArray {
        val x = buffer(0.12)
        val r = Random(11)
        var low = 0.0
        for (i in x.indices) {
            val t = i.toDouble() / RATE
            val hits = exp(-t / 0.010) + 0.55 * (if (t > 0.032) exp(-(t - 0.032) / 0.012) else 0.0)
            low += 0.25 * ((r.nextDouble() * 2 - 1) - low)
            x[i] = low * hits + sin(2 * PI * 420 * t) * hits * 0.35
        }
        return pcm(x, 0.6)
    }

    /** A quiet electronic tick, as from a desk clock's piezo. */
    private fun tickDigital(): ShortArray {
        val x = buffer(0.03)
        for (i in x.indices) {
            val t = i.toDouble() / RATE
            val attack = (t / 0.001).coerceAtMost(1.0)
            x[i] = sin(2 * PI * 2700 * t) * attack * exp(-t / 0.006)
        }
        return pcm(x, 0.4)
    }

    /** A tiny electric crackle. */
    private fun tickNeon(): ShortArray {
        val x = buffer(0.05)
        val r = Random(5)
        for (i in x.indices) {
            val t = i.toDouble() / RATE
            x[i] = ((r.nextDouble() * 2 - 1) * 0.7 + sin(2 * PI * 120 * t) * 0.5) * exp(-t / 0.008)
        }
        return pcm(x, 0.4)
    }

    /**
     * Mains hum with crackle, gated by the same stutter the tubes show ([strike]: the full
     * power-on, about a second; otherwise a short flick for a changed digit).
     */
    private fun neonBuzz(strike: Boolean): ShortArray {
        val length = if (strike) 1.15 else 0.32
        val x = buffer(length)
        val r = Random(3)
        var high = 0.0
        var lastNoise = 0.0
        for (i in x.indices) {
            val t = i.toDouble() / RATE
            val ms = t * 1000
            val gate = if (strike) strikeGate(ms) else flickGate(ms)
            // A rectified 50 Hz supply: strong 100 Hz with odd harmonics, slightly clipped.
            var hum = 0.0
            for (h in 1..6) hum += sin(2 * PI * 100 * h * t) / h * (if (h % 2 == 0) 0.6 else 1.0)
            hum = hum.coerceIn(-1.1, 1.1)
            // Crackle: high-passed noise, bursting on each switch-on.
            val noise = r.nextDouble() * 2 - 1
            high = 0.92 * (high + noise - lastNoise)
            lastNoise = noise
            val burst = if (r.nextDouble() < 0.004 * gate) 1.0 else 0.0
            val crack = high * (0.35 + 2.5 * burst)
            val tail = if (t > length - 0.12) (length - t) / 0.12 else 1.0
            x[i] = (hum * 0.55 + crack) * gate * tail
        }
        return pcm(x, if (strike) 0.6 else 0.35)
    }

    /** Matches [ClockFaceView]'s strike(): off, on, off, on, low, then rising to steady. */
    fun strikeGate(ms: Double): Double = when {
        ms < 80 -> 0.0
        ms < 140 -> 1.0
        ms < 260 -> 0.05
        ms < 320 -> 1.0
        ms < 420 -> 0.3
        ms < 900 -> 0.7 + 0.3 * (ms - 420) / 480
        else -> 1.0 - (ms - 900) / 250 * 0.85
    }.coerceIn(0.0, 1.0)

    private fun flickGate(ms: Double): Double = when {
        ms < 40 -> 1.0
        ms < 110 -> 0.1
        ms < 170 -> 0.9
        else -> 0.6
    }

    /** A playable WAV file around [samples]. */
    fun wav(samples: ShortArray): ByteArray {
        val data = samples.size * 2
        return ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data)
            samples.forEach { putShort(it) }
        }.array()
    }
}

/** Loads the synthesised sounds once per screensaver and plays them on demand. */
class ClockSounds(ctx: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()).build()
    private val ids = mutableMapOf<ClockSynth.Sound, Int>()
    private val ready = mutableSetOf<Int>()
    private var pending: Pair<ClockSynth.Sound, Float>? = null
    @Volatile private var released = false

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            ready += id
            // A strike asked for before its sound was loaded still plays, a moment late.
            pending?.let { (sound, volume) -> if (ids[sound] == id) { pending = null; play(sound, volume) } }
        }
        val dir = File(ctx.cacheDir, "clock_sounds").apply { mkdirs() }
        Thread {
            for (sound in ClockSynth.Sound.values()) {
                if (released) return@Thread
                val file = File(dir, "${sound.file}_v$VERSION.wav")
                runCatching {
                    if (!file.isFile) FileOutputStream(file).use { it.write(ClockSynth.wav(ClockSynth.render(sound))) }
                    synchronized(this) { if (!released) ids[sound] = pool.load(file.path, 1) }
                }.onFailure { Log.w("ClockSounds", "could not prepare ${sound.file}", it) }
            }
        }.start()
    }

    fun play(sound: ClockSynth.Sound, volume: Float = 0.5f) {
        if (released) return
        val id = synchronized(this) { ids[sound] }
        if (id == null || id !in ready) { pending = sound to volume; return }
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() = synchronized(this) {
        released = true
        pool.release()
    }

    private companion object { const val VERSION = 1 }
}
