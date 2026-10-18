package app.bertopia.cardrender

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.compose.resources.decodeToImageBitmap

internal expect fun decodeSvgPainter(bytes: ByteArray, density: Density): Painter?

object CardMediaFiles {
    fun resolve(mediaDirectory: String, source: String): Path? {
        val trimmed = source.trim()
        if (trimmed.isEmpty() || trimmed.contains("://") || trimmed.startsWith("data:")) return null
        val candidates = listOf(trimmed, percentDecode(trimmed)).distinct()
        return candidates.firstNotNullOfOrNull { name ->
            if (name.startsWith("/") || name.split('/', '\\').any { it == ".." }) null
            else Path(mediaDirectory, name).takeIf { SystemFileSystem.exists(it) }
        }
    }

    private fun percentDecode(text: String): String {
        if ('%' !in text) return text
        val bytes = mutableListOf<Byte>()
        var position = 0
        while (position < text.length) {
            val character = text[position]
            if (character == '%' && position + 2 <= text.lastIndex) {
                val value = text.substring(position + 1, position + 3).toIntOrNull(16)
                if (value != null) {
                    bytes += value.toByte()
                    position += 3
                    continue
                }
            }
            bytes += character.toString().encodeToByteArray().toList()
            position++
        }
        return bytes.toByteArray().decodeToString()
    }
}

private val painterCache = mutableMapOf<String, Painter>()

private fun readPainter(path: Path, density: Density): Painter? = runCatching {
    val bytes = SystemFileSystem.source(path).buffered().use { it.readByteArray() }
    if (path.name.lowercase().endsWith(".svg")) decodeSvgPainter(bytes, density)
    else BitmapPainter(bytes.decodeToImageBitmap())
}.getOrNull()

@Composable
fun rememberMediaPainter(mediaDirectory: String, source: String): MediaPainterState {
    val density = LocalDensity.current
    val key = "$mediaDirectory|$source"
    val state by produceState<MediaPainterState>(painterCache[key]?.let { MediaPainterState.Loaded(it) } ?: MediaPainterState.Loading, key) {
        painterCache[key]?.let {
            value = MediaPainterState.Loaded(it)
            return@produceState
        }
        value = MediaPainterState.Loading
        val path = CardMediaFiles.resolve(mediaDirectory, source)
        val painter = path?.let { withContext(Dispatchers.Default) { readPainter(it, density) } }
        value = if (painter == null) MediaPainterState.Missing else MediaPainterState.Loaded(painter).also { painterCache[key] = painter }
    }
    return state
}

sealed interface MediaPainterState {
    data object Loading : MediaPainterState
    data object Missing : MediaPainterState
    data class Loaded(val painter: Painter) : MediaPainterState
}

internal expect fun zoomDialogProperties(): DialogProperties

@Composable
internal fun ZoomDialog(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = zoomDialogProperties()) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xF0141A19))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1.2f) 1f else 2.5f
                            offset = Offset.Zero
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .padding(12.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            ) { content() }
            BasicText(
                "Close",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onClose)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .semantics { contentDescription = "Close zoomed image" },
                style = TextStyle(color = Color.White, fontSize = 17.sp),
            )
        }
    }
}

@Composable
internal fun MediaImage(painter: Painter, description: String?, modifier: Modifier = Modifier) {
    Image(
        painter = painter,
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
