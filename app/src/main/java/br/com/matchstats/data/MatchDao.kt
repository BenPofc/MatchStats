package br.com.matchstats.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val SCORED_MATCHES = """
    SELECT m.id, m.teamId, m.opponent, m.startsAt, m.teamScore AS scored, m.opponentScore AS conceded
    FROM matches AS m
    WHERE m.status = 'FINISHED' AND m.teamScore IS NOT NULL AND m.opponentScore IS NOT NULL
"""

@Dao
interface MatchDao {
    @Query(SCORED_MATCHES + " AND m.teamId IN (SELECT id FROM teams WHERE sport = :sport)")
    suspend fun scoresForSport(sport: Sport): List<ScoredMatch>

    @Query(SCORED_MATCHES + " AND m.teamId = :teamId")
    fun observeScoresForTeam(teamId: Long): Flow<List<ScoredMatch>>
    @Transaction
    @Query("""
        SELECT * FROM matches WHERE status = :status
        ORDER BY CASE WHEN status = 'SCHEDULED' AND id IN (SELECT matchId FROM live_sessions) THEN 0 ELSE 1 END,
                 CASE WHEN status = 'SCHEDULED' THEN startsAt END,
                 CASE WHEN status = 'FINISHED' THEN startsAt END DESC, id DESC
    """)
    fun observeByStatus(status: MatchStatus): Flow<List<MatchWithTeam>>

    @Transaction
    @Query("SELECT * FROM matches WHERE id = :id")
    fun observeDetails(id: Long): Flow<MatchWithTeam?>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun find(id: Long): Match?

    @Transaction
    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun details(id: Long): MatchWithTeam?

    @Query("SELECT EXISTS(SELECT 1 FROM matches WHERE teamId = :teamId)")
    suspend fun containsTeam(teamId: Long): Boolean

    @Transaction
    @Query("""
        SELECT * FROM matches WHERE status = 'SCHEDULED'
           AND (startsAt >= :now OR id IN (SELECT matchId FROM live_sessions))
        ORDER BY CASE WHEN id IN (SELECT matchId FROM live_sessions) THEN 0 ELSE 1 END, startsAt, id LIMIT 1
    """)
    suspend fun upcoming(now: Long): MatchWithTeam?

    @Transaction
    @Query("SELECT * FROM matches WHERE status = 'FINISHED' ORDER BY startsAt DESC, id DESC LIMIT 3")
    suspend fun recentResults(): List<MatchWithTeam>

    @Insert
    suspend fun insert(match: Match): Long

    @Update
    suspend fun update(match: Match): Int

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface MatchStatsDao {
    @Query("""
        SELECT p.id AS playerId, p.name AS playerName, m.teamId, t.name AS teamName,
               SUM(s.goals) AS goals, SUM(s.assists) AS assists,
               SUM(s.points) AS points, SUM(s.rebounds) AS rebounds
        FROM player_match_stats AS s
        JOIN matches AS m ON m.id = s.matchId
        JOIN teams AS t ON t.id = m.teamId
        JOIN players AS p ON p.id = s.playerId
        WHERE t.sport = :sport AND m.status = 'FINISHED'
              AND m.teamScore IS NOT NULL AND m.opponentScore IS NOT NULL
        GROUP BY p.id, p.name, m.teamId, t.name
    """)
    suspend fun totalsForSport(sport: Sport): List<PlayerTotals>

    @Query("SELECT COALESCE(SUM(goals), 0) FROM player_match_stats WHERE matchId = :matchId")
    suspend fun totalGoals(matchId: Long): Long

    @Query("SELECT * FROM player_match_stats WHERE matchId = :matchId")
    suspend fun forMatch(matchId: Long): List<PlayerMatchStats>

    @Query("SELECT EXISTS(SELECT 1 FROM player_match_stats WHERE matchId = :matchId)")
    suspend fun containsMatch(matchId: Long): Boolean

    @Query("""
        SELECT p.* FROM players p
        WHERE p.id IN (SELECT playerId FROM team_memberships WHERE teamId = :teamId)
           OR p.id IN (SELECT playerId FROM player_match_stats WHERE matchId = :matchId)
        ORDER BY p.name COLLATE NOCASE, p.id
    """)
    suspend fun eligiblePlayers(matchId: Long, teamId: Long): List<Player>

    @Query("""
        SELECT s.playerId, t.sport, COUNT(*) AS games,
               SUM(s.goals) AS goals, SUM(s.assists) AS assists,
               SUM(s.yellowCards) AS yellowCards, SUM(s.redCards) AS redCards,
               SUM(s.points) AS points, SUM(s.rebounds) AS rebounds
        FROM player_match_stats s
        JOIN matches m ON m.id = s.matchId
        JOIN teams t ON t.id = m.teamId
        WHERE m.status = 'FINISHED' AND m.teamScore IS NOT NULL AND m.opponentScore IS NOT NULL
          AND (:playerId IS NULL OR s.playerId = :playerId)
        GROUP BY s.playerId, t.sport
    """)
    suspend fun playerTotals(playerId: Long?): List<PlayerSportTotals>

    @Query("""
        SELECT m.*, t.name AS teamName, t.sport,
               s.goals, s.assists, s.yellowCards, s.redCards, s.points, s.rebounds
        FROM player_match_stats s
        JOIN matches m ON m.id = s.matchId
        JOIN teams t ON t.id = m.teamId
        WHERE s.playerId = :playerId AND m.status = 'FINISHED'
          AND m.teamScore IS NOT NULL AND m.opponentScore IS NOT NULL
        ORDER BY m.startsAt DESC, m.id DESC
    """)
    suspend fun playerGames(playerId: Long): List<PlayerGame>

    @Upsert
    suspend fun upsert(rows: List<PlayerMatchStats>)

    @Query("DELETE FROM player_match_stats WHERE matchId = :matchId AND playerId = :playerId")
    suspend fun delete(matchId: Long, playerId: Long)
}
