package br.com.matchstats.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "tactical_plays",
    foreignKeys = [ForeignKey(entity = Team::class, parentColumns = ["id"], childColumns = ["teamId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("teamId")],
)
internal data class TacticalPlay(
    val name: String,
    val teamId: Long?,
    val mode: CourtMode,
    val document: String,
    val createdAt: Long,
    val updatedAt: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

internal data class TacticalPlaySummary(
    val id: Long, val name: String, val teamId: Long?, val teamName: String?, val mode: CourtMode, val updatedAt: Long,
)

@Dao
internal interface TacticalPlayDao {
    @Query("""
        SELECT p.id, p.name, p.teamId, t.name AS teamName, p.mode, p.updatedAt
        FROM tactical_plays p LEFT JOIN teams t ON t.id = p.teamId
        ORDER BY p.updatedAt DESC, p.id DESC
    """)
    fun list(): Flow<List<TacticalPlaySummary>>

    @Query("SELECT * FROM tactical_plays WHERE id = :id")
    suspend fun find(id: Long): TacticalPlay?

    @Insert suspend fun insert(play: TacticalPlay): Long

    @Query("""
        UPDATE tactical_plays SET name = :name, teamId = :teamId, mode = :mode,
            document = :document, updatedAt = :updatedAt WHERE id = :id
    """)
    suspend fun update(id: Long, name: String, teamId: Long?, mode: CourtMode, document: String, updatedAt: Long)

    @Query("DELETE FROM tactical_plays WHERE id = :id")
    suspend fun delete(id: Long): Int
}

internal object TacticalDocumentCodec {
    private const val VERSION = 1

    fun encode(board: TacticalBoard): String = JSONObject()
        .put("version", VERSION)
        .put("mode", board.mode.name)
        .put("attack", board.showAttack)
        .put("defense", board.showDefense)
        .put("ball", board.showBall)
        .put("markers", JSONArray().apply {
            board.markers.forEach { marker ->
                put(JSONObject().put("side", marker.side.name).put("number", marker.number)
                    .put("point", encodePoint(marker.point)).put("player", marker.playerId ?: JSONObject.NULL)
                    .put("visible", marker.visible))
            }
        })
        .put("strokes", JSONArray().apply {
            board.strokes.forEach { stroke ->
                put(JSONObject().put("type", stroke.type.name).put("points", JSONArray().apply {
                    stroke.points.forEach { put(encodePoint(it)) }
                }))
            }
        }).toString()

    fun decode(document: String): TacticalBoard? = try {
        require(document.length <= 560_000)
        val root = JSONObject(document)
        require(root.getInt("version") == VERSION)
        val markers = root.getJSONArray("markers")
        val strokes = root.getJSONArray("strokes")
        require(markers.length() == 11 && strokes.length() <= MAX_STROKES)
        TacticalBoard(
            mode = CourtMode.valueOf(root.getString("mode")),
            markers = List(markers.length()) { index ->
                val marker = markers.getJSONObject(index)
                TacticalMarker(TacticalSide.valueOf(marker.getString("side")), marker.getInt("number"),
                    decodePoint(marker.getJSONArray("point")), if (marker.isNull("player")) null else marker.getLong("player"),
                    marker.optBoolean("visible", true))
            },
            strokes = List(strokes.length()) { index ->
                val stroke = strokes.getJSONObject(index)
                val points = stroke.getJSONArray("points")
                require(points.length() in 2..MAX_STROKE_POINTS)
                TacticalStroke(TacticalStrokeType.valueOf(stroke.getString("type")),
                    List(points.length()) { decodePoint(points.getJSONArray(it)) })
            },
            showAttack = root.getBoolean("attack"),
            showDefense = root.getBoolean("defense"),
            showBall = root.getBoolean("ball"),
        )
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: org.json.JSONException) {
        null
    }

    private fun encodePoint(point: BoardPoint) =
        JSONArray().put(point.x.toString().toDouble()).put(point.y.toString().toDouble())

    private fun decodePoint(values: JSONArray): BoardPoint {
        require(values.length() == 2)
        val x = values.getDouble(0)
        val y = values.getDouble(1)
        require(x in 0.0..1.0 && y in 0.0..1.0)
        return BoardPoint(x.toFloat(), y.toFloat())
    }
}

internal class TacticalPlayStore(private val database: MatchStatsDatabase) {
    private val dao = database.tacticalPlays()
    fun list() = dao.list()
    suspend fun find(id: Long) = dao.find(id)

    suspend fun save(id: Long?, expected: Long?, name: String, snapshot: TacticalSnapshot): Pair<WriteResult, TacticalPlay?> =
        database.withTransaction {
            if (name.isBlank()) return@withTransaction WriteResult.NAME_REQUIRED to null
            val old = id?.let { dao.find(it) }
            if (id != null && old == null) return@withTransaction WriteResult.RECORD_MISSING to null
            if (old != null && old.updatedAt != expected) return@withTransaction WriteResult.TACTICAL_PLAY_CHANGED to null
            val team = snapshot.teamId?.let { database.teams().find(it) }?.takeIf { it.sport == Sport.BASKETBALL }
            val members = team?.let { database.players().roster(it.id) }.orEmpty().map { it.id }.toSet()
            val board = snapshot.board.copy(markers = snapshot.board.markers.map { marker ->
                if (marker.playerId in members) marker else marker.copy(playerId = null)
            })
            val now = maxOf(System.currentTimeMillis(), old?.updatedAt?.plus(1) ?: 0L)
            val play = TacticalPlay(name.trim(), team?.id, board.mode, TacticalDocumentCodec.encode(board), old?.createdAt ?: now, now, id ?: 0)
            val saved = if (id == null) play.copy(id = dao.insert(play)) else play.also {
                dao.update(id, it.name, it.teamId, it.mode, it.document, it.updatedAt)
            }
            WriteResult.SAVED to saved
        }

    suspend fun duplicate(id: Long, name: String): WriteResult = database.withTransaction {
        val play = dao.find(id) ?: return@withTransaction WriteResult.RECORD_MISSING
        val board = TacticalDocumentCodec.decode(play.document)?.takeIf { it.mode == play.mode }
            ?: return@withTransaction WriteResult.INVALID_TACTICAL_PLAY
        save(null, null, name, TacticalSnapshot(board, play.teamId)).first
    }

    suspend fun delete(id: Long) = if (dao.delete(id) > 0) WriteResult.SAVED else WriteResult.RECORD_MISSING
}
