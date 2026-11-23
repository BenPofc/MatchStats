package br.com.matchstats

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.matchstats.data.BoxBuiltin
import br.com.matchstats.data.BoxMeasurement
import br.com.matchstats.data.MatchInput
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.PlayerInput
import br.com.matchstats.data.Sport
import br.com.matchstats.data.TeamInput
import br.com.matchstats.data.WriteResult
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveGameUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun basketballStartsRecordsForBothSidesAndReopensHistory() = withFixture(Sport.BASKETBALL) { repo, matchId ->
        waitFor("10:00")
        button("Iniciar cronômetro")
        waitFor("Pausar cronômetro")
        button("Pausar cronômetro")
        waitFor("Pausado", substring = true)
        button("Resetar 14 s")
        waitFor("14 s")
        button("Nº 8")
        button("+2")
        button("Adversário")
        field("Camisa do adversário", "7")
        button("+3")
        button("Sua equipe")
        capture("basquete")
        runBlocking {
            val box = repo.observeBoxScore(matchId).first()!!
            assertEquals(BoxMeasurement(1, 1), box.players.single().measurements[BoxBuiltin.TWO.id])
            assertEquals(7, box.opponents.single().jersey)
            assertEquals(3, box.opponents.single().canonical!!.points)
        }

        button("Histórico de eventos")
        waitFor("Cesta de três pontos")
        button("Ajustar placar")
        field("Novo placar do lado selecionado", "4")
        button("Salvar")
        waitFor("4 × 3")
        back()
        button("Finalizar partida")
        button("Finalizar partida", last = true, scroll = false)
        button("Histórico de eventos")
        waitFor("Ajuste de placar", substring = true)
        runBlocking {
            assertEquals(MatchStatus.FINISHED, repo.observeMatch(matchId).first()!!.match.status)
            assertEquals(4, repo.observeMatch(matchId).first()!!.match.teamScore)
        }
    }

    @Test
    fun footballResumesSameSessionAndRecordsTeamEventInSecondHalf() = withFixture(Sport.FOOTBALL) { repo, matchId ->
        waitFor("00:00")
        button("Iniciar cronômetro")
        button("Pausar cronômetro")
        button("Próximo tempo")
        button("Avançar", scroll = false)
        waitFor("2º tempo", substring = true)
        waitFor("45:00")
        back()
        button("Retomar acompanhamento")
        waitFor("2º tempo", substring = true)
        button("Gol")
        val live = runBlocking { repo.live.observe(matchId).first()!! }
        assertEquals(1, live.session!!.homeScore)
        assertEquals(null, live.events.single().playerId)
        capture("futebol")
        button("Finalizar partida")
        button("Finalizar partida", last = true, scroll = false)
        waitFor("Finalizada")
    }

    private fun withFixture(sport: Sport, play: (MatchStatsRepository, Long) -> Unit) {
        val suffix = "LV${System.nanoTime().toString().takeLast(8)}"
        val teamName = "União $suffix"
        val playerName = "Ana $suffix"
        val opponent = "Rivais $suffix"
        val repo = (compose.activity.application as MatchStatsApplication).repository
        var matchId: Long? = null
        try {
            val id = runBlocking {
                assertEquals(WriteResult.SAVED, repo.saveTeam(null, TeamInput(teamName, sport, "", "")))
                val team = repo.observeTeams().first().single { it.name == teamName }.id
                assertEquals(WriteResult.SAVED, repo.createPlayer(PlayerInput(playerName, team, "8", null, false)))
                val match = MatchInput(team, opponent, LocalDate.now().plusDays(1), LocalTime.NOON, "", MatchStatus.SCHEDULED, "", "")
                assertEquals(WriteResult.SAVED, repo.saveMatch(null, match))
                repo.observeMatches(MatchStatus.SCHEDULED).first().single { it.match.opponent == opponent }.match.id
            }
            matchId = id
            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            compose.onNode(hasText("Partidas") and tab).performSemanticsAction(SemanticsActions.OnClick) { it() }
            button(opponent)
            button("Iniciar ao vivo")
            button("Preparar sessão")
            play(repo, id)
        } finally {
            runBlocking {
                matchId?.let { id ->
                    val match = repo.observeMatch(id).first()
                    if (match?.live != null && match.match.status == MatchStatus.SCHEDULED) repo.live.discard(id)
                }
                repo.observeTeams().first().filter { it.name == teamName }.forEach { repo.deleteTeam(it.id) }
                repo.observeDirectory().first().filter { it.player.name == playerName }.forEach { repo.deletePlayer(it.player.id) }
            }
        }
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun button(text: String, last: Boolean = false, scroll: Boolean = true) {
        val condition = hasText(text) and hasClickAction() and !hasSetTextAction()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodes(condition).fetchSemanticsNodes().isNotEmpty()
        }
        val node = if (last) compose.onAllNodes(condition)[compose.onAllNodes(condition).fetchSemanticsNodes().lastIndex]
        else compose.onNode(condition)
        if (scroll) node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun field(label: String, text: String) {
        waitFor(label)
        val node = compose.onNode(hasSetTextAction() and (hasText(label) or hasAnyDescendant(hasText(label))))
        node.performScrollTo().performTextReplacement(text)
        node.performImeAction()
    }

    private fun back() = compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick) { it() }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureLive") != "true") return
        compose.waitForIdle()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.cacheDir, "matchstats-phase7-$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
