package br.com.matchstats.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeDatabaseTest {
    private lateinit var db: MatchStatsDatabase
    private lateinit var repo: MatchStatsRepository
    private lateinit var store: PracticeStore
    private var elapsed = 1_000L

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MatchStatsDatabase::class.java).build()
        repo = MatchStatsRepository(db)
        store = PracticeStore(db, { elapsed }, "practice-clock-first")
    }

    @After fun close() = db.close()

    @Test
    fun reusableRosterAndIndividualPracticesUpdateHome() = runBlocking {
        val teamId = db.teams().insert(Team("Vila", Sport.BASKETBALL, null, null))
        val ana = db.players().insert(Player("Ana"))
        val bia = db.players().insert(Player("Bia"))
        db.players().insertMembership(TeamMembership(ana, teamId, 8, null, false))
        val start = System.currentTimeMillis() + 3_600_000
        val teamPractice = create("Bandejas", Sport.FOOTBALL, setOf(ana, bia), teamId, start)
        val solo = create("Finalização", Sport.FOOTBALL, setOf(ana), startsAt = start + 1)

        val data = store.details(teamPractice).first()!!
        assertEquals(Sport.BASKETBALL, data.summary.session.sport)
        assertEquals(listOf(ana, bia), data.players.map { it.playerId })
        assertEquals(listOf(8, null), data.players.map { it.jerseyNumber })
        assertEquals(Sport.FOOTBALL, store.details(solo).first()!!.summary.session.sport)
        assertNull(store.details(solo).first()!!.summary.teamName)
        assertEquals(ana, store.details(solo).first()!!.players.single().playerId)
        assertEquals(2, repo.observeHome().first().practiceCount)
        assertEquals(teamPractice, repo.observeHome().first().nextPractice!!.session.id)
        assertEquals(2, repo.observeHome().first().counts.players)
        saved(store.start(teamPractice))
        assertEquals(solo, repo.observeHome().first().nextPractice!!.session.id)
        saved(store.complete(teamPractice))
        assertEquals(teamPractice, store.list(true).first().single().session.id)
        assertEquals(1, store.facts(Sport.BASKETBALL, playerId = ana).first().practicePerformance().sessions)
        assertEquals(0, store.facts(Sport.FOOTBALL, playerId = ana).first().practicePerformance().sessions)
    }

    @Test
    fun bulkIsAtomicAndConcurrentRetriesAndCorrectionsDoNotAddDuplicates() = runBlocking {
        val ana = db.players().insert(Player("Ana"))
        val bia = db.players().insert(Player("Bia"))
        val id = create("Arremessos", Sport.BASKETBALL, setOf(ana, bia))
        val pair = drill(id, "Lances livres", PracticeMeasure.MAKES_AND_ATTEMPTS)
        val count = drill(id, "Rebotes", PracticeMeasure.COUNT)
        assertEquals(WriteResult.PRACTICE_CLOSED, store.submit(id, pair, "early", listOf(PracticeRow(ana, 5, 10))))
        saved(store.start(id))
        val rows = listOf(PracticeRow(ana, 15, 20), PracticeRow(bia, 6, 10))
        val first = async { store.submit(id, pair, "first", rows) }
        val retry = async { store.submit(id, pair, "first", rows.reversed()) }
        saved(first.await())
        saved(retry.await())
        assertEquals(2, store.history(id).first().size)
        assertEquals(WriteResult.INVALID_STATS, store.submit(id, pair, "invalid",
            listOf(PracticeRow(ana, 9, 8), PracticeRow(bia, 2, 2))))
        assertEquals(WriteResult.INVALID_STATS, store.submit(id, count, "invalid-count", listOf(PracticeRow(ana, -1, null))))
        assertEquals(WriteResult.PRACTICE_SUBMISSION_CONFLICT, store.submit(id, pair, "first", rows.take(1)))
        assertEquals(2, store.history(id).first().size)

        saved(store.submit(id, pair, "second", listOf(PracticeRow(ana, 10, 20))))
        saved(store.submit(id, count, "rebound", listOf(PracticeRow(ana, 12, null))))
        assertTrue(store.details(id).first()!!.totals.contains(PracticeTotals(ana, pair, 25, 40)))
        val old = store.history(id).first().single { it.batch.submissionId == "first" && it.batch.playerId == ana }.batch
        saved(store.correct(old, PracticeRow(ana, 5, 10)))
        saved(store.correct(old, PracticeRow(ana, 5, 10)))
        saved(store.submit(id, pair, "first", rows))
        assertEquals(WriteResult.PRACTICE_SUBMISSION_CONFLICT, store.correct(old, PracticeRow(ana, 6, 10)))
        assertTrue(store.details(id).first()!!.totals.contains(PracticeTotals(ana, pair, 15, 30)))
        assertTrue(store.details(id).first()!!.totals.contains(PracticeTotals(ana, count, 12, null)))
        assertEquals(4, store.history(id).first().size)
        assertEquals(WriteResult.PRACTICE_PLAYER_HAS_BATCHES,
            store.save(id, PracticeInput("Arremessos", Sport.BASKETBALL, null, 1, "", "", setOf(bia))).first)

        saved(store.complete(id))
        assertEquals(PracticeStatus.COMPLETED, store.details(id).first()!!.summary.session.status)
        assertEquals(WriteResult.PRACTICE_CLOSED, store.submit(id, pair, "late", listOf(PracticeRow(ana, 1, 1))))
        val performance = store.facts(Sport.BASKETBALL, playerId = ana).first().practicePerformance()
        assertEquals(PracticePerformance(1, 2, 15, 30, 12), performance)
        assertEquals(0.5, performance.accuracy!!, 0.0001)
        saved(store.delete(id))
        assertTrue(store.history(id).first().isEmpty())
        assertTrue(store.facts(Sport.BASKETBALL).first().isEmpty())
        assertNotNull(db.players().find(ana))
    }

    @Test
    fun weightedStatisticsAndGameRankingsUseIndependentRecords() = runBlocking {
        val athlete = db.players().insert(Player("Rafael"))
        val team = db.teams().insert(Team("Pioneiros", Sport.FOOTBALL, null, null))
        db.players().insertMembership(TeamMembership(athlete, team, 10, Position.FORWARD, false))
        val game = db.matches().insert(Match(team, "Rivais", 1, null, MatchStatus.FINISHED, 2, 1))
        db.matchStats().upsert(listOf(PlayerMatchStats(game, athlete, StatValues(goals = 2, assists = 1))))
        val first = create("Campo 1", Sport.FOOTBALL, setOf(athlete), team)
        val second = create("Campo 2", Sport.FOOTBALL, setOf(athlete))
        listOf(first to PracticeRow(athlete, 5, 10), second to PracticeRow(athlete, 9, 30)).forEach { (id, row) ->
            val drill = drill(id, "Pênaltis", PracticeMeasure.MAKES_AND_ATTEMPTS)
            saved(store.start(id))
            saved(store.submit(id, drill, "shot-$id", listOf(row)))
            saved(store.complete(id))
        }
        val zero = create("Quadra", Sport.BASKETBALL, setOf(athlete))
        val drills = drill(zero, "Bandejas", PracticeMeasure.MAKES_AND_ATTEMPTS)
        saved(store.start(zero))
        saved(store.submit(zero, drills, "zero", listOf(PracticeRow(athlete, 0, 0))))
        saved(store.complete(zero))
        val football = store.facts(Sport.FOOTBALL).first().practicePerformance()
        assertEquals(PracticePerformance(2, 2, 14, 40, 0), football)
        assertEquals(0.35, football.accuracy!!, 0.0001)
        assertEquals(PracticePerformance(1, 1, 5, 10, 0), store.facts(Sport.FOOTBALL, teamId = team).first().practicePerformance())
        assertNull(store.facts(Sport.BASKETBALL).first().practicePerformance().accuracy)
        assertEquals(2L, repo.observeSportStatistics(Sport.FOOTBALL).first().forTeam(null).ranking(StatMetric.GOALS).single().total)
        assertEquals(2, repo.observeBoxScore(game).first()!!.fixture.match.teamScore)
        assertEquals(1, repo.observePlayer(athlete).first()!!.games.size)
    }

    @Test
    fun timerRestoresOnlyCheckpointAndCanResumePauseAndStop() = runBlocking {
        val player = db.players().insert(Player("Beatriz"))
        val id = create("Pista", Sport.FOOTBALL, setOf(player))
        saved(store.start(id))
        saved(store.clock(id, PracticeClockAction.START))
        elapsed += 1_500
        saved(store.checkpoint(id))
        elapsed += 50_000
        val restarted = PracticeStore(db, { elapsed }, "practice-clock-restarted")
        fun session() = runBlocking { db.practices().session(id)!! }
        assertFalse(restarted.isRunning(session()))
        assertEquals(1_500L, restarted.elapsedFor(session()))
        saved(restarted.checkpoint(id))
        assertNull(session().runningSince)
        saved(restarted.clock(id, PracticeClockAction.RESUME))
        elapsed += 700
        saved(restarted.clock(id, PracticeClockAction.PAUSE))
        assertEquals(2_200L, restarted.elapsedFor(session()))
        saved(restarted.clock(id, PracticeClockAction.RESUME))
        elapsed += 300
        saved(restarted.clock(id, PracticeClockAction.STOP))
        assertEquals(2_500L, restarted.elapsedFor(session()))
        assertTrue(session().timerStopped)
        assertFalse(restarted.isRunning(session()))
    }

    private suspend fun create(title: String, sport: Sport, players: Set<Long>, team: Long? = null, startsAt: Long = 1): Long {
        val (result, id) = store.save(null, PracticeInput(title, sport, team, startsAt, "", "", players))
        saved(result)
        return checkNotNull(id)
    }

    private suspend fun drill(practiceId: Long, name: String, type: PracticeMeasure): Long {
        val (result, id) = store.addDrill(practiceId, name, type, "")
        saved(result)
        return checkNotNull(id)
    }

    private fun saved(result: WriteResult) = assertEquals(WriteResult.SAVED, result)
}
