package app.bertopia.cardrender

object CssColors {
    private val named = mapOf(
        "black" to 0x000000, "white" to 0xFFFFFF, "red" to 0xFF0000, "green" to 0x008000, "blue" to 0x0000FF,
        "yellow" to 0xFFFF00, "orange" to 0xFFA500, "purple" to 0x800080, "gray" to 0x808080, "grey" to 0x808080,
        "pink" to 0xFFC0CB, "brown" to 0xA52A2A, "navy" to 0x000080, "teal" to 0x008080, "maroon" to 0x800000,
        "olive" to 0x808000, "lime" to 0x00FF00, "aqua" to 0x00FFFF, "cyan" to 0x00FFFF, "fuchsia" to 0xFF00FF,
        "magenta" to 0xFF00FF, "silver" to 0xC0C0C0, "darkred" to 0x8B0000, "darkgreen" to 0x006400,
        "darkblue" to 0x00008B, "gold" to 0xFFD700, "crimson" to 0xDC143C, "indigo" to 0x4B0082,
        "violet" to 0xEE82EE, "coral" to 0xFF7F50, "salmon" to 0xFA8072, "tomato" to 0xFF6347,
        "orangered" to 0xFF4500, "darkorange" to 0xFF8C00, "forestgreen" to 0x228B22, "seagreen" to 0x2E8B57,
        "steelblue" to 0x4682B4, "royalblue" to 0x4169E1, "dodgerblue" to 0x1E90FF, "skyblue" to 0x87CEEB,
        "lightblue" to 0xADD8E6, "lightgreen" to 0x90EE90, "lightgray" to 0xD3D3D3, "lightgrey" to 0xD3D3D3,
        "darkgray" to 0xA9A9A9, "darkgrey" to 0xA9A9A9, "dimgray" to 0x696969, "slategray" to 0x708090,
        "khaki" to 0xF0E68C, "beige" to 0xF5F5DC, "tan" to 0xD2B48C, "chocolate" to 0xD2691E,
    )

    fun parse(value: String): Long? {
        val color = value.trim().lowercase()
        return when {
            color.startsWith("#") -> parseHex(color.removePrefix("#"))
            color.startsWith("rgb") -> parseRgb(color)
            else -> named[color]?.let { 0xFF000000L or it.toLong() }
        }
    }

    private fun parseHex(hex: String): Long? {
        if (!hex.all { it.isDigit() || it in 'a'..'f' }) return null
        return when (hex.length) {
            3 -> 0xFF000000L or hex.map { "$it$it" }.joinToString("").toLong(16)
            6 -> 0xFF000000L or hex.toLong(16)
            8 -> {
                val rgb = hex.substring(0, 6).toLong(16)
                val alpha = hex.substring(6, 8).toLong(16)
                (alpha shl 24) or rgb
            }
            else -> null
        }
    }

    private fun parseRgb(color: String): Long? {
        val arguments = color.substringAfter('(').substringBefore(')').split(',', ' ', '/').filter { it.isNotBlank() }
        if (arguments.size < 3) return null
        val channels = arguments.take(3).map { argument ->
            if (argument.endsWith("%")) argument.removeSuffix("%").toFloatOrNull()?.times(2.55f)?.toInt()
            else argument.toFloatOrNull()?.toInt()
        }
        if (channels.any { it == null }) return null
        val (red, green, blue) = channels.map { it!!.coerceIn(0, 255).toLong() }
        val alpha = arguments.getOrNull(3)?.let { argument ->
            if (argument.endsWith("%")) argument.removeSuffix("%").toFloatOrNull()?.div(100f) else argument.toFloatOrNull()
        }?.coerceIn(0f, 1f) ?: 1f
        return ((alpha * 255).toLong() shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
