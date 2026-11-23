package br.com.matchstats.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MatchStatsDatabaseTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: MatchStatsDatabase
    private lateinit var repository: MatchStatsRepository

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, MatchStatsDatabase::class.java).build()
        repository = MatchStatsRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun rosterCountsUpdateAndTeamDeletionPreservesProfiles() = runBlocking {
        val football = newTeam("União", Sport.FOOTBALL)
        val basketball = newTeam("Vila", Sport.BASKETBALL)
        assertEquals(WriteResult.SAVED, repository.createPlayer(player(football, "Rafa", "7")))
        assertEquals(WriteResult.SAVED, repository.createPlayer(player(football, "Luiza", "")))
        assertEquals(WriteResult.SAVED, repository.createPlayer(player(football, "João", "")))
        assertEquals(WriteResult.SAVED, repository.createPlayer(player(basketball, "Ana", "7")))
        assertEquals(DashboardCounts(2, 4), repository.observeDashboard().first())
        assertEquals(3, repository.observeTeamSummaries().first().single { it.team.id == football }.playerCount)

        val roster = repository.observeRoster(football).first()!!
        assertEquals(WriteResult.SAVED, repository.deletePlayer(roster.players.single { it.name == "Rafa" }.id))
        assertEquals(2, repository.observeRoster(football).first()!!.players.size)
        assertEquals(WriteResult.SAVED, repository.deleteTeam(football))
        assertNull(repository.observeRoster(football).first())
        assertEquals(DashboardCounts(1, 3), repository.observeDashboard().first())
        assertEquals("Ana", repository.observeRoster(basketball).first()!!.players.single().name)
    }

    @Test
    fun jerseyDuplicateIsRejectedEvenDuringConcurrentWrites() = runBlocking {
        val teamId = newTeam("União", Sport.FOOTBALL)
        val first = async { repository.createPlayer(player(teamId, "Rafa", "07")) }
        val second = async { repository.createPlayer(player(teamId, "João", "7")) }
        assertEquals(
            setOf(WriteResult.SAVED, WriteResult.DUPLICATE_JERSEY),
            setOf(first.await(), second.await()),
        )
        val saved = repository.observeRoster(teamId).first()!!.players.single()
        assertEquals(WriteResult.SAVED, repository.renamePlayer(saved.id, "Novo nome"))
        assertEquals(1, repository.observeDirectory().first().size)
        assertEquals("Novo nome", repository.observePlayer(saved.id).first()!!.player.name)
    }

    @Test
    fun reusablePlayerCanJoinMultipleTeamsWithIndependentRosterFields() = runBlocking {
        val football = newTeam("União", Sport.FOOTBALL)
        val basketball = newTeam("Vila", Sport.BASKETBALL)
        assertEquals(WriteResult.SAVED, repository.createPlayer(PlayerInput("Rafa", null, "", null, false)))
        val unassigned = repository.observeDirectory().first().single()
        val playerId = unassigned.player.id
        assertEquals(emptyList<PlayerTeam>(), unassigned.teams)
        assertNull(unassigned.primaryTeam(null))
        val footballRole = MembershipInput(football, "7", Position.GOALKEEPER, false)
        assertEquals(WriteResult.TEAM_MISSING, repository.saveMembership(playerId, footballRole.copy(teamId = Long.MAX_VALUE), false))
        assertEquals(WriteResult.INVALID_POSITION, repository.saveMembership(playerId, footballRole.copy(teamId = basketball), false))
        assertEquals(WriteResult.SAVED, repository.saveMembership(playerId, footballRole, false))
        assertEquals(WriteResult.DUPLICATE_MEMBERSHIP, repository.saveMembership(playerId, footballRole, false))
        val basketballRole = MembershipInput(basketball, "7", Position.POINT_GUARD, true)
        assertEquals(WriteResult.SAVED, repository.saveMembership(playerId, basketballRole, false))
        assertEquals(DashboardCounts(2, 1), repository.observeHome().first().counts)
        assertEquals(WriteResult.SAVED, repository.createPlayer(player(basketball, "Ana", "8")))
        assertEquals(WriteResult.DUPLICATE_JERSEY, repository.saveMembership(playerId, basketballRole.copy(jersey = "8"), true))
        assertEquals(WriteResult.SAVED, repository.saveMembership(playerId, basketballRole.copy(jersey = "9"), true))
        val reused = repository.observeDirectory().first().single { it.player.id == playerId }
        assertEquals(listOf(7, 9), reused.teams.mapNotNull { it.jerseyNumber }.sorted())
        assertEquals(Position.GOALKEEPER, repository.observeRoster(football).first()!!.players.single().position)
        assertEquals(Position.POINT_GUARD, reused.teams.single { it.team.id == basketball }.position)
        assertEquals(true, reused.teams.single { it.team.id == basketball }.captain)
    }

    @Test
    fun editingTeamSportClearsIncompatiblePositionsAndKeepsPlayerAndTeamIds() = runBlocking {
        val id = newTeam("União", Sport.FOOTBALL)
        assertEquals(WriteResult.SAVED, repository.createPlayer(player(id, "Rafa", "10").copy(position = Position.FORWARD)))
        val previous = repository.observeRoster(id).first()!!
        assertEquals(WriteResult.SAVED, repository.saveTeam(id, TeamInput("União BC", Sport.BASKETBALL, "Sub-20", "Ana")))
        val current = repository.observeRoster(id).first()!!
        assertEquals("União BC", current.team.name)
        assertEquals("Sub-20", current.team.category)
        assertEquals("Ana", current.team.coachName)
        assertEquals(previous.team.createdAt, current.team.createdAt)
        assertEquals(previous.players.single().copy(position = null), current.players.single())
    }

    @Test
    fun fileDatabaseRetainsRecordsAfterClosingAndReopening() = runBlocking {
        val filename = "matchstats-test-${UUID.randomUUID()}.db"
        val open = { Room.databaseBuilder(context, MatchStatsDatabase::class.java, filename).build() }
        try {
            val first = open()
            try {
                val id = first.teams().insert(Team("União", Sport.FOOTBALL, null, null))
                assertEquals(WriteResult.SAVED, MatchStatsRepository(first).createPlayer(player(id, "Rafa", "7")))
            } finally {
                first.close()
            }
            val reopened = open()
            try {
                val repo = MatchStatsRepository(reopened)
                assertEquals(DashboardCounts(1, 1), repo.observeDashboard().first())
                val id = repo.observeTeamSummaries().first().single().team.id
                assertEquals("Rafa", repo.observeRoster(id).first()!!.players.single().name)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
        Unit
    }

    private suspend fun newTeam(name: String, sport: Sport): Long = database.teams().insert(
        Team(name, sport, null, null),
    )

    private fun player(team: Long, name: String, jersey: String) = PlayerInput(name, team, jersey, null, false)
}
