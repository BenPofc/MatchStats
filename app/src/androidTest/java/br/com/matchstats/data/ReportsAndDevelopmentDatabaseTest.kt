package br.com.matchstats.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.matchstats.R
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportsAndDevelopmentDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: MatchStatsDatabase
    private lateinit var repository: MatchStatsRepository

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MatchStatsDatabase::class.java).build()
        repository = MatchStatsRepository(db)
    }

    @After fun close() { db.close() }

    @Test fun soccerReportUsesFinalScoreOptionalValuesConfiguredColumnsAndPreservesData() = runBlocking {
        val team = team("União", Sport.FOOTBALL)
        val ana = player(team, "Márcia da Conceição", 10)
        val bia = player(team, "Bia", 8)
        val id = match(team, "Atlético", 3, 1, location = null)
        db.matchStats().upsert(listOf(PlayerMatchStats(id, ana, StatValues(goals = 2)), PlayerMatchStats(id, bia, StatValues(goals = 1))))
        db.boxScores().upsertMeasurements(listOf(
            BoxStoredMeasurement(id, ana, BoxBuiltin.PASSES.id, 2, 5),
            BoxStoredMeasurement(id, bia, BoxBuiltin.PASSES.id, 9, 10),
        ))
        configure(id, Sport.FOOTBALL, BoxBuiltin.GOALS.id, BoxBuiltin.PASSES.id, BoxBuiltin.SAVES.id)

        val snapshot = checkNotNull(repository.readMatchReport(id))
        val report = prepareMatchReport(context, snapshot)
        assertEquals("3 × 1", report.score)
        assertEquals("União", report.team)
        assertEquals("Atlético", report.opponent)
        assertTrue(report.details.contains("Futebol"))
        assertNull(report.location)
        assertEquals(listOf(context.getString(R.string.stat_goals), context.getString(R.string.box_passes),
            context.getString(R.string.box_saves)), report.columns)
        val rows = report.tables.single().rows
        val row = rows.first { it.jersey == "10" }
        assertEquals("Márcia da Conceição", row.player)
        assertEquals(listOf("2", "2/5 (40%)", "—"), row.values)
        assertEquals(listOf("3", "11/15 (73,3%)", "—"), rows.last().values)
        verifyPdf(report, 1, "soccer")
        assertEquals(snapshot, repository.readMatchReport(id))
        val scheduled = db.matches().insert(Match(team, "Em breve", 10, null, MatchStatus.SCHEDULED, null, null))
        assertNull(repository.readMatchReport(scheduled))
        Unit
    }

    @Test fun basketballReportSupportsCustomColumnsOpponentAndPagination() = runBlocking {
        val team = team("Águias do Pavilhão", Sport.BASKETBALL)
        val home = player(team, "João de Sá", 4)
        val id = db.matches().insert(Match(team, "Estrela Olímpica", 1_760_000_000_000, "Ginásio São José",
            MatchStatus.SCHEDULED, null, null))
        assertEquals(WriteResult.SAVED, repository.live.start(id, 10, 24))
        assertEquals(WriteResult.SAVED, repository.live.record(id, "home-point",
            LiveInput(LiveSide.HOME, LiveAction.TWO_MADE.id, playerId = home)))
        assertEquals(WriteResult.SAVED, repository.live.record(id, "home-miss",
            LiveInput(LiveSide.HOME, LiveAction.TWO_MISSED.id, playerId = home)))
        assertEquals(WriteResult.SAVED, repository.live.record(id, "away-point",
            LiveInput(LiveSide.AWAY, LiveAction.THREE_MADE.id, jersey = 23, opponentName = "Núbia")))
        assertNull(repository.readMatchReport(id))
        assertEquals(WriteResult.SAVED, repository.live.finish(id))
        (5..42).forEach { number -> player(team, "Jogador $number com nome e sobrenome extensos", number) }
        val custom = BoxCustomMetric.create(Sport.BASKETBALL, "Interceptações úteis", "IU", BoxMetricKind.TOTAL)
        db.boxScores().upsertMetrics(listOf(custom))
        db.boxScores().upsertMeasurements(listOf(
            BoxStoredMeasurement(id, home, BoxBuiltin.MINUTES.id, 12, null),
            BoxStoredMeasurement(id, home, custom.id, 7, null),
        ))
        val columns = listOf(BoxBuiltin.POINTS.id, BoxBuiltin.MINUTES.id, BoxBuiltin.TWO.id, BoxBuiltin.FIELD_PERCENT.id,
            custom.id, BoxBuiltin.FREE.id, BoxBuiltin.THREE.id, BoxBuiltin.REBOUNDS.id, BoxBuiltin.STEALS.id,
            BoxBuiltin.PERSONAL_FOULS.id, BoxBuiltin.BLOCKS.id, BoxBuiltin.BASKET_ASSISTS.id)
        configure(id, Sport.BASKETBALL, *columns.toTypedArray())
        val snapshot = checkNotNull(repository.readMatchReport(id))
        val report = prepareMatchReport(context, snapshot)
        assertEquals("2 × 3", report.score)
        assertEquals(12, report.columns.size)
        assertEquals("Interceptações úteis", report.columns[4])
        assertEquals("Local · Ginásio São José", report.location)
        val owners = report.tables.first().rows
        assertEquals(listOf("2", "12", "1/2", "1/2 (50%)", "7", "—"), owners.first().values.take(6))
        assertEquals(owners.first().values, owners.last().values)
        assertTrue(owners.last().total)
        val away = report.tables.last().rows.first()
        assertEquals("Núbia", away.player)
        assertEquals("23", away.jersey)
        assertEquals("3", away.values[0])
        assertEquals("1/1", away.values[6])
        verifyPdf(report, 8, "basketball")
        val after = checkNotNull(repository.readMatchReport(id))
        assertEquals(snapshot.fixture, after.fixture)
        assertEquals(snapshot.players, after.players)
        assertEquals(report, prepareMatchReport(context, after))
        Unit
    }

    @Test fun developmentReadsBothSportsAndCorrectedPracticeBatchesIndependently() = runBlocking {
        val home = team("Quadra", Sport.BASKETBALL)
        val other = team("Outra quadra", Sport.BASKETBALL)
        val soccer = team("Campo", Sport.FOOTBALL)
        val athlete = player(home, "Renê", 7)
        db.players().insertMembership(TeamMembership(athlete, other, 12, null, false))
        db.players().insertMembership(TeamMembership(athlete, soccer, 5, null, false))
        val matchA = match(home, "Liga", 8, 4, date = 800)
        val matchB = match(other, "Copa", 10, 7, date = 900)
        val matchF = match(soccer, "Amistoso", 3, 1, date = 700)
        db.matchStats().upsert(listOf(
            PlayerMatchStats(matchA, athlete, StatValues(points = 8)),
            PlayerMatchStats(matchB, athlete, StatValues(points = 10)),
            PlayerMatchStats(matchF, athlete, StatValues(goals = 2)),
        ))
        val custom = BoxCustomMetric.create(Sport.BASKETBALL, "Desvios", "DV", BoxMetricKind.TOTAL)
        db.boxScores().upsertMetrics(listOf(custom.copy(archived = true)))
        db.boxScores().upsertMeasurements(listOf(
            BoxStoredMeasurement(matchA, athlete, custom.id, 4, null),
            BoxStoredMeasurement(matchA, athlete, BoxBuiltin.FREE.id, 0, 0),
        ))
        val practiceA = practice(athlete, home, "Treino de terça", 500)
        val practiceB = practice(athlete, home, "Treino de quinta", 600)
        val drillA = checkNotNull(repository.practices.addDrill(practiceA, "Lances livres", PracticeMeasure.MAKES_AND_ATTEMPTS, "").second)
        val drillB = checkNotNull(repository.practices.addDrill(practiceB, "Lances livres", PracticeMeasure.MAKES_AND_ATTEMPTS, "").second)
        assertEquals(WriteResult.SAVED, repository.practices.submit(practiceA, drillA, "a", listOf(PracticeRow(athlete, 3, 10))))
        assertEquals(WriteResult.SAVED, repository.practices.submit(practiceA, drillA, "b", listOf(PracticeRow(athlete, 2, 10))))
        assertEquals(WriteResult.SAVED, repository.practices.submit(practiceB, drillB, "c", listOf(PracticeRow(athlete, 9, 10))))
        assertEquals(WriteResult.SAVED, repository.practices.complete(practiceA))
        assertEquals(WriteResult.SAVED, repository.practices.complete(practiceB))
        val firstBatch = repository.practices.history(practiceA).first().first { it.batch.submissionId == "a" }.batch
        assertEquals(WriteResult.SAVED, repository.practices.correct(firstBatch, PracticeRow(athlete, 4, 10)))

        var data = checkNotNull(repository.observeDevelopment(athlete, Sport.BASKETBALL).first())
        val points = data.gameMetrics().first()
        assertEquals(18L, data.gameSeries(points, DEVELOPMENT_ALL_TEAMS, null, ::title).total)
        assertEquals(8L, data.gameSeries(points, home, null, ::title).total)
        assertEquals(4L, data.gameSeries(data.gameMetrics().single { it.id == custom.id }, home, null, ::title).total)
        val free = data.gameSeries(data.gameMetrics().single { it.id == BoxBuiltin.FREE_PERCENT.id }, home, null, ::title)
        assertEquals(1, free.points.size)
        assertNull(free.accuracy)
        val drill = data.drills(home).single()
        val accuracy = data.practiceMetrics(drill).single { it.value == DevelopmentValue.RATE }
        val practices = data.practiceSeries(accuracy, home, null, drill)
        assertEquals(listOf(6L, 9L), practices.points.map { it.measure.value })
        assertEquals(50.0, practices.accuracy!!, .01)
        assertEquals(0, data.practiceSeries(accuracy, other, null, drill).points.size)
        data = checkNotNull(repository.observeDevelopment(athlete, Sport.FOOTBALL).first())
        assertEquals(2L, data.gameSeries(data.gameMetrics().first(), DEVELOPMENT_ALL_TEAMS, null, ::title).total)
        assertTrue(data.practices.isEmpty())
    }

    private suspend fun team(name: String, sport: Sport) = db.teams().insert(Team(name, sport, null, null))

    private suspend fun player(teamId: Long, name: String, jersey: Int): Long {
        val id = db.players().insert(Player(name))
        db.players().insertMembership(TeamMembership(id, teamId, jersey, null, false))
        return id
    }

    private suspend fun match(teamId: Long, opponent: String, own: Int, other: Int, location: String? = null, date: Long = 1_760_000_000_000) =
        db.matches().insert(Match(teamId, opponent, date, location, MatchStatus.FINISHED, own, other))

    private suspend fun practice(player: Long, teamId: Long, title: String, at: Long): Long {
        val id = checkNotNull(repository.practices.save(null, PracticeInput(title, Sport.BASKETBALL,
            teamId, at, "", "", setOf(player))).second)
        assertEquals(WriteResult.SAVED, repository.practices.start(id))
        return id
    }

    private suspend fun configure(id: Long, sport: Sport, vararg ids: String) {
        db.boxScores().upsertMatchLayout(BoxMatchLayout(id, sport, ids.map { BoxColumn(it, true) }.encodeBoxLayout()))
    }

    private fun verifyPdf(report: MatchReportData, minimumPages: Int, sample: String) {
        val file = File.createTempFile("match-report-", ".pdf", context.cacheDir)
        try {
            file.outputStream().use { writeMatchReport(context, report, it) }
            val prefix = file.inputStream().use {
                ByteArray(5).also { prefix -> java.io.DataInputStream(it).readFully(prefix) }
            }.decodeToString()
            assertEquals("%PDF-", prefix)
            val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
            try {
                assertTrue(renderer.pageCount >= minimumPages)
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        assertEquals(842, page.width)
                        assertEquals(595, page.height)
                    }
                }
            } finally {
                renderer.close()
            }
            if (InstrumentationRegistry.getArguments().getString("phase13PdfPreview") == "true") {
                file.copyTo(File(context.cacheDir, "phase13-$sample.pdf"), overwrite = true)
            }
        } finally {
            file.delete()
        }
    }

    private fun title(team: String, opponent: String) = "$team × $opponent"
}
