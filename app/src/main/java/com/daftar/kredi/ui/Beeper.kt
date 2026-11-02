package com.daftar.kredi.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** أصوات الكاشير: نغمة نجاح قصيرة، ونغمتان هابطتان للمنتج الجديد */
object Beeper {
    private const val RATE = 44100

    private fun tone(hz: Double, ms: Int): ShortArray {
        val n = RATE * ms / 1000
        val out = ShortArray(n)
        val fade = RATE * 0.01
        for (i in 0 until n) {
            val env = minOf(1.0, minOf(i, n - i) / fade)
            val v = Math.sin(2.0 * Math.PI * hz * i / RATE) * env * 0.9 * Short.MAX_VALUE
            out[i] = v.toInt().toShort()
        }
        return out
    }

    private fun silence(ms: Int) = ShortArray(RATE * ms / 1000)

    private fun play(samples: ShortArray) {
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            val ms = samples.size * 1000L / RATE + 300
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    track.release()
                } catch (e: Exception) {
                }
            }, ms)
        } catch (e: Exception) {
        }
    }

    /** منتج معروف أُضيف للفاتورة: بيب قصير وحاد */
    fun success() = play(tone(1900.0, 130))

    /** منتج جديد غير مسجّل: نغمتان هابطتان */
    fun newProduct() = play(tone(650.0, 170) + silence(70) + tone(430.0, 280))
}
