package app.bertsatlas.data

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.bertsatlas.domain.ActiveRunRepository
import app.bertsatlas.domain.ProgressReader
import app.bertsatlas.domain.ProgressWriter
import app.bertsatlas.domain.model.ActiveRunSnapshot
import app.bertsatlas.domain.model.Attempt
import app.bertsatlas.domain.model.Mastery
import app.bertsatlas.domain.model.MasteryLevel
import app.bertsatlas.domain.model.MasteryOutcome
import app.bertsatlas.domain.model.PassportStamp
import app.bertsatlas.domain.model.PassportStampKind
import app.bertsatlas.domain.model.ProgressSummary
import app.bertsatlas.domain.model.QuizDuration
import app.bertsatlas.domain.model.QuizTopic
import app.bertsatlas.domain.model.RunResult
import app.bertsatlas.domain.model.Skill
import app.bertsatlas.domain.model.Streak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val questionId: String,
    val countryIdsJson: String,
    val skill: String,
    val correct: Boolean,
    val hinted: Boolean,
    val overtime: Boolean,
    val submittedAnswer: String,
    val correctAnswer: String,
    val responseMillis: Long,
    val pointsAwarded: Int,
    val answeredAtEpochMillis: Long,
    val localEpochDay: Int,
    val hintType: String?,
    val hintPointsDeducted: Int,
    val questionWindowMillis: Long,
    val questionRemainingMillis: Long,
) {
    @Ignore
    @Suppress("UNUSED_PARAMETER")
    constructor(
        id: String,
        runId: String,
        questionOrdinal: Int,
        questionId: String,
        countryId: String,
        skill: String,
        answeredAtEpochMillis: Long,
        localDate: String,
        timeZoneId: String,
        answer: String,
        correctAnswer: String,
        correct: Boolean,
        overtime: Boolean,
        hintType: String?,
        hintDebit: Int,
        responseMillis: Long,
        responseWindowMillis: Long,
        awardedPoints: Int,
        scoreAfterAnswer: Int,
    ) : this(
        id = id,
        runId = runId,
        questionId = questionId,
        countryIdsJson = Json.encodeToString(SetSerializer(String.serializer()), setOf(countryId)),
        skill = skill,
        correct = correct,
        hinted = hintType != null,
        overtime = overtime,
        submittedAnswer = answer,
        correctAnswer = correctAnswer,
        responseMillis = responseMillis,
        pointsAwarded = awardedPoints,
        answeredAtEpochMillis = answeredAtEpochMillis,
        localEpochDay = localDate.toIntOrNull() ?: 0,
        hintType = hintType,
        hintPointsDeducted = hintDebit,
        questionWindowMillis = responseWindowMillis,
        questionRemainingMillis = (responseWindowMillis - responseMillis).coerceAtLeast(0),
    )
}

