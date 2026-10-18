package app.bertopia.cardrender

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual class SoundPlayer actual constructor() {
    private var player: AVAudioPlayer? = null
    private var delegate: FinishDelegate? = null

    actual fun play(path: String, onFinished: () -> Unit): Boolean {
        stop()
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
        val created = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = null)
        val finishDelegate = FinishDelegate {
            stop()
            onFinished()
        }
        created.delegate = finishDelegate
        if (!created.prepareToPlay() || !created.play()) {
            stop()
            return false
        }
        player = created
        delegate = finishDelegate
        return true
    }

    actual fun stop() {
        player?.stop()
        player?.delegate = null
        player = null
        delegate = null
    }

    actual fun release() = stop()

    private class FinishDelegate(private val onFinished: () -> Unit) : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) = onFinished()
    }
}
