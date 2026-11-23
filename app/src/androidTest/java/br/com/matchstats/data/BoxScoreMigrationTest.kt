package br.com.matchstats.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoxScoreMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun migratesVersionThreeWithoutConvertingLegacyCountsToAttempts() = runBlocking {
        val filename = "matchstats-v3-${UUID.randomUUID()}.db"
        try {
            createLegacy(filename, 3)
            val db = Room.databaseBuilder(context, MatchStatsDatabase::class.java, filename).build()
            try {
                val repo = MatchStatsRepository(db)
                assertEquals(7, db.openHelper.writableDatabase.version)
                assertEquals(DashboardCounts(2, 1, 2), repo.observeHome().first().counts)
                val athlete = repo.observePlayer(51).first()!!
                assertEquals(Player("Lia", 103, 51), athlete.player)
                assertEquals(setOf(21L, 22L), athlete.entry.teams.map { it.team.id }.toSet())
                assertEquals(Position.FORWARD, athlete.entry.teams.single { it.team.id == 22L }.position)
                assertEquals(2, athlete.games.size)

                val basket = repo.observeBoxScore(61).first()!!
                assertEquals(9, basket.players.single().jersey)
                assertEquals("Vila", basket.fixture.team.name)
                assertEquals(72, basket.fixture.match.teamScore)
                assertEquals("Águias", basket.fixture.match.opponent)
                fun cell(metric: BoxBuiltin) = basket.config.metrics.getValue(metric.id).cell(basket.players)
                assertEquals(BoxCell(24, null), cell(BoxBuiltin.POINTS))
                assertEquals(BoxCell(8, null), cell(BoxBuiltin.REBOUNDS))
                assertEquals(BoxCell(5, null), cell(BoxBuiltin.BASKET_ASSISTS))
                assertNull(cell(BoxBuiltin.OFFENSIVE))
                assertNull(cell(BoxBuiltin.DEFENSIVE))
                assertNull(cell(BoxBuiltin.FIELD_PERCENT))
                assertEquals(24L, repo.observeSportStatistics(Sport.BASKETBALL).first().forTeam(null).ranking(StatMetric.POINTS).single().total)

                val football = repo.observeBoxScore(62).first()!!
                assertFalse(football.config.metrics.containsKey(BoxBuiltin.POINTS.id))
                assertEquals(11, football.players.single().jersey)
                assertEquals(StatValues(goals = 2, assists = 1, yellowCards = 1), football.players.single().canonical)
                assertEquals(2L, repo.observeSportStatistics(Sport.FOOTBALL).first().forTeam(null).ranking(StatMetric.GOALS).single().total)
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
        Unit
    }

    @Test
    fun migratesVersionFourWithIndependentBoxConfigurationsAndCustomMeasurements() = runBlocking {
        val filename = "matchstats-v4-live-${UUID.randomUUID()}.db"
        try {
            createLegacy(filename, 4)
            val db = Room.databaseBuilder(context, MatchStatsDatabase::class.java, filename).build()
            try {
                val repo = MatchStatsRepository(db)
                val custom = "custom_958be070-34cc-409a-804a-c02b713b8210"
                assertEquals(7, db.openHelper.writableDatabase.version)
                assertEquals(BoxBuiltin.POINTS.id, repo.observeBoxConfiguration(Sport.BASKETBALL).first()!!.visible.first().id)
                val box = repo.observeBoxScore(61).first()!!
                assertEquals(listOf(BoxBuiltin.REBOUNDS.id, custom), box.config.visible.map { it.id })
                assertEquals(BoxMeasurement(3), box.players.single().measurements[custom])
                assertEquals(24, box.players.single().canonical!!.points)
                assertNull(box.fixture.live)
                assertEquals(2, repo.observePlayer(51).first()!!.entry.teams.size)
                assertEquals(2, repo.observePlayer(51).first()!!.games.size)
                assertEquals(2L, repo.observeSportStatistics(Sport.FOOTBALL).first().forTeam(null).ranking(StatMetric.GOALS).single().total)
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
        Unit
    }

    @Test
    fun migratesVersionFiveWithLiveHistoryAndBothBoxConfigurations() = runBlocking {
        val filename = "matchstats-v5-${UUID.randomUUID()}.db"
        try {
            createLegacy(filename, 5)
            val db = Room.databaseBuilder(context, MatchStatsDatabase::class.java, filename).build()
            try {
                assertEquals(7, db.openHelper.writableDatabase.version)
                val repo = MatchStatsRepository(db)
                val athlete = repo.observePlayer(51).first()!!
                assertEquals(listOf(21L, 22L), athlete.entry.teams.map { it.team.id }.sorted())
                assertEquals(2, athlete.games.size)
                assertEquals(72, repo.observeMatch(61).first()!!.match.teamScore)
                assertEquals(2, repo.observeBoxScore(62).first()!!.players.single().canonical!!.goals)
                val custom = "custom_958be070-34cc-409a-804a-c02b713b8210"
                assertEquals(BoxMeasurement(3), repo.observeBoxScore(61).first()!!.players.single().measurements[custom])
                assertEquals("+bk_points,-bk_minutes", db.boxScores().defaults(Sport.BASKETBALL))
                assertEquals("+fb_goals", db.boxScores().defaults(Sport.FOOTBALL))
                assertEquals("+bk_two_made", db.liveGames().preference(Sport.BASKETBALL))
                assertEquals("+fb_goal", db.liveGames().preference(Sport.FOOTBALL))
                assertEquals("basketball-history", db.liveGames().events(61).single().id)
                assertEquals("football-history", db.liveGames().events(62).single().id)
                assertEquals(0, repo.observeHome().first().practiceCount)
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
        Unit
    }

    @Test
    fun migratesVersionSixPreservingGamesPracticesAndLiveOpponentJerseys() = runBlocking {
        val filename = "matchstats-v6-${UUID.randomUUID()}.db"
        try {
            createLegacy(filename, 6)
            val db = Room.databaseBuilder(context, MatchStatsDatabase::class.java, filename).build()
            try {
                val repo = MatchStatsRepository(db)
                assertEquals(7, db.openHelper.writableDatabase.version)
                assertEquals(DashboardCounts(2, 1, 2), repo.observeHome().first().counts)
                assertEquals(1, repo.observeHome().first().practiceCount)
                assertEquals(72, repo.observeMatch(61).first()!!.match.teamScore)
                assertEquals(2, repo.observePlayer(51).first()!!.entry.teams.size)
                assertEquals(2, repo.observePlayer(51).first()!!.games.size)
                assertEquals("+bk_points,-bk_minutes", db.boxScores().defaults(Sport.BASKETBALL))
                assertEquals(1, db.boxScores().measurements(61).size)
                assertEquals(2, db.liveGames().events(61).size)
                assertEquals(23, db.liveGames().events(61).last().jersey)
                assertEquals("+bk_two_made", db.liveGames().preference(Sport.BASKETBALL))
                val practice = repo.practices.details(71).first()!!
                assertEquals(PracticeStatus.COMPLETED, practice.summary.session.status)
                assertEquals(51L, practice.players.single().playerId)
                assertEquals(9, practice.players.single().jerseyNumber)
                assertEquals(PracticeMeasure.MAKES_AND_ATTEMPTS, practice.drills.single().measure)
                assertEquals(PracticeTotals(51, 81, 15, 20), practice.totals.single())
                assertEquals(1, repo.practices.history(71).first().single().batch.revision)
                assertEquals(0.75, repo.practices.facts(Sport.BASKETBALL, playerId = 51).first().practicePerformance().accuracy!!, .0001)
                assertEquals(emptyList<TacticalPlaySummary>(), repo.tactics.list().first())
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
        Unit
    }

    private fun createLegacy(filename: String, version: Int) {
        val schema = instrumentation.context.assets.open("br.com.matchstats.data.MatchStatsDatabase/$version.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(filename, Context.MODE_PRIVATE, null).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
            db.execSQL("""
                INSERT INTO teams (id, name, sport, category, coachName, createdAt)
                VALUES (21,'Vila','BASKETBALL','Adulto','Ana',101),
                       (22,'União','FOOTBALL',NULL,NULL,102)
            """.trimIndent())
            db.execSQL("INSERT INTO players (id, name, createdAt) VALUES (51,'Lia',103)")
            db.execSQL("""
                INSERT INTO team_memberships (playerId, teamId, jerseyNumber, position, captain)
                VALUES (51,21,9,'POINT_GUARD',0), (51,22,11,'FORWARD',1)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO matches (id, teamId, opponent, startsAt, location, status, teamScore, opponentScore, createdAt)
                VALUES (61,21,'Águias',106,'Ginásio','FINISHED',72,68,104),
                       (62,22,'Rivais',107,'Campo','FINISHED',3,1,105)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO player_match_stats (matchId, playerId, goals, assists, yellowCards, redCards, points, rebounds)
                VALUES (61,51,0,5,0,0,24,8), (62,51,2,1,1,0,0,0)
            """.trimIndent())
            if (version >= 4) {
                db.execSQL("INSERT INTO box_custom_metrics VALUES ('custom_958be070-34cc-409a-804a-c02b713b8210','BASKETBALL','Desvios','DV','TOTAL',0)")
                db.execSQL("INSERT INTO box_defaults VALUES ('BASKETBALL','+bk_points,-bk_minutes')")
                db.execSQL("INSERT INTO box_match_layouts VALUES (61,'BASKETBALL','+bk_rebounds,+custom_958be070-34cc-409a-804a-c02b713b8210')")
                db.execSQL("INSERT INTO box_measurements VALUES (61,51,'custom_958be070-34cc-409a-804a-c02b713b8210',3,NULL)")
            }
            if (version >= 5) {
                db.execSQL("INSERT INTO box_defaults VALUES ('FOOTBALL','+fb_goals')")
                db.execSQL("INSERT INTO live_preferences VALUES ('BASKETBALL','+bk_two_made'), ('FOOTBALL','+fb_goal')")
                db.execSQL("INSERT INTO live_sessions VALUES (61,600,24,4,20000,14000,72,68,NULL,NULL,''), (62,2700,0,2,30000,0,3,1,NULL,NULL,'')")
                db.execSQL("""
                    INSERT INTO live_events VALUES
                    ('basketball-history',61,1,NULL,'RECORD','HOME',51,9,'Lia','bk_two_made',NULL,NULL,4,20000,1,200),
                    ('football-history',62,1,NULL,'RECORD','HOME',51,11,'Lia','fb_goal',NULL,NULL,2,30000,1,201)
                """.trimIndent())
            }
            if (version >= 6) {
                db.execSQL("INSERT INTO live_events VALUES ('away-history',61,2,NULL,'RECORD','AWAY',NULL,23,'Adversário','bk_two_made',NULL,NULL,4,19000,1,202)")
                db.execSQL("INSERT INTO practice_sessions VALUES ('Treino','BASKETBALL',21,500,'Quadra',NULL,'COMPLETED',400,14000,NULL,NULL,1,71)")
                db.execSQL("INSERT INTO practice_participants VALUES (71,51,9)")
                db.execSQL("INSERT INTO practice_drills VALUES (71,'Lances livres','BASKETBALL','MAKES_AND_ATTEMPTS',NULL,0,81)")
                db.execSQL("INSERT INTO practice_batches VALUES ('historico-pratica',51,71,81,15,20,14,20,600,1,700)")
            }
            db.version = version
        }
    }
}
