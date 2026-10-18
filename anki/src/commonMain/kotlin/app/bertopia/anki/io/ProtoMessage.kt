package app.bertopia.anki.io

internal class ProtoMessage private constructor(private val values: Map<Int, List<Any>>) {
    fun long(field: Int): Long = (values[field]?.lastOrNull() as? Long) ?: 0L

    fun int(field: Int): Int = long(field).toInt()

    fun bool(field: Int): Boolean = long(field) != 0L

    fun bytes(field: Int): ByteArray = (values[field]?.lastOrNull() as? ByteArray) ?: ByteArray(0)

    fun string(field: Int): String = bytes(field).decodeToString()

    fun has(field: Int): Boolean = values.containsKey(field)

    fun message(field: Int): ProtoMessage? = (values[field]?.lastOrNull() as? ByteArray)?.let { parse(it) }

    fun float(field: Int): Float? = floats(field).lastOrNull()

    fun floats(field: Int): List<Float> = values[field].orEmpty().flatMap { value ->
        when (value) {
            is Fixed32 -> listOf(Float.fromBits(value.bits))
            is ByteArray -> (0 until value.size / 4).map { Float.fromBits(value.littleEndianInt(it * 4)) }
            else -> emptyList()
        }
    }

    private class Fixed32(val bits: Int)

    companion object {
        fun parse(bytes: ByteArray): ProtoMessage {
            val values = LinkedHashMap<Int, MutableList<Any>>()
            var position = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    val byte = bytes[position++].toInt() and 0xFF
                    result = result or ((byte and 0x7F).toLong() shl shift)
                    if (byte < 0x80) return result
                    shift += 7
                }
            }
            while (position < bytes.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                val value: Any = when ((key and 7).toInt()) {
                    0 -> varint()
                    1 -> bytes.littleEndianLong(position).also { position += 8 }
                    2 -> {
                        val length = varint().toInt()
                        bytes.copyOfRange(position, position + length).also { position += length }
                    }
                    5 -> Fixed32(bytes.littleEndianInt(position)).also { position += 4 }
                    else -> throw IllegalArgumentException("Unsupported protobuf wire type")
                }
                values.getOrPut(field) { mutableListOf() } += value
            }
            return ProtoMessage(values)
        }
    }
}

private fun ByteArray.littleEndianInt(offset: Int): Int =
    (0 until 4).fold(0) { acc, i -> acc or ((this[offset + i].toInt() and 0xFF) shl (8 * i)) }

private fun ByteArray.littleEndianLong(offset: Int): Long =
    (0 until 8).fold(0L) { acc, i -> acc or ((this[offset + i].toLong() and 0xFF) shl (8 * i)) }
