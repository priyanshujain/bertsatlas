package app.bertopia.cardrender

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.bertopia.cardrender.resources.Res
import app.bertopia.cardrender.resources.jetbrains_mono_regular
import org.jetbrains.compose.resources.Font

data class TypeAnswerState(
    val typed: String,
    val onTypedChange: (String) -> Unit,
    val expected: String?,
    val revealed: Boolean,
)

private class CardRenderEnvironment(
    val style: CardRenderStyle,
    val codeFamily: FontFamily,
    val mediaDirectory: String,
    val textAlign: TextAlign,
    val typeAnswer: TypeAnswerState?,
    val soundPlayer: SoundPlayer,
    val playingSound: String?,
    val onPlayingSoundChange: (String?) -> Unit,
)

private const val CODE_WITHOUT_LIGATURES = "\"liga\" 0, \"calt\" 0"

private val LocalCardEnvironment = staticCompositionLocalOf<CardRenderEnvironment> { error("CardView environment missing") }

@Composable
fun CardView(
    document: CardDocument,
    style: CardRenderStyle,
    mediaDirectory: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    typeAnswer: TypeAnswerState? = null,
) {
    val soundPlayer = rememberSoundPlayer()
    var playingSound by remember { mutableStateOf<String?>(null) }
    val codeFamily = style.codeFamily ?: FontFamily(Font(Res.font.jetbrains_mono_regular))
    val environment = CardRenderEnvironment(style, codeFamily, mediaDirectory, textAlign, typeAnswer, soundPlayer, playingSound) { playingSound = it }
    CompositionLocalProvider(LocalCardEnvironment provides environment) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            document.blocks.forEach { BlockView(it, textAlign) }
        }
    }
}

@Composable
private fun BlockView(block: CardBlock, textAlign: TextAlign) {
    when (block) {
        is CardBlock.Paragraph -> ParagraphView(block.inlines, block.role, textAlign)
        is CardBlock.ListBlock -> ListView(block)
        is CardBlock.Table -> TableView(block)
        is CardBlock.Quote -> QuoteView(block)
        CardBlock.Rule -> RuleView()
        is CardBlock.Picture -> PictureView(block)
        is CardBlock.CodeBlock -> CodeBlockView(block)
        is CardBlock.DisplayMath -> DisplayMathView(block.tex)
        is CardBlock.Occlusion -> OcclusionView(block)
        is CardBlock.TypeAnswer -> TypeAnswerView()
        is CardBlock.Hint -> HintView(block)
    }
}

