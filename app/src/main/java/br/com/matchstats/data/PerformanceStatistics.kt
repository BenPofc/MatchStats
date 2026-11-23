package br.com.matchstats.data

data class ScoredMatch(
    val id: Long,
    val teamId: Long,
    val opponent: String,
    val startsAt: Long,
    val scored: Int,
    val conceded: Int,
)

data class PlayerTotals(
    val playerId: Long,
    val playerName: String,
    val teamId: Long,
    val teamName: String,
    val goals: Long,
    val assists: Long,
    val points: Long,
    val rebounds: Long,
)

data class PerformanceSummary(
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val scored: Long,
    val conceded: Long,
) {
    val completed: Int get() = wins + draws + losses
    val winPercentage: Int
        get() = if (completed == 0) 0 else ((wins * 100L + completed / 2) / completed).toInt()
}

data class RankedPlayer(
    val playerId: Long,
    val playerName: String,
    val teamName: String,
    val total: Long,
)

data class SportStatistics(
    val teams: List<Team>,
    val matches: List<ScoredMatch>,
    val playerTotals: List<PlayerTotals>,
) {
    fun forTeam(teamId: Long?): FilteredStatistics {
        val results = if (teamId == null) matches else matches.filter { it.teamId == teamId }
        val players = if (teamId == null) playerTotals else playerTotals.filter { it.teamId == teamId }
        return FilteredStatistics(
            performance = summarizePerformance(results),
            recent = results.sortedWith(compareBy<ScoredMatch> { it.startsAt }.thenBy { it.id }).takeLast(4),
            playerTotals = players,
        )
    }
}

data class FilteredStatistics(
    val performance: PerformanceSummary,
    val recent: List<ScoredMatch>,
    val playerTotals: List<PlayerTotals>,
) {
    fun ranking(metric: StatMetric): List<RankedPlayer> = playerTotals.mapNotNull { player ->
        val total = when (metric) {
            StatMetric.GOALS -> player.goals
            StatMetric.ASSISTS -> player.assists
            StatMetric.POINTS -> player.points
            StatMetric.REBOUNDS -> player.rebounds
            StatMetric.YELLOW_CARDS, StatMetric.RED_CARDS -> return@mapNotNull null
        }
        if (total > 0) RankedPlayer(player.playerId, player.playerName, player.teamName, total) else null
    }.groupBy { it.playerId }.values.map { contributions ->
        contributions.first().copy(
            teamName = contributions.map { it.teamName }.distinct().sorted().joinToString(" · "),
            total = contributions.sumOf { it.total },
        )
    }.sortedWith(
        compareByDescending<RankedPlayer> { it.total }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.playerName }
            .thenBy { it.playerId },
    )
}

fun summarizePerformance(matches: List<ScoredMatch>): PerformanceSummary {
    var wins = 0
    var draws = 0
    var losses = 0
    var scored = 0L
    var conceded = 0L
    for (match in matches) {
        when {
            match.scored > match.conceded -> wins++
            match.scored == match.conceded -> draws++
            else -> losses++
        }
        scored += match.scored
        conceded += match.conceded
    }
    return PerformanceSummary(wins, draws, losses, scored, conceded)
}
