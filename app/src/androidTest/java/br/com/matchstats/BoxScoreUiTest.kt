package br.com.matchstats

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.matchstats.data.BoxBuiltin
import br.com.matchstats.data.BoxMeasurement
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoxScoreUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensDefaultsAndEditsAndReordersACompletedBasketballBoxScore() {
        val suffix = "BX${System.nanoTime().toString().takeLast(8)}"
        val teamName = "Vila $suffix"
        val playerName = "Ana $suffix"
        val opponent = "Águias $suffix"
        val repo = (compose.activity.application as MatchStatsApplication).repository
        try {
            val id = runBlocking {
                assertEquals(WriteResult.SAVED, repo.saveTeam(null, TeamInput(teamName, Sport.BASKETBALL, "", "")))
                val team = repo.observeTeams().first().single { it.name == teamName }.id
                assertEquals(WriteResult.SAVED, repo.createPlayer(PlayerInput(playerName, team, "14", null, false)))
                val input = MatchInput(team, opponent, LocalDate.now(), LocalTime.NOON, "", MatchStatus.FINISHED, "72", "68")
                assertEquals(WriteResult.SAVED, repo.saveMatch(null, input))
                val matchId = repo.observeMatches(MatchStatus.FINISHED).first().single { it.match.opponent == opponent }.match.id
                val config = repo.observeBoxConfiguration(Sport.BASKETBALL, matchId).first()!!
                val enabled = listOf(
                    BoxBuiltin.POINTS, BoxBuiltin.TWO, BoxBuiltin.FIELD_PERCENT,
                    BoxBuiltin.THREE_PERCENT, BoxBuiltin.FREE_PERCENT,
                )
                val order = enabled.map { metric -> config.columns.single { it.metricId == metric.id }.copy(enabled = true) }
                val rest = config.columns.filter { column -> enabled.none { it.id == column.metricId } }.map { it.copy(enabled = false) }
                assertEquals(WriteResult.SAVED, repo.saveBoxConfiguration(Sport.BASKETBALL, matchId, order + rest))
                matchId
            }

            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            compose.onNode(hasText("Partidas") and tab).performSemanticsAction(SemanticsActions.OnClick) { it() }
            button("Configurar box score")
            waitFor("Padrão para novas partidas")
            capture("configuracao")
            compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick) { it() }

            button("Resultados")
            button(opponent, scroll = true)
            button("Box score", scroll = true)
            waitFor("Totais da equipe")
            waitFor("Nº 14")
            button(playerName)
            field("Pontos", "12")
            field("Certos", "5")
            field("Tentados", "3")
            button("Salvar", scroll = true)
            waitFor("Informe os dois valores. Os acertos devem ser menores ou iguais às tentativas.")
            field("Certos", "2")
            field("Tentados", "4")
            button("Salvar", scroll = true)
            waitFor("2/4")
            waitFor("50%")
            capture("tabela")
            val horizontal = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
            horizontal.performSemanticsAction(SemanticsActions.ScrollBy) { it(1_000f, 0f) }
            compose.onNodeWithText("FT%").assertIsDisplayed()
            compose.onNodeWithText(playerName).assertIsDisplayed()

            button("Configurar box score")
            waitFor("Prévia das colunas")
            compose.onNodeWithContentDescription("Mover Pontos para baixo").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithContentDescription("Pontos").performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            button("Salvar configuração")
            waitFor("Totais da equipe")
            runBlocking {
                val hidden = repo.observeBoxScore(id).first()!!
                assertEquals(BoxBuiltin.TWO.id, hidden.config.columns.first().metricId)
                assertEquals(BoxBuiltin.TWO.id, hidden.config.visible.first().id)
                assertFalse(hidden.config.visible.any { it.id == BoxBuiltin.POINTS.id })
                assertEquals(12, hidden.players.single().canonical?.points)
                assertEquals(BoxMeasurement(2, 4), hidden.players.single().measurements[BoxBuiltin.TWO.id])
            }

            compose.activityRule.scenario.recreate()
            waitFor("Totais da equipe")
            waitFor("50%")
            runBlocking { assertEquals(12, repo.observeMatchSheet(id).first()!!.saved.single().values.points) }
        } finally {
            runBlocking {
                repo.observeTeams().first().filter { it.name == teamName }.forEach { repo.deleteTeam(it.id) }
                repo.observeDirectory().first().filter { it.player.name == playerName }.forEach { repo.deletePlayer(it.player.id) }
            }
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun button(text: String, scroll: Boolean = false) {
        waitFor(text)
        val node = compose.onNode(hasText(text) and hasClickAction() and !hasSetTextAction())
        if (scroll) node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun field(label: String, value: String) {
        waitFor(label)
        compose.onNode(hasSetTextAction() and (hasText(label) or hasAnyDescendant(hasText(label))))
            .performScrollTo().performTextReplacement(value)
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureBoxScores") != "true") return
        compose.waitForIdle()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.cacheDir, "matchstats-phase6-$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
