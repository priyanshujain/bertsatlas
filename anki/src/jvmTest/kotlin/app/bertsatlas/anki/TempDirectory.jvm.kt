package app.bertsatlas.anki

actual fun temporaryDirectory(): String = System.getProperty("java.io.tmpdir")
