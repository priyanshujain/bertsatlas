package app.bertopia.data

import app.bertopia.PackSummary
import app.bertopia.anki.collection.AnkiCollection
import app.bertopia.anki.collection.renderCard
import app.bertopia.anki.model.Card
import app.bertopia.anki.model.CardData
import app.bertopia.anki.model.CardScheduling
import app.bertopia.anki.model.DECK_NAME_SEPARATOR
import app.bertopia.anki.model.Deck
import app.bertopia.anki.model.RevlogEntry
import app.bertopia.anki.pkg.ImportMonitor
import app.bertopia.anki.template.CardRenderContext
import app.bertopia.anki.template.CardTemplateRenderer
import app.bertopia.domain.CardMastery
import app.bertopia.domain.fsrs.CardScheduler
import app.bertopia.domain.fsrs.DueCounts
import app.bertopia.domain.fsrs.FriendlyStudySettings
import app.bertopia.domain.fsrs.FriendlyStudySettingsMapping
import app.bertopia.domain.fsrs.Rating
import app.bertopia.domain.fsrs.SchedulerTiming
import app.bertopia.domain.fsrs.SchedulingStates
import app.bertopia.domain.fsrs.StudyPreset
import app.bertopia.domain.fsrs.StudyQueue
import app.bertopia.domain.model.BuiltInPacks
import app.bertopia.domain.model.Catalog
import app.bertopia.domain.model.GEOGRAPHY_SKILLS
import app.bertopia.domain.model.Mastery
import app.bertopia.domain.model.MasteryLevel
import app.bertopia.domain.model.Skill
import app.bertopia.domain.model.packId
import app.bertopia.domain.pack.BertMetaCodec
import app.bertopia.domain.pack.BertMetaReading
import app.bertopia.domain.pack.BertNoteTypes
import app.bertopia.domain.pack.BertTags
import app.bertopia.domain.pack.DeckSelection
import app.bertopia.domain.pack.ImportPlan
import app.bertopia.domain.pack.ImportedPack
import app.bertopia.domain.pack.LevelSetting
import app.bertopia.domain.pack.ManifestReading
import app.bertopia.domain.pack.PackImporter
import app.bertopia.domain.pack.PackLibraryLayout
import app.bertopia.domain.pack.PackManifest
import app.bertopia.domain.pack.PackManifestCodec
import app.bertopia.domain.quiz.PackCardReader
import app.bertopia.domain.quiz.PackQuizCatalog
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

data class ReviewCard(
    val packId: String,
    val cardId: Long,
    val questionHtml: String,
    val answerHtml: String,
    val typedAnswerExpected: String?,
    val nextIntervals: Map<Rating, String>,
    val counts: DueCounts,
    val mediaDirectory: String,
)

class PackStore(val libraryDirectory: String) {
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val openPacks = mutableMapOf<String, OpenPack>()
    private val catalogs = mutableMapOf<String, Catalog>()
    private val workingDirectory = Path(libraryDirectory, "incoming").toString()
    private var observedDataVersions: Map<String, Long> = emptyMap()
    private val levelSettings = mutableMapOf<String, LevelSetting>()

    suspend fun chooseLevel(packId: String, level: LevelSetting) = onStore {
        levelSettings[packId] = level
        catalogs.keys.removeAll { it.startsWith("$packId|") }
    }

    suspend fun forgetLevels() = onStore {
        levelSettings.clear()
        catalogs.clear()
    }

    suspend fun summaries(): List<PackSummary> = onStore {
        installedPackIds().mapNotNull { packId -> runCatching { pack(packId).summary() }.getOrNull() }
    }

    suspend fun mastery(): List<Mastery> = onStore {
        installedPackIds().flatMap { packId -> runCatching { pack(packId).allMastery() }.getOrDefault(emptyList()) }
    }

    suspend fun catalogFor(packId: String, sections: List<String>): Catalog = onStore {
        val key = "$packId|${sections.sorted().joinToString("|")}"
        catalogs.getOrPut(key) { pack(packId).quizCatalog(sections) }
    }

    suspend fun recordQuizAnswer(packId: String, itemId: String, rating: Rating, answerMillis: Long): List<Mastery> = onStore {
        val pack = pack(packId)
        val card = pack.cardForItem(itemId) ?: return@onStore emptyList()
        pack.answer(card, rating, answerMillis)
        pack.masteryForItem(itemId)
    }

