package br.com.matchstats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TacticalBoardTest {
    @Test fun courtFitsBothOrientationsAndCoordinatesRoundTrip() {
        val reference = BoardPoint(.23f, .76f)
        for (mode in CourtMode.entries) {
            for (horizontal in listOf(false, true)) {
                val view = CourtViewport(573f, 688f, mode, horizontal)
                val widthMeters = if (horizontal) mode.length else 15f
                val heightMeters = if (horizontal) 15f else mode.length
                assertEquals(widthMeters / heightMeters, view.courtWidth / view.courtHeight, .0001f)
                val restored = view.point(view.x(reference), view.y(reference))
                assertEquals(reference.x, restored.x, .0001f)
                assertEquals(reference.y, restored.y, .0001f)
                assertTrue(view.contains(view.x(reference), view.y(reference)))
                val clamped = view.point(-100f, 1200f, 16f)
                assertTrue(view.x(clamped) - view.left >= 15.99f)
                assertTrue(view.top + view.courtHeight - view.y(clamped) >= 15.99f)
            }
        }
    }

    @Test fun hitTestUsesVisibleMarkersAndTopmostNearbyStroke() {
        val board = TacticalBoard()
        val view = CourtViewport(500f, 500f, CourtMode.HALF, false)
        val defender = board.markers.first { it.side == TacticalSide.DEFENSE }
        val x = view.x(defender.point)
        val y = view.y(defender.point)
        assertNull(view.markerAt(board, x, y, 10f))
        assertEquals(defender, view.markerAt(board.copy(showDefense = true), x, y, 10f))
        val points = listOf(BoardPoint(.2f, .7f), BoardPoint(.8f, .7f))
        val near = BoardPoint(.5f, .7f)
        val strokes = listOf(TacticalStroke(TacticalStrokeType.PASS, points), TacticalStroke(TacticalStrokeType.MOVEMENT, points))
        assertEquals(1, view.strokeAt(strokes, view.x(near), view.y(near), 10f))
        assertNull(view.strokeAt(strokes, view.x(near), view.y(near) - 24f, 10f))
    }

    @Test fun undoAndRedoTrackDragDrawingEraseClearAndTeamChanges() {
        val board = TacticalBoard()
        val base = TacticalSnapshot(board, null)
        val moved = board.move(board.markers.first(), BoardPoint(.23f, .43f)).attachBall(1)
        val stroke = TacticalStroke(TacticalStrokeType.SCREEN, listOf(BoardPoint(.3f, .2f), BoardPoint(.45f, .65f)))
        val drawn = TacticalSnapshot(moved.copy(strokes = listOf(stroke)), 7)
        val history = TacticalHistory(base).edit(TacticalSnapshot(moved, null)).edit(drawn)
        val erased = history.edit(drawn.copy(board = drawn.board.copy(strokes = emptyList())))
        assertEquals(drawn, erased.undo().current)
        assertEquals(moved, erased.undo().undo().current.board)
        assertEquals(base, erased.undo().undo().undo().current)
        assertEquals(erased.current, erased.undo().redo().current)
        assertFalse(erased.undo().edit(drawn.copy(teamId = null)).canRedo)
        assertEquals(0, base.board.strokes.size)
    }

    @Test fun aRosterPlayerCanOccupyOnlyOneOffensiveMarkerAndBallStaysInBounds() {
        val board = TacticalBoard().assign(1, 44).assign(4, 44)
        assertNull(board.markers.first().playerId)
        assertEquals(44L, board.markers.single { it.number == 4 && it.side == TacticalSide.ATTACK }.playerId)
        val nearEdge = board.move(board.markers.first(), BoardPoint(.99f, .1f)).attachBall(1)
        val ball = nearEdge.markers.single { it.side == TacticalSide.BALL }
        assertTrue(ball.point.x < .99f)
        assertEquals(.1f, ball.point.y, .001f)
    }
}
