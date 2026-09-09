package app.bertsatlas.ui

import androidx.compose.runtime.Immutable

enum class BertPose { Idle, Correct, Incorrect, Overtime, Hint, Fact, Results, Streak, Stamp }

enum class AtlasButtonStyle { Primary, Secondary, Quiet, Destructive }

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
