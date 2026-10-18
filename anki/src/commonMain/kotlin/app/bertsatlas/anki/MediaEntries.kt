package app.bertsatlas.anki

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class MediaEntries(
    @ProtoNumber(1) val entries: List<MediaEntry> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
class MediaEntry(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val size: Int = 0,
    @ProtoNumber(3) val sha1: ByteArray = ByteArray(0),
    @ProtoNumber(255) val legacyZipFilename: Int? = null,
)

@OptIn(ExperimentalSerializationApi::class)
fun decodeMediaEntries(bytes: ByteArray): MediaEntries =
    ProtoBuf.decodeFromByteArray(MediaEntries.serializer(), bytes)

@OptIn(ExperimentalSerializationApi::class)
fun encodeMediaEntries(entries: MediaEntries): ByteArray =
    ProtoBuf.encodeToByteArray(MediaEntries.serializer(), entries)
