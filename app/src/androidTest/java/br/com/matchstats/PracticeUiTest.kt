package br.com.matchstats

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.PlayerInput
import br.com.matchstats.data.PracticeInput
import br.com.matchstats.data.PracticeMeasure
import br.com.matchstats.data.PracticeRow
import br.com.matchstats.data.PracticeStatus
import br.com.matchstats.data.Sport
import br.com.matchstats.data.TeamInput
import br.com.matchstats.data.WriteResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val repository: MatchStatsRepository
        get() = (compose.activity.application as MatchStatsApplication).repository

    @Test
    fun createsAnIndividualFootballPracticeAndAddsACountDrillFromHome() {
        val suffix = "PR${System.nanoTime().toString().takeLast(8)}"
        val title = "Finalização $suffix"
        val playerName = "Rafael $suffix"
        val repo = repository
        val store = repo.practices
        val player = runBlocking {
            saved(repo.createPlayer(PlayerInput(playerName, null, "", null, false)))
            repo.observeDirectory().first().single { it.player.name == playerName }.player.id
        }
        try {
            button("Nova prática")
            field("Título da prática", title, substring = false, finish = false)
            button("Selecionados (0)")
            val search = compose.onNode(hasSetTextAction() and hasText("Buscar jogador..."))
            search.performTextReplacement(playerName)
            search.performImeAction()
            button(playerName, scroll = false)
            button("Salvar", useLast = true, scroll = false)
            button("Salvar")
            entryButton("Adicionar exercício")
            val name = compose.onNode(hasSetTextAction() and hasText("Nome do exercício"))
            name.performTextReplacement("Desarmes")
            button("Salvar", useLast = true, scroll = false)
            compose.waitUntil(15_000) {
                runBlocking {
                    val id = store.list(false).first().find { it.session.title == title }?.session?.id
                    id?.let { store.details(it).first()?.drills?.size } == 1
                }
            }
            runBlocking {
                val practice = store.list(false).first().single { it.session.title == title }
                val details = checkNotNull(store.details(practice.session.id).first())
                assertEquals(Sport.FOOTBALL, practice.session.sport)
                assertEquals(null, practice.session.teamId)
                assertEquals(player, details.players.single().playerId)
                assertEquals(PracticeMeasure.COUNT, details.drills.single().measure)
            }
        } finally {
            runBlocking {
                store.list(false).first().filter { it.session.title == title }.forEach { store.delete(it.session.id) }
                repo.deletePlayer(player)
            }
        }
    }

    @Test
    fun bulkEntryRestoresPendingRowsAcrossDrillsAndTabsAndShowsSavedResults() {
        val suffix = "PR${System.nanoTime().toString().takeLast(8)}"
        val title = "Quadra $suffix"
        val repo = repository
        val store = repo.practices
        val (team, players, id) = runBlocking {
            saved(repo.saveTeam(null, TeamInput(title, Sport.BASKETBALL, "", "")))
            val team = repo.observeTeams().first().single { it.name == title }.id
            val players = listOf("Ana $suffix", "Bruno $suffix").mapIndexed { index, name ->
                saved(repo.createPlayer(PlayerInput(name, team, (8 + index).toString(), null, false)))
                repo.observeDirectory().first().single { it.player.name == name }.player.id
            }
            val record = store.save(null, PracticeInput(title, Sport.BASKETBALL, team, System.currentTimeMillis() + 900_000, "", "", players.toSet()))
            saved(record.first)
            val id = checkNotNull(record.second)
            saved(store.addDrill(id, "Lances livres", PracticeMeasure.MAKES_AND_ATTEMPTS, "").first)
            saved(store.addDrill(id, "Rebotes", PracticeMeasure.COUNT, "").first)
            Triple(team, players, id)
        }
        val ana = players[0]
        val bruno = players[1]
        try {
            tab("Partidas")
            button("Práticas")
            button(title)
            entryButton("Iniciar prática")
            entryField("Buscar jogador...", "8")
            entryButton("Filtros e ordenação")
            entryButton("Com rascunho")
            inEntry(hasText("Nenhum jogador encontrado."))
            entryButton("Limpar filtros")
            entryButton("Ocultar filtros")
            input("practice-value-$ana", "15")
            input("practice-attempts-$ana", "20")
            entryField("Exercício", null)
            button("Rebotes", scroll = false)
            input("practice-value-$ana", "12")
            button("Salvar lote (1)", scroll = false)
            waitForRow(id, PracticeRow(ana, 12, null))

            entryField("Exercício", null)
            button("Lances livres", scroll = false)
            tab("Perfil")
            tab("Partidas")
            inEntry(hasTestTag("practice-value-$ana") and hasText("15"))
            inEntry(hasTestTag("practice-attempts-$ana") and hasText("20"))
            input("practice-value-$bruno", "6")
            input("practice-attempts-$bruno", "10")
            button("Salvar lote (2)", scroll = false)
            waitForRow(id, PracticeRow(ana, 15, 20))
            waitForRow(id, PracticeRow(bruno, 6, 10))
            inEntry(hasText("Total salvo: 15/20 · 75%"))
            input("practice-value-$ana", "5")
            input("practice-attempts-$ana", "10")
            inEntry(hasTestTag("practice-save-$ana")).performSemanticsAction(SemanticsActions.OnClick) { it() }
            waitForRow(id, PracticeRow(ana, 5, 10))
            inEntry(hasText("Total salvo: 20/30 · 67%"))
            entryButton("Concluir prática")
            button("Concluir prática", useLast = true, scroll = false)
            compose.waitUntil(15_000) { runBlocking { store.details(id).first()!!.summary.session.status == PracticeStatus.COMPLETED } }
            back()
            button(title)
            inEntry(hasText("Total salvo: 20/30 · 67%"))
            entryButton("Histórico de lotes")
            exists("Corrigir lote")
            back()

            tab("Estatísticas")
            button("Práticas")
            button("Basquete")
            exists("Ranking de práticas")
            exists("1. Ana $suffix")
            exists("20 acertos · 30 tentativas · 67%")

            tab("Início")
            exists("Próxima prática")
            button("Ver jogadores")
            field("Buscar por nome, equipe ou camisa", "Ana $suffix", substring = false)
            button("Ana $suffix")
            button("Práticas")
            button("Basquete")
            exists("Práticas concluídas: 1")
            exists("20 acertos · 30 tentativas · 67%")
            button("Jogos")
            exists("Estatísticas de jogo")
        } finally {
            runBlocking {
                store.delete(id)
                repo.deleteTeam(team)
                players.forEach { repo.deletePlayer(it) }
            }
        }
    }

    private fun entryField(label: String, value: String?) {
        val action = if (value == null) hasClickAction() else hasSetTextAction()
        val node = inEntry(action and (hasText(label) or hasAnyDescendant(hasText(label))))
        if (value == null) node.performSemanticsAction(SemanticsActions.OnClick) { it() }
        else {
            node.performTextReplacement(value)
            node.performImeAction()
        }
    }

    private fun input(tag: String, text: String) {
        val node = inEntry(hasTestTag(tag))
        node.performClick()
        node.performTextReplacement(text)
        node.performImeAction()
    }

    private fun entryButton(label: String) {
        inEntry(hasText(label) and hasClickAction() and !hasSetTextAction())
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun inEntry(matcher: SemanticsMatcher): androidx.compose.ui.test.SemanticsNodeInteraction {
        val list = compose.onNodeWithTag("practice-entry-list")
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("practice-entry-list")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        list.performScrollToNode(matcher)
        return compose.onNode(matcher).assertIsDisplayed()
    }

    private fun waitForRow(id: Long, row: PracticeRow) {
        compose.waitUntil(15_000) {
            runBlocking { repository.practices.history(id).first().any {
                it.batch.playerId == row.playerId && it.batch.value == row.value && it.batch.attempts == row.attempts
            } }
        }
        compose.waitForIdle()
    }

    private fun tab(label: String) {
        val matcher = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.onNode(matcher).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun button(label: String, useLast: Boolean = false, scroll: Boolean = true) {
        val matcher = hasText(label) and hasClickAction() and !hasSetTextAction()
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val node = if (useLast) compose.onAllNodes(matcher)[compose.onAllNodes(matcher).fetchSemanticsNodes().lastIndex]
        else compose.onNode(matcher)
        if (scroll) node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun field(label: String, value: String, substring: Boolean, finish: Boolean = true) {
        val caption = hasText(label, substring = substring)
        val matcher = hasSetTextAction() and (caption or hasAnyDescendant(caption))
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNode(matcher)
        node.performScrollTo().performTextReplacement(value)
        if (finish) node.performImeAction()
    }

    private fun exists(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun back() = compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick) { it() }

    private fun saved(result: WriteResult) = assertEquals(WriteResult.SAVED, result)
}
