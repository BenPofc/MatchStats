package br.com.matchstats.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveGameDatabaseTest {
    private lateinit var db: MatchStatsDatabase
    private lateinit var repo: MatchStatsRepository
    private lateinit var store: LiveGameStore
    private var elapsed = 100L

    @Before fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MatchStatsDatabase::class.java).build()
        repo = MatchStatsRepository(db)
        store = LiveGameStore(db, "first-process", { elapsed })
    }

    @After fun close() = db.close()

    @Test
    fun basketballClocksPauseClampAndRestoreWithANewProcessToken() = runBlocking {
        val (id, _) = match(Sport.BASKETBALL)
        saved(store.start(id, 10, 24))
        saved(store.start(id, 1, 5))
        assertEquals(600_000L, session(id).gameMs)
        assertEquals(24_000L, session(id).shotMs)
        saved(store.setRunning(id, true))
        elapsed += 7_125
        assertEquals(592_875L, store.clock(session(id)).gameMs)
        assertEquals(16_875L, store.clock(session(id)).shotMs)
        saved(store.setRunning(id, false))
        elapsed += 20_300
        assertEquals(592_875L, store.clock(session(id)).gameMs)
        assertEquals(16_875L, store.clock(session(id)).shotMs)
        saved(store.resetShot(id, true))
        assertEquals(14_000L, store.clock(session(id)).shotMs)
        saved(store.setRunning(id, true))
        elapsed += 16_000
        assertEquals(0L, store.clock(session(id)).shotMs)
        assertTrue(store.clock(session(id)).running)
        saved(store.resetShot(id, false))
        elapsed += 3_200
        saved(store.checkpoint(id))
        val checkpoint = session(id)
        elapsed += 50_000
        val restarted = LiveGameStore(db, "second-process", { elapsed })
        assertFalse(restarted.clock(session(id)).running)
        assertEquals(checkpoint.gameMs, restarted.clock(session(id)).gameMs)
        assertEquals(checkpoint.shotMs, restarted.clock(session(id)).shotMs)
        saved(restarted.setRunning(id, true))
        elapsed += 700_000
        assertFalse(restarted.clock(session(id)).running)
        assertEquals(0L, restarted.clock(session(id)).gameMs)
        assertEquals(0L, restarted.clock(session(id)).shotMs)
        saved(restarted.nextPeriod(id))
        assertEquals(2, session(id).period)
        assertEquals(600_000L, session(id).gameMs)
        assertFalse(restarted.clock(session(id)).running)
    }

    @Test
    fun basketballEventsApplyOnceAndUndoAgainstManualAndLiveMeasurements() = runBlocking {
        val (id, player) = match(Sport.BASKETBALL)
        db.matchStats().upsert(listOf(PlayerMatchStats(id, player, StatValues(points = 5, rebounds = 3))))
        db.boxScores().upsertMeasurements(listOf(
            BoxStoredMeasurement(id, player, BoxBuiltin.TWO.id, 1, 2),
            BoxStoredMeasurement(id, player, BoxBuiltin.FREE.id, 0, 0),
        ))
        saved(store.start(id, 10, 24))
        assertEquals(WriteResult.LIVE_ACTIVE, repo.saveMatchStats(id, mapOf(player to StatValues(points = 100))))
        assertEquals(WriteResult.LIVE_ACTIVE, repo.deleteMatch(id))
        val first = LiveInput(LiveSide.HOME, LiveAction.TWO_MADE.id, playerId = player)
        val a = async { store.record(id, "two", first) }
        val b = async { store.record(id, "two", first) }
        saved(a.await())
        saved(b.await())
        tap(id, "three-missed", LiveAction.THREE_MISSED, player)
        tap(id, "free", LiveAction.FREE_MADE, player)
        tap(id, "off", LiveAction.OFFENSIVE, player)
        tap(id, "def", LiveAction.DEFENSIVE, player)
        tap(id, "foul", LiveAction.BASKET_FOUL, player)
        saved(store.record(id, "their-shot", LiveInput(LiveSide.AWAY, LiveAction.THREE_MADE.id, jersey = 23, opponentName = "Nina")))
        val box = repo.observeBoxScore(id).first()!!
        val home = box.players.single()
        val away = box.opponents.single()
        assertEquals(8, home.canonical!!.points)
        assertEquals(5, home.canonical.rebounds)
        assertEquals(BoxMeasurement(2, 3), home.measurements[BoxBuiltin.TWO.id])
        assertEquals(BoxMeasurement(0, 1), home.measurements[BoxBuiltin.THREE.id])
        assertEquals(BoxMeasurement(1, 1), home.measurements[BoxBuiltin.FREE.id])
        assertEquals(BoxMeasurement(1), home.measurements[BoxBuiltin.OFFENSIVE.id])
        assertEquals(BoxMeasurement(1), home.measurements[BoxBuiltin.DEFENSIVE.id])
        assertEquals(50.0, box.config.metrics.getValue(BoxBuiltin.FIELD_PERCENT.id).cell(box.players)!!.percentage!!, 0.01)
        assertEquals(23, away.jersey)
        assertEquals("Nina", away.player.name)
        assertEquals(3, away.canonical!!.points)
        assertEquals(1, repo.observeDirectory().first().size)
        assertEquals(3, session(id).awayScore)

        saved(store.record(id, "correct-three", LiveInput(LiveSide.HOME, LiveAction.THREE_MADE.id, playerId = player), "two"))
        assertEquals(9, session(id).homeScore)
        saved(store.undo(id, "undo-foul", "foul"))
        saved(store.undo(id, "undo-foul", "foul"))
        assertEquals(WriteResult.LIVE_EVENT_ALREADY_CHANGED, store.undo(id, "undo-foul-again", "foul"))
        assertNull(repo.observeBoxScore(id).first()!!.players.single().measurements[BoxBuiltin.PERSONAL_FOULS.id])
        saved(store.undo(id, "undo-FT", "free"))
        assertEquals(BoxMeasurement(0, 0), repo.observeBoxScore(id).first()!!.players.single().measurements[BoxBuiltin.FREE.id])
        saved(store.finish(id))
        assertEquals(8, repo.observeMatch(id).first()!!.match.teamScore)
        assertEquals(8L, repo.observeSportStatistics(Sport.BASKETBALL).first().forTeam(null).ranking(StatMetric.POINTS).single().total)
        assertEquals(WriteResult.LIVE_EVENT_TOTALS, repo.saveBoxPlayer(id, player, mapOf(BoxBuiltin.POINTS.id to BoxMeasurement(2))))
        saved(repo.saveBoxPlayer(id, player, mapOf(BoxBuiltin.POINTS.id to BoxMeasurement(14))))
        saved(store.undo(id, "undo-three", "correct-three"))
        assertEquals(11, repo.observeMatchSheet(id).first()!!.saved.single().values.points)
        assertEquals(5, repo.observeMatch(id).first()!!.match.teamScore)
        assertEquals(11L, repo.observeSportStatistics(Sport.BASKETBALL).first().forTeam(null).ranking(StatMetric.POINTS).single().total)
        assertTrue(store.observe(id).first()!!.events.any { it.operation == LiveOperation.CORRECT })
    }

    @Test
    fun soccerAllowsTeamEventsAndProtectsGoalsDuringScoreCorrectionsAndDiscard() = runBlocking {
        val (id, player) = match(Sport.FOOTBALL)
        db.matchStats().upsert(listOf(PlayerMatchStats(id, player, StatValues(goals = 1))))
        saved(store.start(id, 45, 0))
        saved(store.setRunning(id, true))
        elapsed += 41_000
        saved(store.setRunning(id, false))
        assertEquals(41_000L, store.clock(session(id)).position(session(id), Sport.FOOTBALL))
        saved(store.nextPeriod(id))
        assertEquals(2_700_000L, store.clock(session(id)).position(session(id), Sport.FOOTBALL))
        assertEquals(WriteResult.INVALID_LIVE_CLOCK, store.nextPeriod(id))
        assertEquals(WriteResult.INVALID_LIVE_CLOCK, store.resetShot(id, false))
        saved(store.setRunning(id, true))
        elapsed += 1_000
        tap(id, "goal-home", LiveAction.GOAL, player)
        saved(store.record(id, "team-corner", LiveInput(LiveSide.HOME, LiveAction.CORNER.id)))
        saved(store.record(id, "guest-goal", LiveInput(LiveSide.AWAY, LiveAction.GOAL.id, jersey = 9)))
        assertEquals(2, session(id).homeScore)
        assertEquals(1, session(id).awayScore)
        saved(store.adjustScore(id, "adjust", LiveSide.HOME, 4))
        assertEquals(WriteResult.INVALID_LIVE_TOTAL, store.adjustScore(id, "bad-home", LiveSide.HOME, 1))
        assertEquals(WriteResult.INVALID_LIVE_TOTAL, store.adjustScore(id, "bad-away", LiveSide.AWAY, 0))
        saved(store.undo(id, "undo-adjust", "adjust"))
        assertEquals(2, session(id).homeScore)
        assertEquals(2_701_000L, store.observe(id).first()!!.events.first().clockMs)
        saved(store.discard(id))
        assertNull(db.liveGames().session(id))
        assertTrue(db.liveGames().events(id).isEmpty())
        assertEquals(1, db.matchStats().forMatch(id).single().values.goals)

        saved(store.start(id, 30, 0))
        tap(id, "new-goal", LiveAction.GOAL, player)
        saved(store.finish(id))
        assertEquals(MatchStatus.FINISHED, repo.observeMatch(id).first()!!.match.status)
        assertEquals(2L, repo.observeSportStatistics(Sport.FOOTBALL).first().forTeam(null).ranking(StatMetric.GOALS).single().total)
        assertEquals(2, repo.observeHome().first().results.single().match.teamScore)
    }

    @Test
    fun liveLayoutPersistsSeparateFromBoxColumnsAndSupportsCustomTotals() = runBlocking {
        val (id, player) = match(Sport.BASKETBALL)
        val custom = BoxCustomMetric.create(Sport.BASKETBALL, "Desvios", "DV", BoxMetricKind.TOTAL)
        val pair = BoxCustomMetric.create(Sport.BASKETBALL, "Enterradas", "ENT", BoxMetricKind.PAIR)
        val boxDefaults = repo.observeBoxConfiguration(Sport.BASKETBALL).first()!!
        saved(repo.saveBoxConfiguration(Sport.BASKETBALL, null, boxDefaults.columns, listOf(custom, pair)))
        val boxBefore = repo.observeBoxConfiguration(Sport.BASKETBALL, id).first()!!.columns
        val defaults = store.observeConfiguration(Sport.BASKETBALL, null).first()!!
        assertFalse(pair.id in defaults.catalog)
        val arranged = listOf(BoxColumn(custom.id, true), BoxColumn(LiveAction.TWO_MADE.id, false)) +
            defaults.columns.filterNot { it.metricId == custom.id || it.metricId == LiveAction.TWO_MADE.id }
        saved(store.saveConfiguration(Sport.BASKETBALL, null, arranged))
        saved(store.start(id, 10, 24))
        val palette = store.observe(id).first()!!.config
        assertEquals(custom.id, palette.visible.first().id)
        assertFalse(palette.visible.any { it.id == LiveAction.TWO_MADE.id })
        assertEquals(WriteResult.INVALID_LIVE_EVENT, store.record(id, "blocked", LiveInput(LiveSide.HOME, LiveAction.TWO_MADE.id, player)))
        saved(store.record(id, "custom", LiveInput(LiveSide.HOME, custom.id, player)))
        val hidden = listOf(BoxColumn(LiveAction.THREE_MADE.id, true), BoxColumn(custom.id, false)) +
            arranged.filterNot { it.metricId == custom.id || it.metricId == LiveAction.THREE_MADE.id }
        saved(store.saveConfiguration(Sport.BASKETBALL, id, hidden))
        val reopened = LiveGameStore(db, "other-process", { elapsed })
        val scoped = reopened.observe(id).first()!!.config
        assertEquals(LiveAction.THREE_MADE.id, scoped.visible.first().id)
        assertFalse(scoped.visible.any { it.id == custom.id })
        assertEquals(custom.id, reopened.observeConfiguration(Sport.BASKETBALL, null).first()!!.visible.first().id)
        assertEquals(boxBefore, repo.observeBoxConfiguration(Sport.BASKETBALL, id).first()!!.columns)
        assertEquals(BoxMeasurement(1), repo.observeBoxScore(id).first()!!.players.single().measurements[custom.id])
        saved(store.undo(id, "undo-custom", "custom"))
        assertNull(repo.observeBoxScore(id).first()!!.players.single().measurements[custom.id])
    }

    private suspend fun match(sport: Sport): Pair<Long, Long> {
        val team = db.teams().insert(Team("Equipe", sport, null, null))
        saved(repo.createPlayer(PlayerInput("Ana", team, "8", null, false)))
        val player = repo.observeRoster(team).first()!!.players.single().id
        saved(repo.saveMatch(null, MatchInput(
            team, "Rivais", LocalDate.now().plusDays(1), LocalTime.NOON, "", MatchStatus.SCHEDULED, "", "",
        )))
        return repo.observeMatches(MatchStatus.SCHEDULED).first().single().match.id to player
    }

    private suspend fun session(id: Long) = requireNotNull(db.liveGames().session(id))

    private suspend fun tap(id: Long, request: String, action: LiveAction, player: Long) {
        saved(store.record(id, request, LiveInput(LiveSide.HOME, action.id, playerId = player)))
    }

    private fun saved(value: WriteResult) = assertEquals(WriteResult.SAVED, value)
}
