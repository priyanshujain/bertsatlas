package app.bertsatlas.cardrender

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

expect class SoundPlayer() {
    fun play(path: String, onFinished: () -> Unit): Boolean
    fun stop()
    fun release()
}

@Composable
fun rememberSoundPlayer(): SoundPlayer {
    val player = remember { SoundPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
