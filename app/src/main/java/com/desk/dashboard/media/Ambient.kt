package com.desk.dashboard.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.exp
import kotlin.random.Random

enum class AmbientMode(val label: String, val short: String) {
    OFF("Off", "OFF"), WHITE("White noise", "NOISE"), RAIN("Rain", "RAIN");

    fun next() = entries[(ordinal + 1) % entries.size]
}

/**
 * Ambient sound generated at runtime and streamed through one AudioTrack on the music stream
 * (so the Vol slider controls it). Endless by construction, so there's no loop seam. Mode changes
 * and stops fade over [FADE_S]. Not saved: always starts Off.
 */
object Ambient {
    private const val RATE = 22_050 // plenty for noise, half the CPU of 44.1 kHz
    private const val FADE_S = 1.0
    private const val CHUNK = 1024

    private val _mode = MutableStateFlow(AmbientMode.OFF)
    val mode: StateFlow<AmbientMode> = _mode

    @Volatile private var target = AmbientMode.OFF
    private var thread: Thread? = null

    fun cycle() = set(_mode.value.next())

    /** Fades to Off; the audio thread exits once silent. */
    fun stop() = set(AmbientMode.OFF)

    @Synchronized
    fun set(m: AmbientMode) {
        _mode.value = m
        target = m
        if (m != AmbientMode.OFF && thread == null) {
            thread = Thread(::run, "ambient").apply { priority = Thread.MAX_PRIORITY; start() }
        }
    }

    private fun run() {
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, CHUNK * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()

        val buf = ShortArray(CHUNK)
        val step = (1.0 / (RATE * FADE_S)).toFloat()
        val gen = Generators()
        var playing = AmbientMode.OFF // what's currently audible
        var gain = 0f
        try {
            while (true) {
                val want = target
                for (i in 0 until CHUNK) {
                    // Fade out whatever is playing before switching; then fade the new one in.
                    if (playing != want) {
                        gain -= step
                        if (gain <= 0f) { gain = 0f; playing = want }
                    } else if (playing != AmbientMode.OFF && gain < 1f) {
                        gain = minOf(1f, gain + step)
                    }
                    val s = when (playing) {
                        AmbientMode.WHITE -> gen.white()
                        AmbientMode.RAIN -> gen.rain()
                        AmbientMode.OFF -> 0f
                    }
                    buf[i] = (s * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(buf, 0, CHUNK)
                // Exit once silent, unless a new mode was picked meanwhile (checked under the lock
                // [set] uses, so a pick can't slip in between this check and the exit).
                if (playing == AmbientMode.OFF && synchronized(this) {
                        (target == AmbientMode.OFF).also { if (it) thread = null }
                    }) break
            }
        } finally {
            track.stop()
            track.release()
        }
    }

    /** Cheap per-sample noise sources (a few multiplies each). */
    private class Generators {
        private val rnd = Random(System.nanoTime())
        private fun noise() = rnd.nextFloat() * 2f - 1f

        fun white() = noise() * 0.22f

        // Rain: soft brown-noise body + bright hiss, plus random droplets (short decaying ticks).
        private var brown = 0f
        private var hissLp = 0f
        private var drop = 0f
        private var dropLp = 0f
        private val dropDecay = exp(-1.0 / (RATE * 0.004)).toFloat() // ~4 ms ticks
        private var swell = 0.7f
        private var swellTarget = 0.7f

        fun rain(): Float {
            val w = noise()
            brown = (brown + w * 0.02f) * 0.995f // leaky integrator → low rumble
            hissLp += (w - hissLp) * 0.35f // gentle low-pass on the hiss
            val hiss = w - hissLp // what's left is the high "shhh"
            // Slow random intensity swell so it doesn't sound static.
            if (rnd.nextInt(RATE) == 0) swellTarget = 0.55f + rnd.nextFloat() * 0.45f
            swell += (swellTarget - swell) * 0.00002f
            // Droplets: ~60 per second at random amplitudes.
            if (rnd.nextInt(RATE / 60) == 0) drop = 0.25f + rnd.nextFloat() * 0.5f
            drop *= dropDecay
            dropLp += (w * drop - dropLp) * 0.5f
            return (brown * 1.6f + hiss * 0.18f * swell + dropLp * 0.5f) * 0.9f
        }
    }
}
