package it.pingpro.app.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import it.pingpro.app.data.AppSettings

/** Avvisi vibrazione/suono quando la scheda smette di rispondere o torna raggiungibile. */
class Alerts(private val context: Context) {

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }

    fun down(s: AppSettings) {
        if (s.vibrateOnAlert) runCatching {
            vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250, 120, 400), -1))
        }
        if (s.soundOnAlert) beep(ToneGenerator.TONE_PROP_NACK, 600)
    }

    fun recovered(s: AppSettings) {
        if (s.vibrateOnAlert) runCatching {
            vibrator()?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (s.soundOnAlert) beep(ToneGenerator.TONE_PROP_ACK, 300)
    }

    private fun beep(tone: Int, ms: Int) = runCatching {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tg.startTone(tone, ms)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, ms + 200L)
    }
}
