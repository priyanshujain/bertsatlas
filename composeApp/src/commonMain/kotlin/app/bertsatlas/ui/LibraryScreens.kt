package app.bertsatlas.ui

import app.bertsatlas.domain.pack.LevelSetting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import app.bertsatlas.cardrender.MediaPainterState
import app.bertsatlas.cardrender.rememberMediaPainter
import app.bertsatlas.ui.editor.mobile.ChipRow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen(
    packs: List<PackCardUi>,
    mixes: List<MixCardUi>,
    isFirstLaunch: Boolean,
    streakDays: Int,
    progressSummary: String,
    totalPoints: Int,
    onOpenPack: (String) -> Unit,
    onOpenMix: (String) -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
    onAddPack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenStore: (() -> Unit)? = null,
    onCreatePack: () -> Unit = {},
) {
    var shelfFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var subshelfFilter by rememberSaveable { mutableStateOf<String?>(null) }
    AtlasScreen(
        modifier = modifier,
        maxContentWidth = WIDE_CONTENT_WIDTH,
        headerPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp),
        header = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (isFirstLaunch) Spacer(Modifier.weight(1f)) else PointsBadge(totalPoints, onProgress, Modifier.weight(1f))
                SettingsIconButton(onSettings)
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
        val cardColumns = if (wide) 2 else 1
        Column(
            Modifier.widthIn(max = if (wide) WIDE_CONTENT_WIDTH else 520.dp).fillMaxWidth().align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AtlasText("Bert's Atlas", Modifier.semantics { heading() }, AtlasTheme.typography.title)
                    AtlasText(
                        if (isFirstLaunch) "Pick a pack and Bert will quiz you." else "Getting nerdier every day.",
                        color = AtlasTheme.colors.mutedPlum,
                    )
                }
                HomeBertIllustration(Modifier.size(120.dp))
            }
            val shelves = packs.map { it.shelf }.distinct()
            LibrarySectionLabel("Your packs")
            if (shelves.size > 1) {
                ChipRow(listOf("" to "All") + shelves.map { it to it.ifBlank { "Other" } }, shelfFilter ?: "") { id ->
                    shelfFilter = id.ifEmpty { null }
                    subshelfFilter = null
                }
                val subshelves = shelfFilter?.let { shelf -> packs.filter { it.shelf == shelf }.map { it.subshelf }.distinct() }.orEmpty()
                if (subshelves.size > 1) {
                    ChipRow(listOf("" to "Everything") + subshelves.map { it to it.ifBlank { "Other" } }, subshelfFilter ?: "") { id ->
                        subshelfFilter = id.ifEmpty { null }
                    }
                }
            }
            val visible = packs.filter { pack ->
                (shelfFilter == null || pack.shelf == shelfFilter) && (subshelfFilter == null || pack.subshelf == subshelfFilter)
            }
            visible.groupBy { it.shelf }.forEach { (shelf, shelfPacks) ->
                if (shelves.size > 1) ShelfLabel(shelf.ifBlank { "Other" })
                CardGrid(shelfPacks, cardColumns) { PackCard(it, onOpenPack) }
            }
            onOpenStore?.let { openStore ->
                AtlasButton(
                    "Find more packs",
                    openStore,
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                    contentDescription = "Find more packs in the store",
                )
            }
            Row(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AtlasButton("Make a pack", onCreatePack, Modifier.weight(1f), AtlasButtonStyle.Secondary, contentDescription = "Make your own pack")
                AtlasButton(
                    "Add from a file",
                    onAddPack,
                    Modifier.weight(1f),
                    AtlasButtonStyle.Secondary,
                    contentDescription = "Add a deck from a file, such as an Anki deck",
                )
            }
            if (mixes.isNotEmpty()) LibrarySectionLabel("Mixes")
            CardGrid(mixes, cardColumns) { MixCard(it, onOpenMix) }
            if (!isFirstLaunch) {
                SectionGap()
                val streakSummary = when (streakDays) {
                    0 -> "Answer a question today to start a curious streak"
                    1 -> "1 curious day"
                    else -> "$streakDays curious days"
                }
                AtlasCard(contentDescription = "$streakSummary. $progressSummary") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AtlasText("✦", style = AtlasTheme.typography.title, color = AtlasTheme.colors.coral)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            AtlasText(streakSummary, style = AtlasTheme.typography.label)
                            AtlasText(progressSummary, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
                        }
                    }
                }
                AtlasButton("See your progress", onProgress, Modifier.align(Alignment.CenterHorizontally), AtlasButtonStyle.Quiet)
            }
        }
        }
    }
}

