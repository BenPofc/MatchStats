package br.com.matchstats.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.matchstats.R
import br.com.matchstats.data.BoardPoint
import br.com.matchstats.data.CourtMode
import br.com.matchstats.data.CourtViewport
import br.com.matchstats.data.MAX_STROKE_POINTS
import br.com.matchstats.data.MAX_STROKES
import br.com.matchstats.data.TacticalBoard
import br.com.matchstats.data.TacticalMarker
import br.com.matchstats.data.TacticalSide
import br.com.matchstats.data.TacticalStroke
import br.com.matchstats.data.TacticalStrokeType
import br.com.matchstats.ui.theme.MatchStatsColors
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

internal enum class CourtTool(val label: Int, val stroke: TacticalStrokeType? = null) {
    SELECT(R.string.tactical_select),
    MOVEMENT(R.string.tactical_move, TacticalStrokeType.MOVEMENT),
    PASS(R.string.tactical_pass, TacticalStrokeType.PASS),
    SCREEN(R.string.tactical_screen, TacticalStrokeType.SCREEN),
    ERASE(R.string.tactical_erase),
}

@Composable
internal fun BasketballCourtCanvas(
    board: TacticalBoard,
    modifier: Modifier,
    horizontal: Boolean,
    readOnly: Boolean,
    tool: CourtTool = CourtTool.SELECT,
    selection: TacticalMarker? = null,
    onSelect: (TacticalMarker?) -> Unit = {},
    onEdit: (TacticalBoard) -> Unit = {},
) {
    val latestBoard by rememberUpdatedState(board)
    val select by rememberUpdatedState(onSelect)
    val edit by rememberUpdatedState(onEdit)
    val geometry = remember(board.mode) { courtMarks(board.mode) }
    val text = rememberTextMeasurer()
    val description = stringResource(if (board.mode == CourtMode.FULL) R.string.tactical_full else R.string.tactical_half)
    var moving by remember { mutableStateOf<Pair<TacticalMarker, BoardPoint>?>(null) }
    var trace by remember { mutableStateOf<List<BoardPoint>>(emptyList()) }

    val gestures = if (readOnly) Modifier else Modifier.pointerInput(tool, board.mode, horizontal) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val viewport = CourtViewport(size.width.toFloat(), size.height.toFloat(), latestBoard.mode, horizontal)
            if (!viewport.contains(down.position.x, down.position.y)) return@awaitEachGesture
            val original = latestBoard
            val marker = if (tool == CourtTool.SELECT) viewport.markerAt(original, down.position.x, down.position.y, 23.dp.toPx()) else null
            val grabOffset = marker?.let { down.position - viewport.offset(it.point) } ?: Offset.Zero
            val stroke = tool.stroke?.takeIf { original.strokes.size < MAX_STROKES }
            val first = viewport.point(down.position.x, down.position.y)
            val points = mutableListOf(first)
            var last = down.position
            var end = down.position
            var dragging = false
            var finished = false
            if (tool == CourtTool.SELECT) select(marker)
            down.consume()
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.find { it.id == down.id } ?: break
                    if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                    end = change.position
                    dragging = dragging || (end - down.position).getDistance() >= viewConfiguration.touchSlop
                    if (dragging) {
                        if (marker != null) {
                            val target = end - grabOffset
                            moving = marker to viewport.point(target.x, target.y, 15.dp.toPx())
                        }
                        if (stroke != null) {
                            val point = viewport.point(end.x, end.y)
                            if (stroke == TacticalStrokeType.SCREEN) {
                                trace = listOf(first, point)
                            } else if ((end - last).getDistance() >= 4.dp.toPx() && points.size < MAX_STROKE_POINTS - 1) {
                                last = end
                                if (point != points.last()) points.add(point)
                                trace = points.toList()
                            }
                        }
                    }
                    change.consume()
                    if (!change.pressed) {
                        finished = true
                        break
                    }
                }
                if (finished && dragging && marker != null) {
                    moving?.second?.let { edit(original.move(marker, it)) }
                } else if (finished && dragging && stroke != null) {
                    val endpoint = viewport.point(end.x, end.y)
                    if (endpoint != points.last()) points.add(endpoint)
                    if (points.size >= 2) edit(original.copy(strokes = original.strokes + TacticalStroke(stroke, points.toList())))
                } else if (finished && tool == CourtTool.ERASE && !dragging) {
                    val index = viewport.strokeAt(original.strokes, end.x, end.y, 18.dp.toPx())
                    if (index != null) edit(original.copy(strokes = original.strokes.filterIndexed { i, _ -> i != index }))
                    else viewport.markerAt(original, end.x, end.y, 20.dp.toPx())?.let { edit(original.hide(it)) }
                }
            } finally {
                moving = null
                trace = emptyList()
            }
        }
    }

    Canvas(modifier.testTag(if (readOnly) "tactical-presentation-court" else "tactical-court")
        .semantics { contentDescription = description }.then(gestures)) {
        val viewport = CourtViewport(size.width, size.height, board.mode, horizontal)
        if (viewport.scale <= 0f) return@Canvas
        renderCourt(viewport, geometry)
        board.strokes.forEach { renderStroke(it.type, it.points, viewport) }
        tool.stroke?.let { if (!readOnly) renderStroke(it, trace, viewport) }

        val markerRadius = 14.dp.toPx().coerceAtMost(viewport.scale * .65f)
        board.markers.filter(board::shows).forEach { marker ->
            val point = moving?.takeIf { it.first.side == marker.side && it.first.number == marker.number }?.second ?: marker.point
            val center = viewport.offset(point)
            val selected = !readOnly && selection?.let { marker.side == it.side && marker.number == it.number } == true
            if (marker.side == TacticalSide.BALL) {
                val radius = markerRadius * .63f
                if (selected) drawCircle(MatchStatsColors.Navy, radius + 4.dp.toPx(), center)
                drawCircle(Color(0xFFFF9B45), radius, center)
                drawCircle(MatchStatsColors.Navy, radius, center, style = Stroke(1.35.dp.toPx()))
                drawLine(MatchStatsColors.Navy, center - Offset(radius, 0f), center + Offset(radius, 0f), 1.dp.toPx())
                drawArc(MatchStatsColors.Navy, -65f, 130f, false,
                    center - Offset(radius * 1.37f, radius), Size(radius * 1.37f, radius * 2), style = Stroke(1.dp.toPx()))
            } else {
                if (selected) drawCircle(Color(0xFFF8CF50), markerRadius + 4.dp.toPx(), center)
                val fill = if (marker.side == TacticalSide.ATTACK) Color(0xFF087A38) else MatchStatsColors.Navy
                drawCircle(Color(0x350C1B28), markerRadius + 1.dp.toPx(), center + Offset(0f, 2.dp.toPx()))
                drawCircle(fill, markerRadius, center)
                drawCircle(Color.White, markerRadius, center, style = Stroke(1.8.dp.toPx()))
                val numeral = text.measure(marker.number.toString(), TextStyle(color = Color.White,
                    fontSize = (markerRadius * .98f).toSp(), fontWeight = FontWeight.Bold))
                drawText(numeral, topLeft = center - Offset(numeral.size.width / 2f, numeral.size.height / 2f))
            }
        }
    }
}

