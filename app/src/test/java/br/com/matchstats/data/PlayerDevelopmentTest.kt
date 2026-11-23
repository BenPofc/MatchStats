package br.com.matchstats.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDevelopmentTest {
    private val player = Player("Vitória", id = 6)
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 11, 19)

    @Test fun basketballHistorySortsAndFiltersActualMatchTeams() {
        val input = development(Sport.BASKETBALL, listOf(
            game(3, 21, today, Sport.BASKETBALL, StatValues(points = 12)),
            game(1, 19, today.minusDays(10), Sport.BASKETBALL, StatValues(points = 8)),
            game(2, 19, today.minusDays(2), Sport.BASKETBALL, StatValues(points = 16)),
            game(4, 19, today, Sport.FOOTBALL, StatValues(goals = 2)),
        ))
        val metric = input.gameMetrics().first()
        val all = input.gameSeries(metric, DEVELOPMENT_ALL_TEAMS, null, ::title)
        assertEquals(listOf(1L, 2L, 3L), all.points.map { it.id })
        assertEquals(36L, all.total)
        assertEquals(12.0, all.average!!, .01)
        val teamWeek = input.gameSeries(metric, 19, DevelopmentPeriod.WEEK.bounds(today, zone), ::title)
        assertEquals(listOf(2L), teamWeek.points.map { it.id })
        assertEquals(16L, teamWeek.total)
        assertTrue(input.gameSeries(metric, 999, null, ::title).points.isEmpty())
        assertNull(DevelopmentSeries(emptyList()).average)
    }

    @Test fun soccerGoalsAndPassingDoNotInventMissingAttempts() {
        val input = development(Sport.FOOTBALL, listOf(
            game(1, 10, today.minusDays(2), Sport.FOOTBALL, StatValues(goals = 2)),
            game(2, 10, today.minusDays(1), Sport.FOOTBALL, StatValues(goals = 1)),
            game(3, 10, today, Sport.FOOTBALL, StatValues(goals = 0)),
        ), mapOf(
            1L to mapOf(BoxBuiltin.PASSES.id to BoxMeasurement(4, 5)),
            2L to mapOf(BoxBuiltin.PASSES.id to BoxMeasurement(0, 0)),
        ))
        val goals = input.gameSeries(input.gameMetrics().first(), 10, null, ::title)
        assertEquals(listOf(2L, 1L, 0L), goals.points.map { it.measure.value })
        val passing = input.gameSeries(input.gameMetrics().first { it.id == BoxBuiltin.PASSES.id }, 10, null, ::title)
        assertEquals(listOf(1L, 2L), passing.points.map { it.id })
        assertEquals(80.0, passing.accuracy!!, .01)
        assertNull(passing.points.last().plotted(rate = true))
    }

    @Test fun weightedPracticesGroupDrillsInEachSessionAndExcludeUnrelatedTeams() {
        val input = development(Sport.BASKETBALL, facts = listOf(
            practice(3, 2, today, 10, "Lances livres", 0, 0),
            practice(2, 1, today.minusDays(2), 10, "Lances livres", 9, 10),
            practice(1, 1, today.minusDays(4), 10, "Lances livres", 5, 20),
            practice(1, 2, today.minusDays(4), 10, "Lances livres", 0, 0),
            practice(4, 4, today, 21, "Lances livres", 200, 300),
            practice(5, 5, today, null, "Repetições", 12, null),
        ))
        val drill = input.drills(10).single()
        val rate = input.practiceMetrics(drill).first { it.value == DevelopmentValue.RATE }
        val series = input.practiceSeries(rate, 10, null, drill)
        assertEquals(listOf(1L, 2L, 3L), series.points.map { it.id })
        assertEquals(14L, series.total)
        assertEquals(30L, series.attempts)
        assertEquals(46.6667, series.accuracy!!, .001)
        assertNull(series.points.last().measure.percentage)
        val unassigned = input.drills(DEVELOPMENT_UNASSIGNED).single()
        val count = input.practiceMetrics(unassigned).first { it.value == DevelopmentValue.REPETITIONS }
        assertEquals(12L, input.practiceSeries(count, DEVELOPMENT_UNASSIGNED, null, unassigned).total)
    }

    @Test fun calendarFiltersIncludeFirstDayAndExcludeTomorrow() {
        val month = DevelopmentPeriod.MONTH.bounds(today, zone)!!
        assertFalse(at(today.minusDays(30)) in month)
        assertTrue(at(today.minusDays(29)) in month)
        assertTrue(at(today.plusDays(1)) - 1 in month)
        assertFalse(at(today.plusDays(1)) in month)
        assertNull(DevelopmentPeriod.ALL.bounds(today, zone))
    }

    @Test fun reportFilenameRemovesUnsafeCharactersAndBoundsLongNames() {
        assertEquals("MatchStats_Uniao_vs_Atletico_2026-11-19.pdf",
            matchReportFilename("../União?", "Atlético\\", today))
        val longName = matchReportFilename("Á".repeat(400), "/.../", today)
        assertTrue(longName.matches(Regex("MatchStats_[A-Za-z0-9_]+_vs_Time_2026-11-19[.]pdf")))
        assertTrue(longName.length < 100)
    }

    private fun development(
        sport: Sport,
        games: List<PlayerGame> = emptyList(),
        extra: Map<Long, Map<String, BoxMeasurement>> = emptyMap(),
        facts: List<PracticeFact> = emptyList(),
    ) = PlayerDevelopment(player, sport, emptyList(), games, extra, emptyList(), facts)

    private fun game(id: Long, team: Long, date: LocalDate, sport: Sport, values: StatValues) =
        PlayerGame(Match(team, "Rivais", at(date), null, MatchStatus.FINISHED, 5, 1, id = id), "Casa", sport, values)

    private fun practice(id: Long, drill: Long, date: LocalDate, team: Long?, name: String, value: Long, attempts: Long?) =
        PracticeFact(id, "Treino $id", at(date), Sport.BASKETBALL, team, player.id, player.name, drill, name,
            if (attempts == null) PracticeMeasure.COUNT else PracticeMeasure.MAKES_AND_ATTEMPTS, value, attempts)

    private fun at(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun title(team: String, opponent: String) = "$team × $opponent"
}
