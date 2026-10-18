package app.bertsatlas

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCurrent
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

class IosImagePicker : ImagePicker {
    private var activeDelegate: ImagePickerDelegate? = null

    override fun pickImage(onPicked: (String?) -> Unit) {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
            preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCurrent
        }
        val picker = PHPickerViewController(configuration)
        val delegate = ImagePickerDelegate { path ->
            activeDelegate = null
            onPicked(path)
        }
        activeDelegate = delegate
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    private class ImagePickerDelegate(private val onDone: (String?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
            if (provider == null) {
                onDone(null)
                return
            }
            val type = provider.registeredTypeIdentifiers.filterIsInstance<String>().firstOrNull() ?: UTTypeImage.identifier
            provider.loadDataRepresentationForTypeIdentifier(type) { data, _ ->
                val saved = data?.let(::savePickedImage)
                dispatch_async(dispatch_get_main_queue()) { onDone(saved) }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun savePickedImage(data: NSData): String? {
    val (bytes, extension) = when (data.imageKind()) {
        PickedImageKind.Png -> data to "png"
        PickedImageKind.Jpeg -> data to "jpg"
        PickedImageKind.Gif -> data to "gif"
        PickedImageKind.Other -> (UIImage.imageWithData(data)?.let { UIImageJPEGRepresentation(it, 0.9) } ?: return null) to "jpg"
    }
    val manager = NSFileManager.defaultManager
    val directory = NSTemporaryDirectory() + "picked-images"
    manager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    val target = "$directory/picture-${(NSDate().timeIntervalSince1970 * 1000).toLong()}.$extension"
    return if (bytes.writeToFile(target, atomically = true)) target else null
}

private enum class PickedImageKind { Png, Jpeg, Gif, Other }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.imageKind(): PickedImageKind {
    if (length < 4u) return PickedImageKind.Other
    val start = bytes?.reinterpret<kotlinx.cinterop.UByteVar>() ?: return PickedImageKind.Other
    val first = List(4) { start[it].toInt() }
    return when {
        first == listOf(0x89, 0x50, 0x4E, 0x47) -> PickedImageKind.Png
        first[0] == 0xFF && first[1] == 0xD8 -> PickedImageKind.Jpeg
        first == listOf(0x47, 0x49, 0x46, 0x38) -> PickedImageKind.Gif
        else -> PickedImageKind.Other
    }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}
