package app.bertopia.anki.pkg

import app.bertopia.anki.MediaEntries
import app.bertopia.anki.MediaEntry
import app.bertopia.anki.decodeMediaEntries
import app.bertopia.anki.encodeMediaEntries
import app.bertopia.anki.io.ByteSource
import app.bertopia.anki.io.ProtoMessage
import app.bertopia.anki.io.readAllBytes
import app.bertopia.anki.zstdDecodingSource
import app.bertopia.anki.io.Sha1
import app.bertopia.anki.io.ZipArchive
import app.bertopia.anki.io.ZipFormatException
import app.bertopia.anki.io.ZipWriter
import app.bertopia.anki.zstdCompress
import app.bertopia.anki.zstdDecompress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class PackageFormat { Modern, Legacy }

class PackageMedia(val name: String, val data: ByteArray)

class PackageContents(val format: PackageFormat, val collection: ByteArray, val media: List<PackageMedia>)

class InvalidPackageException(message: String) : Exception(message)

class PackageMediaEntry internal constructor(val name: String, internal val zipEntry: String, val size: Long)

internal class PackageLayout(
    val format: PackageFormat,
    val zip: ZipArchive,
    private val collectionEntry: String,
    private val collectionIsCompressed: Boolean,
    private val mediaIsCompressed: Boolean,
    val media: List<PackageMediaEntry>,
) : AutoCloseable {
    fun openCollection(): ByteSource =
        zip.open(collectionEntry).let { if (collectionIsCompressed) zstdDecodingSource(it) else it }

    fun openMedia(entry: PackageMediaEntry): ByteSource =
        zip.open(entry.zipEntry).let { if (mediaIsCompressed) zstdDecodingSource(it) else it }

    override fun close() = zip.close()
}

object AnkiPackage {
    private const val MODERN_COLLECTION = "collection.anki21b"
    private const val LEGACY_COLLECTION = "collection.anki21"
    private const val OLDEST_COLLECTION = "collection.anki2"
    private const val MEDIA = "media"
    private const val META = "meta"
    private const val MODERN_META_VERSION = 3
    private const val LEGACY_META_VERSION = 2

    fun read(bytes: ByteArray): PackageContents {
        val zip = openZip { ZipArchive.open(bytes) }
        val layout = layoutOf(zip)
        val collection = layout.openCollection().readAllBytes()
        val media = layout.media.map { PackageMedia(it.name, layout.openMedia(it).readAllBytes()) }
        return PackageContents(layout.format, collection, media)
    }

    internal fun openFile(path: String): PackageLayout {
        val zip = openZip { ZipArchive.openFile(path) }
        return try {
            layoutOf(zip)
        } catch (failure: Throwable) {
            zip.close()
            throw failure
        }
    }

    private fun openZip(open: () -> ZipArchive): ZipArchive = try {
        open()
    } catch (failure: ZipFormatException) {
        throw InvalidPackageException(failure.message ?: "Not an Anki package")
    }

    private fun layoutOf(zip: ZipArchive): PackageLayout {
        val metaVersion = if (zip.contains(META)) ProtoMessage.parse(zip.read(META)).int(1) else 1
        val isModern = zip.contains(MODERN_COLLECTION)
        val collectionEntry = when {
            isModern -> MODERN_COLLECTION
            zip.contains(LEGACY_COLLECTION) -> LEGACY_COLLECTION
            zip.contains(OLDEST_COLLECTION) -> OLDEST_COLLECTION
            else -> throw InvalidPackageException("The package has no collection")
        }
        val mediaIsCompressed = metaVersion >= MODERN_META_VERSION
        val media = when {
            !zip.contains(MEDIA) -> emptyList()
            mediaIsCompressed -> decodeMediaEntries(zstdDecompress(zip.read(MEDIA))).entries.mapIndexedNotNull { index, entry ->
                val entryName = (entry.legacyZipFilename ?: index).toString()
                if (zip.contains(entryName)) PackageMediaEntry(entry.name, entryName, entry.size.toLong()) else null
            }
            else -> Json.parseToJsonElement(zip.read(MEDIA).decodeToString()).jsonObject.mapNotNull { (entryName, name) ->
                if (zip.contains(entryName)) PackageMediaEntry(name.jsonPrimitive.content, entryName, zip.uncompressedSize(entryName)) else null
            }
        }
        return PackageLayout(
            format = if (isModern) PackageFormat.Modern else PackageFormat.Legacy,
            zip = zip,
            collectionEntry = collectionEntry,
            collectionIsCompressed = isModern,
            mediaIsCompressed = mediaIsCompressed,
            media = media,
        )
    }

    fun write(collection: ByteArray, placeholderCollection: ByteArray, media: List<PackageMedia>, format: PackageFormat): ByteArray {
        val zip = ZipWriter()
        when (format) {
            PackageFormat.Modern -> {
                zip.add(META, byteArrayOf(0x08, MODERN_META_VERSION.toByte()))
                zip.add(MODERN_COLLECTION, zstdCompress(collection))
                zip.add(OLDEST_COLLECTION, placeholderCollection)
                val entries = media.map { MediaEntry(name = it.name, size = it.data.size, sha1 = Sha1.digest(it.data)) }
                zip.add(MEDIA, zstdCompress(encodeMediaEntries(MediaEntries(entries))))
                media.forEachIndexed { index, file -> zip.add(index.toString(), zstdCompress(file.data)) }
            }
            PackageFormat.Legacy -> {
                zip.add(META, byteArrayOf(0x08, LEGACY_META_VERSION.toByte()))
                zip.add(LEGACY_COLLECTION, collection)
                zip.add(OLDEST_COLLECTION, placeholderCollection)
                val map = JsonObject(media.mapIndexed { index, file -> index.toString() to JsonPrimitive(file.name) }.toMap())
                zip.add(MEDIA, map.toString().encodeToByteArray())
                media.forEachIndexed { index, file -> zip.add(index.toString(), file.data) }
            }
        }
        return zip.finish()
    }
}
