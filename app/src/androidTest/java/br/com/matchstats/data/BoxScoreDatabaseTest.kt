package br.com.matchstats.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoxScoreDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun snapshotsDefaultsAndKeepsReorderedOverridesAndArchivedValuesAcrossReopen() = runBlocking {
        val filename = "matchstats-box-${UUID.randomUUID()}.db"
        val open = { Room.databaseBuilder(context, MatchStatsDatabase::class.java, filename).build() }
        var matchId = 0L
        var playerId = 0L
        val custom = BoxCustomMetric.create(Sport.BASKETBALL, "Bolas recuperadas", "BR", BoxMetricKind.TOTAL)
        val pair = BoxCustomMetric.create(Sport.BASKETBALL, "Enterradas", "ENT", BoxMetricKind.PAIR)
        try {
            val first = open()
            try {
                val repo = MatchStatsRepository(first)
                val basket = repo.observeBoxConfiguration(Sport.BASKETBALL).first()!!
                val soccer = repo.observeBoxConfiguration(Sport.FOOTBALL).first()!!
                assertEquals(16, basket.visible.size)
                assertEquals(11, soccer.visible.size)
                assertEquals(BoxBuiltin.MINUTES.id, basket.visible.first().id)
                assertEquals(BoxBuiltin.GOALS.id, soccer.visible.first().id)

                val preview = listOf(BoxColumn(custom.id, true), BoxColumn(pair.id, true)) + basket.columns
                assertEquals(WriteResult.SAVED, repo.saveBoxConfiguration(Sport.BASKETBALL, null, preview, listOf(custom, pair)))
                val team = first.teams().insert(Team("Vila", Sport.BASKETBALL, null, null))
                assertEquals(WriteResult.SAVED, repo.createPlayer(PlayerInput("Lia", team, "12", null, false)))
                playerId = repo.observeRoster(team).first()!!.players.single().id
                matchId = match(repo, team, "Águias")
                var score = repo.observeBoxScore(matchId).first()!!
                assertEquals(listOf(custom.id, pair.id), score.config.visible.take(2).map { it.id })
                assertEquals(12, score.players.single().jersey)
                assertFalse(score.hasData)
                assertEquals(
                    WriteResult.INVALID_BOX_PAIR,
                    repo.saveBoxPlayer(matchId, playerId, mapOf(pair.id to BoxMeasurement(3, 2))),
                )
                assertEquals(
                    WriteResult.SAVED,
                    repo.saveBoxPlayer(matchId, playerId, mapOf(custom.id to BoxMeasurement(9), pair.id to BoxMeasurement(2, 3))),
                )
                val points = score.config.columns.single { it.metricId == BoxBuiltin.POINTS.id }
                val hidden = listOf(points) + score.config.columns.filterNot { it == points }
                    .map { if (it.metricId == custom.id) it.copy(enabled = false) else it }
                assertEquals(WriteResult.SAVED, repo.saveBoxConfiguration(Sport.BASKETBALL, matchId, hidden))
                score = repo.observeBoxScore(matchId).first()!!
                assertEquals(points.metricId, score.config.visible.first().id)
                assertFalse(score.config.visible.any { it.id == custom.id })
                assertEquals(
                    WriteResult.INVALID_BOX_METRIC,
                    repo.saveBoxPlayer(matchId, playerId, mapOf(custom.id to BoxMeasurement(1))),
                )
                val newDefault = preview.filterNot { it.metricId == custom.id }
                assertEquals(
                    WriteResult.SAVED,
                    repo.saveBoxConfiguration(
                        Sport.BASKETBALL, null, newDefault,
                        custom = listOf(pair.copy(name = "Enterradas registradas", abbreviation = "EN")),
                        archiveIds = setOf(custom.id),
                    ),
                )
                val following = match(repo, team, "Leões")
                assertFalse(repo.observeBoxScore(following).first()!!.config.metrics.containsKey(custom.id))
                assertEquals(pair.id, repo.observeBoxScore(following).first()!!.config.visible.first().id)
            } finally {
                first.close()
            }

            val reopened = open()
            try {
                val repo = MatchStatsRepository(reopened)
                val old = repo.observeBoxScore(matchId).first()!!
                assertEquals(BoxBuiltin.POINTS.id, old.config.visible.first().id)
                assertTrue(old.config.metrics.getValue(custom.id).custom!!.archived)
                assertFalse(old.config.visible.any { it.id == custom.id })
                val reenabled = old.config.columns.map { if (it.metricId == custom.id) it.copy(enabled = true) else it }
                assertEquals(WriteResult.SAVED, repo.saveBoxConfiguration(Sport.BASKETBALL, matchId, reenabled))
                val score = repo.observeBoxScore(matchId).first()!!
                assertEquals(BoxCell(9, null), score.config.metrics.getValue(custom.id).cell(score.players))
                assertEquals("Enterradas registradas", score.config.metrics.getValue(pair.id).custom!!.name)
                assertEquals(BoxCell(2, 3), score.config.metrics.getValue(pair.id).cell(score.players))
                assertEquals(1, repo.observePlayer(playerId).first()!!.games.size)
                assertEquals(WriteResult.SAVED, repo.removeMembership(playerId, score.fixture.team.id))
                assertEquals(playerId, repo.observeBoxScore(matchId).first()!!.players.single().player.id)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
        Unit
    }

    @Test
    fun sharesCanonicalFieldsAndCalculatesWeightedShootingAndTeamTotals() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MatchStatsDatabase::class.java).build()
        try {
            val repo = MatchStatsRepository(db)
            val football = db.teams().insert(Team("União", Sport.FOOTBALL, null, null))
            val basketball = db.teams().insert(Team("Vila", Sport.BASKETBALL, null, null))
            for ((team, name) in listOf(football to "Lia", basketball to "Ana", basketball to "Bia")) {
                assertEquals(WriteResult.SAVED, repo.createPlayer(PlayerInput(name, team, "", null, false)))
            }
            val soccer = match(repo, football, "Rivais")
            val basket = match(repo, basketball, "Águias")
            val footballPlayer = repo.observeRoster(football).first()!!.players.single().id
            val (ana, bia) = repo.observeRoster(basketball).first()!!.players.map { it.id }
            assertEquals(
                WriteResult.GOALS_EXCEED_SCORE,
                repo.saveBoxPlayer(soccer, footballPlayer, mapOf(
                    BoxBuiltin.GOALS.id to BoxMeasurement(41), BoxBuiltin.PASSES.id to BoxMeasurement(8, 10),
                )),
            )
            assertFalse(repo.observeBoxScore(soccer).first()!!.hasData)
            assertEquals(
                WriteResult.SAVED,
                repo.saveBoxPlayer(soccer, footballPlayer, mapOf(
                    BoxBuiltin.GOALS.id to BoxMeasurement(2), BoxBuiltin.PASSES.id to BoxMeasurement(8, 10),
                )),
            )
            assertEquals(2, repo.observeMatchSheet(soccer).first()!!.saved.single().values.goals)
            val soccerScore = repo.observeBoxScore(soccer).first()!!
            assertEquals(80.0, soccerScore.config.metrics.getValue(BoxBuiltin.PASSES.id).cell(soccerScore.players)!!.percentage!!, 0.001)
            assertEquals(2L, repo.observeSportStatistics(Sport.FOOTBALL).first().forTeam(null).ranking(StatMetric.GOALS).single().total)
            assertEquals(WriteResult.SAVED, repo.saveMatchStats(basket, mapOf(ana to StatValues(points = 18, rebounds = 7, assists = 2))))
            assertEquals(
                WriteResult.SAVED,
                repo.saveBoxPlayer(basket, ana, mapOf(BoxBuiltin.TWO.id to BoxMeasurement(3, 5), BoxBuiltin.THREE.id to BoxMeasurement(2, 4))),
            )
            assertEquals(
                WriteResult.SAVED,
                repo.saveBoxPlayer(basket, bia, mapOf(
                    BoxBuiltin.POINTS.id to BoxMeasurement(4), BoxBuiltin.TWO.id to BoxMeasurement(1, 5),
                    BoxBuiltin.FREE.id to BoxMeasurement(0, 0),
                )),
            )
            val score = repo.observeBoxScore(basket).first()!!
            fun total(builtin: BoxBuiltin) = score.config.metrics.getValue(builtin.id).cell(score.players)
            assertEquals(BoxCell(22, null), total(BoxBuiltin.POINTS))
            assertEquals(BoxCell(7, null), total(BoxBuiltin.REBOUNDS))
            assertEquals(BoxCell(2, null), total(BoxBuiltin.BASKET_ASSISTS))
            assertNull(total(BoxBuiltin.OFFENSIVE))
            assertNull(total(BoxBuiltin.DEFENSIVE))
            assertEquals(BoxCell(6, 14), total(BoxBuiltin.FIELD_PERCENT))
            assertEquals(100.0 * 6.0 / 14.0, total(BoxBuiltin.FIELD_PERCENT)!!.percentage!!, 0.001)
            assertEquals(50.0, total(BoxBuiltin.THREE_PERCENT)!!.percentage!!, 0.001)
            assertNull(total(BoxBuiltin.FREE_PERCENT)!!.percentage)
            assertEquals(WriteResult.SAVED, repo.saveMatchStats(basket, mapOf(ana to StatValues())))
            assertEquals(
                BoxMeasurement(3, 5),
                repo.observeBoxScore(basket).first()!!.players.single { it.player.id == ana }.measurements[BoxBuiltin.TWO.id],
            )
            assertEquals(1, repo.observePlayer(ana).first()!!.games.size)
            assertEquals(4L, repo.observeSportStatistics(Sport.BASKETBALL).first().forTeam(null).ranking(StatMetric.POINTS).single().total)
        } finally {
            db.close()
        }
        Unit
    }

    private suspend fun match(repo: MatchStatsRepository, team: Long, opponent: String): Long {
        val input = MatchInput(team, opponent, LocalDate.now(), LocalTime.NOON, "", MatchStatus.FINISHED, "40", "20")
        assertEquals(WriteResult.SAVED, repo.saveMatch(null, input))
        return repo.observeMatches(MatchStatus.FINISHED).first().single { it.match.opponent == opponent }.match.id
    }
}