private fun CourtViewport.offset(point: BoardPoint) = Offset(x(point), y(point))

private data class CourtMark(val points: List<BoardPoint>, val fill: Boolean = false, val dashed: Boolean = false)

private fun courtMarks(mode: CourtMode): List<CourtMark> = buildList {
    val length = mode.length
    fun point(x: Float, y: Float) = BoardPoint((x / 15f).coerceIn(0f, 1f), (y / length).coerceIn(0f, 1f))
    fun arc(cx: Float, cy: Float, r: Float, from: Double, to: Double): List<Pair<Float, Float>> =
        List(49) { index ->
            val angle = from + (to - from) * index / 48.0
            (cx + r * cos(angle).toFloat()) to (cy + r * sin(angle).toFloat())
        }

    for (far in listOf(false, true).take(if (mode == CourtMode.FULL) 2 else 1)) {
        fun mark(points: List<Pair<Float, Float>>, fill: Boolean = false, dashed: Boolean = false) {
            add(CourtMark(points.map { (x, y) -> point(x, if (far) length - y else y) }, fill, dashed))
        }

        val paint = listOf(5.05f to 0f, 5.05f to 5.8f, 9.95f to 5.8f, 9.95f to 0f, 5.05f to 0f)
        mark(paint, fill = true)
        mark(paint)
        mark(arc(7.5f, 5.8f, 1.8f, 0.0, Math.PI))
        mark(arc(7.5f, 5.8f, 1.8f, Math.PI, Math.PI * 2), dashed = true)
        val baseAngle = acos(6.60 / 6.75)
        val threePointArc = arc(7.5f, 1.575f, 6.75f, Math.PI - baseAngle, baseAngle)
        mark(listOf(.9f to 0f) + threePointArc + listOf(14.1f to 0f))
        mark(arc(7.5f, 1.575f, 1.30f, 0.0, Math.PI))
        mark(arc(7.5f, 1.575f, .225f, 0.0, Math.PI * 2))
        mark(listOf(6.6f to 1.2f, 8.4f to 1.2f))
    }
    if (mode == CourtMode.FULL) {
        add(CourtMark(listOf(point(0f, 14f), point(15f, 14f))))
        add(CourtMark(arc(7.5f, 14f, 1.8f, 0.0, Math.PI * 2).map { point(it.first, it.second) }))
    }
    add(CourtMark(listOf(point(0f, 0f), point(15f, 0f), point(15f, length), point(0f, length), point(0f, 0f))))
}

