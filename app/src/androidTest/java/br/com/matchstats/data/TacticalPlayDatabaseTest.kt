package br.com.matchstats.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TacticalPlayDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun persistsStructuredPlaysAcrossOpenAndProtectsConcurrentEdits() = runBlocking {
        val filename = "matchstats-tactics-${UUID.randomUUID()}.db"
        val doc = sampleBoard()
        val original = try {
            val db = Room.databaseBuilder(context, MatchStatsDatabase::class.java, filename).build()
            try {
                val store = TacticalPlayStore(db)
                val (result, item) = store.save(null, null, " Saída alta ", TacticalSnapshot(doc, null))
                assertEquals(WriteResult.SAVED, result)
                checkNotNull(item)
            } finally {
                db.close()
            }
        } catch (error: Throwable) {
            context.deleteDatabase(filename)
            throw error
        }
        try {
            val db = Room.databaseBuilder(context, MatchStatsDatabase::class.java, filename).build()
            try {
                val store = TacticalPlayStore(db)
                val loaded = checkNotNull(store.find(original.id))
                assertEquals("Saída alta", loaded.name)
                assertEquals(CourtMode.FULL, loaded.mode)
                assertEquals(doc, TacticalDocumentCodec.decode(loaded.document))
                val (result, renamed) = store.save(loaded.id, loaded.updatedAt, "Saída 1", TacticalSnapshot(doc, null))
                assertEquals(WriteResult.SAVED, result)
                assertEquals(loaded.createdAt, renamed!!.createdAt)
                assertTrue(renamed.updatedAt > loaded.updatedAt)
                assertEquals(WriteResult.TACTICAL_PLAY_CHANGED,
                    store.save(loaded.id, loaded.updatedAt, "Versão antiga", TacticalSnapshot(doc, null)).first)
                assertEquals(WriteResult.SAVED, store.duplicate(loaded.id, "Cópia de Saída 1"))
                val copy = store.list().first().single { it.name == "Cópia de Saída 1" }
                assertNotEquals(copy.id, loaded.id)
                assertEquals(doc, TacticalDocumentCodec.decode(checkNotNull(store.find(copy.id)).document))
                assertEquals(WriteResult.SAVED, store.delete(copy.id))
                assertEquals(listOf("Saída 1"), store.list().first().map { it.name })
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
        Unit
    }

    @Test fun deletingRosterLinksAndTeamsPreservesPlaysAndFiltersUnrelatedAssignments() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MatchStatsDatabase::class.java).build()
        try {
            val store = TacticalPlayStore(db)
            val teamId = db.teams().insert(Team("Basquete", Sport.BASKETBALL, null, null))
            val opponent = db.teams().insert(Team("Futebol", Sport.FOOTBALL, null, null))
            val playerId = db.players().insert(Player("Ana"))
            val outsider = db.players().insert(Player("Rafael"))
            db.players().insertMembership(TeamMembership(playerId, teamId, 27, null, false))
            val board = sampleBoard().assign(1, playerId).assign(2, outsider)
            val item = checkNotNull(store.save(null, null, "Equipe", TacticalSnapshot(board, teamId)).second)
            assertEquals(teamId, item.teamId)
            assertEquals(27, db.players().roster(teamId).single().jerseyNumber)
            val saved = checkNotNull(TacticalDocumentCodec.decode(item.document))
            assertEquals(playerId, saved.markers.first().playerId)
            assertNull(saved.markers[1].playerId)
            db.players().detach(playerId, teamId)
            assertEquals(item.document, store.find(item.id)!!.document)
            db.teams().delete(teamId)
            val detached = checkNotNull(store.find(item.id))
            assertNull(detached.teamId)
            assertEquals(saved.strokes, TacticalDocumentCodec.decode(detached.document)!!.strokes)
            val empty = store.save(null, null, "Sem associação", TacticalSnapshot(board, opponent)).second!!
            assertNull(empty.teamId)
            assertTrue(TacticalDocumentCodec.decode(empty.document)!!.markers.none { it.playerId != null })
        } finally {
            db.close()
        }
        Unit
    }

    @Test fun rejectsUnknownDocumentVersionInvalidCoordinatesAndInvalidStrokeTypes() {
        val encoded = TacticalDocumentCodec.encode(sampleBoard())
        assertEquals(sampleBoard(), TacticalDocumentCodec.decode(encoded))
        assertNull(TacticalDocumentCodec.decode(JSONObject(encoded).put("version", 2).toString()))
        val coordinate = JSONObject(encoded)
        coordinate.getJSONArray("markers").getJSONObject(0).getJSONArray("point").put(0, 1.000000001)
        assertNull(TacticalDocumentCodec.decode(coordinate.toString()))
        val type = JSONObject(encoded)
        type.getJSONArray("strokes").getJSONObject(0).put("type", "UNKNOWN")
        assertNull(TacticalDocumentCodec.decode(type.toString()))
        assertNull(TacticalDocumentCodec.decode("malformed"))
    }

    private fun sampleBoard() = TacticalBoard(mode = CourtMode.FULL, showDefense = true).let { board ->
        board.move(board.markers.first(), BoardPoint(.34f, .56f)).copy(strokes = TacticalStrokeType.entries.map { type ->
            TacticalStroke(type, listOf(BoardPoint(.17f, .58f), BoardPoint(.62f, .71f)))
        })
    }
}
