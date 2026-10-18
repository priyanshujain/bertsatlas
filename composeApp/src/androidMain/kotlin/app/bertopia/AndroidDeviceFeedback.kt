package app.bertopia

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator

class AndroidDeviceFeedback(context: Context) : DeviceFeedback {
    private val vibrator = context.applicationContext.getSystemService(Vibrator::class.java)
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 45)

    override fun play(correct: Boolean, soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (soundEnabled) {
            toneGenerator.startTone(if (correct) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 90)
        }
        if (hapticsEnabled && vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createOneShot(if (correct) 35 else 70, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
