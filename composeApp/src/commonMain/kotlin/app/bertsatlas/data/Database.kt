package app.bertsatlas.data

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.bertsatlas.domain.model.ActiveRunSnapshot
import app.bertsatlas.domain.model.PassportStamp
import app.bertsatlas.domain.model.PassportStampKind
import app.bertsatlas.domain.model.RunResult
import app.bertsatlas.domain.model.Streak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Entity(tableName = "pack_answer_totals")
data class PackAnswerTotalsEntity(
    @PrimaryKey val packId: String,
    val answeredQuestions: Int,
    val correctAnswers: Int,
    val netPoints: Int,
)

data class AnswerTotals(
    val answeredQuestions: Int = 0,
    val correctAnswers: Int = 0,
    val netPoints: Int = 0,
)

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val duration: String,
    val topic: String,
    val status: String,
    val score: Int,
    val correctAnswers: Int,
    val answeredQuestions: Int,
    val averageResponseMillis: Long,
    val bestCorrectStreak: Int,
    val improvedMasteryItems: Int,
    val revisitCardIdsJson: String,
    val completedAtEpochMillis: Long?,
)

@Entity(tableName = "personal_bests", primaryKeys = ["packId", "duration"])
data class PersonalBestEntity(
    val packId: String,
    val duration: String,
    val score: Int,
    val runId: String,
)

@Entity(tableName = "streak")
data class StreakEntity(
    @PrimaryKey val singletonId: Int = 1,
    val currentDays: Int,
    val bestDays: Int,
    val lastCompletedEpochDay: Int?,
)

@Entity(tableName = "stamps", primaryKeys = ["packId", "stampId"])
data class StampEntity(
    val packId: String,
    val stampId: String,
    val earnedAtEpochMillis: Long,
)

@Entity(tableName = "active_run")
data class ActiveRunSnapshotEntity(
    @PrimaryKey val singletonId: Int = 1,
    val runId: String,
    val packId: String,
    val schemaVersion: Int,
    val catalogVersion: String,
    val snapshotJson: String,
)

@Dao
abstract class RunWriteDao {
    @Query("INSERT OR IGNORE INTO pack_answer_totals (packId, answeredQuestions, correctAnswers, netPoints) VALUES (:packId, 0, 0, 0)")
    protected abstract suspend fun ensureAnswerTotals(packId: String)

    @Query(
        "UPDATE pack_answer_totals SET answeredQuestions = answeredQuestions + 1, " +
            "correctAnswers = correctAnswers + :correctIncrement, netPoints = netPoints + :netPoints WHERE packId = :packId",
    )
    protected abstract suspend fun addAnswerToTotals(packId: String, correctIncrement: Int, netPoints: Int)

    @Upsert
    abstract suspend fun saveActiveRun(snapshot: ActiveRunSnapshotEntity)

    @Query("DELETE FROM active_run WHERE runId = :runId")
    abstract suspend fun clearActiveRun(runId: String)

    @Query("DELETE FROM active_run")
    abstract suspend fun clearActiveRun()

    @Query("DELETE FROM pack_answer_totals")
    protected abstract suspend fun clearAnswerTotals()

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

    @Query("SELECT * FROM personal_bests WHERE packId = :packId AND duration = :duration")
    protected abstract suspend fun personalBest(packId: String, duration: String): PersonalBestEntity?

    @Upsert
    protected abstract suspend fun upsertPersonalBest(personalBest: PersonalBestEntity)

    @Upsert
    abstract suspend fun saveStreak(streak: StreakEntity)

    @Upsert
    protected abstract suspend fun upsertStamps(stamps: List<StampEntity>)

    @Transaction
    open suspend fun recordAcceptedAnswer(
        cardPackId: String,
        correct: Boolean,
        netPoints: Int,
        snapshot: ActiveRunSnapshotEntity,
        streak: StreakEntity,
    ) {
        ensureAnswerTotals(cardPackId)
        addAnswerToTotals(cardPackId, if (correct) 1 else 0, netPoints)
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
        upsertRun(run)
        val existingBest = personalBest(run.packId, run.duration)
        if (run.score > (existingBest?.score ?: 0)) {
            upsertPersonalBest(PersonalBestEntity(run.packId, run.duration, run.score, run.id))
        }
        saveStreak(streak)
        if (stamps.isNotEmpty()) upsertStamps(stamps)
        clearActiveRun(run.id)
    }

