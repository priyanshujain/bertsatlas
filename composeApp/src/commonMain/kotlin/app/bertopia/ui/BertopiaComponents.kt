package app.bertopia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.min
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path as FilePath
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.painterResource

val BertopiaCardShape = RoundedCornerShape(24.dp)
val BertopiaControlShape = RoundedCornerShape(16.dp)

@Composable
fun BertopiaScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    maxContentWidth: Dp = DEFAULT_MAX_CONTENT_WIDTH,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    headerPadding: PaddingValues = PaddingValues(0.dp),
    header: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BertopiaTheme.colors.parchment)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = maxContentWidth).fillMaxSize()) {
            header?.let {
                Box(Modifier.fillMaxWidth().padding(headerPadding), contentAlignment = Alignment.TopCenter) {
                    it()
                }
            }
            val contentModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .let { base -> if (scrollable) base.verticalScroll(rememberScrollState()) else base }
                .padding(contentPadding)
            Box(modifier = contentModifier, content = content)
        }
    }
}

@Composable
fun AdaptivePanes(
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    leading: @Composable ColumnScope.() -> Unit,
    trailing: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= WIDE_LAYOUT_MIN_WIDTH) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing), content = leading)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing), content = trailing)
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                leading()
                trailing()
            }
        }
    }
}

val WIDE_LAYOUT_MIN_WIDTH = 820.dp
val TopBarPadding = PaddingValues(start = 8.dp, top = 12.dp, end = 8.dp)
val WIDE_CONTENT_WIDTH = 1040.dp
private val DEFAULT_MAX_CONTENT_WIDTH = 720.dp

@Composable
fun BertopiaScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    maxContentWidth: Dp = DEFAULT_MAX_CONTENT_WIDTH,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    BertopiaScreen(
        modifier = modifier,
        scrollable = scrollable,
        maxContentWidth = maxContentWidth,
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 24.dp),
        headerPadding = TopBarPadding,
        header = { ScreenTopBar(title, onBack, trailing) },
        content = content,
    )
}

@Composable
fun BertopiaText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = BertopiaTheme.typography.body,
    color: Color = BertopiaTheme.colors.plum,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == null) style.copy(color = color) else style.copy(color = color, textAlign = textAlign),
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

@Composable
fun BertopiaHeading(text: String, modifier: Modifier = Modifier, centered: Boolean = false) {
    BertopiaText(
        text = text,
        modifier = modifier.semantics { heading() },
        style = BertopiaTheme.typography.title,
        textAlign = if (centered) TextAlign.Center else null,
    )
}

@Composable
fun BertopiaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BertopiaButtonStyle = BertopiaButtonStyle.Primary,
    enabled: Boolean = true,
    contentDescription: String = text,
) {
    val colors = BertopiaTheme.colors
    val background = when (style) {
        BertopiaButtonStyle.Primary -> colors.bertopiaBlue
        BertopiaButtonStyle.Secondary -> colors.paper
        BertopiaButtonStyle.Quiet -> Color.Transparent
        BertopiaButtonStyle.Destructive -> colors.error
    }
    val foreground = when (style) {
        BertopiaButtonStyle.Primary, BertopiaButtonStyle.Destructive -> colors.white
        BertopiaButtonStyle.Secondary, BertopiaButtonStyle.Quiet -> colors.plum
    }
    val border = if (!enabled) Modifier else when (style) {
        BertopiaButtonStyle.Secondary -> Modifier.border(1.5.dp, colors.bertopiaBlue.copy(alpha = .75f), BertopiaControlShape)
        BertopiaButtonStyle.Destructive -> Modifier.border(1.5.dp, colors.orangeAccent, BertopiaControlShape)
        else -> Modifier
    }
    val resolvedBackground = when {
        !enabled -> colors.outline.copy(alpha = .45f)
        style == BertopiaButtonStyle.Destructive -> colors.paper
        else -> background
    }
    val resolvedForeground = when {
        !enabled -> colors.mutedPlum
        style == BertopiaButtonStyle.Destructive -> colors.error
        else -> foreground
    }
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .then(border)
            .background(resolvedBackground, BertopiaControlShape)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            }
            .clip(BertopiaControlShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BertopiaText(text, style = BertopiaTheme.typography.label, color = resolvedForeground, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun BertopiaCard(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BertopiaTheme.colors.paper, BertopiaCardShape)
            .border(1.dp, BertopiaTheme.colors.outline.copy(alpha = .25f), BertopiaCardShape)
            .then(if (contentDescription == null) Modifier else Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription })
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() },
    )
}

