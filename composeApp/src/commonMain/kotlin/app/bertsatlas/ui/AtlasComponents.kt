package app.bertsatlas.ui

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.min
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ResourceItem
import org.jetbrains.compose.resources.painterResource
import berts_atlas.composeapp.generated.resources.Res
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

private val ScreenShape = RoundedCornerShape(24.dp)
private val ControlShape = RoundedCornerShape(16.dp)

@Composable
fun AtlasScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().background(AtlasTheme.colors.parchment),
        contentAlignment = Alignment.TopCenter,
    ) {
        val contentModifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .padding(contentPadding)
            .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
        Box(modifier = contentModifier, content = content)
    }
}

@Composable
fun AtlasText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AtlasTheme.typography.body,
    color: Color = AtlasTheme.colors.plum,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == null) style.copy(color = color) else style.copy(color = color, textAlign = textAlign),
    )
}

@Composable
fun AtlasHeading(text: String, modifier: Modifier = Modifier, centered: Boolean = false) {
    AtlasText(
        text = text,
        modifier = modifier.semantics { heading() },
        style = AtlasTheme.typography.title,
        textAlign = if (centered) TextAlign.Center else null,
    )
}

@Composable
fun AtlasButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AtlasButtonStyle = AtlasButtonStyle.Primary,
    enabled: Boolean = true,
    contentDescription: String = text,
) {
    val colors = AtlasTheme.colors
    val background = when (style) {
        AtlasButtonStyle.Primary -> colors.atlasBlue
        AtlasButtonStyle.Secondary -> colors.paper
        AtlasButtonStyle.Quiet -> Color.Transparent
        AtlasButtonStyle.Destructive -> colors.error
    }
    val foreground = when (style) {
        AtlasButtonStyle.Primary, AtlasButtonStyle.Destructive -> colors.white
        AtlasButtonStyle.Secondary, AtlasButtonStyle.Quiet -> colors.plum
    }
    val border = if (style == AtlasButtonStyle.Secondary) Modifier.border(2.dp, colors.atlasBlue, ControlShape) else Modifier
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .then(border)
            .background(if (enabled) background else colors.outline, ControlShape)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        AtlasText(text, style = AtlasTheme.typography.label, color = foreground, textAlign = TextAlign.Center)
    }
}

@Composable
fun AtlasCard(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AtlasTheme.colors.paper, ScreenShape)
            .border(1.dp, AtlasTheme.colors.outline.copy(alpha = .45f), ScreenShape)
            .then(if (contentDescription == null) Modifier else Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription })
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { content() },
    )
}