@Composable
private fun ParagraphView(inlines: List<CardInline>, role: ParagraphRole, textAlign: TextAlign, modifier: Modifier = Modifier) {
    val environment = LocalCardEnvironment.current
    val style = environment.style
    val density = LocalDensity.current
    val baseStyle = when (role) {
        ParagraphRole.Body -> style.body.copy(color = style.text)
        ParagraphRole.Heading -> style.body.copy(color = style.text, fontWeight = FontWeight.Bold)
        ParagraphRole.Muted -> style.body.copy(color = style.mutedText, fontSize = style.body.fontSize * 0.88f, lineHeight = style.body.lineHeight * 0.88f)
    }
    val fontSizeDp = with(density) { baseStyle.fontSize.toDp().value }
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val text = buildAnnotatedString {
        inlines.forEachIndexed { index, inline ->
            when (inline) {
                is CardInline.Text -> withStyle(spanStyle(inline.style, style, environment.codeFamily, baseStyle.fontSize)) { append(inline.text) }
                CardInline.LineBreak -> append('\n')
                is CardInline.Sound -> {
                    val id = "sound-$index"
                    appendInlineContent(id, "[audio]")
                    inlineContent[id] = InlineTextContent(Placeholder(1.9.em, 1.9.em, PlaceholderVerticalAlign.Center)) {
                        SoundButton(inline.file)
                    }
                }
                is CardInline.Math -> {
                    val id = "math-$index"
                    appendInlineContent(id, MathSpeech.speakable(inline.tex))
                    val request = MathRequest(inline.tex, display = false, color = baseStyle.color, fontSizeDp = fontSizeDp)
                    val (widthDp, heightDp) = measuredInlineMathSizes[request] ?: MathJaxPage.estimatedSize(request)
                    val placeholder = with(density) { Placeholder(widthDp.dp.toSp(), heightDp.dp.toSp(), PlaceholderVerticalAlign.TextCenter) }
                    inlineContent[id] = InlineTextContent(placeholder) {
                        MathFormula(
                            request = request,
                            onMeasured = { width, height -> rememberInlineMathSize(request, width, height) },
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
    val largestScale = inlines.filterIsInstance<CardInline.Text>().maxOfOrNull { it.style.sizeScale ?: 1f }?.coerceAtLeast(1f) ?: 1f
    val lineHeight = if (baseStyle.lineHeight.isSpecified) baseStyle.lineHeight * largestScale else baseStyle.lineHeight
    BasicText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = baseStyle.copy(textAlign = textAlign, lineHeight = lineHeight),
        inlineContent = inlineContent,
    )
}

private fun spanStyle(inline: InlineStyle, style: CardRenderStyle, codeFamily: FontFamily, baseSize: TextUnit): SpanStyle {
    val decorations = listOfNotNull(
        TextDecoration.Underline.takeIf { inline.underline },
        TextDecoration.LineThrough.takeIf { inline.strikethrough },
    )
    val deckColor = inline.colorArgb?.let { Color(it.toInt()) }?.takeIf { readableOn(it, style.surface) }
    val clozeColor = when (inline.cloze) {
        ClozeRole.Hidden, ClozeRole.Revealed -> style.accent
        else -> null
    }
    val size = inline.sizeScale?.let { baseSize * it }
    val shiftedSize = if (inline.baseline != Baseline.Normal) (size ?: baseSize) * 0.75f else size
    return SpanStyle(
        color = clozeColor ?: deckColor ?: Color.Unspecified,
        fontSize = shiftedSize ?: TextUnit.Unspecified,
        fontWeight = if (inline.bold || inline.cloze == ClozeRole.Hidden || inline.cloze == ClozeRole.Revealed) FontWeight.Bold else null,
        fontStyle = if (inline.italic) FontStyle.Italic else null,
        fontFamily = if (inline.code) codeFamily else null,
        fontFeatureSettings = if (inline.code) CODE_WITHOUT_LIGATURES else null,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        baselineShift = when (inline.baseline) {
            Baseline.Subscript -> BaselineShift.Subscript
            Baseline.Superscript -> BaselineShift.Superscript
            Baseline.Normal -> null
        },
        background = when {
            inline.cloze == ClozeRole.Hidden -> style.accent.copy(alpha = 0.14f)
            inline.code -> style.codeBackground
            inline.highlightArgb != null -> Color(inline.highlightArgb.toInt()).copy(alpha = 0.55f)
            else -> Color.Unspecified
        },
    )
}

private fun readableOn(color: Color, background: Color): Boolean {
    val lighter = maxOf(color.luminance(), background.luminance()) + 0.05f
    val darker = minOf(color.luminance(), background.luminance()) + 0.05f
    return lighter / darker >= 2.2f
}

@Composable
private fun SoundButton(file: String) {
    val environment = LocalCardEnvironment.current
    val path = remember(environment.mediaDirectory, file) { CardMediaFiles.resolve(environment.mediaDirectory, file) }
    val playing = environment.playingSound == file
    val available = path != null
    val accent = environment.style.accent
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (available) accent else environment.style.outline.copy(alpha = 0.4f))
            .clickable(enabled = available, role = Role.Button) {
                if (playing) {
                    environment.soundPlayer.stop()
                    environment.onPlayingSoundChange(null)
                } else if (path != null) {
                    val started = environment.soundPlayer.play(path.toString()) { environment.onPlayingSoundChange(null) }
                    environment.onPlayingSoundChange(if (started) file else null)
                }
            }
            .semantics { contentDescription = if (!available) "Audio unavailable" else if (playing) "Stop audio" else "Play audio" },
        contentAlignment = Alignment.Center,
    ) {
        val iconColor = environment.style.onAccent
        Canvas(Modifier.size(14.dp)) {
            if (playing) {
                drawRect(iconColor, topLeft = Offset(size.width * 0.1f, size.height * 0.1f), size = Size(size.width * 0.8f, size.height * 0.8f))
            } else {
                val triangle = Path().apply {
                    moveTo(size.width * 0.15f, 0f)
                    lineTo(size.width, size.height / 2)
                    lineTo(size.width * 0.15f, size.height)
                    close()
                }
                drawPath(triangle, iconColor)
            }
        }
    }
}

@Composable
private fun ListView(list: CardBlock.ListBlock) {
    val style = LocalCardEnvironment.current.style
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth()) {
                BasicText(
                    if (list.ordered) "${index + 1}." else "•",
                    modifier = Modifier.widthIn(min = 26.dp).padding(end = 6.dp),
                    style = style.body.copy(color = style.accent, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.forEach { BlockView(it, TextAlign.Start) }
                }
            }
        }
    }
}

@Composable
private fun TableView(table: CardBlock.Table) {
    val style = LocalCardEnvironment.current.style
    val density = LocalDensity.current
    val columns = table.rows.maxOf { it.size }
    val shape = RoundedCornerShape(10.dp)
    val characterWidth = with(density) { (style.body.fontSize * 0.72f).toDp() }
    val columnWidths = (0 until columns).map { columnIndex ->
        val longestWord = table.rows.mapNotNull { it.getOrNull(columnIndex) }
            .flatMap { cell -> cell.inlines.filterIsInstance<CardInline.Text>().joinToString("") { it.text }.split(' ', '\n') }
            .maxOfOrNull { it.length } ?: 1
        (characterWidth * longestWord + TABLE_CELL_HORIZONTAL_PADDING * 2).coerceIn(TABLE_MIN_COLUMN_WIDTH, TABLE_MAX_COLUMN_WIDTH)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val naturalWidth = columnWidths.fold(0.dp) { total, width -> total + width }
        val fitsWidth = naturalWidth <= maxWidth
        val tableModifier = if (fitsWidth) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState())
        Column(tableModifier.clip(shape).border(1.dp, style.outline.copy(alpha = 0.5f), shape)) {
            table.rows.forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    (0 until columns).forEach { columnIndex ->
                        val cell = row.getOrNull(columnIndex)
                        val width = columnWidths[columnIndex]
                        Box(
                            Modifier
                                .then(if (fitsWidth) Modifier.weight(width.value) else Modifier.width(width))
                                .fillMaxHeight()
                                .background(if (cell?.isHeader == true) style.accent.copy(alpha = 0.1f) else Color.Transparent)
                                .border(0.5.dp, style.outline.copy(alpha = 0.35f))
                                .padding(horizontal = TABLE_CELL_HORIZONTAL_PADDING, vertical = 8.dp),
                        ) {
                            if (cell != null) ParagraphView(cell.inlines, ParagraphRole.Body, TextAlign.Start)
                        }
                    }
                }
            }
        }
    }
}

