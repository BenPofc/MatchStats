package br.com.matchstats.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

internal enum class PracticeStatus { SCHEDULED, ACTIVE, COMPLETED }
internal enum class PracticeMeasure { COUNT, MAKES_AND_ATTEMPTS }
internal enum class PracticeClockAction { START, PAUSE, RESUME, STOP }

@Entity(
    tableName = "practice_sessions",
    foreignKeys = [ForeignKey(entity = Team::class, parentColumns = ["id"], childColumns = ["teamId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("teamId"), Index("status", "startsAt")],
)
internal data class PracticeSession(
    val title: String,
    val sport: Sport,
    val teamId: Long?,
    val startsAt: Long,
    val location: String?,
    val notes: String?,
    val status: PracticeStatus = PracticeStatus.SCHEDULED,
    val createdAt: Long = System.currentTimeMillis(),
    val elapsedMs: Long = 0,
    val runningSince: Long? = null,
    val clockOwner: String? = null,
    val timerStopped: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "practice_participants",
    primaryKeys = ["practiceId", "playerId"],
    foreignKeys = [
        ForeignKey(entity = PracticeSession::class, parentColumns = ["id"], childColumns = ["practiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Player::class, parentColumns = ["id"], childColumns = ["playerId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playerId")],
)
internal data class PracticeParticipant(val practiceId: Long, val playerId: Long, val jerseyNumber: Int?)

@Entity(
    tableName = "practice_drills",
    foreignKeys = [ForeignKey(entity = PracticeSession::class, parentColumns = ["id"], childColumns = ["practiceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("practiceId"), Index("id", "practiceId", unique = true)],
)
internal data class PracticeDrill(
    val practiceId: Long,
    val name: String,
    val sport: Sport,
    val measure: PracticeMeasure,
    val notes: String?,
    val displayOrder: Int,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "practice_batches",
    primaryKeys = ["submissionId", "playerId"],
    foreignKeys = [
        ForeignKey(entity = PracticeDrill::class, parentColumns = ["id", "practiceId"], childColumns = ["drillId", "practiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PracticeParticipant::class, parentColumns = ["practiceId", "playerId"], childColumns = ["practiceId", "playerId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("drillId", "practiceId"), Index("practiceId", "playerId")],
)
internal data class PracticeStatBatch(
    val submissionId: String,
    val playerId: Long,
    val practiceId: Long,
    val drillId: Long,
    val value: Int,
    val attempts: Int?,
    val originalValue: Int = value,
    val originalAttempts: Int? = attempts,
    val recordedAt: Long = System.currentTimeMillis(),
    val revision: Int = 0,
    val correctedAt: Long? = null,
)

internal data class PracticeInput(
    val title: String,
    val sport: Sport,
    val teamId: Long?,
    val startsAt: Long?,
    val location: String,
    val notes: String,
    val playerIds: Set<Long>,
)
internal data class PracticeRow(val playerId: Long, val value: Int, val attempts: Int?) {
    fun validFor(measure: PracticeMeasure) = value >= 0 && when (measure) {
        PracticeMeasure.COUNT -> attempts == null
        PracticeMeasure.MAKES_AND_ATTEMPTS -> attempts != null && attempts >= value
    }
}
internal data class PracticeSummary(@Embedded val session: PracticeSession, val teamName: String?)
internal data class PracticePlayer(val playerId: Long, val name: String, val jerseyNumber: Int?)
internal data class PracticeTotals(val playerId: Long, val drillId: Long, val value: Long, val attempts: Long?)
internal data class PracticeDetails(
    val summary: PracticeSummary,
    val players: List<PracticePlayer>,
    val drills: List<PracticeDrill>,
    val totals: List<PracticeTotals>,
)
internal data class PracticeHistoryRow(@Embedded val batch: PracticeStatBatch, val playerName: String, val drillName: String, val measure: PracticeMeasure)
internal data class PracticeFact(
    val practiceId: Long,
    val title: String,
    val startsAt: Long,
    val sport: Sport,
    val teamId: Long?,
    val playerId: Long,
    val playerName: String,
    val drillId: Long?,
    val drillName: String?,
    val measure: PracticeMeasure?,
    val value: Long?,
    val attempts: Long?,
)

internal data class PracticePerformance(
    val sessions: Int,
    val drills: Int,
    val makes: Long,
    val attempts: Long,
    val count: Long,
) {
    val accuracy get() = makes.practiceAccuracy(attempts)
}

internal fun Iterable<PracticeFact>.practicePerformance(): PracticePerformance {
    val measured = filter { it.drillId != null }
    val pairs = measured.filter { it.measure == PracticeMeasure.MAKES_AND_ATTEMPTS }
    return PracticePerformance(
        sessions = map { it.practiceId }.distinct().size,
        drills = measured.map { it.drillId }.distinct().size,
        makes = pairs.sumOf { it.value ?: 0 },
        attempts = pairs.sumOf { it.attempts ?: 0 },
        count = measured.filter { it.measure == PracticeMeasure.COUNT }.sumOf { it.value ?: 0 },
    )
}

internal fun Long.practiceAccuracy(attempts: Long?): Double? =
    attempts?.takeIf { it > 0 }?.let { toDouble() / it }
