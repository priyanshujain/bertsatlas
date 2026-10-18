package app.bertopia.anki

import app.bertopia.anki.io.ByteSource

expect fun zstdCompress(data: ByteArray, level: Int = 3): ByteArray

expect fun zstdDecompress(data: ByteArray): ByteArray

internal expect fun zstdDecodingSource(compressed: ByteSource): ByteSource