@Entity(tableName = "mastery", primaryKeys = ["countryId", "skill"])
data class MasteryEntity(
    val countryId: String,
    val skill: String,
    val level: String,
    val attempts: Int,
    val correctAnswers: Int,
    val unhintedCorrectAnswers: Int,
    val unhintedCorrectRunIdsJson: String,
    val unhintedCorrectEpochDaysJson: String,
    val recentOutcomesJson: String,
    val nextReviewEpochDay: Int?,
) {
    @Ignore
    @Suppress("UNUSED_PARAMETER")
    constructor(
        countryId: String,
        skill: String,
        status: String,
        attempts: Int,
        unhintedCorrect: Int,
        distinctCorrectRuns: Int,
        distinctCorrectDays: Int,
        nextEligibleDate: String,
        recentOutcomes: String,
    ) : this(
        countryId = countryId,
        skill = skill,
        level = status,
        attempts = attempts,
        correctAnswers = recentOutcomes.decodeMastery()?.correctAnswers ?: unhintedCorrect,
        unhintedCorrectAnswers = unhintedCorrect,
        unhintedCorrectRunIdsJson = recentOutcomes.decodeMastery()?.unhintedCorrectRunIds?.encodeStringSet() ?: emptySet<String>().encodeStringSet(),
        unhintedCorrectEpochDaysJson = recentOutcomes.decodeMastery()?.unhintedCorrectEpochDays?.encodeIntSet() ?: emptySet<Int>().encodeIntSet(),
        recentOutcomesJson = recentOutcomes.decodeMastery()?.recentOutcomes?.let {
            Json.encodeToString(ListSerializer(MasteryOutcome.serializer()), it)
        } ?: "[]",
        nextReviewEpochDay = nextEligibleDate.toIntOrNull(),
    )

    @get:Ignore val status: String get() = level
    @get:Ignore val unhintedCorrect: Int get() = unhintedCorrectAnswers
    @get:Ignore val distinctCorrectRuns: Int get() = unhintedCorrectRunIdsJson.decodeStringSet().size
    @get:Ignore val distinctCorrectDays: Int get() = unhintedCorrectEpochDaysJson.decodeIntSet().size
    @get:Ignore val nextEligibleDate: String get() = nextReviewEpochDay?.toString().orEmpty()
    @get:Ignore val recentOutcomes: String
        get() = toDomain(Json)?.let { Json.encodeToString(Mastery.serializer(), it) }.orEmpty()
}

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,
    val duration: String?,
    val topic: String?,
    val status: String,
    val score: Int,
    val correctAnswers: Int,
    val answeredQuestions: Int,
    val averageResponseMillis: Long,
    val bestCorrectStreak: Int,
    val improvedMasteryItems: Int,
    val revisitCountryIdsJson: String,
    val completedAtEpochMillis: Long?,
) {
    @Ignore
    @Suppress("UNUSED_PARAMETER")
    constructor(
        id: String,
        duration: String,
        topic: String,
        status: String,
        score: Int,
        correctAnswers: Int,
        answeredQuestions: Int,
        totalResponseMillis: Long,
        bestCorrectStreak: Int,
        completedAtEpochMillis: Long?,
        completedLocalDate: String?,
        completedTimeZoneId: String?,
    ) : this(
        id = id,
        duration = duration,
        topic = topic,
        status = status,
        score = score,
        correctAnswers = correctAnswers,
        answeredQuestions = answeredQuestions,
        averageResponseMillis = if (answeredQuestions == 0) 0 else totalResponseMillis / answeredQuestions,
        bestCorrectStreak = bestCorrectStreak,
        improvedMasteryItems = 0,
        revisitCountryIdsJson = "[]",
        completedAtEpochMillis = completedAtEpochMillis,
    )

    @get:Ignore val totalResponseMillis: Long get() = averageResponseMillis * answeredQuestions
}

@Entity(tableName = "personal_bests", primaryKeys = ["duration", "topic"])
data class PersonalBestEntity(
    val duration: String,
    val topic: String,
    val score: Int,
    val runId: String,
)

@Entity(tableName = "streak")
data class StreakEntity(
    @PrimaryKey val singletonId: Int = 1,
    val currentDays: Int,
    val bestDays: Int,
    val lastCompletedEpochDay: Int?,
) {
    @Ignore
    @Suppress("UNUSED_PARAMETER")
    constructor(
        singletonId: Int = 1,
        currentDays: Int,
        bestDays: Int,
        lastCompletionDate: String?,
        lastCompletionTimeZoneId: String?,
    ) : this(singletonId, currentDays, bestDays, lastCompletionDate?.toIntOrNull())

    @get:Ignore val lastCompletionDate: String? get() = lastCompletedEpochDay?.toString()
}

@Entity(tableName = "stamps")
data class StampEntity(
    @PrimaryKey val id: String,
    val earnedAtEpochMillis: Long,
)

@Entity(tableName = "active_run")
data class ActiveRunSnapshotEntity(
    @PrimaryKey val singletonId: Int = 1,
    val runId: String,
    val schemaVersion: Int,
    val catalogVersion: String,
    val snapshotJson: String,
) {
    @Ignore
    constructor(
        singletonId: Int = 1,
        schemaVersion: Int,
        catalogVersion: String,
        snapshotJson: String,
    ) : this(
        singletonId = singletonId,
        runId = snapshotJson.decodeActiveRunId(),
        schemaVersion = schemaVersion,
        catalogVersion = catalogVersion,
        snapshotJson = snapshotJson,
    )
}

