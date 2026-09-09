package app.bertsatlas

import androidx.compose.ui.window.ComposeUIViewController
import app.bertsatlas.data.buildBertsAtlasDatabase
import app.bertsatlas.data.createPreferencesStore
import app.bertsatlas.data.getDatabaseBuilder
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

private var activeController: AppController? = null

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun MainViewController(): platform.UIKit.UIViewController {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val preferencesPath = requireNotNull(directory?.path) + "/berts-atlas.preferences_pb"
    val database = getDatabaseBuilder().buildBertsAtlasDatabase()
    val preferences = createPreferencesStore(preferencesPath)
    return ComposeUIViewController {
        App(database, preferences, debugBuild = Platform.isDebugBinary, deviceFeedback = IosDeviceFeedback()) { activeController = it }
    }
}

fun pauseApp() = activeController?.pauseForInterruption()

fun resumeApp() = activeController?.resumeAfterInterruption()
