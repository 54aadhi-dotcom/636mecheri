package com.mecheri636.offline.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator

// Pure offline ToneGenerator audio (no TTS IPC). Wrapped safely against OEM RuntimeException.
object PosSoundFeedback {
    fun beepAdd() {
        playTone(ToneGenerator.TONE_PROP_BEEP, 70)
    }

    fun beepPaid() {
        playTone(ToneGenerator.TONE_PROP_ACK, 160)
    }

    fun beepError() {
        playTone(ToneGenerator.TONE_PROP_NACK, 180)
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
            tg.startTone(toneType, durationMs)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { runCatching { tg.release() } },
                (durationMs + 80).toLong()
            )
        }
    }
}
