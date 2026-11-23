package br.com.matchstats.data

import kotlin.math.hypot

internal enum class CourtMode(val length: Float) { HALF(14f), FULL(28f) }
internal enum class TacticalSide { ATTACK, DEFENSE, BALL }
internal enum class TacticalStrokeType { MOVEMENT, PASS, SCREEN }

internal data class BoardPoint(val x: Float, val y: Float) {
    init { require(x in 0f..1f && y in 0f..1f) }
}

internal data class TacticalMarker(
    val side: TacticalSide,
    val number: Int,
    val point: BoardPoint,
    val playerId: Long? = null,
    val visible: Boolean = true,
) {
    init {
        require(if (side == TacticalSide.BALL) number == 0 else number in 1..5)
        require(playerId == null || side == TacticalSide.ATTACK && playerId > 0)
    }
}

internal data class TacticalStroke(val type: TacticalStrokeType, val points: List<BoardPoint>) {
    init {
        require(points.size in 2..MAX_STROKE_POINTS)
        require(points.zipWithNext().any { (start, end) -> start != end })
        require(type != TacticalStrokeType.SCREEN || points.size == 2)
    }
}

internal const val MAX_STROKE_POINTS = 160
internal const val MAX_STROKES = 80

internal data class TacticalBoard(
    val mode: CourtMode = CourtMode.HALF,
    val markers: List<TacticalMarker> = initialMarkers(),
    val strokes: List<TacticalStroke> = emptyList(),
    val showAttack: Boolean = true,
    val showDefense: Boolean = false,
    val showBall: Boolean = true,
) {
    init {
        require(markers.size == 11 && markers.distinctBy { it.side to it.number }.size == 11)
        val assigned = markers.mapNotNull { it.playerId }
        require(assigned.toSet().size == assigned.size)
        require(strokes.size <= MAX_STROKES)
    }

    fun shows(marker: TacticalMarker): Boolean = marker.visible && when (marker.side) {
        TacticalSide.ATTACK -> showAttack
        TacticalSide.DEFENSE -> showDefense
        TacticalSide.BALL -> showBall
    }

    fun move(marker: TacticalMarker, point: BoardPoint) = copy(markers = markers.map {
        if (it.side == marker.side && it.number == marker.number) it.copy(point = point) else it
    })

    fun hide(marker: TacticalMarker): TacticalBoard =
        if (marker.side == TacticalSide.BALL) copy(showBall = false)
        else copy(markers = markers.map { if (it == marker) it.copy(visible = false) else it })

    fun assign(number: Int, playerId: Long?) = copy(markers = markers.map { marker ->
        when {
            marker.side != TacticalSide.ATTACK -> marker
            marker.number == number -> marker.copy(playerId = playerId)
            playerId != null && marker.playerId == playerId -> marker.copy(playerId = null)
            else -> marker
        }
    })

    fun attachBall(number: Int): TacticalBoard {
        val player = markers.first { it.side == TacticalSide.ATTACK && it.number == number }
        val direction = if (player.point.x < 0.80f) 1f else -1f
        val nearby = BoardPoint((player.point.x + 0.075f * direction).coerceIn(0f, 1f), player.point.y)
        return move(markers.first { it.side == TacticalSide.BALL }, nearby).copy(showBall = true)
    }
}

internal data class TacticalSnapshot(val board: TacticalBoard, val teamId: Long?)

internal class TacticalHistory(
    val current: TacticalSnapshot,
    private val previous: List<TacticalSnapshot> = emptyList(),
    private val next: List<TacticalSnapshot> = emptyList(),
) {
    val canUndo get() = previous.isNotEmpty()
    val canRedo get() = next.isNotEmpty()

    fun edit(snapshot: TacticalSnapshot): TacticalHistory =
        if (snapshot == current) this else TacticalHistory(snapshot, (previous + current).takeLast(40))

    fun undo(): TacticalHistory =
        if (!canUndo) this else TacticalHistory(previous.last(), previous.dropLast(1), listOf(current) + next)

    fun redo(): TacticalHistory =
        if (!canRedo) this else TacticalHistory(next.first(), previous + current, next.drop(1))
}

private fun initialMarkers(): List<TacticalMarker> {
    val attack = listOf(.50f to .83f, .15f to .61f, .85f to .61f, .69f to .45f, .39f to .31f)
    val defense = listOf(.53f to .61f, .24f to .49f, .76f to .49f, .58f to .35f, .49f to .22f)
    return attack.mapIndexed { index, (x, y) -> TacticalMarker(TacticalSide.ATTACK, index + 1, BoardPoint(x, y)) } +
        defense.mapIndexed { index, (x, y) -> TacticalMarker(TacticalSide.DEFENSE, index + 1, BoardPoint(x, y)) } +
        TacticalMarker(TacticalSide.BALL, 0, BoardPoint(.575f, .83f))
}

internal data class CourtViewport(val width: Float, val height: Float, val mode: CourtMode, val horizontal: Boolean) {
    val scale = minOf(width / (if (horizontal) mode.length else 15f), height / (if (horizontal) 15f else mode.length))
    val courtWidth = scale * if (horizontal) mode.length else 15f
    val courtHeight = scale * if (horizontal) 15f else mode.length
    val left = (width - courtWidth) / 2f
    val top = (height - courtHeight) / 2f

    fun x(point: BoardPoint): Float = left + courtWidth * if (horizontal) point.y else point.x
    fun y(point: BoardPoint): Float = top + courtHeight * if (horizontal) (1f - point.x) else point.y

    fun contains(x: Float, y: Float) = x in left..(left + courtWidth) && y in top..(top + courtHeight)

    fun point(x: Float, y: Float, inset: Float = 0f): BoardPoint {
        val u = ((x - left) / courtWidth).coerceIn(
            (inset / courtWidth).coerceAtMost(.49f), 1f - (inset / courtWidth).coerceAtMost(.49f),
        )
        val v = ((y - top) / courtHeight).coerceIn(
            (inset / courtHeight).coerceAtMost(.49f), 1f - (inset / courtHeight).coerceAtMost(.49f),
        )
        return if (horizontal) BoardPoint(1f - v, u) else BoardPoint(u, v)
    }

    fun markerAt(board: TacticalBoard, x: Float, y: Float, tolerance: Float): TacticalMarker? =
        board.markers.asReversed().asSequence().filter(board::shows)
            .map { it to hypot(x - x(it.point), y - y(it.point)) }
            .filter { (_, distance) -> distance <= tolerance }.minByOrNull { it.second }?.first

    fun strokeAt(strokes: List<TacticalStroke>, x: Float, y: Float, tolerance: Float): Int? =
        strokes.indices.reversed().firstOrNull { index ->
            strokes[index].points.zipWithNext().any { (a, b) ->
                val dx = x(b) - x(a)
                val dy = y(b) - y(a)
                val norm = dx * dx + dy * dy
                val progress = if (norm == 0f) 0f else (((x - x(a)) * dx + (y - y(a)) * dy) / norm).coerceIn(0f, 1f)
                hypot(x - (x(a) + dx * progress), y - (y(a) + dy * progress)) <= tolerance
            }
        }
}
