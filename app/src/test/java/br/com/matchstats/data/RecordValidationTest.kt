package br.com.matchstats.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordValidationTest {
    @Test
    fun teamRequiresNonBlankName() {
        val input = TeamInput("  ", Sport.FOOTBALL, "", "")
        assertEquals(WriteResult.NAME_REQUIRED, input.validate())
        assertNull(input.copy(name = "União").validate())
    }

    @Test
    fun playerRequiresNameAndTeamIsOptional() {
        val player = PlayerInput(" ", null, "", null, false)
        assertEquals(WriteResult.NAME_REQUIRED, player.validate())
        assertNull(player.copy(name = "Ana").validate())
        assertNull(player.copy(name = "Ana", teamId = 1).validate())
    }

    @Test
    fun jerseyAcceptsOptionalValuesAndNumbersFromZeroToNinetyNine() {
        val player = PlayerInput("Ana", 1, " ", null, false)
        assertNull(player.validate())
        for (number in listOf("0", " 07 ", "99")) {
            assertNull(player.copy(jersey = number).validate())
        }
    }

    @Test
    fun jerseyRejectsNegativeOversizeAndMalformedValues() {
        val player = PlayerInput("Ana", 1, "", null, false)
        for (number in listOf("-1", "100", "2,5", "+4", "A", "99999999999999")) {
            assertEquals(WriteResult.INVALID_JERSEY, player.copy(jersey = number).validate())
        }
    }
}
