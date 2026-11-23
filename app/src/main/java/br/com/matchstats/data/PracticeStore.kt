package br.com.matchstats.data

import android.os.SystemClock
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.map

internal class PracticeStore(
    private val database: MatchStatsDatabase,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val clockOwner: String = PROCESS_OWNER,
) {
    private val dao = database.practices()

    fun list(completed: Boolean) = dao.list(completed)
    fun history(id: Long) = dao.history(id)
    fun facts(sport: Sport, playerId: Long? = null, teamId: Long? = null) = dao.facts(sport, playerId, teamId)

    fun details(id: Long) = database.invalidationTracker.createFlow(
        "practice_sessions", "practice_participants", "practice_drills", "practice_batches", "players", "teams",
    ).map {
        database.withTransaction {
            val practice = dao.summary(id) ?: return@withTransaction null
            PracticeDetails(practice, dao.participants(id), dao.drills(id), dao.totals(id))
        }
    }

    fun candidates() = database.invalidationTracker.createFlow("teams", "players", "team_memberships").map {
        database.withTransaction {
            PracticeCandidates(database.teams().all(), database.players().all(), database.players().allMemberships())
        }
    }

    suspend fun save(id: Long?, input: PracticeInput): Pair<WriteResult, Long?> = database.withTransaction {
        fun error(result: WriteResult) = result to null
        if (input.title.isBlank()) return@withTransaction error(WriteResult.NAME_REQUIRED)
        val date = input.startsAt ?: return@withTransaction error(WriteResult.INVALID_MATCH_TIME)
        if (input.playerIds.isEmpty()) return@withTransaction error(WriteResult.PRACTICE_PLAYERS_REQUIRED)
        val old = id?.let { dao.session(it) }
        if (id != null && old == null) return@withTransaction error(WriteResult.RECORD_MISSING)
        val team = input.teamId?.let { database.teams().find(it) }
        if (input.teamId != null && team == null) return@withTransaction error(WriteResult.TEAM_MISSING)
        val sport = team?.sport ?: input.sport
        if (old != null && old.sport != sport && dao.drills(old.id).isNotEmpty()) {
            return@withTransaction error(WriteResult.PRACTICE_SPORT_LOCKED)
        }
        val existing = id?.let { dao.participants(it) }.orEmpty().associateBy { it.playerId }
        val removed = existing.keys - input.playerIds
        if (id != null && removed.isNotEmpty() && dao.recordedParticipants(id).any { it in removed }) {
            return@withTransaction error(WriteResult.PRACTICE_PLAYER_HAS_BATCHES)
        }
        if (input.playerIds.any { database.players().find(it) == null }) {
            return@withTransaction error(WriteResult.RECORD_MISSING)
        }
        val base = old ?: PracticeSession("", sport, null, date, null, null)
        val record = base.copy(
            title = input.title.trim(), sport = sport, teamId = team?.id, startsAt = date,
            location = input.location.trim().ifEmpty { null }, notes = input.notes.trim().ifEmpty { null },
        )
        val savedId = if (old == null) dao.insertSession(record) else old.id.also { dao.updateSession(record) }
        val teamChanged = old?.teamId != record.teamId
        val replace = if (teamChanged) {
            val recorded = dao.recordedParticipants(savedId).toSet()
            existing.keys - recorded
        } else emptySet()
        dao.removeParticipants(savedId, removed + replace)
        val roster = team?.let { database.players().roster(it.id) }.orEmpty().associateBy { it.id }
        dao.insertParticipants((input.playerIds - (existing.keys - replace)).map { playerId ->
            PracticeParticipant(savedId, playerId, roster[playerId]?.jerseyNumber)
        })
        WriteResult.SAVED to savedId
    }

    suspend fun delete(id: Long) = update(id) { dao.deleteSession(it) }

    suspend fun start(id: Long) = update(id) {
        if (it.status != PracticeStatus.SCHEDULED) return@update
        dao.updateSession(it.copy(status = PracticeStatus.ACTIVE))
    }

    suspend fun complete(id: Long) = update(id) {
        if (it.status != PracticeStatus.ACTIVE) return@update
        dao.updateSession(it.copy(
            status = PracticeStatus.COMPLETED, elapsedMs = elapsedFor(it),
            runningSince = null, clockOwner = null, timerStopped = true,
        ))
    }

    suspend fun addDrill(id: Long, name: String, measure: PracticeMeasure, notes: String): Pair<WriteResult, Long?> =
        database.withTransaction {
            val session = dao.session(id) ?: return@withTransaction WriteResult.RECORD_MISSING to null
            if (session.status == PracticeStatus.COMPLETED) return@withTransaction WriteResult.PRACTICE_CLOSED to null
            if (name.isBlank()) return@withTransaction WriteResult.NAME_REQUIRED to null
            val drills = dao.drills(id)
            if (drills.any { it.name.equals(name.trim(), ignoreCase = true) }) {
                return@withTransaction WriteResult.PRACTICE_DUPLICATE_DRILL to null
            }
            val drillId = dao.insertDrill(PracticeDrill(
                id, name.trim(), session.sport, measure, notes.trim().ifEmpty { null }, drills.size,
            ))
            WriteResult.SAVED to drillId
        }

    suspend fun submit(id: Long, drillId: Long, submissionId: String, rows: List<PracticeRow>): WriteResult =
        database.withTransaction {
            if (submissionId.isBlank() || rows.isEmpty() || rows.distinctBy { it.playerId }.size != rows.size) {
                return@withTransaction WriteResult.INVALID_STATS
            }
            val previous = dao.submission(submissionId)
            if (previous.isNotEmpty()) {
                val originals = previous.map { PracticeRow(it.playerId, it.originalValue, it.originalAttempts) }.toSet()
                return@withTransaction if (previous.all { it.practiceId == id && it.drillId == drillId } &&
                    rows.toSet() == originals
                ) WriteResult.SAVED else WriteResult.PRACTICE_SUBMISSION_CONFLICT
            }
            val practice = dao.session(id) ?: return@withTransaction WriteResult.RECORD_MISSING
            if (practice.status != PracticeStatus.ACTIVE) return@withTransaction WriteResult.PRACTICE_CLOSED
            val drill = dao.drill(drillId)?.takeIf { it.practiceId == id } ?: return@withTransaction WriteResult.RECORD_MISSING
            val participants = dao.participants(id).map { it.playerId }.toSet()
            if (rows.any { it.playerId !in participants || !it.validFor(drill.measure) }) {
                return@withTransaction WriteResult.INVALID_STATS
            }
            dao.insertBatches(rows.map { PracticeStatBatch(submissionId, it.playerId, id, drillId, it.value, it.attempts) })
            WriteResult.SAVED
        }

    suspend fun correct(batch: PracticeStatBatch, row: PracticeRow): WriteResult = database.withTransaction {
        val saved = dao.submission(batch.submissionId).find { it.playerId == batch.playerId }
            ?: return@withTransaction WriteResult.RECORD_MISSING
        val drill = dao.drill(saved.drillId) ?: return@withTransaction WriteResult.RECORD_MISSING
        if (row.playerId != saved.playerId || !row.validFor(drill.measure)) return@withTransaction WriteResult.INVALID_STATS
        if (saved.value == row.value && saved.attempts == row.attempts) return@withTransaction WriteResult.SAVED
        if (saved.revision != batch.revision) return@withTransaction WriteResult.PRACTICE_SUBMISSION_CONFLICT
        dao.updateBatch(saved.copy(
            value = row.value, attempts = row.attempts, revision = saved.revision + 1, correctedAt = System.currentTimeMillis(),
        ))
        WriteResult.SAVED
    }

    fun elapsedFor(session: PracticeSession): Long {
        val since = session.runningSince?.takeIf { session.clockOwner == clockOwner } ?: return session.elapsedMs
        return session.elapsedMs + (elapsed() - since).coerceAtLeast(0)
    }

    fun isRunning(session: PracticeSession) = session.runningSince != null && session.clockOwner == clockOwner

    suspend fun checkpoint(id: Long) = update(id) {
        val since = it.runningSince ?: return@update
        if (isRunning(it)) {
            val now = elapsed()
            dao.updateSession(it.copy(elapsedMs = it.elapsedMs + (now - since).coerceAtLeast(0), runningSince = now))
        } else {
            dao.updateSession(it.copy(runningSince = null, clockOwner = null))
        }
    }

    suspend fun clock(id: Long, action: PracticeClockAction) = update(id) {
        if (it.status != PracticeStatus.ACTIVE) return@update
        val running = isRunning(it)
        val updated = when (action) {
            PracticeClockAction.START -> it.takeUnless { running || it.elapsedMs > 0 && !it.timerStopped }?.copy(
                elapsedMs = 0, runningSince = elapsed(), clockOwner = clockOwner, timerStopped = false,
            )
            PracticeClockAction.RESUME -> it.takeUnless { running || it.timerStopped }?.copy(
                runningSince = elapsed(), clockOwner = clockOwner,
            )
            PracticeClockAction.PAUSE -> it.takeIf { running }?.copy(
                elapsedMs = elapsedFor(it), runningSince = null, clockOwner = null,
            )
            PracticeClockAction.STOP -> it.copy(
                elapsedMs = elapsedFor(it), runningSince = null, clockOwner = null, timerStopped = true,
            )
        }
        if (updated != null) dao.updateSession(updated)
    }

    private suspend fun update(id: Long, operation: suspend (PracticeSession) -> Unit) = database.withTransaction {
        val practice = dao.session(id) ?: return@withTransaction WriteResult.RECORD_MISSING
        operation(practice)
        WriteResult.SAVED
    }

    companion object {
        private val PROCESS_OWNER = UUID.randomUUID().toString()
    }
}

internal data class PracticeCandidates(val teams: List<Team>, val players: List<Player>, val memberships: List<PlayerTeam>)