    suspend fun nextReviewCard(packId: String, section: String?): ReviewCard? = onStore {
        val pack = pack(packId)
        val queue = pack.studyQueue(section)
        val nowSecs = Clock.System.now().epochSeconds
        val dueLearning = queue.learning.filter { it.scheduling.queue != CardScheduler.QUEUE_LEARN || it.scheduling.due <= nowSecs }
        val card = dueLearning.firstOrNull() ?: queue.reviews.firstOrNull() ?: queue.newCards.firstOrNull() ?: queue.learning.firstOrNull()
            ?: return@onStore null
        pack.reviewCard(card, queue.counts)
    }

    suspend fun dueCounts(packId: String, section: String?): DueCounts = onStore { pack(packId).studyQueue(section).counts }

    suspend fun answerReview(packId: String, cardId: Long, rating: Rating, answerMillis: Long) = onStore {
        val pack = pack(packId)
        val card = pack.collection.card(cardId) ?: return@onStore
        pack.answer(card, rating, answerMillis)
    }

    suspend fun saveStudySettings(packId: String, settings: FriendlyStudySettings) = onStore {
        val pack = pack(packId)
        val config = pack.collection.deckConfig(pack.deck.configId) ?: return@onStore
        pack.collection.saveDeckConfig(FriendlyStudySettingsMapping.applied(config, settings))
        pack.schedulers.clear()
    }

    suspend fun resetAllProgress() = onStore {
        installedPackIds().forEach { packId -> runCatching { pack(packId).forgetProgress() } }
    }

    suspend fun planImport(importer: PackImporter, path: String, monitor: ImportMonitor): ImportPlan = onStore {
        closeAll()
        importer.plan(path, monitor)
    }

    suspend fun runImport(importer: PackImporter, plan: ImportPlan, selections: List<DeckSelection>, monitor: ImportMonitor): List<ImportedPack> = onStore {
        closeAll()
        try {
            importer.import(plan, selections, monitor)
        } finally {
            plan.close()
            catalogs.clear()
        }
    }

    suspend fun discardImport(plan: ImportPlan) = onStore { plan.close() }

    suspend fun <T> edit(block: () -> T): T = onStore {
        closeAll()
        catalogs.clear()
        try {
            block()
        } finally {
            observedDataVersions = currentDataVersions()
        }
    }

    suspend fun takeExternalChanges(): Boolean = onStore {
        val versions = currentDataVersions()
        val changedPacks = versions.filter { (packId, version) -> observedDataVersions[packId] != version }.keys +
            (observedDataVersions.keys - versions.keys)
        val firstObservation = observedDataVersions.isEmpty()
        if (!firstObservation && changedPacks.isNotEmpty()) {
            changedPacks.forEach { packId -> openPacks.remove(packId)?.collection?.close() }
            catalogs.clear()
        }
        observedDataVersions = currentDataVersions()
        !firstObservation && changedPacks.isNotEmpty()
    }

    private fun currentDataVersions(): Map<String, Long> =
        installedPackIds().associateWith { packId -> runCatching { pack(packId).collection.dataVersion() }.getOrDefault(-1L) }

    suspend fun <T> withPacksClosed(block: suspend () -> T): T = onStore {
        closeAll()
        catalogs.clear()
        block()
    }

    fun importer(): PackImporter = PackImporter(libraryDirectory, workingDirectory)

    fun mediaDirectory(packId: String): String = PackLibraryLayout.mediaDirectory(libraryDirectory, packId)

    private suspend fun <T> onStore(block: suspend () -> T): T = withContext(dispatcher) { block() }

    private fun closeAll() {
        openPacks.values.forEach { runCatching { it.collection.close() } }
        openPacks.clear()
    }

    private fun installedPackIds(): List<String> {
        val library = Path(libraryDirectory)
        if (!SystemFileSystem.exists(library)) return emptyList()
        return SystemFileSystem.list(library)
            .filter { SystemFileSystem.exists(Path(it, PackLibraryLayout.COLLECTION_FILE)) }
            .map { it.name }
            .sortedWith(compareBy({ it !in BUILT_IN_ORDER }, { BUILT_IN_ORDER.indexOf(it) }, { it }))
    }