private fun DrawScope.renderCourt(view: CourtViewport, geometry: List<CourtMark>) {
    val origin = Offset(view.left, view.top)
    val area = Size(view.courtWidth, view.courtHeight)
    drawRect(Brush.linearGradient(listOf(Color(0xFFFFDA9D), Color(0xFFEABC7C)), origin, origin + Offset(area.width, area.height)),
        origin, area)
    val rows = 22
    for (i in 1 until rows) {
        val y = view.top + area.height * i / rows
        drawLine(Color(0x237D4A25), Offset(view.left, y), Offset(view.left + area.width, y), .65.dp.toPx())
    }
    geometry.forEach { mark ->
        val path = outline(mark.points, view)
        if (mark.fill) drawPath(path, Color(0xFF69A871))
        else drawPath(path, Color(0xFFFFFAED), style = Stroke(width = 1.65.dp.toPx(),
            pathEffect = if (mark.dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null))
    }
}

private fun outline(points: List<BoardPoint>, view: CourtViewport) = Path().apply {
    val start = view.offset(points.first())
    moveTo(start.x, start.y)
    points.drop(1).forEach { point ->
        val end = view.offset(point)
        lineTo(end.x, end.y)
    }
}

private fun DrawScope.renderStroke(type: TacticalStrokeType, points: List<BoardPoint>, view: CourtViewport) {
    if (points.size < 2 || points.first() == points.last() && points.size == 2) return
    val color = if (type == TacticalStrokeType.PASS) MatchStatsColors.Navy else Color(0xFF075328)
    val lineWidth = 3.1.dp.toPx()
    val dash = if (type == TacticalStrokeType.PASS) PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())) else null
    val path = outline(points, view)
    drawPath(path, Color.White, style = Stroke(lineWidth + 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash))
    drawPath(path, color, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash))
    val tip = view.offset(points.last())
    if (type == TacticalStrokeType.SCREEN) {
        val vector = tip - view.offset(points.first())
        val side = Offset(-vector.y, vector.x) * (7.dp.toPx() / vector.getDistance())
        listOf(tip, view.offset(points.first())).forEach { end ->
            drawLine(Color.White, end - side, end + side, lineWidth + 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, end - side, end + side, lineWidth, StrokeCap.Round)
        }
    } else {
        val previous = points.asReversed().drop(1).map(view::offset).firstOrNull { (tip - it).getDistance() >= 4.dp.toPx() }
            ?: return
        val vector = tip - previous
        val direction = vector / vector.getDistance()
        val wing = Offset(-direction.y, direction.x) * 5.dp.toPx()
        val tail = tip - direction * 9.dp.toPx()
        val arrow = Path().apply {
            moveTo((tail + wing).x, (tail + wing).y)
            lineTo(tip.x, tip.y)
            lineTo((tail - wing).x, (tail - wing).y)
        }
        drawPath(arrow, Color.White, style = Stroke(lineWidth + 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(arrow, color, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
