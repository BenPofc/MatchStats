package br.com.matchstats.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Query("""
        SELECT teams.*, (SELECT COUNT(*) FROM team_memberships WHERE teamId = teams.id) AS playerCount
        FROM teams ORDER BY name COLLATE NOCASE, id
    """)
    fun observeSummaries(): Flow<List<TeamSummary>>

    @Query("SELECT * FROM teams ORDER BY name COLLATE NOCASE, id")
    fun observeTeams(): Flow<List<Team>>

    @Query("SELECT * FROM teams ORDER BY name COLLATE NOCASE, id")
    suspend fun all(): List<Team>

    @Query("SELECT * FROM teams WHERE sport = :sport ORDER BY name COLLATE NOCASE, id")
    suspend fun forSport(sport: Sport): List<Team>

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun find(id: Long): Team?

    @Insert
    suspend fun insert(team: Team): Long

    @Update
    suspend fun update(team: Team): Int

    @Query("DELETE FROM teams WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface PlayerDao {
    @Query("""
        SELECT p.*, tm.jerseyNumber, tm.position, tm.captain
        FROM team_memberships tm JOIN players p ON p.id = tm.playerId
        WHERE tm.teamId = :teamId ORDER BY p.name COLLATE NOCASE, p.id
    """)
    suspend fun roster(teamId: Long): List<RosterPlayer>

    @Query("SELECT * FROM players ORDER BY name COLLATE NOCASE, id")
    suspend fun all(): List<Player>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun find(id: Long): Player?

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM team_memberships
            WHERE teamId = :teamId AND jerseyNumber = :number AND playerId != :editingId
        )
    """)
    suspend fun jerseyAssigned(teamId: Long, number: Int, editingId: Long): Boolean

    @Query("UPDATE team_memberships SET position = NULL WHERE teamId = :teamId AND position IS NOT NULL")
    suspend fun clearPositions(teamId: Long)

    @Query("""
        SELECT t.*, tm.playerId, tm.jerseyNumber, tm.position, tm.captain
        FROM team_memberships tm JOIN teams t ON t.id = tm.teamId
        ORDER BY t.name COLLATE NOCASE, t.id
    """)
    suspend fun allMemberships(): List<PlayerTeam>

    @Query("""
        SELECT t.*, tm.playerId, tm.jerseyNumber, tm.position, tm.captain
        FROM team_memberships tm JOIN teams t ON t.id = tm.teamId
        WHERE tm.playerId = :playerId ORDER BY t.name COLLATE NOCASE, t.id
    """)
    suspend fun memberships(playerId: Long): List<PlayerTeam>

    @Query("SELECT EXISTS(SELECT 1 FROM team_memberships WHERE playerId = :playerId AND teamId = :teamId)")
    suspend fun hasMembership(playerId: Long, teamId: Long): Boolean

    @Insert
    suspend fun insertMembership(membership: TeamMembership)

    @Update
    suspend fun updateMembership(membership: TeamMembership): Int

    @Query("DELETE FROM team_memberships WHERE playerId = :playerId AND teamId = :teamId")
    suspend fun detach(playerId: Long, teamId: Long): Int

    @Insert
    suspend fun insert(player: Player): Long

    @Update
    suspend fun update(player: Player): Int

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun delete(id: Long): Int
}

private const val DASHBOARD_COUNTS = """
    SELECT (SELECT COUNT(*) FROM teams) AS teams,
           (SELECT COUNT(*) FROM players) AS players,
           (SELECT COUNT(*) FROM matches) AS matches
"""

@Dao
interface DashboardDao {
    @Query(DASHBOARD_COUNTS)
    fun observeCounts(): Flow<DashboardCounts>

    @Query(DASHBOARD_COUNTS)
    suspend fun counts(): DashboardCounts
}

@Database(
    entities = [
        Team::class, Player::class, TeamMembership::class, Match::class, PlayerMatchStats::class,
        BoxCustomMetric::class, BoxDefaultLayout::class, BoxMatchLayout::class, BoxStoredMeasurement::class,
        LiveSession::class, LiveEvent::class, LivePreference::class, LiveMatchLayout::class,
        PracticeSession::class, PracticeParticipant::class, PracticeDrill::class, PracticeStatBatch::class,
        TacticalPlay::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7),
    ],
)
abstract class MatchStatsDatabase : RoomDatabase() {
    abstract fun teams(): TeamDao
    abstract fun players(): PlayerDao
    abstract fun dashboard(): DashboardDao
    abstract fun matches(): MatchDao
    abstract fun matchStats(): MatchStatsDao
    abstract fun boxScores(): BoxScoreDao
    abstract fun liveGames(): LiveGameDao
    internal abstract fun practices(): PracticeDao
    internal abstract fun tacticalPlays(): TacticalPlayDao

    companion object {
        fun open(context: Context): MatchStatsDatabase = Room.databaseBuilder(
            context.applicationContext,
            MatchStatsDatabase::class.java,
            "matchstats.db",
        ).addMigrations(MIGRATION_2_3).build()
    }
}
