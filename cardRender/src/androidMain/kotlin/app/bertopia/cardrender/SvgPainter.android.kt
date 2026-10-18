package app.bertopia.cardrender

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import com.caverock.androidsvg.SVG
import kotlin.math.ceil

internal actual fun decodeSvgPainter(bytes: ByteArray, density: Density): Painter? = runCatching {
    val svg = SVG.getFromString(bytes.decodeToString())
    val viewBox = svg.documentViewBox
    val widthDp = svg.documentWidth.takeIf { it > 0 } ?: viewBox?.width() ?: FALLBACK_SIZE_DP
    val heightDp = svg.documentHeight.takeIf { it > 0 } ?: viewBox?.height() ?: FALLBACK_SIZE_DP
    val widthPx = ceil(widthDp * density.density).toInt().coerceIn(1, MAX_SIZE_PX)
    val heightPx = ceil(heightDp * density.density).toInt().coerceIn(1, MAX_SIZE_PX)
    if (viewBox == null) svg.setDocumentViewBox(0f, 0f, widthDp, heightDp)
    svg.setDocumentWidth(widthPx.toFloat())
    svg.setDocumentHeight(heightPx.toFloat())
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    svg.renderToCanvas(Canvas(bitmap))
    BitmapPainter(bitmap.asImageBitmap())
}.getOrNull()

private const val FALLBACK_SIZE_DP = 300f
private const val MAX_SIZE_PX = 4096
