package br.com.matchstats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceStatisticsTest {
    @Test
    fun emptyPerformanceHasZeroTotalsAndPercent() {
        val empty = SportStatistics(emptyList(), emptyList(), emptyList()).forTeam(null)
        assertEquals(PerformanceSummary(0, 0, 0, 0, 0), empty.performance)
        assertEquals(0, empty.performance.winPercentage)
        assertTrue(empty.recent.isEmpty())
        assertTrue(empty.ranking(StatMetric.GOALS).isEmpty())
    }

    @Test
    fun ratesRoundUsingAllDecisionsAndScoreTotalsUseLongs() {
        val scores = listOf(
            match(1, scored = Int.MAX_VALUE, conceded = 1),
            match(2, scored = Int.MAX_VALUE, conceded = Int.MAX_VALUE),
            match(3, scored = 0, conceded = 1),
        )
        val totals = summarizePerformance(scores)
        assertEquals(3, totals.completed)
        assertEquals(PerformanceSummary(1, 1, 1, 4294967294, 2147483649), totals)
        assertEquals(33, totals.winPercentage)
        assertEquals(67, PerformanceSummary(2, 0, 1, 0, 0).winPercentage)
        assertEquals(100, PerformanceSummary(1, 0, 0, 0, 0).winPercentage)
    }

    @Test
    fun rankOrdersEachMetricOnItsOwnAndExcludesZeroValues() {
        val players = listOf(
            player(5, "Bia", goals = 3, assists = 1),
            player(2, "Ana", goals = 3, assists = 0),
            player(1, "Ana", goals = 3, assists = 3),
            player(8, "Lia", goals = 0, assists = 5),
        )
        val data = SportStatistics(emptyList(), emptyList(), players).forTeam(null)
        assertEquals(listOf(1L, 2L, 5L), data.ranking(StatMetric.GOALS).map { it.playerId })
        assertEquals(listOf(8L, 1L, 5L), data.ranking(StatMetric.ASSISTS).map { it.playerId })
    }

    @Test
    fun selectingTeamScopesRankingsAndRetainsOnlyFourMostRecentScores() {
        val scores = (1L..6).map { match(it, scored = it.toInt(), conceded = 0) } +
            match(7, team = 2, scored = 10, conceded = 0)
        val players = listOf(
            player(1, "Ana", goals = 1),
            player(2, "Lia", team = 2, goals = 5),
        )
        val data = SportStatistics(emptyList(), scores.reversed(), players)
        val team = data.forTeam(1)
        assertEquals(6, team.performance.completed)
        assertEquals(listOf(3L, 4L, 5L, 6L), team.recent.map { it.id })
        assertEquals("Ana", team.ranking(StatMetric.GOALS).single().playerName)
        assertEquals("Lia", data.forTeam(null).ranking(StatMetric.GOALS).first().playerName)
    }

    private fun match(id: Long, team: Long = 1, scored: Int, conceded: Int) =
        ScoredMatch(id, team, "Adversário", id * 1000, scored, conceded)

    private fun player(id: Long, name: String, team: Long = 1, goals: Long, assists: Long = 0) =
        PlayerTotals(id, name, team, "Equipe", goals, assists, 0, 0)
}