    private fun pack(packId: String): OpenPack = openPacks.getOrPut(packId) {
        val collection = AnkiCollection.open(PackLibraryLayout.collectionPath(libraryDirectory, packId))
        if (collection.isWritable) collection.useWriteAheadLog()
        val (deck, manifest) = collection.decks().filter { it.isTopLevel }.firstNotNullOfOrNull { deck ->
            (PackManifestCodec.readDeckDescription(deck.description) as? ManifestReading.Found)?.let { deck to it.manifest }
        } ?: run {
            collection.close()
            error("Pack $packId has no manifest")
        }
        OpenPack(packId, collection, deck, manifest, mediaDirectory(packId))
    }

    private inner class OpenPack(
        val packId: String,
        val collection: AnkiCollection,
        val deck: Deck,
        val manifest: PackManifest,
        val mediaDirectory: String,
    ) {
        val schedulers = mutableMapOf<Long, CardScheduler>()
        private val geographySkills: List<Skill> = GEOGRAPHY_SKILLS.filter { skill ->
            packId in BuiltInPacks.GEOGRAPHY && skill.packId == packId
        }
        private val countryNoteIds: Map<String, Long> by lazy { readCountryNoteIds() }
        private val noteCountries: Map<Long, String> by lazy { countryNoteIds.entries.associate { it.value to it.key } }

        val isGeography: Boolean get() = geographySkills.isNotEmpty()

        fun deckIds(section: String?): List<Long> {
            val root = if (section == null) deck.name else deck.name + DECK_NAME_SEPARATOR + section
            return collection.decks().filter { it.name == root || it.name.startsWith(root + DECK_NAME_SEPARATOR) }.map { it.id }
        }

        fun sections(): List<String> = collection.subdecksOf(deck).map { it.name.removePrefix(deck.name + DECK_NAME_SEPARATOR) }

        fun timing(nowMillis: Long = Clock.System.now().toEpochMilliseconds()): SchedulerTiming =
            SchedulerTiming.forCollection(collection.creationSecs, collection.configuration(), nowMillis, TimeZone.currentSystemDefault())

        fun presetFor(deckId: Long): StudyPreset {
            val cardDeck = collection.deck(deckId) ?: deck
            val config = collection.deckConfig(cardDeck.configId) ?: collection.deckConfig(deck.configId) ?: collection.deckConfigs().first()
            return StudyPreset.from(config, cardDeck)
        }

        fun schedulerFor(deckId: Long): CardScheduler {
            val configId = (collection.deck(deckId) ?: deck).configId
            return schedulers.getOrPut(configId) { CardScheduler(presetFor(deckId)) }
        }

        val level: LevelSetting get() = levelSettings[packId] ?: LevelSetting.All

        fun noteIdsOutsideLevel(): Set<Long> =
            if (level == LevelSetting.All) emptySet() else collection.notes().filterNot { level.admits(it.tags) }.mapTo(mutableSetOf()) { it.id }

        fun studyQueue(section: String?): StudyQueue {
            val deckIds = deckIds(section)
            val timing = timing()
            val rootDeckId = deckIds.firstOrNull { collection.deck(it)?.name == (if (section == null) deck.name else deck.name + DECK_NAME_SEPARATOR + section) } ?: deck.id
            val outsideLevel = noteIdsOutsideLevel()
            return StudyQueue.build(
                cards = collection.cardsInDecks(deckIds).filter { it.noteId !in outsideLevel },
                preset = presetFor(rootDeckId),
                studiedToday = collection.studiedToday(rootDeckId, timing.today),
                timing = timing,
            )
        }

        fun answer(card: Card, rating: Rating, answerMillis: Long) {
            val timing = timing()
            val record = schedulerFor(card.deckId).answer(card, rating, timing, answerMillis, collection.revlogOfCard(card.id))
            collection.recordReview(record)
        }

        fun nextStates(card: Card): SchedulingStates =
            schedulerFor(card.deckId).nextStates(card, timing(), collection.revlogOfCard(card.id))

        fun reviewCard(card: Card, counts: DueCounts): ReviewCard? {
            val rendered = collection.renderCard(card) ?: return null
            val states = nextStates(card)
            return ReviewCard(
                packId = packId,
                cardId = card.id,
                questionHtml = rendered.question,
                answerHtml = rendered.answer,
                typedAnswerExpected = typedAnswerExpected(card, rendered.question),
                nextIntervals = mapOf(
                    Rating.Again to IntervalWords.describe(states.again.intervalKind),
                    Rating.Hard to IntervalWords.describe(states.hard.intervalKind),
                    Rating.Good to IntervalWords.describe(states.good.intervalKind),
                    Rating.Easy to IntervalWords.describe(states.easy.intervalKind),
                ),
                counts = counts,
                mediaDirectory = mediaDirectory,
            )
        }

        private fun typedAnswerExpected(card: Card, questionHtml: String): String? {
            val marker = TYPE_MARKER.find(questionHtml)?.groupValues?.get(1) ?: return null
            val note = collection.note(card.noteId) ?: return null
            val notetype = collection.notetype(note.notetypeId) ?: return null
            val fields = notetype.fieldNames.zip(note.fields).toMap()
            val parts = marker.split(':')
            val fieldName = parts.last()
            val raw = fields[fieldName] ?: return null
            return if (parts.contains("cloze")) {
                CardTemplateRenderer.renderQuestion(
                    "{{cloze-only:$fieldName}}",
                    CardRenderContext(fields = fields, clozeOrdinal = card.ordinal + 1),
                ).let(PackCardReader::plainText)
            } else {
                PackCardReader.plainText(raw)
            }
        }

        fun summary(): PackSummary {
            val notes = collection.notes()
            val retiredNoteIds = notes.filter { BertTags.RETIRED in it.tags }.map { it.id }.toSet()
            val tagLevels = notes.filter { it.id !in retiredNoteIds }.flatMapTo(linkedSetOf()) { BertTags.levels(it.tags) }
            val outsideLevel = noteIdsOutsideLevel()
            val cards = collection.cardsInDecks(deckIds(null)).filter { it.noteId !in retiredNoteIds && it.noteId !in outsideLevel }
            val revlogByCard = collection.revlog().groupBy { it.cardId }
            val levels = cards.map { CardMastery.level(it, revlogByCard[it.id].orEmpty()) }
            val config = collection.deckConfig(deck.configId)
            return PackSummary(
                packId = packId,
                title = manifest.title,
                subtitle = manifest.subtitle.orEmpty(),
                accentColor = manifest.accentColor ?: DEFAULT_ACCENT,
                cardCount = cards.size,
                category = manifest.category.orEmpty(),
                subcategory = manifest.subcategory.orEmpty(),
                official = manifest.official,
                sections = sections(),
                dueCount = studyQueue(null).counts.total,
                masteredCount = levels.count { it == MasteryLevel.Mastered },
                learningCount = levels.count { it == MasteryLevel.Learning || it == MasteryLevel.Familiar },
                familiarCount = levels.count { it == MasteryLevel.Familiar },
                studySettings = config?.let(FriendlyStudySettingsMapping::read),
                coverMedia = manifest.coverMedia,
                levels = LevelSetting.entries.filter { it in tagLevels },
                level = level,
            )
        }

        fun allMastery(): List<Mastery> {
            val timing = timing()
            val todayEpochDay = epochDay(timing.nowMillis)
            val cardsById = collection.cardsInDecks(deckIds(null)).associateBy { it.id }
            return collection.revlog().groupBy { it.cardId }.flatMap { (cardId, entries) ->
                val card = cardsById[cardId] ?: return@flatMap emptyList()
                masteryOf(card, entries, timing.today, todayEpochDay)
            }
        }

        fun masteryForItem(itemId: String): List<Mastery> {
            val card = cardForItem(itemId) ?: return emptyList()
            val timing = timing()
            return masteryOf(card, collection.revlogOfCard(card.id), timing.today, epochDay(timing.nowMillis))
        }

        private fun masteryOf(card: Card, revlog: List<RevlogEntry>, collectionToday: Int, todayEpochDay: Int): List<Mastery> {
            val (itemId, skills) = if (isGeography) {
                (noteCountries[card.noteId] ?: return emptyList()) to geographySkills
            } else {
                val guid = collection.note(card.noteId)?.guid ?: return emptyList()
                "$guid:${card.ordinal}" to listOf(Skill.CardRecall)
            }
            return CardMastery.forCard(itemId, skills, card, revlog, collectionToday, todayEpochDay, ::epochDay)
        }

        fun cardForItem(itemId: String): Card? {
            if (isGeography) {
                val noteId = countryNoteIds[itemId] ?: return null
                return collection.cardsOfNote(noteId).firstOrNull()
            }
            val guid = itemId.substringBeforeLast(':')
            val ordinal = itemId.substringAfterLast(':').toIntOrNull() ?: return null
            val note = collection.noteByGuid(guid) ?: return null
            return collection.cardsOfNote(note.id).firstOrNull { it.ordinal == ordinal }
        }

        fun quizCatalog(sections: List<String>): Catalog {
            val deckIds = if (sections.isEmpty()) deckIds(null) else sections.flatMap { deckIds(it) }.distinct()
            val decksById = collection.decks().associateBy { it.id }
            val notesById = collection.notes().associateBy { it.id }
            val notetypesById = collection.notetypes().associateBy { it.id }
            val outsideLevel = noteIdsOutsideLevel()
            val playable = collection.cardsInDecks(deckIds).filter { it.scheduling.queue != CardScheduler.QUEUE_SUSPENDED && it.noteId !in outsideLevel }
            val runCards = cardsForRun(playable)
            val runCardIds = runCards.mapTo(mutableSetOf()) { it.id }
            val distractorCards = if (runCards.size == playable.size) emptyList() else playable.filter { it.id !in runCardIds }.shuffled(Random(timing().today)).take(DISTRACTOR_CARD_LIMIT)
            fun read(cards: List<Card>) = cards.mapNotNull { card ->
                val note = notesById[card.noteId] ?: return@mapNotNull null
                val notetype = notetypesById[note.notetypeId] ?: return@mapNotNull null
                runCatching { PackCardReader.read(packId, notetype, note, card.ordinal, decksById[card.deckId]?.name ?: deck.name) }.getOrNull()
            }
            return PackQuizCatalog.build(manifest.version, read(runCards), read(distractorCards))
        }

        private fun cardsForRun(playable: List<Card>): List<Card> {
            if (playable.size <= RUN_CARD_LIMIT) return playable
            val today = timing().today
            val nowSecs = timing().nowSecs
            val due = playable.filter { card ->
                when (card.scheduling.queue) {
                    CardScheduler.QUEUE_LEARN -> card.scheduling.due <= nowSecs
                    CardScheduler.QUEUE_REVIEW, CardScheduler.QUEUE_DAY_LEARN -> card.scheduling.due <= today
                    else -> false
                }
            }
            val fresh = playable.filter { it.scheduling.queue == CardScheduler.QUEUE_NEW }.sortedBy { it.scheduling.due }
            val rest = (playable - due.toSet() - fresh.toSet()).shuffled(Random(today))
            return (due + fresh.take(RUN_CARD_LIMIT / 2) + rest).distinctBy { it.id }.take(RUN_CARD_LIMIT)
        }

        fun forgetProgress() {
            collection.transaction {
                collection.cardsInDecks(deckIds(null)).forEachIndexed { index, card ->
                    val position = CardData.decode(card.scheduling.data).originalPosition
                        ?: if (card.scheduling.type == CardScheduler.TYPE_NEW) card.scheduling.due else index.toLong()
                    collection.saveCard(card.copy(scheduling = CardScheduling(due = position)))
                }
                collection.deleteRevlog()
            }
        }

        private fun readCountryNoteIds(): Map<String, Long> {
            val notetypeName = when (packId) {
                BuiltInPacks.FLAGS -> BertNoteTypes.Flag.name
                BuiltInPacks.CAPITALS -> BertNoteTypes.Capital.name
                else -> return emptyMap()
            }
            val notetype = collection.notetypeByName(notetypeName) ?: return emptyMap()
            val metaIndex = notetype.fieldNames.indexOf(BertNoteTypes.BERT_META_FIELD)
            return collection.notes().filter { it.notetypeId == notetype.id && BertTags.RETIRED !in it.tags }.mapNotNull { note ->
                val field = note.fields.getOrNull(metaIndex) ?: return@mapNotNull null
                val countryId = when (packId) {
                    BuiltInPacks.FLAGS -> (BertMetaCodec.readFlag(field) as? BertMetaReading.Valid)?.meta?.countryId
                    else -> (BertMetaCodec.readCapital(field) as? BertMetaReading.Valid)?.meta?.countryId
                }
                countryId?.let { it to note.id }
            }.toMap()
        }
    }

    private companion object {
        val BUILT_IN_ORDER = listOf(BuiltInPacks.FLAGS, BuiltInPacks.CAPITALS)
        const val DEFAULT_ACCENT = "#27635B"
        const val RUN_CARD_LIMIT = 600
        const val DISTRACTOR_CARD_LIMIT = 2_000
        val TYPE_MARKER = Regex("\\[\\[type:([^\\]]+)]]")

        fun epochDay(millis: Long): Int =
            Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toInt()
    }
}
