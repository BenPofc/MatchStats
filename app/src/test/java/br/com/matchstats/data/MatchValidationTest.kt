package br.com.matchstats.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchValidationTest {
    private val utc = ZoneOffset.UTC
    private val draft = MatchInput(
        1, "Vila", LocalDate.of(2026, 11, 18), LocalTime.of(16, 0), "",
        MatchStatus.SCHEDULED, "", "",
    )

    @Test fun requiresTeamOpponentDateAndTime() {
        assertEquals(WriteResult.TEAM_REQUIRED, draft.copy(teamId = null).validate(utc))
        assertEquals(WriteResult.OPPONENT_REQUIRED, draft.copy(opponent = " ").validate(utc))
        assertEquals(WriteResult.DATE_REQUIRED, draft.copy(date = null).validate(utc))
        assertEquals(WriteResult.TIME_REQUIRED, draft.copy(time = null).validate(utc))
        assertNull(draft.validate(utc))
    }

    @Test fun finishedRequiresTwoNonnegativeIntegersAndScheduledIgnoresScoreDrafts() {
        val scores = draft.copy(status = MatchStatus.FINISHED, teamScore = "0", opponentScore = " 9 ")
        assertNull(scores.validate(utc))
        for (value in listOf("", "-1", "1.5", "+3", "2147483648")) {
            assertEquals(WriteResult.INVALID_TEAM_SCORE, scores.copy(teamScore = value).validate(utc))
            assertEquals(WriteResult.INVALID_OPPONENT_SCORE, scores.copy(opponentScore = value).validate(utc))
        }
        assertNull(draft.copy(teamScore = "-5", opponentScore = "abc").validate(utc))
    }

    @Test fun rejectsNonexistentLocalTime() {
        val gap = draft.copy(date = LocalDate.of(2026, 3, 8), time = LocalTime.of(2, 30))
        assertEquals(WriteResult.INVALID_MATCH_TIME, gap.validate(ZoneId.of("America/New_York")))
        assertNull(gap.validate(utc))
    }

    @Test fun metricsMatchSportAndDefaultToZero() {
        assertTrue(StatValues().validate(Sport.FOOTBALL))
        assertTrue(StatValues().validate(Sport.BASKETBALL))
        assertTrue(StatValues(assists = 1, points = 11, rebounds = 5).validate(Sport.BASKETBALL))
        assertFalse(StatValues(goals = 2).validate(Sport.BASKETBALL))
        assertFalse(StatValues(redCards = -1).validate(Sport.FOOTBALL))
        assertEquals(0, StatValues.from(mapOf(StatMetric.GOALS to 2))[StatMetric.ASSISTS])
    }
}
