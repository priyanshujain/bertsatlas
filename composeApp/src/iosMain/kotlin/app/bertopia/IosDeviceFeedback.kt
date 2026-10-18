package app.bertopia

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate

@OptIn(ExperimentalForeignApi::class)
class IosDeviceFeedback : DeviceFeedback {
    override fun play(correct: Boolean, soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (soundEnabled) AudioServicesPlaySystemSound(if (correct) 1104u else 1053u)
        if (hapticsEnabled) AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
    }
}
