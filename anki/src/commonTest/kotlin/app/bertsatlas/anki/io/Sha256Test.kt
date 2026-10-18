package app.bertsatlas.anki.io

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test
    fun matchesPublishedVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".encodeToByteArray()))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
    }

    @Test
    fun incrementalUpdatesMatchOneShot() {
        val bytes = ByteArray(10_000) { (it * 31 + 7).toByte() }
        val incremental = Sha256()
        var offset = 0
        listOf(1, 63, 64, 65, 1000, 4000).forEach { length ->
            incremental.update(bytes, offset, length)
            offset += length
        }
        incremental.update(bytes, offset, bytes.size - offset)
        assertEquals(Sha256.hex(bytes), incremental.hexDigest())
    }
}