@Composable
fun BertopiaChoice(
    choice: ChoiceUi,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
) {
    val colors = BertopiaTheme.colors
    val background = when (choice.state) {
        AnswerState.Available -> colors.paper
        AnswerState.Selected -> colors.seaGlass
        AnswerState.Correct -> colors.success.copy(alpha = .16f)
        AnswerState.Incorrect -> colors.orangeAccent.copy(alpha = .18f)
        AnswerState.Eliminated -> colors.outline.copy(alpha = .12f)
    }
    val outline = when (choice.state) {
        AnswerState.Available -> colors.outline
        AnswerState.Selected -> colors.success
        AnswerState.Correct -> colors.success
        AnswerState.Incorrect -> colors.orangeAccent
        AnswerState.Eliminated -> colors.outline.copy(alpha = .5f)
    }
    val stateText = when {
        !choice.enabled && choice.state == AnswerState.Selected -> "Used"
        choice.state == AnswerState.Available -> "Available"
        choice.state == AnswerState.Selected -> "Selected"
        choice.state == AnswerState.Correct -> "Correct answer"
        choice.state == AnswerState.Incorrect -> "Incorrect answer"
        else -> "Eliminated"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(background, BertopiaControlShape)
            .border(2.dp, outline, BertopiaControlShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = choice.label
                stateDescription = stateText
                selected = choice.state == AnswerState.Selected
                if (!choice.enabled) disabled()
            }
            .clip(BertopiaControlShape)
            .clickable(enabled = choice.enabled) { onClick(choice.id) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        shortcut?.let {
            Box(
                Modifier.size(26.dp).border(1.5.dp, colors.outline, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) { BertopiaText(it, style = BertopiaTheme.typography.detail, color = colors.mutedPlum) }
        }
        choice.flag?.let {
            FlagImage(
                flag = it,
                modifier = Modifier.width(82.dp),
                accessible = false,
                showFallbackLabel = false,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (choice.flag == null) BertopiaText(choice.label, style = BertopiaTheme.typography.label)
            if (choice.flag == null) {
                choice.supportingText?.let { BertopiaText(it, style = BertopiaTheme.typography.detail, color = colors.mutedPlum) }
            }
            if (choice.state == AnswerState.Correct) BertopiaText("✓ Correct", style = BertopiaTheme.typography.detail, color = colors.success)
            if (choice.state == AnswerState.Incorrect) BertopiaText("× Your answer", style = BertopiaTheme.typography.detail, color = colors.error)
        }
    }
}

val LocalFlagMediaDirectory = staticCompositionLocalOf { "" }

private val flagBitmapCache = mutableMapOf<String, ImageBitmap>()

@Composable
fun FlagImage(
    flag: FlagUi,
    modifier: Modifier = Modifier,
    accessible: Boolean = true,
    showFallbackLabel: Boolean = true,
) {
    val alpha2 = flag.alpha2.lowercase()
    val validCode = alpha2.length == 2 && alpha2.all { it in 'a'..'z' }
    val imagePath = FilePath(LocalFlagMediaDirectory.current, "$alpha2.png").toString()
    val bitmap by produceState(flagBitmapCache[imagePath], imagePath) {
        if (validCode && value == null) {
            value = withContext(Dispatchers.Default) { readFlagBitmap(imagePath) }?.also { flagBitmapCache[imagePath] = it }
        }
    }
    val accessibleLabel = if (flag.countryName.startsWith("Flag ", ignoreCase = true)) flag.countryName else "Flag of ${flag.countryName}"
    val imageModifier = modifier
        .aspectRatio(FLAG_SLOT_RATIO)
        .clip(RoundedCornerShape(8.dp))
        .background(BertopiaTheme.colors.paper, RoundedCornerShape(8.dp))
        .border(1.dp, BertopiaTheme.colors.outline.copy(alpha = .55f), RoundedCornerShape(8.dp))
        .then(if (accessible) Modifier.semantics { contentDescription = accessibleLabel } else Modifier)
    val loaded = bitmap
    when {
        loaded != null -> Box(
            modifier
                .aspectRatio(FLAG_SLOT_RATIO)
                .then(if (accessible) Modifier.semantics { contentDescription = accessibleLabel } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val flagRatio = loaded.width.toFloat() / loaded.height
            val shape = RoundedCornerShape(8.dp)
            val framed = if (loaded.hasTransparentCorner()) Modifier else Modifier
                .clip(shape)
                .border(1.dp, BertopiaTheme.colors.outline.copy(alpha = .55f), shape)
            Image(
                bitmap = loaded,
                contentDescription = null,
                modifier = Modifier
                    .aspectRatio(flagRatio, matchHeightConstraintsFirst = flagRatio < FLAG_SLOT_RATIO)
                    .then(framed),
                contentScale = ContentScale.Fit,
            )
        }
        validCode && SystemFileSystem.exists(FilePath(imagePath)) -> Box(imageModifier)
        else -> FlagFallback(flag.countryName, imageModifier, showFallbackLabel)
    }
}

private const val FLAG_SLOT_RATIO = 3f / 2f

private fun ImageBitmap.hasTransparentCorner(): Boolean {
    val corner = IntArray(1)
    readPixels(corner, startX = width - 1, startY = 0, width = 1, height = 1)
    return (corner[0] ushr 24) == 0
}

private fun readFlagBitmap(path: String): ImageBitmap? = runCatching {
    SystemFileSystem.source(FilePath(path)).buffered().use { it.readByteArray() }.decodeToImageBitmap()
}.getOrNull()

@Composable
private fun FlagFallback(countryName: String, modifier: Modifier, showLabel: Boolean) {
    Box(modifier, contentAlignment = Alignment.Center) {
        BertopiaText("⚑", style = BertopiaTheme.typography.heading, color = BertopiaTheme.colors.bertopiaBlue)
        if (showLabel) {
            BertopiaText(
                countryName,
                Modifier.align(Alignment.BottomCenter).padding(3.dp),
                style = BertopiaTheme.typography.detail,
                color = BertopiaTheme.colors.mutedPlum,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun BertopiaToggle(setting: SettingToggleUi, onToggle: (String, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                stateDescription = if (setting.checked) "On" else "Off"
                if (!setting.enabled) disabled()
            }
            .clickable(enabled = setting.enabled) { onToggle(setting.id, !setting.checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BertopiaText(setting.title, style = BertopiaTheme.typography.label)
            BertopiaText(setting.description, style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
        }
        Box(
            Modifier.size(width = 54.dp, height = 32.dp)
                .background(if (setting.checked) BertopiaTheme.colors.success else BertopiaTheme.colors.seaGlass, CircleShape)
                .border(
                    2.dp,
                    if (setting.checked) BertopiaTheme.colors.success else BertopiaTheme.colors.outline,
                    CircleShape,
                )
                .padding(4.dp),
            contentAlignment = if (setting.checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(24.dp).background(BertopiaTheme.colors.white, CircleShape))
        }
    }
}

@Composable
fun BertopiaProgress(label: String, progress: Float, detail: String, modifier: Modifier = Modifier) {
    val value = progress.coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BertopiaText(label, style = BertopiaTheme.typography.label)
            BertopiaText(detail, style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum)
        }
        Box(
            Modifier.fillMaxWidth().height(12.dp).background(BertopiaTheme.colors.outline.copy(alpha = .24f), CircleShape)
                .semantics { progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, 0f..1f) },
        ) {
            Box(Modifier.fillMaxWidth(value).height(12.dp).background(BertopiaTheme.colors.bertopiaBlue, CircleShape))
        }
    }
}

@Composable
fun BertIllustration(
    pose: BertPose,
    modifier: Modifier = Modifier,
    contentDescription: String = "Bert, the curious brain",
    reducedMotion: Boolean = BertopiaTheme.reducedMotion,
) {
    val transition = rememberInfiniteTransition(label = "Bert bulb")
    val animatedProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                when (pose) {
                    BertPose.Correct, BertPose.Overtime -> 520
                    BertPose.Incorrect -> 260
                    BertPose.Hint -> 1_100
                    BertPose.Fact -> 800
                    BertPose.Results, BertPose.Stamp -> 900
                    BertPose.Streak -> 700
                    BertPose.Idle -> 1_400
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "Bert ${pose.name.lowercase()} motion",
    )
    val progress = if (reducedMotion) .5f else animatedProgress
    val wobble = with(LocalDensity.current) { ((progress - .5f) * 6f).dp.toPx() }
    Box(
        modifier.size(180.dp).semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) { drawBertDecoration(pose, progress) }
        StaticBertIllustration(
            pose = pose,
            modifier = Modifier.matchParentSize().graphicsLayer {
                when (pose) {
                    BertPose.Incorrect -> translationX = wobble
                    BertPose.Idle -> {
                        scaleX = .99f + progress * .01f
                        scaleY = .99f + progress * .01f
                    }
                    else -> Unit
                }
            },
            contentDescription = null,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBertDecoration(pose: BertPose, progress: Float) {
    val green = Color(0xFF27635B)
    val blue = Color(0xFF778BA5)
    val seaGlass = Color(0xFFB7CFC8)
    val coral = Color(0xFFF7BFBE)
    val peach = Color(0xFFE9AF8B)
    val orange = Color(0xFFE1814E)
    val unit = size.minDimension / 180f
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun star(center: Offset, radius: Float, color: Color) {
        val path = Path()
        repeat(10) { index ->
            val angle = -kotlin.math.PI.toFloat() / 2f + index * kotlin.math.PI.toFloat() / 5f
            val length = if (index % 2 == 0) radius else radius * .42f
            val point = Offset(center.x + kotlin.math.cos(angle) * length, center.y + kotlin.math.sin(angle) * length)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color)
    }
    fun successBurst(includeClock: Boolean) {
        val alpha = .35f + progress * .65f
        val radius = (5f + progress * 4f) * unit
        star(p(22f, 38f), radius, seaGlass.copy(alpha = alpha))
        star(p(158f, 42f), radius * .82f, coral.copy(alpha = alpha))
        star(p(24f, 142f), radius * .72f, peach.copy(alpha = alpha))
        star(p(155f, 136f), radius, seaGlass.copy(alpha = alpha))
        if (includeClock) {
            val center = p(155f, 151f)
            drawCircle(Color(0xFFF6E9E1), 15f * unit, center)
            drawCircle(green, 15f * unit, center, style = Stroke(3f * unit))
            drawLine(green, center, p(155f, 140f), 2.5f * unit, StrokeCap.Round)
            drawLine(green, center, p(163f, 155f), 2.5f * unit, StrokeCap.Round)
        }
    }
    when (pose) {
        BertPose.Idle, BertPose.Incorrect -> Unit
        BertPose.Correct -> successBurst(includeClock = false)
        BertPose.Overtime -> successBurst(includeClock = true)
        BertPose.Hint -> {
            val lift = progress * 3f
            drawCircle(seaGlass.copy(alpha = .7f), 4f * unit, p(139f, 38f + lift))
            drawCircle(seaGlass.copy(alpha = .8f), 7f * unit, p(151f, 27f + lift))
            drawCircle(seaGlass, 10f * unit, p(165f, 17f + lift))
        }
        BertPose.Fact -> {
            val center = p(151f, 32f)
            drawCircle(peach, 10f * unit, center)
            val rayLength = 7f + 4f * progress
            drawLine(orange, p(151f, 14f), p(151f, 14f - rayLength), 3.5f * unit, StrokeCap.Round)
            drawLine(orange, p(165f, 19f), p(165f + rayLength * .7f, 19f - rayLength * .7f), 3.5f * unit, StrokeCap.Round)
        }
        BertPose.Results -> {
            drawCircle(blue.copy(alpha = .18f + progress * .12f), (72f + progress * 5f) * unit, p(90f, 88f), style = Stroke(4f * unit))
        }
        BertPose.Streak -> {
            val flame = Path().apply {
                moveTo(158f * unit, 161f * unit)
                cubicTo(139f * unit, 151f * unit, 151f * unit, (127f - progress * 5f) * unit, 159f * unit, 121f * unit)
                cubicTo(177f * unit, 141f * unit, 174f * unit, 157f * unit, 158f * unit, 161f * unit)
                close()
            }
            drawPath(flame, orange.copy(alpha = .8f + progress * .2f))
        }
        BertPose.Stamp -> {
            val center = p(155f, 150f)
            drawCircle(blue.copy(alpha = .7f), (13f + progress * 2f) * unit, center, style = Stroke(3f * unit))
            drawLine(green, p(148f, 150f), p(153f, 155f), 3f * unit, StrokeCap.Round)
            drawLine(green, p(153f, 155f), p(163f, 143f), 3f * unit, StrokeCap.Round)
        }
    }
}

@Composable
private fun StaticBertIllustration(
    pose: BertPose,
    modifier: Modifier,
    contentDescription: String?,
) {
    val colors = BertopiaTheme.colors
    val accessibleModifier = if (contentDescription == null) Modifier else Modifier.semantics { this.contentDescription = contentDescription }
    Canvas(modifier = modifier.size(180.dp).then(accessibleModifier)) {
        val scale = min(size.width, size.height) / 180f
        fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
        val brain = Path().apply {
            moveTo(38f * scale, 96f * scale)
            cubicTo(18f * scale, 85f * scale, 25f * scale, 55f * scale, 47f * scale, 52f * scale)
            cubicTo(48f * scale, 27f * scale, 80f * scale, 20f * scale, 94f * scale, 38f * scale)
            cubicTo(113f * scale, 23f * scale, 143f * scale, 38f * scale, 139f * scale, 63f * scale)
            cubicTo(159f * scale, 74f * scale, 151f * scale, 105f * scale, 133f * scale, 110f * scale)
            cubicTo(127f * scale, 139f * scale, 91f * scale, 146f * scale, 74f * scale, 128f * scale)
            cubicTo(53f * scale, 139f * scale, 29f * scale, 119f * scale, 38f * scale, 96f * scale)
            close()
        }
        drawPath(brain, colors.coral)
        drawPath(brain, colors.plum, style = Stroke(width = 4f * scale))
        listOf(Rect(p(43f, 67f), Size(43f * scale, 34f * scale)), Rect(p(94f, 67f), Size(43f * scale, 34f * scale))).forEach {
            drawRoundRect(
                color = colors.paper,
                topLeft = it.topLeft,
                size = it.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f * scale),
            )
            drawRoundRect(
                color = colors.plum,
                topLeft = it.topLeft,
                size = it.size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f * scale),
                style = Stroke(5f * scale),
            )
        }
        drawLine(colors.plum, p(86f, 82f), p(94f, 82f), 5f * scale, StrokeCap.Round)
        drawCircle(colors.plum, 4f * scale, p(65f, 84f))
        drawCircle(colors.plum, 4f * scale, p(115f, 84f))
        val mouthY = if (pose == BertPose.Incorrect) 121f else 113f
        val mouth = Path().apply {
            moveTo(72f * scale, mouthY * scale)
            quadraticTo(90f * scale, (if (pose == BertPose.Incorrect) 106f else 130f) * scale, 109f * scale, mouthY * scale)
        }
        drawPath(mouth, colors.plum, style = Stroke(4f * scale, cap = StrokeCap.Round))
    }
}

@Composable
fun ScreenTopBar(title: String, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        onBack?.let { Box(Modifier.align(Alignment.CenterStart)) { BertopiaBackButton(it) } }
        BertopiaText(
            title,
            Modifier.fillMaxWidth().padding(horizontal = 72.dp).semantics { heading() },
            style = BertopiaTheme.typography.heading,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        trailing?.let { Box(Modifier.align(Alignment.CenterEnd).heightIn(min = 48.dp), contentAlignment = Alignment.Center) { it() } }
    }
}

@Composable
fun BertopiaBackButton(onClick: () -> Unit, contentDescription: String = "Back") {
    val color = BertopiaTheme.colors.plum
    Box(
        Modifier.size(48.dp)
            .semantics { role = Role.Button; this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawLine(color, Offset(size.width * .7f, size.height * .16f), Offset(size.width * .3f, size.height * .5f), 3.2.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(size.width * .3f, size.height * .5f), Offset(size.width * .7f, size.height * .84f), 3.2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun BertopiaSearchButton(onClick: () -> Unit, contentDescription: String = "Search") {
    val color = BertopiaTheme.colors.plum
    Box(
        Modifier.size(48.dp)
            .semantics { role = Role.Button; this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.6.dp.toPx()
            drawCircle(color, size.width * .3f, Offset(size.width * .42f, size.height * .42f), style = Stroke(stroke))
            drawLine(color, Offset(size.width * .64f, size.height * .64f), Offset(size.width * .88f, size.height * .88f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun BertopiaExitButton(onClick: () -> Unit, contentDescription: String = "Leave trivia") {
    val color = BertopiaTheme.colors.plum
    Box(
        Modifier.size(48.dp)
            .semantics { role = Role.Button; this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val stroke = 2.6.dp.toPx()
            drawLine(color, Offset(size.width * .18f, size.height * .12f), Offset(size.width * .18f, size.height * .88f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .18f, size.height * .12f), Offset(size.width * .56f, size.height * .12f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .18f, size.height * .88f), Offset(size.width * .56f, size.height * .88f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .48f, size.height * .5f), Offset(size.width * .9f, size.height * .5f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .73f, size.height * .32f), Offset(size.width * .9f, size.height * .5f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .9f, size.height * .5f), Offset(size.width * .73f, size.height * .68f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun BertopiaLinkRow(text: String, onClick: () -> Unit, destructive: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .semantics { role = Role.Button; contentDescription = text }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BertopiaText(text, Modifier.weight(1f), style = BertopiaTheme.typography.label, color = if (destructive) BertopiaTheme.colors.error else BertopiaTheme.colors.plum)
        BertopiaText("›", style = BertopiaTheme.typography.heading, color = BertopiaTheme.colors.mutedPlum)
    }
}

@Composable
fun StatRow(stats: List<StatUi>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { stat -> StatCell(stat, Modifier.weight(1f).fillMaxHeight()) }
    }
}

@Composable
private fun StatCell(stat: StatUi, modifier: Modifier) {
    Column(
        modifier.background(BertopiaTheme.colors.paper, BertopiaControlShape).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BertopiaText(stat.value, style = BertopiaTheme.typography.heading, textAlign = TextAlign.Center)
        BertopiaText(stat.label, style = BertopiaTheme.typography.detail, color = BertopiaTheme.colors.mutedPlum, textAlign = TextAlign.Center)
    }
}

@Composable
fun <T> EqualHeightGrid(
    items: List<T>,
    columns: Int,
    spacing: Dp = 10.dp,
    cell: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { item -> cell(item, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun SectionGap() = Spacer(Modifier.height(18.dp))
