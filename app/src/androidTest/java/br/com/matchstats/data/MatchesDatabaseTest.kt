package br.com.matchstats.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchesDatabaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var db: MatchStatsDatabase
    private lateinit var repo: MatchStatsRepository

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, MatchStatsDatabase::class.java).build()
        repo = MatchStatsRepository(db)
    }

    @After fun close() { db.close() }

    @Test
    fun upgradesVersionOneAndRetainsTeamsPlayersMatchesAndStatsAcrossReopen() = runBlocking {
        val file = "matchstats-migration-${UUID.randomUUID()}.db"
        try {
            val exported = instrumentation.context.assets
                .open("br.com.matchstats.data.MatchStatsDatabase/1.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            context.openOrCreateDatabase(file, Context.MODE_PRIVATE, null).use { legacy ->
                legacy.setForeignKeyConstraintsEnabled(true)
                val entities = exported.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    for (j in 0 until (indices?.length() ?: 0)) {
                        legacy.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = exported.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) legacy.execSQL(setup.getString(i))
                legacy.execSQL("INSERT INTO teams (id,name,sport,category,coachName,createdAt) VALUES (41,'União','FOOTBALL','Sub-20','Ana',121)")
                legacy.execSQL("INSERT INTO players (id,teamId,name,jerseyNumber,position,captain,createdAt) VALUES (71,41,'Lia',7,'FORWARD',1,122)")
                legacy.version = 1
            }

            val migrated = Room.databaseBuilder(context, MatchStatsDatabase::class.java, file).addMigrations(MIGRATION_2_3).build()
            var matchId = 0L
            try {
                val repository = MatchStatsRepository(migrated)
                val roster = requireNotNull(repository.observeRoster(41).first())
                assertEquals(Team("União", Sport.FOOTBALL, "Sub-20", "Ana", 121, 41), roster.team)
                assertEquals(RosterPlayer(Player("Lia", 122, 71), 7, Position.FORWARD, true), roster.players.single())
                assertEquals(DashboardCounts(1, 1, 0), repository.observeDashboard().first())
                assertEquals(WriteResult.SAVED, repository.saveMatch(null, input(41, "Vila", MatchStatus.FINISHED).copy(teamScore = "3")))
                matchId = repository.observeMatches(MatchStatus.FINISHED).first().single().match.id
                assertEquals(WriteResult.SAVED, repository.saveMatchStats(matchId, mapOf(71L to StatValues(goals = 3))))
                migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {
                    assertFalse(it.moveToFirst())
                }
            } finally {
                migrated.close()
            }

            val reopened = Room.databaseBuilder(context, MatchStatsDatabase::class.java, file).addMigrations(MIGRATION_2_3).build()
            try {
                val repository = MatchStatsRepository(reopened)
                val sheet = requireNotNull(repository.observeMatchSheet(matchId).first())
                assertEquals("Vila", sheet.fixture.match.opponent)
                assertEquals(3, sheet.saved.single().values.goals)
                assertEquals(71L, sheet.roster.single().id)
                assertEquals(7, reopened.openHelper.writableDatabase.version)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(file)
        }
        Unit
    }

    @Test
    fun listsAndDashboardTrackRealCountsUpcomingAndResults() = runBlocking {
        val football = team("União", Sport.FOOTBALL)
        val basketball = team("Vila", Sport.BASKETBALL)
        player(football)
        val tomorrow = LocalDate.now().plusDays(1)
        assertSaved(input(football, "Calendário posterior", date = tomorrow.plusDays(1)))
        assertSaved(input(football, "Próxima", date = tomorrow).copy(teamScore = "-3", opponentScore = "4"))
        assertSaved(input(football, "Atrasada", date = tomorrow.minusDays(2)))
        assertSaved(input(basketball, "Resultado", MatchStatus.FINISHED, tomorrow).copy(teamScore = "78", opponentScore = "65"))

        val scheduled = repo.observeMatches(MatchStatus.SCHEDULED).first()
        assertEquals(listOf("Atrasada", "Próxima", "Calendário posterior"), scheduled.map { it.match.opponent })
        assertNull(scheduled[1].match.teamScore)
        assertNull(scheduled[1].match.opponentScore)
        assertEquals(78, repo.observeMatches(MatchStatus.FINISHED).first().single().match.teamScore)

        val home = repo.observeHome().first()
        assertEquals(DashboardCounts(2, 1, 4), home.counts)
        assertEquals("Próxima", home.upcoming?.match?.opponent)
        assertEquals("Resultado", home.results.single().match.opponent)
    }

    @Test
    fun editsKeepIdentityAndProtectSportStatusTeamAndPlayerHistory() = runBlocking {
        val football = team("União", Sport.FOOTBALL)
        val basketball = team("Vila", Sport.BASKETBALL)
        val player = player(football)
        val matchId = assertSaved(input(football, "Base"))
        val original = requireNotNull(repo.observeMatch(matchId).first()).match
        assertEquals(WriteResult.TEAM_HAS_MATCHES, repo.saveTeam(football, TeamInput("União", Sport.BASKETBALL, "", "")))

        val edited = input(football, "Editada", MatchStatus.FINISHED).copy(location = "Campo Municipal", teamScore = "2")
        assertEquals(WriteResult.SAVED, repo.saveMatch(matchId, edited))
        val result = requireNotNull(repo.observeMatch(matchId).first()).match
        assertEquals(original.id, result.id)
        assertEquals(original.createdAt, result.createdAt)
        assertEquals("Campo Municipal", result.location)
        assertEquals(2, result.teamScore)

        assertEquals(WriteResult.MATCH_ALREADY_FINISHED, repo.saveMatch(matchId, edited.copy(status = MatchStatus.SCHEDULED)))
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(matchId, mapOf(player to StatValues(goals = 1))))
        assertEquals(WriteResult.MATCH_HAS_STATS, repo.saveMatch(matchId, edited.copy(teamId = basketball)))
        assertEquals(
            WriteResult.SAVED,
            repo.saveMembership(player, MembershipInput(basketball, "", null, false), editing = false),
        )
        assertTrue(repo.observeRoster(football).first()!!.hasMatchHistory)
    }

    @Test
    fun eachSportValidatesAndUpsertsOnlyEligiblePlayersOnFinishedMatches() = runBlocking {
        val football = team("União", Sport.FOOTBALL)
        val basketball = team("Vila", Sport.BASKETBALL)
        val footballPlayer = player(football)
        val basketballPlayer = player(basketball)
        val footballMatch = assertSaved(input(football, "Soccer", MatchStatus.FINISHED).copy(teamScore = "6"))
        val basketballMatch = assertSaved(input(basketball, "Basket", MatchStatus.FINISHED))
        val scheduled = assertSaved(input(football, "Longe"))
        assertEquals(WriteResult.MATCH_NOT_FINISHED, repo.saveMatchStats(scheduled, mapOf(footballPlayer to StatValues(goals = 1))))
        assertEquals(WriteResult.INVALID_MATCH_PLAYER, repo.saveMatchStats(footballMatch, mapOf(basketballPlayer to StatValues(goals = 1))))
        assertEquals(WriteResult.INVALID_STATS, repo.saveMatchStats(footballMatch, mapOf(footballPlayer to StatValues(points = 5))))
        assertEquals(WriteResult.INVALID_STATS, repo.saveMatchStats(basketballMatch, mapOf(basketballPlayer to StatValues(assists = -1))))

        val goalStats = StatValues(goals = 5, assists = 2, yellowCards = 1, redCards = 1)
        val basketStats = StatValues(points = 19, rebounds = 7, assists = 3)
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(footballMatch, mapOf(footballPlayer to goalStats)))
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(basketballMatch, mapOf(basketballPlayer to basketStats)))
        val amended = goalStats.copy(goals = 6)
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(footballMatch, mapOf(footballPlayer to amended)))
        assertEquals(amended, repo.observeMatchSheet(footballMatch).first()!!.saved.single().values)
        assertEquals(basketStats, repo.observeMatchSheet(basketballMatch).first()!!.saved.single().values)
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(footballMatch, mapOf(footballPlayer to StatValues())))
        assertTrue(repo.observeMatchSheet(footballMatch).first()!!.saved.isEmpty())
    }

    @Test
    fun cascadesFromMatchesPlayersAndTeamsWithoutRemovingOtherRecords() = runBlocking {
        val football = team("União", Sport.FOOTBALL)
        val other = team("Vila", Sport.BASKETBALL)
        val player = player(football)
        val match = assertSaved(input(football, "Base", MatchStatus.FINISHED))
        val otherMatch = assertSaved(input(other, "Outra", MatchStatus.FINISHED))
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(match, mapOf(player to StatValues(goals = 1))))
        assertEquals(WriteResult.SAVED, repo.deleteMatch(match))
        assertTrue(db.matchStats().forMatch(match).isEmpty())
        assertEquals(1, repo.observeRoster(football).first()!!.players.size)

        val again = assertSaved(input(football, "De novo", MatchStatus.FINISHED))
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(again, mapOf(player to StatValues(goals = 1))))
        assertEquals(WriteResult.SAVED, repo.deletePlayer(player))
        assertTrue(db.matchStats().forMatch(again).isEmpty())
        val remainingPlayer = player(football)
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(again, mapOf(remainingPlayer to StatValues(goals = 1))))
        assertEquals(WriteResult.SAVED, repo.deleteTeam(football))
        assertNull(repo.observeMatch(again).first())
        assertTrue(repo.observePlayer(remainingPlayer).first()!!.entry.teams.isEmpty())
        assertTrue(db.matchStats().forMatch(again).isEmpty())
        assertEquals(otherMatch, repo.observeMatches(MatchStatus.FINISHED).first().single().match.id)
    }

    private suspend fun assertSaved(input: MatchInput): Long {
        assertEquals(WriteResult.SAVED, repo.saveMatch(null, input, ZoneOffset.UTC))
        return repo.observeMatches(input.status).first().single { it.match.opponent == input.opponent }.match.id
    }

    private suspend fun team(name: String, sport: Sport) = db.teams().insert(Team(name, sport, null, null))
    private suspend fun player(teamId: Long): Long {
        val id = db.players().insert(Player("Lia"))
        db.players().insertMembership(TeamMembership(id, teamId, null, null, false))
        return id
    }
    private fun input(
        team: Long,
        opponent: String,
        status: MatchStatus = MatchStatus.SCHEDULED,
        date: LocalDate = LocalDate.now().plusDays(1),
    ) = MatchInput(team, opponent, date, LocalTime.of(17, 0), "", status, "1", "0")
}
