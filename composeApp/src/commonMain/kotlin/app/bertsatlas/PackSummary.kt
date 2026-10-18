package app.bertsatlas

import app.bertsatlas.domain.fsrs.FriendlyStudySettings

data class PackSummary(
    val packId: String,
    val title: String,
    val subtitle: String,
    val accentColor: String,
    val cardCount: Int,
    val category: String,
    val subcategory: String,
    val official: Boolean = true,
    val sections: List<String> = emptyList(),
    val dueCount: Int = 0,
    val masteredCount: Int = 0,
    val learningCount: Int = 0,
    val familiarCount: Int = 0,
    val studySettings: FriendlyStudySettings? = null,
    val coverMedia: String? = null,
)
