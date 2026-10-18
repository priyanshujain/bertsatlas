package app.bertsatlas

import androidx.compose.ui.window.ComposeUIViewController
import app.bertsatlas.data.buildBertsAtlasDatabase
import app.bertsatlas.data.createPreferencesStore
import app.bertsatlas.data.getDatabaseBuilder
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
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
    val applicationSupport = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val libraryDirectory = requireNotNull(applicationSupport?.path) + "/packs"
    val database = getDatabaseBuilder().buildBertsAtlasDatabase()
    val preferences = createPreferencesStore(preferencesPath)
    val filePicker = IosPackFilePicker()
    val imagePicker = IosImagePicker()
    val packSharer = IosPackSharer()
    return ComposeUIViewController {
        App(database, preferences, libraryDirectory, debugBuild = Platform.isDebugBinary, deviceFeedback = IosDeviceFeedback(), filePicker = filePicker, packSharer = packSharer, imagePicker = imagePicker) { activeController = it }
    }
}

fun pauseApp() = activeController?.pauseForInterruption()

fun resumeApp() = activeController?.resumeAfterInterruption()
