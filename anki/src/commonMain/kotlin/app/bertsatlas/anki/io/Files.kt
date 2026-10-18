package app.bertsatlas.anki.io

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

internal object Files {
    fun readSource(source: ByteSource): ByteArray = source.readAllBytes()

    fun read(path: String): ByteArray = SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }

    fun write(path: String, bytes: ByteArray) {
        Path(path).parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(Path(path)).buffered().use { it.write(bytes) }
    }

    fun exists(path: String): Boolean = SystemFileSystem.exists(Path(path))

    fun delete(path: String) {
        if (exists(path)) SystemFileSystem.delete(Path(path))
    }

    fun createDirectories(path: String) = SystemFileSystem.createDirectories(Path(path))

    fun list(directory: String): List<String> =
        if (exists(directory)) SystemFileSystem.list(Path(directory)).map { it.name } else emptyList()

    fun join(directory: String, name: String): String = Path(directory, name).toString()
}