private val TABLE_CELL_HORIZONTAL_PADDING = 10.dp
private val TABLE_MIN_COLUMN_WIDTH = 52.dp
private val TABLE_MAX_COLUMN_WIDTH = 220.dp

@Composable
private fun QuoteView(quote: CardBlock.Quote) {
    val style = LocalCardEnvironment.current.style
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(style.accent.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quote.blocks.forEach { BlockView(it, TextAlign.Start) }
        }
    }
}

@Composable
private fun RuleView() {
    val style = LocalCardEnvironment.current.style
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(style.outline.copy(alpha = 0.45f)))
}

@Composable
private fun PictureView(picture: CardBlock.Picture) {
    val environment = LocalCardEnvironment.current
    val state = rememberMediaPainter(environment.mediaDirectory, picture.source)
    var zoomed by remember { mutableStateOf(false) }
    when (state) {
        MediaPainterState.Loading -> Box(Modifier.fillMaxWidth().height(120.dp))
        MediaPainterState.Missing -> MissingMedia(picture.description ?: picture.source)
        is MediaPainterState.Loaded -> {
            val intrinsic = state.painter.intrinsicSize
            val ratio = if (intrinsic.width > 0 && intrinsic.height > 0) intrinsic.width / intrinsic.height else 1.5f
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MediaImage(
                    painter = state.painter,
                    description = picture.description ?: "Card image",
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Image, onClickLabel = "Zoom image") { zoomed = true },
                )
            }
            if (zoomed) {
                ZoomDialog(onClose = { zoomed = false }) {
                    MediaImage(state.painter, picture.description, Modifier.fillMaxWidth().aspectRatio(ratio))
                }
            }
        }
    }
}

@Composable
private fun MissingMedia(label: String) {
    val style = LocalCardEnvironment.current.style
    BasicText(
        "Image not available: $label",
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, style.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        style = style.body.copy(color = style.mutedText, fontSize = style.body.fontSize * 0.85f, textAlign = TextAlign.Center),
    )
}

