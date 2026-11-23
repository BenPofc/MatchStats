package br.com.matchstats.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsDatabaseTest {
    private lateinit var db: MatchStatsDatabase
    private lateinit var repo: MatchStatsRepository

    @Before fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MatchStatsDatabase::class.java).build()
        repo = MatchStatsRepository(db)
    }

    @After fun close() { db.close() }

    @Test
    fun aggregatesOnlyCompletedMatchesAndEligiblePlayersBySportAndTeam() = runBlocking {
        val football = team("União", Sport.FOOTBALL)
        val other = team("Estrela", Sport.FOOTBALL)
        val basket = team("Vila", Sport.BASKETBALL)
        val ana = player(football, "Ana")
        val bia = player(football, "Bia")
        val lia = player(other, "Lia")
        val sara = player(basket, "Sara")
        val victory = save(input(football, "Rival", 3, 1))
        val pending = save(input(football, "Futuro", 6, 0).copy(status = MatchStatus.SCHEDULED))
        val draw = save(input(other, "Empate", 1, 1))
        val basketLoss = save(input(basket, "Vila BC", 60, 72))
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(victory, mapOf(ana to StatValues(goals = 2), bia to StatValues(goals = 1, assists = 2))))
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(draw, mapOf(lia to StatValues(goals = 1, assists = 1))))
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(basketLoss, mapOf(sara to StatValues(points = 20, rebounds = 8, assists = 4))))

        // Registros novos exigem elegibilidade no momento da gravação.
        assertEquals(
            WriteResult.INVALID_MATCH_PLAYER,
            repo.saveMatchStats(victory, mapOf(lia to StatValues(goals = 1))),
        )
        // Uma linha legada de partida agendada continua fora do ranking.
        db.matchStats().upsert(listOf(PlayerMatchStats(pending, ana, StatValues(goals = 50))))

        val footballStats = repo.observeSportStatistics(Sport.FOOTBALL).first()
        val all = footballStats.forTeam(null)
        assertEquals(PerformanceSummary(1, 1, 0, 4, 2), all.performance)
        assertEquals(50, all.performance.winPercentage)
        assertEquals(listOf("Ana", "Bia", "Lia"), all.ranking(StatMetric.GOALS).map { it.playerName })
        assertEquals(listOf(2L, 1L, 1L), all.ranking(StatMetric.GOALS).map { it.total })
        assertEquals("Bia", all.ranking(StatMetric.ASSISTS).first().playerName)
        assertEquals(PerformanceSummary(1, 0, 0, 3, 1), footballStats.forTeam(football).performance)
        assertEquals(listOf("Lia"), footballStats.forTeam(other).ranking(StatMetric.GOALS).map { it.playerName })

        val basketball = repo.observeSportStatistics(Sport.BASKETBALL).first().forTeam(null)
        assertEquals(PerformanceSummary(0, 0, 1, 60, 72), basketball.performance)
        assertEquals(20L, basketball.ranking(StatMetric.POINTS).single().total)
        assertEquals(8L, basketball.ranking(StatMetric.REBOUNDS).single().total)
        assertEquals(4L, basketball.ranking(StatMetric.ASSISTS).single().total)
        assertTrue(basketball.ranking(StatMetric.GOALS).isEmpty())
    }

    @Test
    fun resultsAndRankingRefreshAfterEditAndDeletionUsingOneActiveFlow() = runBlocking {
        val teamId = team("União", Sport.FOOTBALL)
        val player = player(teamId, "Ana")
        val originallyWon = input(teamId, "Rival", 4, 2)
        val match = save(originallyWon)
        val updates = Channel<SportStatistics>(Channel.CONFLATED)
        val collection = launch { repo.observeSportStatistics(Sport.FOOTBALL).collect { updates.send(it) } }
        try {
            assertEquals(100, updates.await { it.matches.size == 1 }.forTeam(null).performance.winPercentage)
            assertEquals(WriteResult.SAVED, repo.saveMatchStats(match, mapOf(player to StatValues(goals = 3))))
            updates.await { it.forTeam(null).ranking(StatMetric.GOALS).singleOrNull()?.total == 3L }

            assertEquals(WriteResult.SAVED, repo.saveMatch(match, originallyWon.copy(opponentScore = "5"), ZoneOffset.UTC))
            val updated = updates.await { it.matches.singleOrNull()?.conceded == 5 }.forTeam(null)
            assertEquals(PerformanceSummary(0, 0, 1, 4, 5), updated.performance)
            assertEquals(updated.performance, repo.observeTeamPerformance(teamId).first())

            assertEquals(WriteResult.SAVED, repo.deletePlayer(player))
            updates.await { it.playerTotals.isEmpty() }
            assertEquals(WriteResult.SAVED, repo.deleteMatch(match))
            val empty = updates.await { it.matches.isEmpty() }.forTeam(null)
            assertEquals(0, empty.performance.completed)
            assertEquals(0, empty.performance.winPercentage)
            assertTrue(empty.ranking(StatMetric.GOALS).isEmpty())
        } finally {
            collection.cancel()
            updates.close()
        }
    }

    @Test
    fun goalLimitIncludesUnchangedPlayersAndRejectsLoweringTheScore() = runBlocking {
        val teamId = team("União", Sport.FOOTBALL)
        val ana = player(teamId, "Ana")
        val bia = player(teamId, "Bia")
        val record = input(teamId, "Rival", 2, 1)
        val match = save(record)
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(match, mapOf(ana to StatValues(goals = 1))))
        assertEquals(WriteResult.GOALS_EXCEED_SCORE, repo.saveMatchStats(match, mapOf(bia to StatValues(goals = 2))))
        assertEquals(listOf(ana), db.matchStats().forMatch(match).map { it.playerId })
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(match, mapOf(bia to StatValues(goals = 1))))
        assertEquals(WriteResult.SCORE_BELOW_RECORDED_GOALS, repo.saveMatch(match, record.copy(teamScore = "1"), ZoneOffset.UTC))
        assertEquals(2, repo.observeMatch(match).first()!!.match.teamScore)
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(match, mapOf(ana to StatValues(), bia to StatValues(goals = 2))))
        assertEquals(2, db.matchStats().forMatch(match).single().values.goals)
    }

    @Test
    fun legacyExcessIsPreservedUntilAnExplicitCorrection() = runBlocking {
        val teamId = team("União", Sport.FOOTBALL)
        val player = player(teamId, "Ana")
        val input = input(teamId, "Rival", 2, 0)
        val match = save(input)
        db.matchStats().upsert(listOf(PlayerMatchStats(match, player, StatValues(goals = 3))))
        assertEquals(WriteResult.SAVED, repo.saveMatch(match, input.copy(location = "Ginásio"), ZoneOffset.UTC))
        assertEquals(3, repo.observeMatchSheet(match).first()!!.saved.single().values.goals)
        assertEquals(WriteResult.GOALS_EXCEED_SCORE, repo.saveMatchStats(match, mapOf(player to StatValues(goals = 3, assists = 1))))
        assertEquals(StatValues(goals = 3), db.matchStats().forMatch(match).single().values)
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(match, mapOf(player to StatValues(goals = 2))))
        assertEquals(StatValues(goals = 2), db.matchStats().forMatch(match).single().values)
    }

    @Test
    fun concurrentWritesCannotTogetherExceedTheScore() = runBlocking {
        val teamId = team("União", Sport.FOOTBALL)
        val ana = player(teamId, "Ana")
        val bia = player(teamId, "Bia")
        val match = save(input(teamId, "Rival", 2, 1))
        val one = async { repo.saveMatchStats(match, mapOf(ana to StatValues(goals = 2))) }
        val two = async { repo.saveMatchStats(match, mapOf(bia to StatValues(goals = 2))) }
        assertEquals(setOf(WriteResult.SAVED, WriteResult.GOALS_EXCEED_SCORE), setOf(one.await(), two.await()))
        assertEquals(2L, db.matchStats().totalGoals(match))
    }

    private suspend fun Channel<SportStatistics>.await(predicate: (SportStatistics) -> Boolean) =
        withTimeout(10_000) {
            var stats = receive()
            while (!predicate(stats)) stats = receive()
            stats
        }

    private suspend fun team(name: String, sport: Sport) = db.teams().insert(Team(name, sport, null, null))
    private suspend fun player(teamId: Long, name: String): Long {
        val id = db.players().insert(Player(name))
        db.players().insertMembership(TeamMembership(id, teamId, null, null, false))
        return id
    }
    private suspend fun save(input: MatchInput): Long {
        assertEquals(WriteResult.SAVED, repo.saveMatch(null, input, ZoneOffset.UTC))
        return repo.observeMatches(input.status).first().single { it.match.opponent == input.opponent }.match.id
    }

    private fun input(teamId: Long, opponent: String, score: Int, against: Int) =
        MatchInput(teamId, opponent, LocalDate.of(2026, 11, 1), LocalTime.NOON, "", MatchStatus.FINISHED, score.toString(), against.toString())
}