@Composable
fun AtlasChoice(
    choice: ChoiceUi,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AtlasTheme.colors
    val background = when (choice.state) {
        AnswerState.Available -> colors.paper
        AnswerState.Selected -> colors.mustard.copy(alpha = .28f)
        AnswerState.Correct -> colors.success.copy(alpha = .16f)
        AnswerState.Incorrect -> colors.error.copy(alpha = .14f)
        AnswerState.Eliminated -> colors.outline.copy(alpha = .12f)
    }
    val outline = when (choice.state) {
        AnswerState.Available -> colors.outline
        AnswerState.Selected -> colors.mustard
        AnswerState.Correct -> colors.success
        AnswerState.Incorrect -> colors.error
        AnswerState.Eliminated -> colors.outline.copy(alpha = .5f)
    }
    val stateText = when (choice.state) {
        AnswerState.Available -> "Available"
        AnswerState.Selected -> "Selected"
        AnswerState.Correct -> "Correct answer"
        AnswerState.Incorrect -> "Incorrect answer"
        AnswerState.Eliminated -> "Eliminated"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(background, ControlShape)
            .border(2.dp, outline, ControlShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = choice.label
                stateDescription = stateText
                if (!choice.enabled) disabled()
            }
            .clickable(enabled = choice.enabled) { onClick(choice.id) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        choice.flag?.let {
            FlagImage(
                flag = it,
                modifier = Modifier.width(82.dp),
                accessible = false,
                showFallbackLabel = false,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (choice.flag == null) AtlasText(choice.label, style = AtlasTheme.typography.label)
            if (choice.flag == null) {
                choice.supportingText?.let { AtlasText(it, style = AtlasTheme.typography.detail, color = colors.mutedPlum) }
            }
            if (choice.state == AnswerState.Correct) AtlasText("✓ Correct", style = AtlasTheme.typography.detail, color = colors.success)
            if (choice.state == AnswerState.Incorrect) AtlasText("× Your answer", style = AtlasTheme.typography.detail, color = colors.error)
        }
    }
}

@OptIn(InternalResourceApi::class)
@Composable
fun FlagImage(
    flag: FlagUi,
    modifier: Modifier = Modifier,
    accessible: Boolean = true,
    showFallbackLabel: Boolean = true,
) {
    val alpha2 = flag.alpha2.lowercase()
    val validCode = alpha2.length == 2 && alpha2.all { it in 'a'..'z' }
    val resource = remember(alpha2) {
        DrawableResource(
            id = "flag:$alpha2",
            items = setOf(
                ResourceItem(
                    qualifiers = setOf(),
                    path = "composeResources/berts_atlas.composeapp.generated.resources/files/flag-renders/$alpha2.png",
                    offset = -1,
                    size = -1,
                ),
            ),
        )
    }
    val imageModifier = modifier
        .aspectRatio(3f / 2f)
        .clip(RoundedCornerShape(8.dp))
        .background(AtlasTheme.colors.paper, RoundedCornerShape(8.dp))
        .border(1.dp, AtlasTheme.colors.outline.copy(alpha = .55f), RoundedCornerShape(8.dp))
        .then(if (accessible) Modifier.semantics { contentDescription = "Flag of ${flag.countryName}" } else Modifier)
    if (validCode) {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = imageModifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        FlagFallback(flag.countryName, imageModifier, showFallbackLabel)
    }
}

@Composable
private fun FlagFallback(countryName: String, modifier: Modifier, showLabel: Boolean) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AtlasText("⚑", style = AtlasTheme.typography.heading, color = AtlasTheme.colors.atlasBlue)
        if (showLabel) {
            AtlasText(
                countryName,
                Modifier.align(Alignment.BottomCenter).padding(3.dp),
                style = AtlasTheme.typography.detail,
                color = AtlasTheme.colors.mutedPlum,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun AtlasToggle(setting: SettingToggleUi, onToggle: (String, Boolean) -> Unit) {
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
            AtlasText(setting.title, style = AtlasTheme.typography.label)
            AtlasText(setting.description, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
        }
        Box(
            Modifier.size(width = 54.dp, height = 32.dp)
                .background(if (setting.checked) AtlasTheme.colors.success else AtlasTheme.colors.outline, CircleShape)
                .padding(4.dp),
            contentAlignment = if (setting.checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(24.dp).background(AtlasTheme.colors.white, CircleShape))
        }
    }
}

@Composable
fun AtlasProgress(label: String, progress: Float, detail: String, modifier: Modifier = Modifier) {
    val value = progress.coerceIn(0f, 1f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AtlasText(label, style = AtlasTheme.typography.label)
            AtlasText(detail, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
        }
        Box(
            Modifier.fillMaxWidth().height(12.dp).background(AtlasTheme.colors.outline.copy(alpha = .24f), CircleShape)
                .semantics { progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value, 0f..1f) },
        ) {
            Box(Modifier.fillMaxWidth(value).height(12.dp).background(AtlasTheme.colors.atlasBlue, CircleShape))
        }
    }
}

@Composable
fun BertIllustration(
    pose: BertPose,
    modifier: Modifier = Modifier,
    contentDescription: String = "Bert, the curious atlas brain",
    reducedMotion: Boolean = AtlasTheme.reducedMotion,
) {
    if (reducedMotion) {
        StaticBertIllustration(pose, modifier, contentDescription)
        return
    }
    val compositionResult = rememberLottieComposition(pose) {
        LottieCompositionSpec.JsonString(
            Res.readBytes("files/${pose.animationFileName}").decodeToString(),
        )
    }
    val composition = compositionResult.value
    if (composition == null || compositionResult.isFailure) {
        StaticBertIllustration(pose, modifier, contentDescription)
        return
    }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Compottie.IterateForever,
    )
    Box(
        modifier = modifier.size(180.dp).semantics { this.contentDescription = contentDescription },
    ) {
        StaticBertIllustration(pose, Modifier.matchParentSize(), contentDescription = null)
        Image(
            painter = rememberLottiePainter(composition = composition, progress = { progress }),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
    }
}

private val BertPose.animationFileName: String
    get() = when (this) {
        BertPose.Idle -> "bert_idle.json"
        BertPose.Correct -> "bert_correct.json"
        BertPose.Incorrect -> "bert_incorrect.json"
        BertPose.Overtime -> "bert_overtime.json"
        BertPose.Hint -> "bert_hint.json"
        BertPose.Fact -> "bert_fact.json"
        BertPose.Results -> "bert_results.json"
        BertPose.Streak -> "bert_streak.json"
        BertPose.Stamp -> "bert_stamp.json"
    }

@Composable
private fun StaticBertIllustration(
    pose: BertPose,
    modifier: Modifier,
    contentDescription: String?,
) {
    val colors = AtlasTheme.colors
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
        val accent = when (pose) {
            BertPose.Correct, BertPose.Streak, BertPose.Stamp -> colors.success
            BertPose.Incorrect -> colors.error
            BertPose.Overtime, BertPose.Hint -> colors.mustard
            BertPose.Fact, BertPose.Results, BertPose.Idle -> colors.atlasBlue
        }
        drawCircle(accent, 10f * scale, p(145f, 39f))
        drawLine(accent, p(145f, 17f), p(145f, 7f), 4f * scale, StrokeCap.Round)
        drawLine(accent, p(161f, 25f), p(169f, 18f), 4f * scale, StrokeCap.Round)
    }
}

@Composable
fun ScreenTopBar(title: String, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onBack != null) AtlasButton("Back", onBack, style = AtlasButtonStyle.Quiet)
        AtlasText(title, Modifier.weight(1f).semantics { heading() }, style = AtlasTheme.typography.heading)
        trailing?.invoke()
    }
}

@Composable
fun StatRow(stats: List<StatUi>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 440.dp && stats.size > 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.forEach { stat -> StatCell(stat, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.forEach { stat -> StatCell(stat, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatCell(stat: StatUi, modifier: Modifier) {
    Column(
        modifier.background(AtlasTheme.colors.paper, ControlShape).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AtlasText(stat.value, style = AtlasTheme.typography.heading, textAlign = TextAlign.Center)
        AtlasText(stat.label, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionGap() = Spacer(Modifier.height(18.dp))
