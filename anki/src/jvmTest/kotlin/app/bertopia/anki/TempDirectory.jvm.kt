package app.bertopia.anki

actual fun temporaryDirectory(): String = System.getProperty("java.io.tmpdir")
