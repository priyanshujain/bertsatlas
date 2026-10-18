package app.bertopia.domain.editor

import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.anki.model.Card
import app.bertopia.anki.model.Deck
import app.bertopia.anki.model.Note

data class NoteSnapshot(val note: Note, val cards: List<Card>)

data class PackChange(
    val notesBefore: Map<Long, NoteSnapshot?>,
    val notesAfter: Map<Long, NoteSnapshot?>,
    val decksBefore: Map<Long, Deck?>,
    val decksAfter: Map<Long, Deck?>,
) {
    fun followedBy(next: PackChange): PackChange = PackChange(
        notesBefore = next.notesBefore + notesBefore,
        notesAfter = notesAfter + next.notesAfter,
        decksBefore = next.decksBefore + decksBefore,
        decksAfter = decksAfter + next.decksAfter,
    )

    val isEmpty: Boolean get() = notesBefore.isEmpty() && notesAfter.isEmpty() && decksBefore.isEmpty() && decksAfter.isEmpty()
}

data class EditEntry(val packId: String, val label: String, val coalesceKey: String?, val change: PackChange)

class EditHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val undoStack = ArrayDeque<EditEntry>()
    private val redoStack = ArrayDeque<EditEntry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label

    fun record(entry: EditEntry) {
        if (entry.change.isEmpty) return
        redoStack.clear()
        val top = undoStack.lastOrNull()
        if (top != null && entry.coalesceKey != null && top.packId == entry.packId && top.coalesceKey == entry.coalesceKey) {
            undoStack[undoStack.lastIndex] = top.copy(change = top.change.followedBy(entry.change))
            return
        }
        undoStack.addLast(entry)
        while (undoStack.size > limit) undoStack.removeFirst()
    }

    fun breakCoalescing() {
        val top = undoStack.lastOrNull() ?: return
        undoStack[undoStack.lastIndex] = top.copy(coalesceKey = null)
    }

    internal fun takeUndo(): EditEntry? = undoStack.removeLastOrNull()?.also { redoStack.addLast(it) }

    internal fun takeRedo(): EditEntry? = redoStack.removeLastOrNull()?.also { undoStack.addLast(it) }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}

internal class ChangeRecorder(private val collection: AnkiCollection) {
    private val notesBefore = linkedMapOf<Long, NoteSnapshot?>()
    private val decksBefore = linkedMapOf<Long, Deck?>()
    private val initialDeckIds = collection.decks().map { it.id }.toSet()

    fun touchNote(noteId: Long) {
        if (noteId !in notesBefore) notesBefore[noteId] = snapshot(noteId)
    }

    fun touchNewNote(noteId: Long) {
        if (noteId !in notesBefore) notesBefore[noteId] = null
    }

    fun touchDeck(deckId: Long) {
        if (deckId !in decksBefore) decksBefore[deckId] = collection.deck(deckId)
    }

    fun touchNewDeck(deckId: Long) {
        if (deckId !in decksBefore) decksBefore[deckId] = null
    }

    fun finish(): PackChange {
        collection.decks().map { it.id }.filter { it !in initialDeckIds }.forEach(::touchNewDeck)
        return buildChange()
    }

    private fun buildChange(): PackChange = PackChange(
        notesBefore = notesBefore.toMap(),
        notesAfter = notesBefore.keys.associateWith { snapshot(it) },
        decksBefore = decksBefore.toMap(),
        decksAfter = decksBefore.keys.associateWith { collection.deck(it) },
    )

    private fun snapshot(noteId: Long): NoteSnapshot? = collection.note(noteId)?.let { NoteSnapshot(it, collection.cardsOfNote(noteId)) }
}

internal object PackChangeApplier {
    fun restore(collection: AnkiCollection, notes: Map<Long, NoteSnapshot?>, decks: Map<Long, Deck?>) {
        decks.filterValues { it != null }.forEach { (_, deck) -> collection.saveDeck(deck!!) }
        notes.forEach { (noteId, snapshot) ->
            if (snapshot == null) {
                if (collection.note(noteId) != null) collection.deleteNote(noteId)
            } else {
                collection.saveNote(snapshot.note)
                val keep = snapshot.cards.map { it.id }.toSet()
                collection.cardsOfNote(noteId).filter { it.id !in keep }.forEach { collection.deleteCard(it.id) }
                snapshot.cards.forEach { collection.saveCard(it) }
            }
        }
        decks.filterValues { it == null }.keys.forEach { collection.deleteDeck(it) }
    }
}
