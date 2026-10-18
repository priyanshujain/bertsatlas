package app.bertopia.ui

import app.bertopia.domain.pack.BundledMixes

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class BertPose { Idle, Correct, Incorrect, Overtime, Hint, Fact, Results, Streak, Stamp }

enum class BertopiaButtonStyle { Primary, Secondary, Quiet, Destructive }

enum class AnswerState { Available, Selected, Correct, Incorrect, Eliminated }

@Immutable
data class FlagUi(
    val alpha2: String,
    val countryName: String,
)

@Immutable
data class ChoiceUi(
    val id: String,
    val label: String,
    val supportingText: String? = null,
    val state: AnswerState = AnswerState.Available,
    val enabled: Boolean = true,
    val flag: FlagUi? = null,
)

@Immutable
data class LetterTileUi(
    val id: String,
    val letter: String,
    val placedSlot: Int? = null,
    val enabled: Boolean = true,
)

@Immutable
data class LetterSlotUi(
    val position: Int,
    val character: String? = null,
    val tileId: String? = null,
    val fixed: Boolean = false,
)

@Immutable
data class StatUi(val label: String, val value: String)

@Immutable
data class ProgressGroupUi(
    val label: String,
    val progress: Float,
    val detail: String,
)

@Immutable
data class StampUi(
    val id: String,
    val title: String,
    val description: String,
    val earned: Boolean,
)

@Immutable
data class SettingToggleUi(
    val id: String,
    val title: String,
    val description: String,
    val checked: Boolean,
    val enabled: Boolean = true,
)

@Immutable
data class DebugActionUi(
    val id: String,
    val title: String,
    val detail: String? = null,
    val destructive: Boolean = false,
)

enum class PackCover { Flags, Capitals, Mix, Generic }

@Immutable
data class PackCardUi(
    val packId: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val cover: PackCover,
    val cardCount: Int,
    val masteredCount: Int,
    val learningCount: Int,
    val dueCount: Int = 0,
    val shelf: String = "",
    val subshelf: String = "",
    val official: Boolean = true,
    val coverImage: CoverImage? = null,
)

@Immutable
data class CoverImage(val mediaDirectory: String, val name: String)

@Immutable
data class MixCardUi(
    val mixId: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val memberPacks: List<PackCardUi>,
    val missingPackNames: List<String>,
) {
    val cardCount: Int get() = memberPacks.sumOf { it.cardCount }
    val cover: PackCover get() = if (mixId == BundledMixes.Geography.mixId) PackCover.Mix else PackCover.Generic
}


enum class StudyPush(val title: String, val description: String) {
    Relaxed("Easy going", "Fewer reviews, a little more forgetting"),
    Balanced("Steady", "Bert's recommended balance"),
    Intense("Push me", "More reviews so you rarely forget"),
}

@Immutable
data class StudySettingsUi(
    val dailyNewCards: Int,
    val push: StudyPush,
)
