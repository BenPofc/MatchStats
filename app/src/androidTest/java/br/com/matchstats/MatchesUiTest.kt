package br.com.matchstats

import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.matchstats.data.MatchInput
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun recordsMatchesAndBothSportsThenUpdatesDashboardAndDeletesMatch() {
        val suffix = "P3${System.nanoTime().toString().takeLast(6)}"
        val football = "União $suffix"
        val basketball = "Vila $suffix"
        val footballOpponent = "Estrela $suffix"
        val basketballOpponent = "Capital $suffix"
        val upcomingOpponent = "Próxima $suffix"
        val repo = (compose.activity.application as MatchStatsApplication).repository
        val baseline = runBlocking { repo.observeDashboard().first() }
        var keepFootballForRestart = false

        try {
            runBlocking {
                assertEquals(WriteResult.SAVED, repo.saveTeam(null, TeamInput(football, Sport.FOOTBALL, "", "")))
                assertEquals(WriteResult.SAVED, repo.saveTeam(null, TeamInput(basketball, Sport.BASKETBALL, "", "")))
                val teams = repo.observeTeams().first()
                val footballId = teams.single { it.name == football }.id
                val basketballId = teams.single { it.name == basketball }.id
                assertEquals(WriteResult.SAVED, repo.createPlayer(PlayerInput("Lia $suffix", footballId, "7", null, false)))
                assertEquals(WriteResult.SAVED, repo.createPlayer(PlayerInput("Bia $suffix", basketballId, "9", null, false)))
                assertEquals(
                    WriteResult.SAVED,
                    repo.saveMatch(
                        null, MatchInput(
                            footballId, upcomingOpponent, LocalDate.now().plusDays(1), LocalTime.of(17, 0),
                            "Arena Municipal", MatchStatus.SCHEDULED, "", "",
                        ),
                    ),
                )
            }

            tab("Partidas")
            click("Nova partida")
            click("Salvar", scroll = true)
            show("Selecione uma equipe.", scroll = true)
            selectTeam(football)
            click("Salvar", scroll = true)
            show("Informe o nome do adversário.", scroll = true)
            field("Adversário *", footballOpponent)
            click("Salvar", scroll = true)
            show("Selecione a data.", scroll = true)
            selectDate(takeCapture = true)
            click("Salvar", scroll = true)
            show("Selecione o horário.", scroll = true)
            selectTime(takeCapture = true)
            field("Local", "Estádio Municipal")
            capture("nova-partida")
            click("Salvar", scroll = true)

            show("Nova partida")
            show(footballOpponent)
            capture("partidas")
            click(footballOpponent, scroll = true)
            show("Estádio Municipal")
            absent("Estatísticas dos jogadores")
            click("Editar partida", scroll = true)
            chip("Finalizada")
            field("Placar da equipe *", "2")
            field("Placar do adversário *", "1")
            click("Salvar", scroll = true)
            show("2 × 1")
            click("Estatísticas dos jogadores", scroll = true)
            show("Lia $suffix")
            show("Cartões amarelos")
            absent("Pontos")
            field("Gols", "-1")
            click("Salvar", scroll = true)
            show("Informe um número inteiro maior ou igual a zero.", scroll = true)
            field("Gols", "3")
            click("Salvar", scroll = true)
            show("A soma dos gols dos jogadores não pode ultrapassar o placar da equipe.", scroll = true)
            field("Gols", "1")
            field("Assistências", "1")
            field("Cartões amarelos", "1")
            field("Cartões vermelhos", "0")
            capture("futebol")
            click("Salvar", scroll = true)
            show("2 × 1")
            click("Estatísticas dos jogadores", scroll = true)
            fieldValue("Gols", "1")
            field("Gols", "2")
            click("Salvar", scroll = true)
            show("2 × 1")
            back()
            show(footballOpponent)
            absent(upcomingOpponent)
            chip("Próximas")
            show(upcomingOpponent)
            absent(footballOpponent)
            chip("Resultados")
            show(footballOpponent)

            click("Nova partida")
            chip("Finalizada")
            selectTeam(basketball)
            field("Adversário *", basketballOpponent)
            selectDate()
            selectTime()
            field("Placar da equipe *", "-1")
            field("Placar do adversário *", "68")
            click("Salvar", scroll = true)
            show("Informe um número inteiro maior ou igual a zero.", scroll = true)
            field("Placar da equipe *", "72")
            click("Salvar", scroll = true)
            show("Nova partida")
            show(basketballOpponent)
            capture("resultados")
            click(basketballOpponent, scroll = true)
            show("72 × 68")
            capture("detalhes")
            click("Editar partida", scroll = true)
            show("Uma partida finalizada permanece nos resultados.")
            compose.onNode(hasText("Agendada") and role(Role.Checkbox)).assertIsNotEnabled()
            click("Cancelar", scroll = true)
            click("Estatísticas dos jogadores", scroll = true)
            show("Bia $suffix")
            show("Rebotes")
            absent("Gols")
            field("Pontos", "16")
            field("Rebotes", "8")
            field("Assistências", "2")
            capture("basquete")
            click("Salvar", scroll = true)

            show("72 × 68")
            compose.activityRule.scenario.recreate()
            show("72 × 68")
            click("Estatísticas dos jogadores", scroll = true)
            fieldValue("Pontos", "16")
            fieldValue("Rebotes", "8")
            click("Cancelar", scroll = true)

            tab("Início")
            show("Bem-vindo!")
            val expected = baseline.matches + 3
            compose.onNode(hasText("Partidas") and hasText(expected.toString())).assertIsDisplayed()
            show("Próxima partida", scroll = true)
            val next = runBlocking { repo.observeHome().first().upcoming }
            assertNotNull(next)
            show(next!!.match.opponent, scroll = true)
            capture("inicio")
            click(next.match.opponent, scroll = true)
            show("Detalhes da partida")

            tab("Equipes")
            click("Nova equipe")
            click("Cancelar", scroll = true)
            tab("Estatísticas")
            show("Desempenho", scroll = true)
            tab("Perfil")
            show("Perfil local")
            tab("Partidas")
            back()
            click(basketballOpponent, scroll = true)
            click("Excluir partida", scroll = true)
            show("As estatísticas dos jogadores nesta partida também serão excluídas.", substring = true)
            click("Excluir")
            show(footballOpponent)
            absent(basketballOpponent)
            assertEquals(expected - 1, runBlocking { repo.observeDashboard().first().matches })
            if (InstrumentationRegistry.getArguments().getString("checkColdRestart") == "true") {
                val marker = JSONObject()
                    .put("team", football)
                    .put("opponent", footballOpponent)
                    .put("player", "Lia $suffix")
                File(compose.activity.cacheDir, "matchstats-phase3-restart.json").writeText(marker.toString())
                keepFootballForRestart = true
            }
        } finally {
            runBlocking {
                repo.observeTeams().first()
                    .filter { it.name.endsWith(suffix) && !(keepFootballForRestart && it.name == football) }
                    .forEach { repo.deleteTeam(it.id) }
                val testPlayers = if (keepFootballForRestart) setOf("Bia $suffix") else setOf("Lia $suffix", "Bia $suffix")
                repo.observeDirectory().first().filter { it.player.name in testPlayers }
                    .forEach { repo.deletePlayer(it.player.id) }
            }
        }
    }

    private fun role(value: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, value)

    private fun tab(label: String) {
        compose.onNode(hasText(label) and role(Role.Tab)).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun chip(label: String) {
        waitFor(label)
        compose.onNode(hasText(label) and role(Role.Checkbox)).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun back() {
        compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun selectTeam(name: String) {
        click("Equipe *", scroll = true)
        click(name, substring = true)
    }

    private fun selectDate(takeCapture: Boolean = false) {
        click("Data *", scroll = true)
        show("Selecionar data")
        if (takeCapture) capture("data")
        click("Confirmar data")
    }

    private fun selectTime(takeCapture: Boolean = false) {
        click("Hora *", scroll = true)
        show("Selecionar horário")
        if (takeCapture) capture("horario")
        click("Confirmar horário")
    }

    private fun field(label: String, value: String) {
        waitFor(label)
        compose.onNode(textField(label)).performScrollTo().performTextReplacement(value)
        fieldValue(label, value)
    }

    private fun fieldValue(label: String, value: String) {
        val matcher = textField(label) and hasText(value)
        compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
        compose.onNode(matcher).performScrollTo().assertIsDisplayed()
    }

    private fun textField(label: String) = hasSetTextAction() and (hasText(label) or hasAnyDescendant(hasText(label)))

    private fun click(label: String, scroll: Boolean = false, substring: Boolean = false) {
        val matcher = hasText(label, substring = substring) and hasClickAction()
        compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
        val node = compose.onNode(matcher)
        if (scroll) node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun show(label: String, scroll: Boolean = false, substring: Boolean = false) {
        waitFor(label, substring)
        val node = compose.onNodeWithText(label, substring = substring)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
    }

    private fun absent(label: String) {
        compose.onAllNodesWithText(label).assertCountEquals(0)
    }

    private fun waitFor(label: String, substring: Boolean = false) {
        try {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(label, substring = substring).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Texto esperado: $label\n${compose.onRoot().printToString()}", timeout)
        }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureMatchStats") != "true") return
        val activity = compose.activity
        compose.runOnIdle {
            activity.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.cacheDir, "matchstats-phase3-$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
