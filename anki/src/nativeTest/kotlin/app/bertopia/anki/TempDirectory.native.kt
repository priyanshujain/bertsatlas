package app.bertopia.anki

import platform.Foundation.NSTemporaryDirectory

actual fun temporaryDirectory(): String = NSTemporaryDirectory()
