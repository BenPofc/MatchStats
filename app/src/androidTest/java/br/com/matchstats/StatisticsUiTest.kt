package br.com.matchstats

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.matchstats.data.MatchInput
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.PlayerInput
import br.com.matchstats.data.Sport
import br.com.matchstats.data.StatValues
import br.com.matchstats.data.TeamInput
import br.com.matchstats.data.WriteResult
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardFiltersRanksAndRefreshesWithTeamSummaryAndLocalProfile() {
        val suffix = "P4${System.nanoTime().toString().takeLast(6)}"
        val football = "União $suffix"
        val basket = "Vila $suffix"
        val alternate = "Estrela $suffix"
        val scorer = "Lia $suffix"
        val assistant = "Ana $suffix"
        val basketballPlayer = "Bia $suffix"
        val activity = compose.activity
        val repo = (activity.application as MatchStatsApplication).repository
        val prefs = activity.getSharedPreferences("matchstats_profile", Context.MODE_PRIVATE)
        val previousName = prefs.getString("name", null)
        if (InstrumentationRegistry.getArguments().getString("captureMode") == "large") {
            assertTrue(activity.resources.configuration.fontScale >= 1.5f)
        }
        try {
            val fixture = runBlocking {
                val footballId = addTeam(repo, football, Sport.FOOTBALL)
                val basketId = addTeam(repo, basket, Sport.BASKETBALL)
                val alternateId = addTeam(repo, alternate, Sport.FOOTBALL)
                val lia = addPlayer(repo, footballId, scorer)
                val ana = addPlayer(repo, footballId, assistant)
                val bia = addPlayer(repo, basketId, basketballPlayer)
                val win = match(footballId, "Rivais $suffix", 3, 1)
                val winId = save(repo, win)
                val draw = save(repo, match(footballId, "Capital $suffix", 0, 0))
                save(repo, match(footballId, "Partida futura $suffix", 0, 0).copy(status = MatchStatus.SCHEDULED))
                val other = save(repo, match(alternateId, "Estádio $suffix", 1, 1))
                val basketWin = save(repo, match(basketId, "Central $suffix", 74, 68))
                assertEquals(
                    WriteResult.SAVED,
                    repo.saveMatchStats(winId, mapOf(lia to StatValues(goals = 2), ana to StatValues(goals = 1, assists = 2))),
                )
                assertEquals(WriteResult.SAVED, repo.saveMatchStats(basketWin, mapOf(bia to StatValues(points = 18, rebounds = 9, assists = 5))))
                Fixture(win, winId, draw, other)
            }

            tab("Estatísticas")
            visible("Todas as equipes", scroll = true)
            teamFilter(football)
            metric("Partidas finalizadas", "2")
            metric("Taxa de vitórias", "50%")
            metric("Vitórias", "1")
            metric("Empates", "1")
            metric("Derrotas", "0")
            metric("Gols marcados", "3")
            metric("Gols sofridos", "1")
            metric("Artilheiro", scorer)
            metric("Maior assistente", assistant)
            visible("Desempenho de jogadores e equipes em um só lugar.", scroll = true)
            capture("futebol")
            visible("Gols nas últimas partidas", scroll = true)
            visible("Rivais $suffix", scroll = true, substring = true)
            assertTrue(compose.onAllNodes(hasText("Partida futura $suffix", substring = true)).fetchSemanticsNodes().isEmpty())
            visible("Ranking de jogadores", scroll = true)
            metric(scorer, "2")
            click("Assistências", role(Role.Checkbox), scroll = true)
            metric(assistant, "2")
            capture("ranking")

            teamFilter(alternate)
            metric("Partidas finalizadas", "1")
            metric("Taxa de vitórias", "0%")
            visible("Nenhum valor registrado para este indicador.", scroll = true)
            assertEquals(WriteResult.SAVED, runBlocking { repo.deleteMatch(fixture.alternateMatch) })
            visible("Sem estatísticas de futebol", scroll = true)
            teamFilter(football)
            assertEquals(WriteResult.SAVED, runBlocking { repo.saveMatch(fixture.winId, fixture.win.copy(opponentScore = "4")) })
            metric("Gols sofridos", "4")
            metric("Derrotas", "1")
            metric("Taxa de vitórias", "0%")
            assertEquals(WriteResult.SAVED, runBlocking { repo.deleteMatch(fixture.draw) })
            metric("Partidas finalizadas", "1")

            tab("Equipes")
            click(football, scroll = true)
            visible("Desempenho da equipe", scroll = true)
            metric("Gols sofridos", "4")
            capture("equipe")

            tab("Estatísticas")
            click("Basquete", role(Role.Checkbox), scroll = true)
            teamFilter(basket)
            metric("Pontos marcados", "74")
            metric("Pontos sofridos", "68")
            metric("Taxa de vitórias", "100%")
            metric("Cestinha", basketballPlayer)
            metric("Líder em rebotes", basketballPlayer)
            visible("Desempenho de jogadores e equipes em um só lugar.", scroll = true)
            capture("basquete")
            visible("Pontos nas últimas partidas", scroll = true)
            click("Rebotes", role(Role.Checkbox), scroll = true)
            metric(basketballPlayer, "9")

            tab("Perfil")
            click("Editar nome", scroll = true)
            val label = "Nome do perfil"
            val nameField = hasSetTextAction() and (hasText(label) or hasAnyDescendant(hasText(label)))
            waitFor(nameField)
            compose.onNode(nameField).performTextReplacement("Camila $suffix")
            click("Salvar")
            visible("Camila $suffix", scroll = true)
            compose.activityRule.scenario.recreate()
            visible("Camila $suffix", scroll = true)
            capture("perfil")
        } finally {
            runBlocking {
                repo.observeTeams().first().filter { it.name.endsWith(suffix) }.forEach { repo.deleteTeam(it.id) }
                val testPlayers = setOf(scorer, assistant, basketballPlayer)
                repo.observeDirectory().first().filter { it.player.name in testPlayers }
                    .forEach { repo.deletePlayer(it.player.id) }
            }
            if (previousName == null) prefs.edit().remove("name").commit()
            else prefs.edit().putString("name", previousName).commit()
        }
    }


    private data class Fixture(val win: MatchInput, val winId: Long, val draw: Long, val alternateMatch: Long)

    private suspend fun addTeam(repo: MatchStatsRepository, name: String, sport: Sport): Long {
        assertEquals(WriteResult.SAVED, repo.saveTeam(null, TeamInput(name, sport, "", "")))
        return repo.observeTeams().first().single { it.name == name }.id
    }

    private suspend fun addPlayer(repo: MatchStatsRepository, teamId: Long, name: String): Long {
        assertEquals(WriteResult.SAVED, repo.createPlayer(PlayerInput(name, teamId, "", null, false)))
        return repo.observeRoster(teamId).first()!!.players.single { it.name == name }.id
    }

    private suspend fun save(repo: MatchStatsRepository, input: MatchInput): Long {
        assertEquals(WriteResult.SAVED, repo.saveMatch(null, input))
        return repo.observeMatches(input.status).first().single { it.match.opponent == input.opponent }.match.id
    }

    private fun match(teamId: Long, opponent: String, scored: Int, conceded: Int) =
        MatchInput(teamId, opponent, LocalDate.now(), LocalTime.NOON, "", MatchStatus.FINISHED, scored.toString(), conceded.toString())

    private fun teamFilter(name: String) {
        click("Equipe", scroll = true)
        click(name)
    }

    private fun tab(label: String) = click(label, role(Role.Tab))

    private fun role(value: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, value)

    private fun click(label: String, role: SemanticsMatcher? = null, scroll: Boolean = false) {
        val clickable = hasText(label) and hasClickAction()
        val matcher = if (role == null) clickable else clickable and role
        waitFor(matcher)
        val node = compose.onNode(matcher)
        if (scroll) node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun metric(label: String, value: String) {
        val matcher = hasText(label) and hasText(value)
        waitFor(matcher)
        compose.onNode(matcher).performScrollTo().assertIsDisplayed()
    }

    private fun visible(label: String, scroll: Boolean = false, substring: Boolean = false) {
        waitFor(hasText(label, substring = substring))
        val node = compose.onNodeWithText(label, substring = substring)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        try {
            compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("$matcher\n${compose.onRoot().printToString(maxDepth = 6)}", timeout)
        }
    }

    private fun capture(name: String) {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("captureMatchStats") != "true") return
        val mode = args.getString("captureMode") ?: "normal"
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(compose.activity.cacheDir, "matchstats-phase4-$mode-$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
