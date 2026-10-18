package app.bertsatlas

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject

class IosPackFilePicker : PackFilePicker {
    private var activeDelegate: PickerDelegate? = null

    override fun pickPackFile(onPicked: (String?) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        val delegate = PickerDelegate { url ->
            activeDelegate = null
            onPicked(url?.let(::copyIntoIncoming))
        }
        activeDelegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    private class PickerDelegate(private val onDone: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            onDone(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onDone(null)
        }
    }
}

fun openPackFile(url: NSURL) {
    copyIntoIncoming(url)?.let(IncomingPackFiles::deliver)
}

@OptIn(ExperimentalForeignApi::class)
private fun copyIntoIncoming(url: NSURL): String? {
    val accessing = url.startAccessingSecurityScopedResource()
    try {
        val manager = NSFileManager.defaultManager
        val directory = NSTemporaryDirectory() + "incoming"
        manager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
        val name = url.lastPathComponent ?: "deck.apkg"
        val target = "$directory/$name"
        manager.removeItemAtPath(target, error = null)
        val copied = manager.copyItemAtPath(requireNotNull(url.path), toPath = target, error = null)
        return if (copied) target else null
    } finally {
        if (accessing) url.stopAccessingSecurityScopedResource()
    }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}
