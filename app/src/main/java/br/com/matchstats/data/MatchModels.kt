package br.com.matchstats.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class MatchStatus { SCHEDULED, FINISHED }

@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(entity = Team::class, parentColumns = ["id"], childColumns = ["teamId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("teamId"), Index(value = ["status", "startsAt"])],
)
data class Match(
    val teamId: Long,
    val opponent: String,
    val startsAt: Long,
    val location: String?,
    val status: MatchStatus,
    val teamScore: Int?,
    val opponentScore: Int?,
    val createdAt: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

data class MatchWithTeam(
    @Embedded val match: Match,
    @Relation(parentColumn = "teamId", entityColumn = "id") val team: Team,
    @Relation(parentColumn = "id", entityColumn = "matchId") val live: LiveSession? = null,
)

enum class StatMetric {
    GOALS, ASSISTS, YELLOW_CARDS, RED_CARDS, POINTS, REBOUNDS;

    companion object {
        fun forSport(sport: Sport): List<StatMetric> = when (sport) {
            Sport.FOOTBALL -> listOf(GOALS, ASSISTS, YELLOW_CARDS, RED_CARDS)
            Sport.BASKETBALL -> listOf(POINTS, REBOUNDS, ASSISTS)
        }
    }
}

data class StatValues(
    @ColumnInfo(defaultValue = "0") val goals: Int = 0,
    @ColumnInfo(defaultValue = "0") val assists: Int = 0,
    @ColumnInfo(defaultValue = "0") val yellowCards: Int = 0,
    @ColumnInfo(defaultValue = "0") val redCards: Int = 0,
    @ColumnInfo(defaultValue = "0") val points: Int = 0,
    @ColumnInfo(defaultValue = "0") val rebounds: Int = 0,
) {
    operator fun get(metric: StatMetric): Int = when (metric) {
        StatMetric.GOALS -> goals
        StatMetric.ASSISTS -> assists
        StatMetric.YELLOW_CARDS -> yellowCards
        StatMetric.RED_CARDS -> redCards
        StatMetric.POINTS -> points
        StatMetric.REBOUNDS -> rebounds
    }

    fun validate(sport: Sport): Boolean {
        val allowed = StatMetric.forSport(sport)
        return StatMetric.entries.all { this[it] >= 0 && (it in allowed || this[it] == 0) }
    }

    companion object {
        fun from(values: Map<StatMetric, Int>) = StatValues(
            goals = values[StatMetric.GOALS] ?: 0,
            assists = values[StatMetric.ASSISTS] ?: 0,
            yellowCards = values[StatMetric.YELLOW_CARDS] ?: 0,
            redCards = values[StatMetric.RED_CARDS] ?: 0,
            points = values[StatMetric.POINTS] ?: 0,
            rebounds = values[StatMetric.REBOUNDS] ?: 0,
        )
    }
}

@Entity(
    tableName = "player_match_stats",
    primaryKeys = ["matchId", "playerId"],
    foreignKeys = [
        ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Player::class, parentColumns = ["id"], childColumns = ["playerId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("playerId")],
)
data class PlayerMatchStats(
    val matchId: Long,
    val playerId: Long,
    @Embedded val values: StatValues = StatValues(),
)

data class MatchStatsSheet(
    val fixture: MatchWithTeam,
    val roster: List<Player>,
    val saved: List<PlayerMatchStats>,
)

internal data class HomeDashboard(
    val counts: DashboardCounts,
    val upcoming: MatchWithTeam?,
    val results: List<MatchWithTeam>,
    val practiceCount: Int = 0,
    val nextPractice: PracticeSummary? = null,
)

data class MatchInput(
    val teamId: Long?,
    val opponent: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val location: String,
    val status: MatchStatus,
    val teamScore: String,
    val opponentScore: String,
) {
    fun validate(zone: ZoneId): WriteResult? = when {
        teamId == null -> WriteResult.TEAM_REQUIRED
        opponent.isBlank() -> WriteResult.OPPONENT_REQUIRED
        date == null -> WriteResult.DATE_REQUIRED
        time == null -> WriteResult.TIME_REQUIRED
        startsAt(zone) == null -> WriteResult.INVALID_MATCH_TIME
        status == MatchStatus.FINISHED && teamScore.nonNegativeInt() == null -> WriteResult.INVALID_TEAM_SCORE
        status == MatchStatus.FINISHED && opponentScore.nonNegativeInt() == null -> WriteResult.INVALID_OPPONENT_SCORE
        else -> null
    }

    fun startsAt(zone: ZoneId): Long? {
        return practiceOrMatchStart(date ?: return null, time ?: return null, zone)
    }
}

internal fun practiceOrMatchStart(date: LocalDate, time: LocalTime, zone: ZoneId): Long? {
    val local = LocalDateTime.of(date, time)
    val offset = zone.rules.getValidOffsets(local).firstOrNull() ?: return null
    return local.toInstant(offset).toEpochMilli()
}

fun String.nonNegativeInt(): Int? =
    trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
