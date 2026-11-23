package br.com.matchstats.data

import androidx.room.Embedded
import java.text.Normalizer
import java.util.Locale

data class PlayerTeam(
    @Embedded val team: Team,
    val playerId: Long,
    val jerseyNumber: Int?,
    val position: Position?,
    val captain: Boolean,
)

data class PlayerGame(
    @Embedded val match: Match,
    val teamName: String,
    val sport: Sport,
    @Embedded val values: StatValues,
)

data class PlayerSportTotals(
    val playerId: Long,
    val sport: Sport,
    val games: Int,
    val goals: Long,
    val assists: Long,
    val yellowCards: Long,
    val redCards: Long,
    val points: Long,
    val rebounds: Long,
) {
    operator fun get(metric: StatMetric): Long = when (metric) {
        StatMetric.GOALS -> goals
        StatMetric.ASSISTS -> assists
        StatMetric.YELLOW_CARDS -> yellowCards
        StatMetric.RED_CARDS -> redCards
        StatMetric.POINTS -> points
        StatMetric.REBOUNDS -> rebounds
    }
}

data class DirectoryPlayer(val player: Player, val teams: List<PlayerTeam>, val totals: List<PlayerSportTotals>) {
    fun games(sport: Sport?): Int = totals.filter { sport == null || it.sport == sport }.sumOf { it.games }

    fun primaryTeam(sport: Sport?): Team? =
        teams.firstOrNull { sport == null || it.team.sport == sport }?.team

    fun totals(sport: Sport): PlayerSportTotals? = totals.firstOrNull { it.sport == sport }

    fun plays(sport: Sport): Boolean = teams.any { it.team.sport == sport } || totals(sport) != null

    fun matches(query: String, sport: Sport? = null): Boolean {
        val search = query.trim().forPlayerSearch()
        val number = search.toIntOrNull()
        return player.name.forPlayerSearch().contains(search) || teams.any {
            (sport == null || it.team.sport == sport) &&
                (it.team.name.forPlayerSearch().contains(search) || number != null && it.jerseyNumber == number)
        }
    }
}

data class PlayerDetails(val entry: DirectoryPlayer, val games: List<PlayerGame>) {
    val player get() = entry.player
}

internal fun String.forPlayerSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
