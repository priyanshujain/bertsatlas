package app.bertopia.cardrender

import android.media.AudioAttributes
import android.media.MediaPlayer

actual class SoundPlayer actual constructor() {
    private var player: MediaPlayer? = null

    actual fun play(path: String, onFinished: () -> Unit): Boolean {
        stop()
        return runCatching {
            val started = MediaPlayer()
            player = started
            started.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            started.setDataSource(path)
            started.setOnCompletionListener {
                if (player === it) stop()
                onFinished()
            }
            started.prepare()
            started.start()
        }.onFailure { stop() }.isSuccess
    }

    actual fun stop() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
    }

    actual fun release() = stop()
}