@Composable
private fun <T> CardGrid(items: List<T>, columns: Int, card: @Composable (T) -> Unit) {
    items.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { item -> Box(Modifier.weight(1f).fillMaxHeight()) { card(item) } }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ShelfLabel(text: String) {
    AtlasText(
        text,
        Modifier.padding(top = 4.dp).semantics { heading() },
        style = AtlasTheme.typography.label,
        color = AtlasTheme.colors.mutedPlum,
    )
}

@Composable
private fun LibrarySectionLabel(text: String) {
    AtlasText(
        text,
        Modifier.padding(top = 10.dp).semantics { heading() },
        style = AtlasTheme.typography.heading,
    )
}

@Composable
private fun PackCard(pack: PackCardUi, onOpen: (String) -> Unit) {
    LibraryCard(
        title = pack.title,
        subtitle = pack.subtitle,
        meta = pack.libraryMetaLabel(),
        onClick = { onOpen(pack.packId) },
    ) { PackCoverArt(pack.accent, pack.cover, Modifier.size(92.dp), RoundedCornerShape(18.dp), pack.title, pack.coverImage) }
}

@Composable
private fun MixCard(mix: MixCardUi, onOpen: (String) -> Unit) {
    LibraryCard(
        title = mix.title,
        subtitle = mix.subtitle,
        meta = "Draws from ${(mix.memberPacks.map { it.title } + mix.missingPackNames).joinToString(" and ")}",
        onClick = { onOpen(mix.mixId) },
    ) { PackCoverArt(mix.accent, mix.cover, Modifier.size(92.dp), RoundedCornerShape(18.dp), mix.title) }
}

@Composable
private fun LibraryCard(
    title: String,
    subtitle: String,
    meta: String,
    onClick: () -> Unit,
    cover: @Composable () -> Unit,
) {
    val colors = AtlasTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(colors.paper, shape)
            .border(1.dp, colors.outline.copy(alpha = .25f), shape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = listOf(title, subtitle, meta).filter { it.isNotBlank() }.joinToString(". ")
            }
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        cover()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AtlasText(title, style = AtlasTheme.typography.heading)
            if (subtitle.isNotBlank()) AtlasText(subtitle, style = AtlasTheme.typography.detail, color = colors.mutedPlum)
            AtlasText(meta, style = AtlasTheme.typography.detail, color = colors.mutedPlum.copy(alpha = .75f))
        }
        AtlasText("›", style = AtlasTheme.typography.title, color = colors.mutedPlum)
    }
}