@Composable
private fun OcclusionView(occlusion: CardBlock.Occlusion) {
    val environment = LocalCardEnvironment.current
    val state = rememberMediaPainter(environment.mediaDirectory, occlusion.imageSource)
    var zoomed by remember { mutableStateOf(false) }
    when (state) {
        MediaPainterState.Loading -> Box(Modifier.fillMaxWidth().height(160.dp))
        MediaPainterState.Missing -> MissingMedia(occlusion.imageSource)
        is MediaPainterState.Loaded -> {
            val intrinsic = state.painter.intrinsicSize
            val ratio = if (intrinsic.width > 0 && intrinsic.height > 0) intrinsic.width / intrinsic.height else 1.5f
            val hiddenCount = occlusion.shapes.count { it.state == OcclusionState.Hidden }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OccludedImage(
                    occlusion,
                    state,
                    ratio,
                    Modifier
                        .heightIn(max = 380.dp)
                        .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Image, onClickLabel = "Zoom image") { zoomed = true }
                        .semantics {
                            contentDescription = if (hiddenCount > 0) "Image with $hiddenCount hidden label to identify" else "Image with the label revealed"
                        },
                )
            }
            if (zoomed) {
                ZoomDialog(onClose = { zoomed = false }) {
                    OccludedImage(occlusion, state, ratio, Modifier.fillMaxWidth().aspectRatio(ratio))
                }
            }
        }
    }
}

