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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerDirectoryMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun preservesVersionTwoHistoryAfterChangingAndRemovingMembershipsAndReopening() = runBlocking {
        val file = "matchstats-v2-${UUID.randomUUID()}.db"
        try {
            createVersionTwo(file)
            val open = { Room.databaseBuilder(context, MatchStatsDatabase::class.java, file).addMigrations(MIGRATION_2_3).build() }
            val migrated = open()
            try {
                val repo = MatchStatsRepository(migrated)
                assertEquals(7, migrated.openHelper.writableDatabase.version)
                assertEquals(DashboardCounts(3, 2, 3), repo.observeHome().first().counts)
                val roster = requireNotNull(repo.observeRoster(41).first())
                assertEquals(Team("União", Sport.FOOTBALL, "Sub-20", "Ana", 121, 41), roster.team)
                assertEquals(RosterPlayer(Player("Lía", 122, 71), 7, Position.FORWARD, true), roster.players.single())
                assertEquals(StatValues(goals = 2, assists = 1, yellowCards = 1, redCards = 1), migrated.matchStats().forMatch(81).single().values)
                assertEquals(StatValues(assists = 3, points = 19, rebounds = 7), migrated.matchStats().forMatch(82).single().values)
                assertEquals("Estádio", repo.observeMatch(81).first()!!.match.location)
                migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }

                assertEquals(WriteResult.SAVED, repo.saveMembership(71, MembershipInput(43, "17", Position.FORWARD, false), false))
                assertEquals(WriteResult.SAVED, repo.saveMembership(71, MembershipInput(42, "17", Position.CENTER, true), false))
                assertEquals(
                    WriteResult.SAVED,
                    repo.saveMatchStats(82, mapOf(71L to StatValues(points = 9, rebounds = 4, assists = 2))),
                )
                assertEquals(WriteResult.SAVED, repo.saveMatchStats(83, mapOf(71L to StatValues(goals = 3))))
                assertEquals(WriteResult.SAVED, repo.removeMembership(71, 41))
                assertEquals(WriteResult.SAVED, repo.removeMembership(71, 42))
                assertTrue(repo.observeRoster(41).first()!!.players.isEmpty())
                assertEquals(71L, repo.observeMatchSheet(81).first()!!.roster.single().id)
                assertEquals(
                    WriteResult.SAVED,
                    repo.saveMatchStats(81, mapOf(71L to StatValues(goals = 2, assists = 2, yellowCards = 1, redCards = 1))),
                )
                assertEquals(WriteResult.SAVED, repo.createPlayer(PlayerInput("Novo", null, "", null, false)))
                assertTrue(repo.observeDirectory().first().single { it.player.name == "Novo" }.player.id > 200)
            } finally {
                migrated.close()
            }
            val reopened = open()
            try {
                val repo = MatchStatsRepository(reopened)
                val profile = requireNotNull(repo.observePlayer(71).first())
                assertEquals(listOf(43L), profile.entry.teams.map { it.team.id })
                assertEquals(setOf(81L, 82L, 83L), profile.games.map { it.match.id }.toSet())
                val soccer = profile.entry.totals(Sport.FOOTBALL)!!
                val basket = profile.entry.totals(Sport.BASKETBALL)!!
                assertEquals(2, soccer.games)
                assertEquals(5L, soccer.goals)
                assertEquals(2L, soccer.assists)
                assertEquals(9L, basket.points)
                assertEquals(2L, basket.assists)
                assertTrue(profile.entry.matches("lia"))
                assertTrue(profile.entry.matches("017", Sport.FOOTBALL))
                assertFalse(profile.entry.matches("017", Sport.BASKETBALL))
                assertTrue(profile.entry.plays(Sport.BASKETBALL))
                val ranks = repo.observeSportStatistics(Sport.FOOTBALL).first()
                assertEquals(5L, ranks.forTeam(null).ranking(StatMetric.GOALS).single().total)
                assertEquals(2L, ranks.forTeam(41).ranking(StatMetric.GOALS).single().total)
                assertEquals(3L, ranks.forTeam(43).ranking(StatMetric.GOALS).single().total)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(file)
        }
        Unit
    }

    private fun createVersionTwo(file: String) {
        val schema = instrumentation.context.assets.open("br.com.matchstats.data.MatchStatsDatabase/2.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(file, Context.MODE_PRIVATE, null).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) {
                    db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("""
                INSERT INTO teams (id, name, sport, category, coachName, createdAt)
                VALUES (41,'União','FOOTBALL','Sub-20','Ana',121),
                       (42,'Vila','BASKETBALL',NULL,NULL,121),
                       (43,'Capital','FOOTBALL',NULL,NULL,121)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO players (id, teamId, name, jerseyNumber, position, captain, createdAt)
                VALUES (71,41,'Lía',7,'FORWARD',1,122), (72,42,'Bia',NULL,'POINT_GUARD',0,123)
            """.trimIndent())
            db.execSQL("UPDATE sqlite_sequence SET seq = 200 WHERE name = 'players'")
            db.execSQL("""
                INSERT INTO matches (id, teamId, opponent, startsAt, location, status, teamScore, opponentScore, createdAt)
                VALUES (81,41,'Rivais',125,'Estádio','FINISHED',3,1,123),
                       (82,42,'Águias',126,NULL,'FINISHED',72,68,124),
                       (83,43,'Leões',127,NULL,'FINISHED',4,2,125)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO player_match_stats (matchId, playerId, goals, assists, yellowCards, redCards, points, rebounds)
                VALUES (81,71,2,1,1,1,0,0), (82,72,0,3,0,0,19,7)
            """.trimIndent())
            db.version = 2
        }
    }
}
