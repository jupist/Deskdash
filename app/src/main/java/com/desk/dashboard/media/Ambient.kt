package com.desk.dashboard.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.desk.dashboard.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteOrder
import kotlin.random.Random

enum class AmbientMode(val label: String, val short: String) {
    OFF("Off", "OFF"), NOISE("Noise", "NOISE"), RAIN("Rain", "RAIN");

    fun next() = entries[(ordinal + 1) % entries.size]
}

/**
 * Ambient sound through one AudioTrack on the music stream (so the Vol slider controls it).
 *  - Rain: a real recording (res/raw/rain.ogg, see CREDITS.md), prepared offline as a seamless
 *    loop (its end is crossfaded into its start), decoded once and looped sample-exact.
 *  - Noise: soft pink/brown noise generated live, decorrelated per channel. Generated rather than
 *    recorded because a recording of noise is the same signal, only with a loop seam.
 * Mode changes and stops fade over [FADE_S]. Not saved: always starts Off.
 */
object Ambient {
    private const val TAG = "Ambient"
    private const val RATE = 44_100
    private const val FADE_S = 1.0
    private const val FRAMES = 1024 // per write; stereo, so 2 × this many samples

    private val _mode = MutableStateFlow(AmbientMode.OFF)
    val mode: StateFlow<AmbientMode> = _mode

    @Volatile private var target = AmbientMode.OFF
    private var thread: Thread? = null
    private lateinit var app: Context

    /** Call once (MainActivity.onCreate) so the rain file can be read. */
    fun init(context: Context) { app = context.applicationContext }

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
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, FRAMES * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()

        val buf = ShortArray(FRAMES * 2)
        val step = (1.0 / (RATE * FADE_S)).toFloat()
        val noise = SoftNoise()
        var rain: ShortArray? = null // interleaved stereo, loaded on first use
        var rainPos = 0
        var playing = AmbientMode.OFF // what's currently audible
        var gain = 0f
        try {
            while (true) {
                val want = target
                if (want == AmbientMode.RAIN && rain == null) rain = loadRain()
                for (i in 0 until FRAMES) {
                    // Fade out whatever is playing before switching; then fade the new one in.
                    if (playing != want) {
                        gain -= step
                        if (gain <= 0f) { gain = 0f; playing = want }
                    } else if (playing != AmbientMode.OFF && gain < 1f) {
                        gain = minOf(1f, gain + step)
                    }
                    var l = 0f
                    var r = 0f
                    when (playing) {
                        AmbientMode.NOISE -> { l = noise.left(); r = noise.right() }
                        AmbientMode.RAIN -> rain?.let { pcm ->
                            l = pcm[rainPos] / 32768f
                            r = pcm[rainPos + 1] / 32768f
                            rainPos += 2
                            if (rainPos >= pcm.size) rainPos = 0
                        }
                        AmbientMode.OFF -> {}
                    }
                    buf[2 * i] = (l * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                    buf[2 * i + 1] = (r * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(buf, 0, buf.size)
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

    /** Decodes res/raw/rain.ogg to interleaved 16-bit stereo PCM at [RATE]. */
    private fun loadRain(): ShortArray? = try {
        val fd = app.resources.openRawResourceFd(R.raw.rain)
        val ex = MediaExtractor().apply { setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        fd.close()
        ex.selectTrack(0)
        val fmt = ex.getTrackFormat(0)
        val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val out = ShortArrayBuilder()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val ii = codec.dequeueInputBuffer(10_000)
                if (ii >= 0) {
                    val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val oi = codec.dequeueOutputBuffer(info, 10_000)
            if (oi >= 0) {
                val sb = codec.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                val samples = ShortArray(info.size / 2).also { sb.get(it) }
                if (channels == 1) samples.forEach { out.add(it); out.add(it) } else samples.forEach(out::add)
                codec.releaseOutputBuffer(oi, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop(); codec.release(); ex.release()
        out.toArray()
    } catch (e: Exception) {
        Log.w(TAG, "rain decode failed", e)
        null
    }

    private class ShortArrayBuilder {
        private var a = ShortArray(RATE * 2 * 50)
        private var n = 0
        fun add(s: Short) {
            if (n == a.size) a = a.copyOf(a.size * 2)
            a[n++] = s
        }
        fun toArray() = a.copyOf(n)
    }

    /**
     * Soft noise: mostly pink (Paul Kellet's filter) with some brown for body, gently low-passed so
     * there's no hiss. Each ear gets its own noise, which sounds wide rather than inside the head.
     */
    private class SoftNoise {
        private val rnd = Random(System.nanoTime())
        private val l = Channel()
        private val r = Channel()
        fun left() = l.next(rnd)
        fun right() = r.next(rnd)

        private class Channel {
            private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
            private var b3 = 0f; private var b4 = 0f; private var b5 = 0f; private var b6 = 0f
            private var brown = 0f
            private var lp = 0f

            fun next(rnd: Random): Float {
                val w = rnd.nextFloat() * 2f - 1f
                b0 = 0.99886f * b0 + w * 0.0555179f
                b1 = 0.99332f * b1 + w * 0.0750759f
                b2 = 0.96900f * b2 + w * 0.1538520f
                b3 = 0.86650f * b3 + w * 0.3104856f
                b4 = 0.55000f * b4 + w * 0.5329522f
                b5 = -0.7616f * b5 - w * 0.0168980f
                val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.11f
                b6 = w * 0.115926f
                brown = (brown + w * 0.02f) / 1.02f
                val mix = pink * 0.75f + brown * 2.2f
                lp += (mix - lp) * 0.32f // ~3.5 kHz one-pole low-pass at 44.1 kHz
                return lp * 0.45f
            }
        }
    }
}