@Composable
private fun OccludedImage(occlusion: CardBlock.Occlusion, state: MediaPainterState.Loaded, ratio: Float, modifier: Modifier) {
    val style = LocalCardEnvironment.current.style
    val intrinsic = state.painter.intrinsicSize
    Box(modifier) {
        MediaImage(state.painter, null, Modifier.fillMaxWidth().aspectRatio(ratio))
        Canvas(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            val strokeWidth = 2.dp.toPx()
            occlusion.shapes.forEach { shape ->
                val scaleX = if (shape.usesPixelCoordinates && intrinsic.width > 0) size.width / intrinsic.width else size.width
                val scaleY = if (shape.usesPixelCoordinates && intrinsic.height > 0) size.height / intrinsic.height else size.height
                val (fill, stroke) = when (shape.state) {
                    OcclusionState.Hidden -> style.hiddenMask to Color(0xFF8C2F2F)
                    OcclusionState.Revealed -> style.accent.copy(alpha = 0.12f) to style.accent
                    OcclusionState.InactiveMasked -> style.inactiveMask to Color(0xFF8A7330)
                    OcclusionState.InactiveVisible -> return@forEach
                }
                when (val geometry = shape.geometry) {
                    is OcclusionGeometry.Rect -> {
                        val topLeft = Offset(geometry.left * scaleX, geometry.top * scaleY)
                        val rectSize = Size(geometry.width * scaleX, geometry.height * scaleY)
                        drawRect(fill, topLeft, rectSize)
                        drawRect(stroke, topLeft, rectSize, style = Stroke(strokeWidth))
                    }
                    is OcclusionGeometry.Ellipse -> {
                        val topLeft = Offset(geometry.left * scaleX, geometry.top * scaleY)
                        val ovalSize = Size(geometry.radiusX * 2 * scaleX, geometry.radiusY * 2 * scaleY)
                        drawOval(fill, topLeft, ovalSize)
                        drawOval(stroke, topLeft, ovalSize, style = Stroke(strokeWidth))
                    }
                    is OcclusionGeometry.Polygon -> {
                        val path = Path().apply {
                            geometry.points.forEachIndexed { index, (x, y) ->
                                if (index == 0) moveTo(x * scaleX, y * scaleY) else lineTo(x * scaleX, y * scaleY)
                            }
                            close()
                        }
                        drawPath(path, fill)
                        drawPath(path, stroke, style = Stroke(strokeWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: CardBlock.CodeBlock) {
    val environment = LocalCardEnvironment.current
    val style = environment.style
    val highlighted = remember(block) {
        buildAnnotatedString {
            SyntaxHighlighter.tokenize(block.code, block.language).forEach { token ->
                val color = style.syntaxColors[token.kind]
                if (color == null) append(token.text)
                else withStyle(SpanStyle(color = color, fontStyle = if (token.kind == CodeTokenKind.Comment) FontStyle.Italic else null)) { append(token.text) }
            }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(style.codeBackground)
            .border(1.dp, style.outline.copy(alpha = 0.3f), shape)
            .padding(14.dp),
    ) {
        SyntaxHighlighter.canonicalLanguage(block.language)?.let { language ->
            BasicText(
                language,
                modifier = Modifier.padding(bottom = 6.dp),
                style = style.body.copy(color = style.mutedText, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        BasicText(
            highlighted,
            style = TextStyle(
                color = style.text,
                fontFamily = environment.codeFamily,
                fontFeatureSettings = CODE_WITHOUT_LIGATURES,
                fontSize = style.body.fontSize * 0.78f,
                lineHeight = style.body.fontSize * 1.15f,
            ),
        )
    }
}

private val measuredInlineMathSizes = mutableStateMapOf<MathRequest, Pair<Float, Float>>()

private fun rememberInlineMathSize(request: MathRequest, widthDp: Float, heightDp: Float) {
    val size = widthDp to heightDp
    if (widthDp > 0 && heightDp > 0 && measuredInlineMathSizes[request] != size) measuredInlineMathSizes[request] = size
}

@Composable
private fun DisplayMathView(tex: String) {
    val style = LocalCardEnvironment.current.style
    val density = LocalDensity.current
    val fontSizeDp = with(density) { style.body.fontSize.toDp().value }
    val request = MathRequest(tex, display = true, color = style.text, fontSizeDp = fontSizeDp)
    var measured by remember(tex) { mutableStateOf<Pair<Float, Float>?>(null) }
    val (width, height) = measured ?: MathJaxPage.estimatedSize(request)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        MathFormula(
            request = request,
            onMeasured = { w, h -> if (w > 0 && h > 0) measured = w to h },
            modifier = Modifier.width(width.dp).height(height.dp).semantics { contentDescription = MathSpeech.speakable(tex) },
        )
    }
}

@Composable
private fun TypeAnswerView() {
    val environment = LocalCardEnvironment.current
    val style = environment.style
    val state = environment.typeAnswer
    val shape = RoundedCornerShape(14.dp)
    if (state == null || !state.revealed) {
        var localText by rememberSaveable { mutableStateOf("") }
        val value = state?.typed ?: localText
        Box(
            Modifier
                .fillMaxWidth()
                .border(2.dp, style.accent.copy(alpha = 0.6f), shape)
                .background(style.surface, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty()) {
                BasicText("Type your answer", style = style.body.copy(color = style.mutedText))
            }
            BasicTextField(
                value = value,
                onValueChange = { state?.onTypedChange?.invoke(it) ?: run { localText = it } },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Your answer" },
                textStyle = style.body.copy(color = style.text),
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
        }
        return
    }
    val expected = state.expected.orEmpty()
    val result = remember(state.typed, expected) { TypedAnswerComparison.compare(state.typed, expected) }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, style.outline.copy(alpha = 0.5f), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val codeStyle = style.body.copy(fontFamily = environment.codeFamily, fontFeatureSettings = CODE_WITHOUT_LIGATURES, textAlign = TextAlign.Center)
        if (state.typed.isBlank()) {
            BasicText("You left this blank", style = style.body.copy(color = style.mutedText, textAlign = TextAlign.Center))
        } else if (result.isExactMatch) {
            BasicText(
                buildAnnotatedString { withStyle(SpanStyle(color = style.correct, fontWeight = FontWeight.Bold)) { append(result.expected) } },
                style = codeStyle,
                modifier = Modifier.semantics { contentDescription = "Correct. ${result.expected}" },
            )
            return@Column
        } else {
            BasicText(
                typedDiff(result, style),
                style = codeStyle,
                modifier = Modifier.semantics { contentDescription = "You typed ${state.typed}" },
            )
            BasicText("↓", style = style.body.copy(color = style.mutedText))
        }
        BasicText(
            result.expected,
            style = codeStyle.copy(fontWeight = FontWeight.Bold, color = style.text),
            modifier = Modifier.semantics { contentDescription = "Answer ${result.expected}" },
        )
    }
}

private fun typedDiff(result: TypedAnswerResult, style: CardRenderStyle): AnnotatedString = buildAnnotatedString {
    result.typedSegments.forEach { segment ->
        val spanStyle = when (segment.kind) {
            TypedSegmentKind.Correct -> SpanStyle(color = style.correct, background = style.correct.copy(alpha = 0.12f))
            TypedSegmentKind.Wrong -> SpanStyle(color = style.wrong, background = style.wrong.copy(alpha = 0.14f), textDecoration = TextDecoration.LineThrough)
            TypedSegmentKind.Missing -> SpanStyle(color = style.mutedText, background = style.outline.copy(alpha = 0.2f))
        }
        withStyle(spanStyle) { append(segment.text) }
    }
}

@Composable
private fun HintView(hint: CardBlock.Hint) {
    val style = LocalCardEnvironment.current.style
    var open by rememberSaveable(hint) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!open) {
            BasicText(
                if (hint.label.equals("hint", ignoreCase = true)) "Show hint" else "Show ${hint.label.lowercase()}",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.5.dp, style.accent.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .clickable(role = Role.Button) { open = true }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                style = style.body.copy(color = style.accent, fontWeight = FontWeight.SemiBold, fontSize = style.body.fontSize * 0.85f),
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(style.accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                hint.blocks.forEach { block ->
                    if (block is CardBlock.Paragraph) ParagraphView(block.inlines, ParagraphRole.Muted, TextAlign.Center)
                    else BlockView(block, TextAlign.Center)
                }
            }
        }
    }
}