@Dao
abstract class RunWriteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAttempt(attempt: AttemptEntity)

    @Upsert
    protected abstract suspend fun upsertMastery(mastery: List<MasteryEntity>)

    @Upsert
    abstract suspend fun saveActiveRun(snapshot: ActiveRunSnapshotEntity)

    @Query("DELETE FROM active_run WHERE runId = :runId")
    abstract suspend fun clearActiveRun(runId: String)

    @Query("DELETE FROM active_run")
    abstract suspend fun clearActiveRun()

    @Query("DELETE FROM attempts")
    protected abstract suspend fun clearAttempts()

    @Query("DELETE FROM mastery")
    protected abstract suspend fun clearMastery()

    @Query("DELETE FROM runs")
    protected abstract suspend fun clearRuns()

    @Query("DELETE FROM personal_bests")
    protected abstract suspend fun clearPersonalBests()

    @Query("DELETE FROM streak")
    protected abstract suspend fun clearStreak()

    @Query("DELETE FROM stamps")
    protected abstract suspend fun clearStamps()

    @Upsert
    protected abstract suspend fun upsertRun(run: RunEntity)

    @Query("SELECT * FROM personal_bests WHERE duration = :duration AND topic = :topic")
    protected abstract suspend fun personalBest(duration: String, topic: String): PersonalBestEntity?

    @Upsert
    protected abstract suspend fun upsertPersonalBest(personalBest: PersonalBestEntity)

    @Upsert
    abstract suspend fun saveStreak(streak: StreakEntity)

    @Upsert
    protected abstract suspend fun upsertStamps(stamps: List<StampEntity>)

    @Query("SELECT * FROM attempts WHERE runId = :runId ORDER BY answeredAtEpochMillis")
    protected abstract suspend fun attemptsForRun(runId: String): List<AttemptEntity>

    @Transaction
    open suspend fun recordAcceptedAnswer(
        attempt: AttemptEntity,
        updatedMastery: List<MasteryEntity>,
        snapshot: ActiveRunSnapshotEntity,
        streak: StreakEntity,
    ) {
        require(updatedMastery.isNotEmpty()) { "An accepted answer must update at least one mastery item" }
        val countryIds = updatedMastery.mapTo(linkedSetOf()) { it.countryId }
        require(countryIds.size == updatedMastery.size) { "An answer cannot update the same mastery item twice" }
        require(updatedMastery.all { it.skill == attempt.skill }) {
            "Mastery updates must use the attempt's skill"
        }
        require(snapshot.runId == attempt.runId) { "Attempt and snapshot must belong to the same run" }
        insertAttempt(attempt.copy(countryIdsJson = countryIds.encodeStringSet()))
        upsertMastery(updatedMastery)
        saveStreak(streak)
        saveActiveRun(snapshot)
    }

    @Transaction
    open suspend fun completeRun(
        run: RunEntity,
        streak: StreakEntity,
        stamps: List<StampEntity>,
    ) {
        require(run.status == "Completed")
        val duration = requireNotNull(run.duration)
        val topic = requireNotNull(run.topic)
        upsertRun(run.withDerivedHistory())
        val existingBest = personalBest(duration, topic)
        if (existingBest == null || run.score > existingBest.score) {
            upsertPersonalBest(PersonalBestEntity(duration, topic, run.score, run.id))
        }
        saveStreak(streak)
        if (stamps.isNotEmpty()) upsertStamps(stamps)
        clearActiveRun(run.id)
    }

    @Transaction
    open suspend fun completeRun(
        run: RunEntity,
        personalBest: PersonalBestEntity?,
        streak: StreakEntity,
        stamps: List<StampEntity>,
    ) {
        require(run.status == "Completed")
        upsertRun(run.withDerivedHistory())
        personalBest?.let {
            val existingBest = personalBest(it.duration, it.topic)
            if (existingBest == null || it.score > existingBest.score) upsertPersonalBest(it)
        }
        saveStreak(streak)
        if (stamps.isNotEmpty()) upsertStamps(stamps)
        clearActiveRun(run.id)
    }

    private suspend fun RunEntity.withDerivedHistory(): RunEntity {
        val attempts = attemptsForRun(id)
        if (attempts.isEmpty()) return this
        val revisitCountryIds = attempts
            .filter { !it.correct || it.hinted }
            .flatMap { it.countryIdsJson.decodeStringSet() }
            .distinct()
        val touchedMasteryItems = attempts
            .flatMap { attempt -> attempt.countryIdsJson.decodeStringSet().map { it to attempt.skill } }
            .distinct()
            .size
        return copy(
            improvedMasteryItems = maxOf(improvedMasteryItems, touchedMasteryItems),
            revisitCountryIdsJson = Json.encodeToString(ListSerializer(String.serializer()), revisitCountryIds),
        )
    }

    @Transaction
    open suspend fun abandonRun(runId: String, streak: StreakEntity? = null) {
        upsertRun(
            RunEntity(
                id = runId,
                duration = null,
                topic = null,
                status = "Abandoned",
                score = 0,
                correctAnswers = 0,
                answeredQuestions = 0,
                averageResponseMillis = 0,
                bestCorrectStreak = 0,
                improvedMasteryItems = 0,
                revisitCountryIdsJson = "[]",
                completedAtEpochMillis = null,
            ),
        )
        if (streak != null) saveStreak(streak)
        clearActiveRun(runId)
    }

    @Transaction
    open suspend fun abandonRun(run: RunEntity, streak: StreakEntity? = null) {
        upsertRun(run.copy(status = "Abandoned", completedAtEpochMillis = null))
        if (streak != null) saveStreak(streak)
        clearActiveRun(run.id)
    }

    @Transaction
    open suspend fun resetProgress() {
        clearAttempts()
        clearMastery()
        clearRuns()
        clearPersonalBests()
        clearStreak()
        clearStamps()
        clearActiveRun()
    }
}

