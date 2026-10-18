package app.bertsatlas.anki

import platform.Foundation.NSTemporaryDirectory

actual fun temporaryDirectory(): String = NSTemporaryDirectory()
