package app.bertsatlas.ui.editor.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.bertsatlas.cardrender.MediaPainterState
import app.bertsatlas.cardrender.rememberMediaPainter
import app.bertsatlas.ui.AtlasButton
import app.bertsatlas.ui.AtlasButtonStyle
import app.bertsatlas.ui.AtlasText
import app.bertsatlas.ui.AtlasTheme
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ClozeField(value: String, label: String, onValueChange: (String) -> Unit) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = field.copy(text = value, selection = TextRange(value.length))
    EditorTextFieldValue(field, { field = it; onValueChange(it.text) }, label, singleLine = false, placeholder = "Paris is the capital of France")
    val selection = field.selection
    val hasSelection = !selection.collapsed
    AtlasButton(
        if (hasSelection) "Hide the selected words" else "Select words, then hide them",
        {
            val wrapped = ClozeText.hideSelection(field.text, selection.min, selection.max)
            field = TextFieldValue(wrapped, TextRange(wrapped.length))
            onValueChange(wrapped)
        },
        Modifier.fillMaxWidth(),
        AtlasButtonStyle.Secondary,
        enabled = hasSelection,
    )
}

object ClozeText {
    private val blankNumber = Regex("""\{\{c(\d+)::""")

    fun nextNumber(text: String): Int = (blankNumber.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0) + 1

    fun hideSelection(text: String, start: Int, end: Int): String {
        if (start >= end) return text
        val chosen = text.substring(start, end)
        val leading = chosen.takeWhile { it.isWhitespace() }
        val trailing = chosen.takeLastWhile { it.isWhitespace() }
        val word = chosen.trim()
        if (word.isEmpty()) return text
        return text.substring(0, start) + leading + "{{c${nextNumber(text)}::$word}}" + trailing + text.substring(end)
    }
}

data class MaskBox(val left: Float, val top: Float, val width: Float, val height: Float)

object MaskText {
    private val rect = Regex("""\{\{c(\d+)::image-occlusion:rect:([^}]*)\}\}""")

    fun parse(text: String): List<MaskBox> = rect.findAll(text).mapNotNull { match ->
        val properties = match.groupValues[2].split(':').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1).toFloatOrNull()
        }.toMap()
        MaskBox(properties["left"] ?: return@mapNotNull null, properties["top"] ?: return@mapNotNull null, properties["width"] ?: return@mapNotNull null, properties["height"] ?: return@mapNotNull null)
    }.toList()

    fun write(boxes: List<MaskBox>): String = boxes.mapIndexed { index, box ->
        "{{c${index + 1}::image-occlusion:rect:left=${box.left.short()}:top=${box.top.short()}:width=${box.width.short()}:height=${box.height.short()}:oi=1}}"
    }.joinToString("")

    private fun Float.short(): String {
        val scaled = (this * 10000).roundToInt()
        val whole = scaled / 10000
        val fraction = (scaled % 10000).toString().padStart(4, '0').trimEnd('0')
        return if (fraction.isEmpty()) "$whole" else "$whole.$fraction"
    }
}

@Composable
fun MaskEditor(mediaDirectory: String, imageName: String, value: String, onValueChange: (String) -> Unit) {
    val painterState = rememberMediaPainter(mediaDirectory, imageName)
    val boxes = remember(value) { MaskText.parse(value) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val colors = AtlasTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AtlasText("Drag across the picture to hide a part. Each box becomes its own card.", style = AtlasTheme.typography.detail, color = colors.mutedPlum)
        val loaded = painterState as? MediaPainterState.Loaded ?: return@Column
        val intrinsic = loaded.painter.intrinsicSize
        val ratio = if (intrinsic.width > 0 && intrinsic.height > 0) intrinsic.width / intrinsic.height else 1.5f
        Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.Center)
                .heightIn(max = 420.dp)
                .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                .semantics { contentDescription = "Picture with ${boxes.size} hidden ${if (boxes.size == 1) "part" else "parts"}" }
                .pointerInput(value) {
                    detectDragGestures(
                        onDragStart = { dragStart = it; dragEnd = it },
                        onDrag = { change, _ -> dragEnd = change.position },
                        onDragEnd = {
                            val start = dragStart
                            val end = dragEnd
                            dragStart = null
                            dragEnd = null
                            if (start != null && end != null && abs(end.x - start.x) > 12f && abs(end.y - start.y) > 12f) {
                                val box = MaskBox(
                                    left = (min(start.x, end.x) / size.width).coerceIn(0f, 1f),
                                    top = (min(start.y, end.y) / size.height).coerceIn(0f, 1f),
                                    width = (abs(end.x - start.x) / size.width).coerceIn(0f, 1f),
                                    height = (abs(end.y - start.y) / size.height).coerceIn(0f, 1f),
                                )
                                onValueChange(MaskText.write(boxes + box))
                            }
                        },
                        onDragCancel = { dragStart = null; dragEnd = null },
                    )
                },
        ) {
            Image(loaded.painter, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize()) {
                boxes.forEach { box ->
                    val topLeft = Offset(box.left * size.width, box.top * size.height)
                    val boxSize = Size(box.width * size.width, box.height * size.height)
                    drawRect(colors.plum.copy(alpha = .88f), topLeft, boxSize)
                    drawRect(colors.orangeAccent, topLeft, boxSize, style = Stroke(3.dp.toPx()))
                }
                val start = dragStart
                val end = dragEnd
                if (start != null && end != null) {
                    drawRect(colors.seaGlass.copy(alpha = .7f), Offset(min(start.x, end.x), min(start.y, end.y)), Size(abs(end.x - start.x), abs(end.y - start.y)))
                }
            }
        }
        }
        if (boxes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AtlasButton("Undo last box", { onValueChange(MaskText.write(boxes.dropLast(1))) }, Modifier.weight(1f), AtlasButtonStyle.Secondary)
                AtlasButton("Clear", { onValueChange("") }, Modifier.weight(1f), AtlasButtonStyle.Quiet)
            }
        }
    }
}
