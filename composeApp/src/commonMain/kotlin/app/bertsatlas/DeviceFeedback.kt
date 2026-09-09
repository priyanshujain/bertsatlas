package app.bertsatlas

fun interface DeviceFeedback {
    fun play(correct: Boolean, soundEnabled: Boolean, hapticsEnabled: Boolean)
}

val NoDeviceFeedback = DeviceFeedback { _, _, _ -> }
