package app.bertopia.domain.editor

data class CardDraft(
    val styleName: String,
    val fields: Map<String, String>,
    val tags: List<String> = emptyList(),
    val section: String? = null,
    val guid: String? = null,
)

data class CardChange(val fields: Map<String, String> = emptyMap(), val tags: List<String>? = null) {
    val isEmpty: Boolean get() = fields.isEmpty() && tags == null
}

data class AddedCard(val index: Int, val noteId: Long, val guid: String, val ref: String, val cards: Int)

data class AddCardsResult(val added: List<AddedCard>, val warnings: List<EditorProblem>) {
    val cardCount: Int get() = added.sumOf { it.cards }
}

data class EditedCard(val noteId: Long, val newCards: Int)

data class MediaAdded(val name: String, val bytes: Long, val reference: String, val reusedExisting: Boolean)

data class PackDraft(
    val title: String,
    val subtitle: String? = null,
    val category: String,
    val subcategory: String,
    val accentColor: String? = null,
    val coverMedia: String? = null,
    val packId: String? = null,
)

data class PackAppearance(
    val title: String,
    val subtitle: String?,
    val accentColor: String?,
    val coverMedia: String?,
)

data class PackPlacement(val category: String, val subcategory: String)

sealed interface BulkFieldEdit {
    val fieldName: String

    data class SetValue(override val fieldName: String, val value: String) : BulkFieldEdit

    data class ReplaceText(override val fieldName: String, val find: String, val replacement: String, val ignoreCase: Boolean = true) : BulkFieldEdit
}