@Dao
interface ProgressReadDao {
    @Query("SELECT * FROM active_run WHERE singletonId = 1")
    suspend fun activeRun(): ActiveRunSnapshotEntity?

    @Query("SELECT * FROM mastery ORDER BY countryId, skill")
    fun mastery(): Flow<List<MasteryEntity>>

    @Query("SELECT * FROM mastery ORDER BY countryId, skill")
    suspend fun masteryNow(): List<MasteryEntity>

    @Query("SELECT * FROM mastery WHERE countryId = :countryId AND skill = :skill")
    suspend fun mastery(countryId: String, skill: String): MasteryEntity?

    @Query("SELECT * FROM runs WHERE status = 'Completed' ORDER BY completedAtEpochMillis DESC")
    fun completedRuns(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE status = 'Completed' ORDER BY completedAtEpochMillis DESC")
    suspend fun completedRunsNow(): List<RunEntity>

    @Query("SELECT * FROM attempts ORDER BY answeredAtEpochMillis DESC")
    fun attempts(): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts ORDER BY answeredAtEpochMillis DESC")
    suspend fun attemptsNow(): List<AttemptEntity>

    @Query("SELECT * FROM attempts WHERE runId = :runId ORDER BY answeredAtEpochMillis")
    suspend fun attemptsForRun(runId: String): List<AttemptEntity>

    @Query("SELECT * FROM personal_bests")
    fun personalBests(): Flow<List<PersonalBestEntity>>

    @Query("SELECT * FROM personal_bests")
    suspend fun personalBestsNow(): List<PersonalBestEntity>

    @Query("SELECT * FROM streak WHERE singletonId = 1")
    fun streak(): Flow<StreakEntity?>

    @Query("SELECT * FROM streak WHERE singletonId = 1")
    suspend fun streakNow(): StreakEntity?

    @Query("SELECT * FROM stamps ORDER BY earnedAtEpochMillis")
    fun stamps(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps ORDER BY earnedAtEpochMillis")
    suspend fun stampsNow(): List<StampEntity>
}

@Database(
    entities = [
        AttemptEntity::class,
        MasteryEntity::class,
        RunEntity::class,
        PersonalBestEntity::class,
        StreakEntity::class,
        StampEntity::class,
        ActiveRunSnapshotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(BertsAtlasDatabaseConstructor::class)
abstract class BertsAtlasDatabase : RoomDatabase() {
    abstract fun runWriter(): RunWriteDao
    abstract fun progressReader(): ProgressReadDao
}

class RoomProgressRepository(
    database: BertsAtlasDatabase,
    private val json: Json = Json,
) : ProgressReader, ProgressWriter, ActiveRunRepository {
    private val writer = database.runWriter()
    private val reader = database.progressReader()

    override suspend fun mastery(): List<Mastery> = reader.masteryNow().mapNotNull { it.toDomain(json) }

    override fun observeProgress(): Flow<ProgressSummary> = combine(
        reader.attempts(),
        reader.mastery(),
        reader.streak(),
        reader.stamps(),
    ) { attempts, mastery, streak, stamps ->
        ProgressSummary(
            totalAnswered = attempts.size,
            totalCorrect = attempts.count { it.correct },
            mastery = mastery.mapNotNull { it.toDomain(json) },
            streak = streak?.toDomain() ?: Streak(),
            stamps = stamps.mapNotNull(StampEntity::toDomain),
        )
    }

    override suspend fun recordAcceptedAnswer(
        attempt: Attempt,
        updatedMastery: List<Mastery>,
        activeRunSnapshot: ActiveRunSnapshot,
        streak: Streak,
    ) {
        require(attempt.countryIds.isNotEmpty())
        writer.recordAcceptedAnswer(
            attempt = attempt.toEntity(json),
            updatedMastery = updatedMastery
                .filter { it.countryId in attempt.countryIds && it.skill == attempt.skill }
                .map { it.toEntity(json) },
            snapshot = activeRunSnapshot.toEntity(json),
            streak = streak.toEntity(),
        )
    }

    override suspend fun recordCompletedRun(
        result: RunResult,
        streak: Streak,
        newlyEarnedStamps: List<PassportStamp>,
    ) {
        writer.completeRun(result.toEntity(json), streak.toEntity(), newlyEarnedStamps.map(PassportStamp::toEntity))
    }

    override suspend fun recordAbandonedRun(runId: String, streak: Streak?) =
        writer.abandonRun(runId, streak?.toEntity())

    override suspend fun resetProgress() = writer.resetProgress()

    override suspend fun loadActiveRun(): ActiveRunSnapshot? {
        val entity = reader.activeRun() ?: return null
        return try {
            json.decodeFromString<ActiveRunSnapshot>(entity.snapshotJson).takeIf {
                it.schemaVersion == entity.schemaVersion &&
                    it.catalogVersion == entity.catalogVersion &&
                    it.state.runId == entity.runId
            } ?: error("Active-run snapshot metadata does not match its payload")
        } catch (_: Exception) {
            writer.clearActiveRun(entity.runId)
            null
        }
    }

    override suspend fun saveActiveRun(snapshot: ActiveRunSnapshot) = writer.saveActiveRun(snapshot.toEntity(json))

    override suspend fun clearActiveRun(runId: String) = writer.clearActiveRun(runId)
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object BertsAtlasDatabaseConstructor : RoomDatabaseConstructor<BertsAtlasDatabase> {
    override fun initialize(): BertsAtlasDatabase
}

fun RoomDatabase.Builder<BertsAtlasDatabase>.buildBertsAtlasDatabase(): BertsAtlasDatabase =
    addMigrations(Migration1To2)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()

private object Migration1To2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """CREATE TABLE IF NOT EXISTS `attempts_new` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `questionId` TEXT NOT NULL, `countryIdsJson` TEXT NOT NULL, `skill` TEXT NOT NULL, `correct` INTEGER NOT NULL, `hinted` INTEGER NOT NULL, `overtime` INTEGER NOT NULL, `submittedAnswer` TEXT NOT NULL, `correctAnswer` TEXT NOT NULL, `responseMillis` INTEGER NOT NULL, `pointsAwarded` INTEGER NOT NULL, `answeredAtEpochMillis` INTEGER NOT NULL, `localEpochDay` INTEGER NOT NULL, `hintType` TEXT, `hintPointsDeducted` INTEGER NOT NULL, `questionWindowMillis` INTEGER NOT NULL, `questionRemainingMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
        )
        connection.execSQL(
            """INSERT INTO `attempts_new` (`id`, `runId`, `questionId`, `countryIdsJson`, `skill`, `correct`, `hinted`, `overtime`, `submittedAnswer`, `correctAnswer`, `responseMillis`, `pointsAwarded`, `answeredAtEpochMillis`, `localEpochDay`, `hintType`, `hintPointsDeducted`, `questionWindowMillis`, `questionRemainingMillis`) SELECT `id`, `runId`, `questionId`, `countryIdsJson`, `skill`, `correct`, `hinted`, `overtime`, `submittedAnswer`, `correctAnswer`, `responseMillis`, `pointsAwarded`, `answeredAtEpochMillis`, `localEpochDay`, NULL, 0, 0, 0 FROM `attempts`""",
        )
        connection.execSQL("DROP TABLE `attempts`")
        connection.execSQL("ALTER TABLE `attempts_new` RENAME TO `attempts`")
    }
}

private fun Attempt.toEntity(json: Json) = AttemptEntity(
    id = id,
    runId = runId,
    questionId = questionId,
    countryIdsJson = json.encodeToString(SetSerializer(String.serializer()), countryIds.toSet()),
    skill = skill.name,
    correct = correct,
    hinted = hinted,
    overtime = overtime,
    submittedAnswer = submittedAnswer,
    correctAnswer = correctAnswer,
    responseMillis = responseMillis,
    pointsAwarded = pointsAwarded,
    answeredAtEpochMillis = answeredAtEpochMillis,
    localEpochDay = localEpochDay,
    hintType = hintType?.name,
    hintPointsDeducted = hintPointsDeducted,
    questionWindowMillis = questionWindowMillis,
    questionRemainingMillis = questionRemainingMillis,
)

private fun Mastery.toEntity(json: Json) = MasteryEntity(
    countryId = countryId,
    skill = skill.name,
    level = level.name,
    attempts = attempts,
    correctAnswers = correctAnswers,
    unhintedCorrectAnswers = unhintedCorrectAnswers,
    unhintedCorrectRunIdsJson = json.encodeToString(SetSerializer(String.serializer()), unhintedCorrectRunIds),
    unhintedCorrectEpochDaysJson = json.encodeToString(SetSerializer(Int.serializer()), unhintedCorrectEpochDays),
    recentOutcomesJson = json.encodeToString(ListSerializer(MasteryOutcome.serializer()), recentOutcomes),
    nextReviewEpochDay = nextReviewEpochDay,
)

private fun MasteryEntity.toDomain(json: Json): Mastery? {
    val parsedSkill = Skill.entries.firstOrNull { it.name == skill } ?: return null
    val parsedLevel = MasteryLevel.entries.firstOrNull { it.name == level } ?: return null
    return runCatching {
        Mastery(
            countryId = countryId,
            skill = parsedSkill,
            level = parsedLevel,
            attempts = attempts,
            correctAnswers = correctAnswers,
            unhintedCorrectAnswers = unhintedCorrectAnswers,
            unhintedCorrectRunIds = json.decodeFromString(SetSerializer(String.serializer()), unhintedCorrectRunIdsJson),
            unhintedCorrectEpochDays = json.decodeFromString(SetSerializer(Int.serializer()), unhintedCorrectEpochDaysJson),
            recentOutcomes = json.decodeFromString(ListSerializer(MasteryOutcome.serializer()), recentOutcomesJson),
            nextReviewEpochDay = nextReviewEpochDay,
        )
    }.getOrNull()
}

private fun ActiveRunSnapshot.toEntity(json: Json) = ActiveRunSnapshotEntity(
    runId = state.runId,
    schemaVersion = schemaVersion,
    catalogVersion = catalogVersion,
    snapshotJson = json.encodeToString(this),
)

private fun RunResult.toEntity(json: Json) = RunEntity(
    id = runId,
    duration = configuration.duration.name,
    topic = configuration.topic.name,
    status = "Completed",
    score = score,
    correctAnswers = correctAnswers,
    answeredQuestions = answeredQuestions,
    averageResponseMillis = averageResponseMillis,
    bestCorrectStreak = bestCorrectStreak,
    improvedMasteryItems = improvedMasteryItems,
    revisitCountryIdsJson = json.encodeToString(ListSerializer(String.serializer()), revisitCountryIds),
    completedAtEpochMillis = completedAtEpochMillis,
)

private fun Streak.toEntity() = StreakEntity(
    currentDays = currentDays,
    bestDays = bestDays,
    lastCompletedEpochDay = lastCompletedEpochDay,
)

private fun StreakEntity.toDomain() = Streak(
    currentDays = currentDays,
    bestDays = bestDays,
    lastCompletedEpochDay = lastCompletedEpochDay,
)

private fun PassportStamp.toEntity() = StampEntity(kind.name, earnedAtEpochMillis)

private fun StampEntity.toDomain(): PassportStamp? =
    PassportStampKind.entries.firstOrNull { it.name == id }?.let { PassportStamp(it, earnedAtEpochMillis) }

private fun String.decodeStringSet(): Set<String> = runCatching {
    Json.decodeFromString(SetSerializer(String.serializer()), this)
}.getOrDefault(emptySet())

private fun Set<String>.encodeStringSet(): String =
    Json.encodeToString(SetSerializer(String.serializer()), this)

private fun String.decodeIntSet(): Set<Int> = runCatching {
    Json.decodeFromString(SetSerializer(Int.serializer()), this)
}.getOrDefault(emptySet())

private fun Set<Int>.encodeIntSet(): String =
    Json.encodeToString(SetSerializer(Int.serializer()), this)

private fun String.decodeMastery(): Mastery? = runCatching {
    Json.decodeFromString<Mastery>(this)
}.getOrNull()

private fun String.decodeActiveRunId(): String = runCatching {
    Json.decodeFromString<ActiveRunSnapshot>(this).state.runId
}.getOrDefault("")
