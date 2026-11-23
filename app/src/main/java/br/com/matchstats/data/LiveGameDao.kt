package br.com.matchstats.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "live_preferences")
data class LivePreference(@PrimaryKey val sport: Sport, val layout: String)

@Entity(
    tableName = "live_match_layouts",
    foreignKeys = [ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE)],
)
data class LiveMatchLayout(@PrimaryKey val matchId: Long, val sport: Sport, val layout: String)

@Dao
interface LiveGameDao {
    @Query("SELECT * FROM live_sessions WHERE matchId = :matchId")
    suspend fun session(matchId: Long): LiveSession?

    @Upsert
    suspend fun saveSession(session: LiveSession)

    @Query("DELETE FROM live_sessions WHERE matchId = :matchId")
    suspend fun discard(matchId: Long)

    @Query("SELECT * FROM live_events WHERE matchId = :matchId ORDER BY sequence")
    suspend fun events(matchId: Long): List<LiveEvent>

    @Query("SELECT DISTINCT metricId FROM live_events WHERE matchId = :matchId AND metricId IS NOT NULL")
    suspend fun recordedMetrics(matchId: Long): List<String>

    @Query("SELECT * FROM live_events WHERE id = :id")
    suspend fun event(id: String): LiveEvent?

    @Insert
    suspend fun insertEvent(event: LiveEvent)

    @Query("SELECT layout FROM live_preferences WHERE sport = :sport")
    suspend fun preference(sport: Sport): String?

    @Upsert
    suspend fun savePreference(preference: LivePreference)

    @Query("SELECT * FROM live_match_layouts WHERE matchId = :matchId")
    suspend fun layout(matchId: Long): LiveMatchLayout?

    @Upsert
    suspend fun saveLayout(layout: LiveMatchLayout)

    @Query("""
        SELECT EXISTS(SELECT 1 FROM live_sessions l JOIN matches m ON m.id = l.matchId
        WHERE m.teamId = :teamId AND m.status = 'SCHEDULED')
    """)
    suspend fun activeForTeam(teamId: Long): Boolean

    @Query("""
        SELECT EXISTS(SELECT 1 FROM live_events e JOIN matches m ON m.id = e.matchId
        WHERE e.playerId = :playerId AND m.status = 'SCHEDULED')
    """)
    suspend fun activeForPlayer(playerId: Long): Boolean
}