@Composable
fun PackHomeScreen(
    pack: PackCardUi,
    sections: List<String>,
    selectedSection: String?,
    studySettings: StudySettingsUi?,
    notice: String?,
    durations: List<ChoiceUi>,
    onChooseSection: (String?) -> Unit,
    onReview: () -> Unit,
    onChooseDuration: (String) -> Unit,
    onPlay: () -> Unit,
    onChangeStudySettings: (StudySettingsUi) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    banner: @Composable ColumnScope.() -> Unit = {},
    extras: @Composable ColumnScope.() -> Unit = {},
    onEditCards: () -> Unit = {},
    onEditPack: () -> Unit = {},
    onMakeCopy: () -> Unit = {},
    showLevelPicker: Boolean = false,
    selectedLevel: LevelSetting = LevelSetting.All,
    onChooseLevel: (LevelSetting) -> Unit = {},
) {
    AtlasScreenScaffold("", onBack, modifier, maxContentWidth = WIDE_CONTENT_WIDTH) {
        AdaptivePanes(
            Modifier.widthIn(max = WIDE_CONTENT_WIDTH).fillMaxWidth().align(Alignment.TopCenter),
            leading = {
                PackCoverArt(pack.accent, pack.cover, Modifier.fillMaxWidth().aspectRatio(2f), RoundedCornerShape(28.dp), pack.title, pack.coverImage)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AtlasText(pack.title, Modifier.semantics { heading() }, AtlasTheme.typography.display)
                    if (pack.subtitle.isNotBlank()) AtlasText(pack.subtitle, color = AtlasTheme.colors.mutedPlum)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PackStat("Cards", pack.cardCount, Modifier.weight(1f))
                    PackStat("Due today", pack.dueCount, Modifier.weight(1f))
                    PackStat("Mastered", pack.masteredCount, Modifier.weight(1f))
                }
                if (showLevelPicker) LevelPicker(selectedLevel, onChooseLevel)
                if (sections.isNotEmpty()) SectionPicker(sections, selectedSection, onChooseSection)
            },
            trailing = {
                banner()
                notice?.let { message ->
                    AtlasCard(contentDescription = message) { AtlasText(message, style = AtlasTheme.typography.detail) }
                }
                ReviewPrompt(pack.dueCount, onReview)
                PlayOptions(pack.title, durations, onChooseDuration, onPlay)
                studySettings?.let { StudySettingsCard(it, onChangeStudySettings) }
                PackOwnershipCard(pack.official, onEditCards, onEditPack, onMakeCopy)
                extras()
            },
        )
    }
}

