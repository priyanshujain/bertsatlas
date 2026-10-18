package app.bertsatlas.domain.editor

import app.bertsatlas.anki.io.Sha1
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString

object LibraryFiles {
    fun exists(path: String): Boolean = SystemFileSystem.exists(Path(path))

    fun isDirectory(path: String): Boolean = SystemFileSystem.metadataOrNull(Path(path))?.isDirectory == true

    fun size(path: String): Long = SystemFileSystem.metadataOrNull(Path(path))?.size ?: 0L

    fun readText(path: String): String = SystemFileSystem.source(Path(path)).buffered().use { it.readString() }

    fun readBytes(path: String): ByteArray = SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }

    fun sha1(path: String): String = Sha1.hex(readBytes(path))

    fun list(path: String): List<String> =
        if (!exists(path)) emptyList() else SystemFileSystem.list(Path(path)).map { it.name }

    fun copy(from: String, to: String) {
        Path(to).parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.source(Path(from)).buffered().use { source ->
            SystemFileSystem.sink(Path(to)).buffered().use { sink -> sink.transferFrom(source) }
        }
    }

    fun copyDirectory(from: String, to: String) {
        SystemFileSystem.createDirectories(Path(to))
        if (!exists(from)) return
        SystemFileSystem.list(Path(from)).forEach { entry ->
            val target = Path(to, entry.name).toString()
            if (isDirectory(entry.toString())) copyDirectory(entry.toString(), target) else copy(entry.toString(), target)
        }
    }

    fun removeRecursively(path: String) {
        val target = Path(path)
        if (!SystemFileSystem.exists(target)) return
        if (SystemFileSystem.metadataOrNull(target)?.isDirectory == true) {
            SystemFileSystem.list(target).forEach { removeRecursively(it.toString()) }
        }
        SystemFileSystem.delete(target)
    }

    fun move(from: String, to: String) = SystemFileSystem.atomicMove(Path(from), Path(to))

    fun createDirectories(path: String) = SystemFileSystem.createDirectories(Path(path))

    fun delete(path: String) = SystemFileSystem.delete(Path(path))

    fun absolute(path: String, workingDirectory: String): String =
        if (path.startsWith("/")) path else Path(workingDirectory, path).toString()

    fun sameContent(first: String, second: String): Boolean =
        size(first) == size(second) && readBytes(first).contentEquals(readBytes(second))
}
