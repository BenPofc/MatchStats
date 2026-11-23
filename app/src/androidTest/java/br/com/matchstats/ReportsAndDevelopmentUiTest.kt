package br.com.matchstats

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
class ReportsAndDevelopmentUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun profileChartChangesObservationAfterTapAndSeparatesPractices() = withFinishedGames { fixture ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("phase13Font") == "large") {
            assertTrue(compose.activity.resources.configuration.fontScale >= 1.5f)
        }
        click("Ver jogadores")
        val search = hasSetTextAction() and hasText("Buscar por nome, equipe ou camisa")
        waitFor(search)
        val searchField = compose.onNode(search)
        searchField.performClick().performTextInput(fixture.player)
        searchField.performImeAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasText(fixture.player) and hasClickAction() and !hasSetTextAction(),
        )
        click(fixture.player)
        click("Evolução")

        val chart = hasTestTag("development-chart")
        val selection = hasTestTag("development-selection")
        val list = compose.onNodeWithTag("development-screen")
        waitFor(hasTestTag("development-screen"))
        list.performScrollToNode(chart)
        waitFor(selection and hasAnyDescendant(hasText("${fixture.team} × ${fixture.secondOpponent}")))
        compose.onNode(chart).performTouchInput { click(Offset(10f, center.y)) }
        waitFor(selection and hasAnyDescendant(hasText("${fixture.team} × ${fixture.firstOpponent}")))
        waitFor(selection and hasAnyDescendant(hasText("· 12", substring = true)))
        if (InstrumentationRegistry.getArguments().getString("phase13Font") == "large") {
            instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                File(instrumentation.targetContext.cacheDir, "phase13-chart-large.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
        }

        val practices = hasText("Práticas") and hasClickAction()
        list.performScrollToNode(practices)
        compose.onNode(practices).performClick()
        val empty = hasText("Nenhuma observação neste filtro.")
        list.performScrollToNode(empty)
        compose.onNode(empty).assertIsDisplayed()

        click("Estatísticas", SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        click("Basquete", SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        click("Equipe")
        click(fixture.team)
        val shortcut = hasContentDescription("Ver evolução de ${fixture.player}") and hasClickAction()
        waitFor(shortcut)
        compose.onNode(shortcut).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        waitFor(hasTestTag("development-screen"))
    }

    @Test
    fun completedMatchSavesViaDocumentPickerAndOpensNativeSharesheet() = withFinishedGames { fixture ->
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        click("Partidas", tab)
        click("Resultados")
        click(fixture.secondOpponent)
        click("Exportar PDF")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val save = waitForSystemNode { node ->
            val pkg = node.packageName?.toString().orEmpty()
            pkg.endsWith("documentsui") && node.isEnabled &&
                (node.viewIdResourceName?.endsWith(":id/action_menu_save") == true ||
                    node.text?.toString() in setOf("Save", "SAVE", "Salvar", "SALVAR"))
        }
        assertTrue(save.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val targetPackage = instrumentation.targetContext.packageName
        waitForSystemNode { it.packageName == targetPackage }
        val saved = hasText("Relatório PDF salvo.")
        waitFor(saved)
        compose.onNode(saved).performScrollTo().assertIsDisplayed()
        click("Compartilhar relatório")
        try {
            waitForSystemNode { node ->
                val pkg = node.packageName?.toString().orEmpty()
                pkg.endsWith("intentresolver") ||
                    pkg == "android" && node.text?.toString() == "Compartilhar relatório"
            }
        } finally {
            automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    private fun click(label: String, extra: SemanticsMatcher = hasClickAction()) {
        val matcher = hasText(label) and extra and !hasSetTextAction()
        waitFor(matcher)
        val node = compose.onNode(matcher)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
    }

    private fun waitForSystemNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var found: AccessibilityNodeInfo? = null
        compose.waitUntil(60_000) {
            val nodes = ArrayDeque<AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let(nodes::add)
            while (nodes.isNotEmpty() && found == null) {
                val node = nodes.removeFirst()
                if (predicate(node)) found = node
                else (0 until node.childCount).mapNotNull(node::getChild).forEach(nodes::add)
            }
            found != null
        }
        return checkNotNull(found)
    }

    private fun withFinishedGames(test: (Fixture) -> Unit) {
        val repo = (compose.activity.application as MatchStatsApplication).repository
        val suffix = "P13${System.nanoTime().toString().takeLast(6)}"
        val fixture = Fixture("Vila $suffix", "Lia $suffix", "Estrela $suffix", "Capital $suffix")
        try {
            runBlocking {
                assertEquals(WriteResult.SAVED, repo.saveTeam(null, TeamInput(fixture.team, Sport.BASKETBALL, "", "")))
                val teamId = repo.observeTeams().first().single { it.name == fixture.team }.id
                assertEquals(WriteResult.SAVED, repo.createPlayer(PlayerInput(fixture.player, teamId, "8", null, false)))
                val playerId = repo.observeRoster(teamId).first()!!.players.single().id
                saveGame(repo, teamId, playerId, fixture.firstOpponent, LocalDate.now().minusDays(2), 12)
                saveGame(repo, teamId, playerId, fixture.secondOpponent, LocalDate.now().minusDays(1), 24)
            }
            test(fixture)
        } finally {
            runBlocking {
                repo.observeTeams().first().singleOrNull { it.name == fixture.team }?.let { repo.deleteTeam(it.id) }
                repo.observeDirectory().first().singleOrNull { it.player.name == fixture.player }?.let {
                    repo.deletePlayer(it.player.id)
                }
            }
        }
    }

    private suspend fun saveGame(
        repo: MatchStatsRepository, teamId: Long, playerId: Long, opponent: String, day: LocalDate, points: Int,
    ) {
        val match = MatchInput(teamId, opponent, day, LocalTime.NOON, "Arena Municipal", MatchStatus.FINISHED, "45", "31")
        assertEquals(WriteResult.SAVED, repo.saveMatch(null, match))
        val matchId = repo.observeMatches(MatchStatus.FINISHED).first().single { it.match.opponent == opponent }.match.id
        assertEquals(WriteResult.SAVED, repo.saveMatchStats(matchId, mapOf(playerId to StatValues(points = points))))
    }

    private data class Fixture(val team: String, val player: String, val firstOpponent: String, val secondOpponent: String)
}