@Composable
private fun PackOwnershipCard(official: Boolean, onEditCards: () -> Unit, onEditPack: () -> Unit, onMakeCopy: () -> Unit) {
    AtlasCard {
        if (official) {
            AtlasText("Want to change it?", Modifier.semantics { heading() }, AtlasTheme.typography.heading)
            AtlasText("This pack comes from Bert, so it stays as it is. Make your own copy to add or change cards. Your progress comes with it.", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
            AtlasButton("Make my copy", onMakeCopy, Modifier.fillMaxWidth(), AtlasButtonStyle.Secondary)
        } else {
            AtlasText("Your pack", Modifier.semantics { heading() }, AtlasTheme.typography.heading)
            AtlasButton("Add or change cards", onEditCards, Modifier.fillMaxWidth(), AtlasButtonStyle.Secondary)
            AtlasButton("Name, shelf and colour", onEditPack, Modifier.fillMaxWidth(), AtlasButtonStyle.Quiet)
        }
    }
}

@Composable
private fun ReviewPrompt(dueCount: Int, onReview: () -> Unit) {
    AtlasCard {
        AtlasText("Review", Modifier.semantics { heading() }, AtlasTheme.typography.heading)
        AtlasText(
            when (dueCount) {
                0 -> "You're all caught up. New cards arrive tomorrow."
                1 -> "1 card is ready. Flip it, then say how well you knew it."
                else -> "$dueCount cards are ready. Flip each one, then say how well you knew it."
            },
            style = AtlasTheme.typography.detail,
            color = AtlasTheme.colors.mutedPlum,
        )
        AtlasButton(
            if (dueCount == 0) "Review anyway" else "Start review",
            onReview,
            Modifier.fillMaxWidth(),
            if (dueCount == 0) AtlasButtonStyle.Secondary else AtlasButtonStyle.Primary,
        )
    }
}

@Composable
private fun LevelPicker(selected: LevelSetting, onChoose: (LevelSetting) -> Unit) {
    AtlasText("Level", Modifier.semantics { heading() }, AtlasTheme.typography.heading)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LevelSetting.entries.forEach { level ->
            AtlasChoice(
                ChoiceUi(level.name, level.name, state = if (level == selected) AnswerState.Selected else AnswerState.Available),
                { onChoose(level) },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionPicker(sections: List<String>, selected: String?, onChoose: (String?) -> Unit) {
    AtlasText("Sections", Modifier.semantics { heading() }, AtlasTheme.typography.heading)
    val choices = listOf(null to "Whole pack") + sections.map { it to it.replace("::", " › ") }
    choices.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (section, label) ->
                AtlasChoice(
                    ChoiceUi(section ?: ALL_SECTIONS_ID, label, state = if (section == selected) AnswerState.Selected else AnswerState.Available),
                    { onChoose(section) },
                    Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun StudySettingsCard(settings: StudySettingsUi, onChange: (StudySettingsUi) -> Unit) {
    AtlasCard {
        AtlasText("Study settings", Modifier.semantics { heading() }, AtlasTheme.typography.heading)
        AtlasText("New cards a day", style = AtlasTheme.typography.label)
        val goals = (NEW_CARD_GOALS + settings.dailyNewCards).distinct().sorted()
        goals.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { goal ->
                    AtlasChoice(
                        ChoiceUi("goal-$goal", goal.toString(), state = if (goal == settings.dailyNewCards) AnswerState.Selected else AnswerState.Available),
                        { onChange(settings.copy(dailyNewCards = goal)) },
                        Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        AtlasText("How hard should Bert push?", style = AtlasTheme.typography.label)
        StudyPush.entries.forEach { push ->
            AtlasChoice(
                ChoiceUi(push.name, push.title, push.description, if (push == settings.push) AnswerState.Selected else AnswerState.Available),
                { onChange(settings.copy(push = push)) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val ALL_SECTIONS_ID = "all-sections"
private val NEW_CARD_GOALS = listOf(5, 10, 20, 30, 50, 100)

@Composable
fun MixHomeScreen(
    mix: MixCardUi,
    durations: List<ChoiceUi>,
    onChooseDuration: (String) -> Unit,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFindMissingPacks: (() -> Unit)? = null,
) {
    AtlasScreenScaffold("", onBack, modifier, maxContentWidth = WIDE_CONTENT_WIDTH) {
        AdaptivePanes(
            Modifier.widthIn(max = WIDE_CONTENT_WIDTH).fillMaxWidth().align(Alignment.TopCenter),
            leading = {
                PackCoverArt(mix.accent, mix.cover, Modifier.fillMaxWidth().aspectRatio(2f), RoundedCornerShape(28.dp), mix.title)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AtlasText(mix.title, Modifier.semantics { heading() }, AtlasTheme.typography.display)
                    AtlasText(mix.subtitle, color = AtlasTheme.colors.mutedPlum)
                }
                AtlasText("Draws from", Modifier.semantics { heading() }, AtlasTheme.typography.heading)
                mix.memberPacks.forEach { MixMemberRow(it) }
            },
            trailing = {
                if (mix.missingPackNames.isNotEmpty()) {
                    AtlasCard(contentDescription = mix.missingPackMessage()) {
                        AtlasText(mix.missingPackMessage(), style = AtlasTheme.typography.detail)
                        onFindMissingPacks?.let { find ->
                            AtlasButton(
                                if (mix.missingPackNames.size == 1) "Get ${mix.missingPackNames.single()} in the store" else "Find them in the store",
                                find,
                                Modifier.fillMaxWidth(),
                                AtlasButtonStyle.Secondary,
                            )
                        }
                    }
                }
                if (mix.memberPacks.isNotEmpty()) PlayOptions(mix.title, durations, onChooseDuration, onPlay)
            },
        )
    }
}

@Composable
private fun MixMemberRow(pack: PackCardUi) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${pack.title}, ${pack.cardCountLabel()}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PackCoverArt(pack.accent, pack.cover, Modifier.size(52.dp), RoundedCornerShape(12.dp), pack.title)
        AtlasText(pack.title, Modifier.weight(1f), AtlasTheme.typography.label)
        AtlasText(pack.cardCountLabel(), style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
    }
}

@Composable
private fun PlayOptions(title: String, durations: List<ChoiceUi>, onChooseDuration: (String) -> Unit, onPlay: () -> Unit) {
    Spacer(Modifier.height(6.dp))
    AtlasText("How much time have you got?", Modifier.semantics { heading() }, AtlasTheme.typography.heading)
    AtlasText("The clock only runs while you're answering.", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
    durations.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { AtlasChoice(it, onChooseDuration, Modifier.weight(1f)) }
        }
    }
    Spacer(Modifier.height(4.dp))
    AtlasButton("Play", onPlay, Modifier.fillMaxWidth(), contentDescription = "Play $title")
}

private fun MixCardUi.missingPackMessage() =
    "Get ${missingPackNames.joinToString(" and ")} to add it to this mix. The packs you have still play."

@Composable
private fun PackStat(label: String, value: Int, modifier: Modifier) {
    Column(
        modifier
            .background(AtlasTheme.colors.paper, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { contentDescription = "$value $label" }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AtlasText(value.toString(), style = AtlasTheme.typography.heading, textAlign = TextAlign.Center)
        AtlasText(label, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun PackCoverArt(accent: Color, cover: PackCover, modifier: Modifier, shape: RoundedCornerShape, title: String = "", image: CoverImage? = null) {
    BoxWithConstraints(
        modifier.clip(shape).background(accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        val unit = minOf(maxWidth, maxHeight * 1.6f)
        val painter = image?.let { rememberMediaPainter(it.mediaDirectory, it.name) }
        if (painter is MediaPainterState.Loaded) {
            androidx.compose.foundation.Image(painter.painter, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            return@BoxWithConstraints
        }
        when (cover) {
            PackCover.Flags -> FannedFlags(listOf("jp", "br", "fr"), unit * .4f)
            PackCover.Capitals -> CapitalPin(Modifier.size(unit * .5f))
            PackCover.Mix -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * .04f)) {
                FlagImage(FlagUi("ca", "Canada"), Modifier.width(unit * .36f).graphicsLayer { rotationZ = -8f }, accessible = false)
                CapitalPin(Modifier.size(unit * .36f))
            }
            PackCover.Generic -> AtlasText(
                title.initials(),
                style = AtlasTheme.typography.display.copy(fontSize = AtlasTheme.typography.display.fontSize * (unit.value / 92f).coerceIn(.8f, 2.2f)),
                color = AtlasTheme.colors.white,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FannedFlags(codes: List<String>, flagWidth: androidx.compose.ui.unit.Dp) {
    Box(contentAlignment = Alignment.Center) {
        codes.forEachIndexed { index, code ->
            val offset = index - (codes.size - 1) / 2f
            FlagImage(
                FlagUi(code, code),
                Modifier
                    .width(flagWidth)
                    .graphicsLayer {
                        rotationZ = offset * 12f
                        translationX = offset * flagWidth.toPx() * .58f
                        translationY = kotlin.math.abs(offset) * flagWidth.toPx() * .08f
                    },
                accessible = false,
            )
        }
    }
}

@Composable
private fun CapitalPin(modifier: Modifier) {
    val colors = AtlasTheme.colors
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * .045f
        val pin = Path().apply {
            moveTo(w * .5f, h * .96f)
            cubicTo(w * .18f, h * .62f, w * .12f, h * .48f, w * .12f, h * .38f)
            cubicTo(w * .12f, h * .16f, w * .3f, h * .02f, w * .5f, h * .02f)
            cubicTo(w * .7f, h * .02f, w * .88f, h * .16f, w * .88f, h * .38f)
            cubicTo(w * .88f, h * .48f, w * .82f, h * .62f, w * .5f, h * .96f)
            close()
        }
        drawPath(pin, colors.parchment)
        drawPath(pin, colors.plum, style = Stroke(stroke, join = StrokeJoin.Round))
        val star = Path().apply {
            val cx = w * .5f
            val cy = h * .37f
            repeat(10) { index ->
                val radius = if (index % 2 == 0) w * .2f else w * .085f
                val angle = -kotlin.math.PI / 2 + index * kotlin.math.PI / 5
                val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
                val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(star, colors.orangeAccent)
        drawPath(star, colors.plum, style = Stroke(stroke * .6f, join = StrokeJoin.Round))
    }
}

internal fun String.initials(): String =
    split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { word -> word.any { it.isLetter() } }
        .take(2)
        .joinToString("") { word -> word.first { it.isLetter() }.uppercase() }
        .ifEmpty { "?" }

private fun PackCardUi.cardCountLabel() = if (cardCount == 1) "1 card" else "$cardCount cards"

private fun PackCardUi.libraryMetaLabel(): String = listOfNotNull(
    cardCountLabel(),
    if (dueCount > 0) "$dueCount due" else null,
    if (masteredCount > 0) "$masteredCount mastered" else null,
).joinToString(" · ")

@Composable
private fun HomeBertIllustration(modifier: Modifier) {
    val colors = AtlasTheme.colors
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = Alignment.Center) {
        BertIllustration(BertPose.Idle, Modifier.matchParentSize(), contentDescription = "Bert smiles with a bright idea and a folded map")
        Canvas(Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val outline = 4.dp.toPx()
            val map = Path().apply {
                moveTo(w * .01f, h * .68f)
                lineTo(w * .11f, h * .63f)
                lineTo(w * .22f, h * .68f)
                lineTo(w * .33f, h * .63f)
                lineTo(w * .33f, h * .88f)
                lineTo(w * .22f, h * .93f)
                lineTo(w * .11f, h * .88f)
                lineTo(w * .01f, h * .93f)
                close()
            }
            drawPath(map, colors.paper)
            drawPath(map, colors.plum, style = Stroke(outline, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawLine(colors.plum, Offset(w * .11f, h * .63f), Offset(w * .11f, h * .88f), outline)
            drawLine(colors.plum, Offset(w * .22f, h * .68f), Offset(w * .22f, h * .93f), outline)
            drawLine(colors.orangeAccent, Offset(w * .03f, h * .83f), Offset(w * .31f, h * .76f), outline * .7f, StrokeCap.Round)
        }
    }
}

@Composable
private fun PointsBadge(points: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AtlasTheme.colors
    Row(
        modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "View progress, $points total points"
            }
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawCircle(colors.mustard, size.minDimension * .40f)
            drawCircle(colors.plum, size.minDimension * .40f, style = Stroke(2.4.dp.toPx()))
            drawCircle(colors.plum, size.minDimension * .10f)
        }
        AtlasText(points.toString(), style = AtlasTheme.typography.heading, color = colors.plum)
    }
}

@Composable
private fun SettingsIconButton(onClick: () -> Unit) {
    val color = AtlasTheme.colors.plum
    androidx.compose.foundation.layout.Box(
        Modifier.size(48.dp).semantics { role = Role.Button; contentDescription = "Open settings" }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(color, size.minDimension * .27f, center, style = Stroke(3.dp.toPx()))
            drawCircle(color, size.minDimension * .07f, center)
            repeat(8) { index ->
                val angle = index * kotlin.math.PI.toFloat() / 4f
                val start = Offset(center.x + kotlin.math.cos(angle) * size.width * .31f, center.y + kotlin.math.sin(angle) * size.height * .31f)
                val end = Offset(center.x + kotlin.math.cos(angle) * size.width * .43f, center.y + kotlin.math.sin(angle) * size.height * .43f)
                drawLine(color, start, end, 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
fun BootstrapScreen(message: String = "Opening the atlas…", modifier: Modifier = Modifier) {
    AtlasScreen(modifier, scrollable = false) {
        Column(Modifier.fillMaxWidth().align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            BertIllustration(BertPose.Idle)
            AtlasText(message, style = AtlasTheme.typography.heading, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun RecoveryScreen(
    questionSummary: String,
    score: Int,
    timeRemaining: String,
    onResume: () -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AtlasScreen(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BertIllustration(BertPose.Hint, contentDescription = "Bert holds your place in the atlas")
            AtlasHeading("Your place is still marked", Modifier.fillMaxWidth(), centered = true)
            AtlasText("Pick up on the exact question where you left off.", textAlign = TextAlign.Center)
            AtlasCard(contentDescription = "Saved run. Score $score. Time remaining $timeRemaining. $questionSummary") {
                StatRow(listOf(StatUi("Score", score.toString()), StatUi("Time left", timeRemaining)))
                AtlasText(questionSummary, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
            }
            AtlasButton("Resume run", onResume, Modifier.fillMaxWidth())
            AtlasButton("Leave this trivia run", onAbandon, Modifier.fillMaxWidth(), AtlasButtonStyle.Quiet)
            AtlasText("Answered questions still count toward what Bert revisits next.", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
        }
    }
}
