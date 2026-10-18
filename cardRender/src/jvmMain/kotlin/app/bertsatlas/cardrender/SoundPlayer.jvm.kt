package app.bertsatlas.cardrender

import java.io.File
import kotlin.concurrent.thread

actual class SoundPlayer actual constructor() {
    private var process: Process? = null

    actual fun play(path: String, onFinished: () -> Unit): Boolean {
        stop()
        if (!File(path).isFile) return false
        return runCatching {
            val started = ProcessBuilder(MACOS_AUDIO_PLAYER, path).redirectErrorStream(true).start()
            process = started
            thread(isDaemon = true, name = "card-sound") {
                started.inputStream.readAllBytes()
                val exitCode = started.waitFor()
                if (process === started) {
                    process = null
                    if (exitCode == 0) onFinished()
                }
            }
        }.onFailure { stop() }.isSuccess
    }

    actual fun stop() {
        val current = process ?: return
        process = null
        current.destroy()
    }

    actual fun release() = stop()
}

private const val MACOS_AUDIO_PLAYER = "/usr/bin/afplay"
