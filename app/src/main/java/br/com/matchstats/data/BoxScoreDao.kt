package br.com.matchstats.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "box_defaults")
data class BoxDefaultLayout(@PrimaryKey val sport: Sport, val layout: String)

@Entity(
    tableName = "box_match_layouts",
    foreignKeys = [ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE)],
)
data class BoxMatchLayout(@PrimaryKey val matchId: Long, val sport: Sport, val layout: String)

@Entity(
    tableName = "box_measurements",
    primaryKeys = ["matchId", "playerId", "metricId"],
    foreignKeys = [
        ForeignKey(
            entity = PlayerMatchStats::class,
            parentColumns = ["matchId", "playerId"],
            childColumns = ["matchId", "playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BoxStoredMeasurement(
    val matchId: Long,
    val playerId: Long,
    val metricId: String,
    val value: Int,
    val attempts: Int?,
) {
    fun measurement() = BoxMeasurement(value, attempts)
}

@Dao
interface BoxScoreDao {
    @Query("SELECT * FROM box_custom_metrics WHERE sport = :sport ORDER BY name COLLATE NOCASE, id")
    suspend fun metrics(sport: Sport): List<BoxCustomMetric>

    @Query("SELECT * FROM box_custom_metrics WHERE id IN (:ids)")
    suspend fun definitions(ids: List<String>): List<BoxCustomMetric>

    @Upsert
    suspend fun upsertMetrics(metrics: List<BoxCustomMetric>)

    @Query("SELECT layout FROM box_defaults WHERE sport = :sport")
    suspend fun defaults(sport: Sport): String?

    @Upsert
    suspend fun upsertDefault(layout: BoxDefaultLayout)

    @Query("SELECT * FROM box_match_layouts WHERE matchId = :id")
    suspend fun matchLayout(id: Long): BoxMatchLayout?

    @Upsert
    suspend fun upsertMatchLayout(layout: BoxMatchLayout)

    @Query("SELECT * FROM box_measurements WHERE matchId = :matchId")
    suspend fun measurements(matchId: Long): List<BoxStoredMeasurement>

    @Query("""
        SELECT b.* FROM box_measurements b
        JOIN matches m ON m.id = b.matchId
        JOIN teams t ON t.id = m.teamId
        WHERE b.playerId = :playerId AND t.sport = :sport AND m.status = 'FINISHED'
          AND m.teamScore IS NOT NULL AND m.opponentScore IS NOT NULL
    """)
    suspend fun playerMeasurements(playerId: Long, sport: Sport): List<BoxStoredMeasurement>

    @Query("SELECT playerId FROM box_measurements WHERE matchId = :matchId GROUP BY playerId")
    suspend fun measuredPlayers(matchId: Long): List<Long>

    @Upsert
    suspend fun upsertMeasurements(values: List<BoxStoredMeasurement>)

    @Query("DELETE FROM box_measurements WHERE matchId = :matchId AND playerId = :playerId AND metricId IN (:ids)")
    suspend fun clearMeasurements(matchId: Long, playerId: Long, ids: List<String>)
}

internal fun List<BoxColumn>.encodeBoxLayout(): String =
    joinToString(",") { (if (it.enabled) "+" else "-") + it.metricId }

internal fun String.decodeBoxLayout(): List<BoxColumn> =
    split(',').mapNotNull { token ->
        token.takeIf { it.length > 1 && (it[0] == '+' || it[0] == '-') }
            ?.let { BoxColumn(it.substring(1), it[0] == '+') }
    }
