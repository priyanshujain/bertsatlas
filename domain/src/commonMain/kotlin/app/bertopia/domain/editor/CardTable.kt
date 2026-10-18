package app.bertopia.domain.editor

import app.bertopia.anki.model.DECK_NAME_SEPARATOR
import app.bertopia.anki.model.NoteListing
import app.bertopia.domain.pack.BertNoteTypes

enum class CardSort { Created, Modified, Alphabetical, Style }

data class CardQuery(
    val search: String? = null,
    val tag: String? = null,
    val section: String? = null,
    val styleName: String? = null,
    val sort: CardSort = CardSort.Created,
    val descending: Boolean = false,
    val offset: Int = 0,
    val limit: Int = Int.MAX_VALUE,
)

data class CardRow(
    val noteId: Long,
    val guid: String,
    val ref: String,
    val styleName: String,
    val styleTitle: String,
    val preview: String,
    val section: String?,
    val tags: List<String>,
    val cardCount: Int,
    val modifiedSecs: Long,
)

data class CardPage(val total: Int, val rows: List<CardRow>, val tags: List<String>, val sections: List<String>)

object CardTable {
    fun query(pack: OpenedPack, query: CardQuery): CardPage {
        val deckIds = pack.deckIds(query.section?.takeIf { it.isNotBlank() })
        val listings = pack.collection.noteListings(deckIds)
        val notetypeNames = pack.collection.notetypes().associate { it.id to it.name }
        val search = query.search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val tag = query.tag?.trim()?.takeIf { it.isNotEmpty() }
        val matching = listings.asSequence()
            .filter { listing -> query.styleName == null || notetypeNames[listing.notetypeId] == query.styleName }
            .filter { listing -> tag == null || listing.tags.any { it.equals(tag, ignoreCase = true) || it.startsWith("$tag::", ignoreCase = true) } }
            .filter { listing -> search == null || matchesSearch(listing, search) }
            .toList()
        val sorted = when (query.sort) {
            CardSort.Created -> matching.sortedBy { it.noteId }
            CardSort.Modified -> matching.sortedBy { it.modifiedSecs }
            CardSort.Alphabetical -> matching.sortedBy { it.sortField.lowercase() }
            CardSort.Style -> matching.sortedWith(compareBy({ notetypeNames[it.notetypeId].orEmpty() }, { it.noteId }))
        }.let { if (query.descending) it.reversed() else it }
        val rows = sorted.asSequence().drop(query.offset).take(query.limit).map { listing ->
            val styleName = notetypeNames[listing.notetypeId] ?: "unknown"
            CardRow(
                noteId = listing.noteId,
                guid = listing.guid,
                ref = CardRefs.of(listing.guid),
                styleName = styleName,
                styleTitle = BertNoteTypes.byName(styleName)?.let(CardStyles::title) ?: "Card",
                preview = listing.sortField.replace(Regex("\\s+"), " ").trim().take(PREVIEW_LENGTH),
                section = sectionName(pack, listing.deckId),
                tags = listing.tags,
                cardCount = listing.cardCount,
                modifiedSecs = listing.modifiedSecs,
            )
        }.toList()
        return CardPage(
            total = sorted.size,
            rows = rows,
            tags = listings.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            sections = pack.sections(),
        )
    }

    private fun matchesSearch(listing: NoteListing, search: String): Boolean =
        listing.sortField.lowercase().contains(search) || listing.fields.any { it.lowercase().contains(search) }

    private fun sectionName(pack: OpenedPack, deckId: Long): String? {
        if (deckId == pack.rootDeck.id) return null
        val name = pack.collection.deck(deckId)?.name ?: return null
        return name.removePrefix(pack.rootDeck.name + DECK_NAME_SEPARATOR).takeIf { it != name }
    }

    private const val PREVIEW_LENGTH = 80
}
