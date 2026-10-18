package app.bertsatlas.cardrender

enum class OcclusionState { Hidden, Revealed, InactiveMasked, InactiveVisible }

sealed interface OcclusionGeometry {
    data class Rect(val left: Float, val top: Float, val width: Float, val height: Float) : OcclusionGeometry
    data class Ellipse(val left: Float, val top: Float, val radiusX: Float, val radiusY: Float) : OcclusionGeometry
    data class Polygon(val points: List<Pair<Float, Float>>) : OcclusionGeometry
}

data class OcclusionShape(
    val ordinal: Int,
    val geometry: OcclusionGeometry,
    val state: OcclusionState,
    val usesPixelCoordinates: Boolean,
)

object OcclusionShapes {
    const val PREFIX = "image-occlusion:"

    fun parse(text: String, ordinal: Int, role: ClozeRole): OcclusionShape? {
        val body = text.trim().removePrefix(PREFIX)
        if (body.length == text.trim().length) return null
        val parts = body.split(':')
        val kind = parts.firstOrNull() ?: return null
        val properties = parts.drop(1).mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
        }.toMap()
        fun number(name: String): Float? = properties[name]?.toFloatOrNull()
        val geometry = when (kind) {
            "rect" -> OcclusionGeometry.Rect(
                number("left") ?: return null,
                number("top") ?: return null,
                number("width") ?: return null,
                number("height") ?: return null,
            )
            "ellipse" -> OcclusionGeometry.Ellipse(
                number("left") ?: return null,
                number("top") ?: return null,
                number("rx") ?: return null,
                number("ry") ?: return null,
            )
            "polygon" -> OcclusionGeometry.Polygon(
                properties["points"]?.trim()?.split(' ')?.mapNotNull { point ->
                    val coordinates = point.split(',')
                    val x = coordinates.getOrNull(0)?.toFloatOrNull()
                    val y = coordinates.getOrNull(1)?.toFloatOrNull()
                    if (x == null || y == null) null else x to y
                }?.takeIf { it.size >= 3 } ?: return null,
            )
            else -> return null
        }
        val occludeInactive = properties["oi"] == "1"
        val state = when (role) {
            ClozeRole.Hidden -> OcclusionState.Hidden
            ClozeRole.Revealed -> OcclusionState.Revealed
            ClozeRole.Inactive -> if (occludeInactive) OcclusionState.InactiveMasked else OcclusionState.InactiveVisible
        }
        return OcclusionShape(ordinal, geometry, state, usesPixelCoordinates = geometry.coordinates().any { it > 1.5f })
    }

    private fun OcclusionGeometry.coordinates(): List<Float> = when (this) {
        is OcclusionGeometry.Rect -> listOf(left, top, width, height)
        is OcclusionGeometry.Ellipse -> listOf(left, top, radiusX, radiusY)
        is OcclusionGeometry.Polygon -> points.flatMap { listOf(it.first, it.second) }
    }
}