    @Transaction
    open suspend fun abandonRun(run: RunEntity, streak: StreakEntity? = null) {
        upsertRun(run.copy(status = "Abandoned", completedAtEpochMillis = null))
        if (streak != null) saveStreak(streak)
        clearActiveRun(run.id)
    }

    @Transaction
    open suspend fun resetProgress() {
        clearAnswerTotals()
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

    @Query("SELECT * FROM runs WHERE status = 'Completed' ORDER BY completedAtEpochMillis DESC")
    suspend fun completedRunsNow(): List<RunEntity>

    @Query(
        "SELECT COALESCE(SUM(answeredQuestions), 0) AS answeredQuestions, COALESCE(SUM(correctAnswers), 0) AS correctAnswers, " +
            "COALESCE(SUM(netPoints), 0) AS netPoints FROM pack_answer_totals",
    )
    suspend fun answerTotalsAcrossPacksNow(): AnswerTotals

    @Query("SELECT * FROM pack_answer_totals WHERE packId = :packId")
    fun answerTotals(packId: String): Flow<PackAnswerTotalsEntity?>

    @Query("SELECT * FROM personal_bests WHERE packId = :packId AND duration = :duration")
    suspend fun personalBestNow(packId: String, duration: String): PersonalBestEntity?

    @Query("SELECT * FROM streak WHERE singletonId = 1")
    fun streak(): Flow<StreakEntity?>

    @Query("SELECT * FROM streak WHERE singletonId = 1")
    suspend fun streakNow(): StreakEntity?

    @Query("SELECT * FROM stamps ORDER BY earnedAtEpochMillis")
    suspend fun allStampsNow(): List<StampEntity>

    @Query("SELECT * FROM stamps WHERE packId = :packId ORDER BY earnedAtEpochMillis")
    fun stamps(packId: String): Flow<List<StampEntity>>
}

@Database(
    entities = [
        PackAnswerTotalsEntity::class,
        RunEntity::class,
        PersonalBestEntity::class,
        StreakEntity::class,
        StampEntity::class,
        ActiveRunSnapshotEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@ConstructedBy(BertsAtlasDatabaseConstructor::class)
abstract class BertsAtlasDatabase : RoomDatabase() {
    abstract fun runWriter(): RunWriteDao
    abstract fun progressReader(): ProgressReadDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object BertsAtlasDatabaseConstructor : RoomDatabaseConstructor<BertsAtlasDatabase> {
    override fun initialize(): BertsAtlasDatabase
}

fun RoomDatabase.Builder<BertsAtlasDatabase>.buildBertsAtlasDatabase(): BertsAtlasDatabase =
    fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()

fun ActiveRunSnapshot.toEntity(packId: String, json: Json) = ActiveRunSnapshotEntity(
    runId = state.runId,
    packId = packId,
    schemaVersion = schemaVersion,
    catalogVersion = catalogVersion,
    snapshotJson = json.encodeToString(this),
)

fun RunResult.toRunEntity(packId: String, json: Json) = RunEntity(
    id = runId,
    packId = packId,
    duration = configuration.duration.name,
    topic = configuration.scope.progressKey,
    status = status.name,
    score = score,
    correctAnswers = correctAnswers,
    answeredQuestions = answeredQuestions,
    averageResponseMillis = averageResponseMillis,
    bestCorrectStreak = bestCorrectStreak,
    improvedMasteryItems = improvedMasteryItems,
    revisitCardIdsJson = json.encodeToString(ListSerializer(String.serializer()), revisitCountryIds),
    completedAtEpochMillis = completedAtEpochMillis,
)

fun Streak.toEntity() = StreakEntity(
    currentDays = currentDays,
    bestDays = bestDays,
    lastCompletedEpochDay = lastCompletedEpochDay,
)

fun StreakEntity.toStreak() = Streak(
    currentDays = currentDays,
    bestDays = bestDays,
    lastCompletedEpochDay = lastCompletedEpochDay,
)

fun PassportStamp.toEntity(packId: String) = StampEntity(packId, kind.name, earnedAtEpochMillis)

fun StampEntity.toPassportStamp(): PassportStamp? =
    PassportStampKind.entries.firstOrNull { it.name == stampId }?.let { PassportStamp(it, earnedAtEpochMillis) }
