package br.com.matchstats.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val PRACTICE_FACTS = """
    SELECT s.id AS practiceId, s.title, s.startsAt, s.sport, s.teamId,
           p.id AS playerId, p.name AS playerName, d.id AS drillId, d.name AS drillName, d.measure,
           SUM(b.value) AS value, SUM(b.attempts) AS attempts
    FROM practice_sessions s
    JOIN practice_participants r ON r.practiceId = s.id
    JOIN players p ON p.id = r.playerId
    LEFT JOIN practice_batches b ON b.practiceId = s.id AND b.playerId = r.playerId
    LEFT JOIN practice_drills d ON d.id = b.drillId
    WHERE s.status = 'COMPLETED' AND s.sport = :sport
          AND (:playerId IS NULL OR p.id = :playerId) AND (:teamId IS NULL OR s.teamId = :teamId)
    GROUP BY s.id, p.id, d.id
    ORDER BY s.startsAt DESC, s.id DESC, d.displayOrder, p.name COLLATE NOCASE
"""

@Dao
internal interface PracticeDao {
    @Query("SELECT * FROM practice_sessions WHERE id = :id")
    suspend fun session(id: Long): PracticeSession?

    @Query("""
        SELECT s.*, t.name AS teamName FROM practice_sessions s LEFT JOIN teams t ON t.id = s.teamId
        WHERE s.id = :id
    """)
    suspend fun summary(id: Long): PracticeSummary?

    @Query("""
        SELECT s.*, t.name AS teamName FROM practice_sessions s LEFT JOIN teams t ON t.id = s.teamId
        WHERE (s.status = 'COMPLETED') = :completed
        ORDER BY CASE WHEN :completed THEN -s.startsAt ELSE s.startsAt END, s.id
    """)
    fun list(completed: Boolean): Flow<List<PracticeSummary>>

    @Query("""
        SELECT s.*, t.name AS teamName FROM practice_sessions s LEFT JOIN teams t ON t.id = s.teamId
        WHERE s.status = 'SCHEDULED' AND s.startsAt >= :now ORDER BY s.startsAt, s.id LIMIT 1
    """)
    suspend fun next(now: Long): PracticeSummary?

    @Query("SELECT COUNT(*) FROM practice_sessions")
    suspend fun count(): Int

    @Insert
    suspend fun insertSession(session: PracticeSession): Long

    @Update
    suspend fun updateSession(session: PracticeSession)

    @Delete
    suspend fun deleteSession(session: PracticeSession)

    @Query("""
        SELECT r.playerId, p.name, r.jerseyNumber
        FROM practice_participants r JOIN players p ON p.id = r.playerId
        WHERE r.practiceId = :id ORDER BY p.name COLLATE NOCASE, p.id
    """)
    suspend fun participants(id: Long): List<PracticePlayer>

    @Insert
    suspend fun insertParticipants(participants: List<PracticeParticipant>)

    @Query("DELETE FROM practice_participants WHERE practiceId = :id AND playerId IN (:players)")
    suspend fun removeParticipants(id: Long, players: Set<Long>)

    @Query("SELECT DISTINCT playerId FROM practice_batches WHERE practiceId = :id")
    suspend fun recordedParticipants(id: Long): List<Long>

    @Query("SELECT * FROM practice_drills WHERE practiceId = :id ORDER BY displayOrder, id")
    suspend fun drills(id: Long): List<PracticeDrill>

    @Query("SELECT * FROM practice_drills WHERE id = :id")
    suspend fun drill(id: Long): PracticeDrill?

    @Insert
    suspend fun insertDrill(drill: PracticeDrill): Long

    @Query("SELECT * FROM practice_batches WHERE submissionId = :id")
    suspend fun submission(id: String): List<PracticeStatBatch>

    @Insert
    suspend fun insertBatches(batches: List<PracticeStatBatch>)

    @Update
    suspend fun updateBatch(batch: PracticeStatBatch)

    @Query("""
        SELECT playerId, drillId, SUM(value) AS value, SUM(attempts) AS attempts
        FROM practice_batches WHERE practiceId = :id GROUP BY playerId, drillId
    """)
    suspend fun totals(id: Long): List<PracticeTotals>

    @Query("""
        SELECT b.*, p.name AS playerName, d.name AS drillName, d.measure
        FROM practice_batches b JOIN players p ON p.id = b.playerId JOIN practice_drills d ON d.id = b.drillId
        WHERE b.practiceId = :id ORDER BY b.recordedAt DESC, b.submissionId, p.name COLLATE NOCASE
    """)
    fun history(id: Long): Flow<List<PracticeHistoryRow>>

    @Query(PRACTICE_FACTS)
    fun facts(sport: Sport, playerId: Long?, teamId: Long?): Flow<List<PracticeFact>>

    @Query(PRACTICE_FACTS)
    suspend fun readFacts(sport: Sport, playerId: Long?, teamId: Long?): List<PracticeFact>
}
