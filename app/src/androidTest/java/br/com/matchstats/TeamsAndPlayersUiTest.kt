package br.com.matchstats

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.matchstats.data.MatchInput
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.PlayerInput
import br.com.matchstats.data.Sport
import br.com.matchstats.data.StatValues
import br.com.matchstats.data.TeamInput
import br.com.matchstats.data.WriteResult
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TeamsAndPlayersUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun createEditFilterAndDeleteTeamsAndPlayers() {
        val prefix = "Teste${System.nanoTime().toString().takeLast(6)}"
        val football = "União $prefix"
        val updatedFootball = "União FC $prefix"
        val basketball = "Vila $prefix"
        val repository = (compose.activity.application as MatchStatsApplication).repository
        val baseline = runBlocking { repository.observeDashboard().first() }
        try {
            tab("Equipes")
            button("Nova equipe")
            waitForText("Nome da equipe *")
            capture("nova-equipe")
            button("Salvar", scroll = true)
            visible("Informe o nome.")
            field("Nome da equipe *", football)
            field("Categoria", "Sub-20")
            field("Nome do treinador", "Ana Silva")
            button("Salvar", scroll = true)

            open(football)
            visible("Técnico: Ana Silva")
            button("Editar equipe")
            field("Nome da equipe *", updatedFootball)
            button("Salvar", scroll = true)
            visible(updatedFootball, scroll = true)
            compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick) { it() }

            button("Nova equipe")
            chip("Basquete")
            field("Nome da equipe *", basketball)
            button("Salvar", scroll = true)
            visible(basketball, scroll = true)
            capture("equipes")

            field("Buscar equipes", "uniao fc $prefix")
            compose.onNode(hasText(updatedFootball) and !hasSetTextAction()).performScrollTo().assertIsDisplayed()
            absent(basketball)
            compose.onNodeWithContentDescription("Limpar busca").performSemanticsAction(SemanticsActions.OnClick) { it() }
            chip("Basquete")
            visible(basketball, scroll = true)
            absent(updatedFootball)
            chip("Todas")

            open(updatedFootball)
            button("Adicionar jogador")
            visible(updatedFootball, scroll = true)
            field("Nome completo *", "Rafael $prefix")
            field("Número da camisa", "100")
            button("Salvar", scroll = true)
            visible("Informe um número inteiro de 0 a 99.", scroll = true)
            field("Número da camisa", "10")
            openPosition()
            visible("Goleiro")
            visible("Meio-campista")
            absent("Armador")
            capture("posicoes")
            button("Meio-campista")
            button("Capitão", scroll = true)
            capture("adicionar-jogador")
            button("Salvar", scroll = true)
            visible("Rafael $prefix", scroll = true)
            capture("detalhes-da-equipe")

            button("Adicionar jogador")
            field("Nome completo *", "Duplicado $prefix")
            field("Número da camisa", "10")
            button("Salvar", scroll = true)
            visible("Este número já está em uso nesta equipe.", scroll = true)
            button("Cancelar", scroll = true)

            open("Rafael $prefix")
            visible("Meio-campista", substring = true, scroll = true)
            visible("Capitão", substring = true, scroll = true)
            button("Editar jogador", scroll = true)
            field("Nome completo *", "Rafa $prefix")
            button("Salvar", scroll = true)
            visible("Rafa $prefix", scroll = true)
            button("Editar vínculo", scroll = true)
            field("Número da camisa", "11")
            button("Salvar", scroll = true)
            visible("Nº 11", substring = true, scroll = true)
            capture("detalhes-do-jogador")
            compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick) { it() }

            open(basketball)
            button("Adicionar jogador")
            field("Nome completo *", "Beatriz $prefix")
            field("Número da camisa", "10")
            openPosition()
            visible("Armador")
            visible("Ala-pivô")
            absent("Goleiro")
            button("Armador")
            button("Salvar", scroll = true)
            visible("Beatriz $prefix", scroll = true)

            tab("Início")
            visible("Bem-vindo!")
            val counts = runBlocking { repository.observeDashboard().first() }
            assertEquals(baseline.copy(teams = baseline.teams + 2, players = baseline.players + 2), counts)
            compose.onNode(hasText("Equipes") and hasText(counts.teams.toString())).assertIsDisplayed()
            compose.onNode(hasText("Jogadores") and hasText(counts.players.toString())).assertIsDisplayed()
            capture("inicio")
            tab("Partidas")
            visible("Nenhuma partida agendada")
            tab("Estatísticas")
            visible("Sem estatísticas de futebol")
            tab("Perfil")
            visible("Perfil local")
            tab("Equipes")
            visible("Beatriz $prefix", scroll = true)
            compose.activityRule.scenario.recreate()
            visible("Beatriz $prefix", scroll = true)

            open("Beatriz $prefix")
            button("Excluir jogador", scroll = true)
            visible("Excluir o perfil de “Beatriz $prefix”?", substring = true)
            capture("confirmar-exclusao")
            button("Cancelar")
            visible("Beatriz $prefix", scroll = true)
            button("Excluir jogador", scroll = true)
            button("Excluir")
            visible("Nenhum jogador nesta equipe.", scroll = true)

            compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick) { it() }
            open(updatedFootball)
            button("Excluir equipe", scroll = true)
            visible("Os perfis dos jogadores serão mantidos.", substring = true)
            button("Excluir")
            visible(basketball, scroll = true)
            absent(updatedFootball)
            val remaining = runBlocking { repository.observeDashboard().first() }
            assertEquals(baseline.copy(teams = baseline.teams + 1, players = baseline.players + 1), remaining)
        } finally {
            runBlocking {
                repository.observeTeams().first().filter { it.name.endsWith(prefix) }.forEach {
                    repository.deleteTeam(it.id)
                }
                repository.observeDirectory().first().filter { it.player.name.endsWith(prefix) }.forEach {
                    repository.deletePlayer(it.player.id)
                }
            }
        }
    }

    @Test
    fun globalDirectoryReusesPlayerAndSortsByPersistedGoals() {
        val suffix = "D${System.nanoTime().toString().takeLast(8)}"
        val first = "Álvaro $suffix"
        val second = "Zeca $suffix"
        val football = "Futebol $suffix"
        val basketball = "Basquete $suffix"
        val repository = (compose.activity.application as MatchStatsApplication).repository
        try {
            runBlocking {
                assertEquals(WriteResult.SAVED, repository.saveTeam(null, TeamInput(football, Sport.FOOTBALL, "", "")))
                assertEquals(WriteResult.SAVED, repository.saveTeam(null, TeamInput(basketball, Sport.BASKETBALL, "", "")))
            }
            button("Adicionar jogador", scroll = true)
            field("Nome completo *", first)
            button("Salvar", scroll = true)
            field("Buscar por nome, equipe ou camisa", "alvaro $suffix")
            scrollDirectoryTo(first)
            visible("Sem equipe")

            directoryTop()
            chip("Equipes")
            field("Buscar equipes", football)
            open(football)
            button("Adicionar jogador existente", scroll = true)
            field("Buscar por nome, equipe ou camisa", first)
            open(first)
            field("Número da camisa", "31")
            button("Salvar", scroll = true)
            open(first)
            button("Vincular a uma equipe", scroll = true)
            button("Equipe *", scroll = true)
            button("$basketball · Basquete")
            field("Número da camisa", "49")
            button("Salvar", scroll = true)
            runBlocking {
                val teams = repository.observeTeams().first()
                val footballId = teams.single { it.name == football }.id
                val player = repository.observeDirectory().first().single { it.player.name == first }
                assertEquals(setOf(31, 49), player.teams.mapNotNull { it.jerseyNumber }.toSet())
                assertEquals(WriteResult.SAVED, repository.createPlayer(PlayerInput(second, footballId, "89", null, false)))
                val scorer = repository.observeDirectory().first().single { it.player.name == second }.player.id
                val opponent = "Rivais $suffix"
                assertEquals(
                    WriteResult.SAVED,
                    repository.saveMatch(null, MatchInput(
                        footballId, opponent, LocalDate.now(), LocalTime.NOON, "", MatchStatus.FINISHED, "3", "0",
                    )),
                )
                val match = repository.observeMatches(MatchStatus.FINISHED).first().single { it.match.opponent == opponent }
                assertEquals(WriteResult.SAVED, repository.saveMatchStats(match.match.id, mapOf(scorer to StatValues(goals = 3))))
            }

            tab("Início")
            button("Ver jogadores", scroll = true)
            directoryTop()
            field("Buscar por nome, equipe ou camisa", "031")
            chip("Futebol")
            scrollDirectoryTo(first)
            absent(second)
            directoryTop()
            chip("Basquete")
            visible("Nenhum jogador encontrado")
            field("Buscar por nome, equipe ou camisa", "049")
            scrollDirectoryTo(first)
            directoryTop()
            field("Buscar por nome, equipe ou camisa", suffix)
            chip("Futebol")
            button("Ordenar por", scroll = true)
            compose.onNode(hasText("Gols") and hasClickAction() and !hasText(first) and !hasText(second))
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            directoryResults()
            assertEquals(second, directoryNames(first, second).first())
            compose.activityRule.scenario.recreate()
            scrollDirectoryTo(second)
            assertEquals(second, directoryNames(first, second).first())
        } finally {
            runBlocking {
                repository.observeTeams().first().filter { it.name.endsWith(suffix) }
                    .forEach { repository.deleteTeam(it.id) }
                repository.observeDirectory().first().filter { it.player.name.endsWith(suffix) }
                    .forEach { repository.deletePlayer(it.player.id) }
            }
        }
    }

    private fun directoryList() = compose.onNode(hasScrollToIndexAction(), useUnmergedTree = true)

    private fun directoryTop() = directoryList().performScrollToIndex(0)

    private fun directoryResults() = directoryList().performScrollToIndex(5)

    private fun scrollDirectoryTo(name: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodes(hasScrollToIndexAction(), useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
        directoryList().performScrollToNode(hasText(name))
        visible(name)
    }

    private fun directoryNames(vararg names: String): List<String> =
        names.sortedBy { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }

    private fun tab(text: String) {
        val node = compose.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
        node.assertIsSelected()
    }

    private fun visible(text: String, substring: Boolean = false, scroll: Boolean = false) {
        waitForText(text, substring)
        val node = compose.onNodeWithText(text, substring = substring)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
    }

    private fun absent(text: String) {
        compose.onAllNodesWithText(text).assertCountEquals(0)
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        try {
            compose.waitUntil(timeoutMillis = 30_000) {
                compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            val tree = compose.onRoot().printToString()
            throw AssertionError("Texto esperado: $text\n$tree", timeout)
        }
    }

    private fun button(text: String, scroll: Boolean = false) {
        waitForText(text)
        val node = compose.onNode(hasText(text) and hasClickAction() and !hasSetTextAction())
        if (scroll) node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun chip(text: String) {
        waitForText(text)
        val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        compose.onNode(hasText(text) and checkbox).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun open(name: String) = button(name, scroll = true)

    private fun field(label: String, value: String) {
        waitForText(label)
        val textField = compose.onNode(
            hasSetTextAction() and (hasText(label) or hasAnyDescendant(hasText(label))),
        )
        textField.performScrollTo().performTextReplacement(value)
        waitForText(value)
        if (label.startsWith("Buscar")) textField.performImeAction()
    }

    private fun openPosition() = button("Posição", scroll = true)

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureMatchStats") != "true") return
        compose.waitForIdle()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(instrumentation.targetContext.cacheDir, "matchstats-phase2-$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
