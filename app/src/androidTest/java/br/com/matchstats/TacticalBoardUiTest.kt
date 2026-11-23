package br.com.matchstats

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.matchstats.data.BoardPoint
import br.com.matchstats.data.CourtMode
import br.com.matchstats.data.CourtViewport
import br.com.matchstats.data.PlayerInput
import br.com.matchstats.data.Sport
import br.com.matchstats.data.TacticalDocumentCodec
import br.com.matchstats.data.TacticalPlay
import br.com.matchstats.data.TacticalSide
import br.com.matchstats.data.TacticalStrokeType
import br.com.matchstats.data.TeamInput
import br.com.matchstats.data.WriteResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TacticalBoardUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val repository get() = (compose.activity.application as MatchStatsApplication).repository

    @Test fun presentsFullCourtInLandscapeAndReturnsToEditor() {
        val originalOrientation = compose.activity.requestedOrientation
        try {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            button("Quadro tático")
            button("Nova jogada", scroll = false)
            button("Quadra inteira")
            button("Apresentar")
            compose.onNodeWithTag("tactical-presentation-court")
                .assertContentDescriptionEquals("Quadra inteira").assertIsDisplayed()
            button("Voltar à edição", scroll = false)
            assertCourt("Quadra inteira")
        } finally {
            compose.runOnUiThread { compose.activity.requestedOrientation = originalOrientation }
        }
    }

    @Test fun drawsMovesErasesAndPresentsThenManagesSavedPlay() {
        val suffix = System.nanoTime().toString().takeLast(8)
        val initial = "Saída $suffix"
        val renamed = "Horns $suffix"
        try {
            button("Quadro tático")
            button("Nova jogada", scroll = false)
            assertCourt("Meia quadra")
            button("Quadra inteira")
            assertCourt("Quadra inteira")
            button("Defesa")
            drag(BoardPoint(.50f, .83f), BoardPoint(.3f, .76f))
            drag(BoardPoint(.53f, .61f), BoardPoint(.67f, .33f))
            drag(BoardPoint(.575f, .83f), BoardPoint(.6f, .72f))

            button("Movimento")
            drag(BoardPoint(.30f, .64f), BoardPoint(.60f, .56f))
            button("Passe")
            drag(BoardPoint(.25f, .25f), BoardPoint(.66f, .18f))
            button("Bloqueio")
            drag(BoardPoint(.68f, .44f), BoardPoint(.82f, .47f))
            button("Apagar")
            tap(BoardPoint(.47f, .213f))
            button("Desfazer")
            button("Refazer")
            button("Desfazer")
            button("Limpar")
            button("Limpar", scroll = false, useLast = true)
            button("Desfazer")

            save(initial)
            val original = waitForSaved(initial)
            assertNull(original.teamId)
            val stored = checkNotNull(TacticalDocumentCodec.decode(original.document))
            assertEquals(CourtMode.FULL, stored.mode)
            assertEquals(TacticalStrokeType.entries, stored.strokes.map { it.type })
            val attack = stored.markers.first()
            assertEquals(.3f, attack.point.x, .025f)
            assertEquals(.76f, attack.point.y, .025f)
            val defense = stored.markers.first { it.side == TacticalSide.DEFENSE }
            assertEquals(.67f, defense.point.x, .025f)
            assertEquals(.33f, defense.point.y, .025f)
            val ball = stored.markers.single { it.side == TacticalSide.BALL }
            assertEquals(.6f, ball.point.x, .025f)
            assertEquals(.72f, ball.point.y, .025f)

            button("Apresentar")
            compose.onNode(hasText("Início") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertDoesNotExist()
            compose.onNode(hasText("Salvar jogada") and hasClickAction()).assertDoesNotExist()
            drag(BoardPoint(.3f, .76f), BoardPoint(.8f, .37f), presenting = true)
            button("Voltar à edição", scroll = false)
            button("Renomear")
            field("Nome da jogada", renamed, scroll = false)
            button("Salvar", scroll = false)
            val updated = waitForSaved(renamed)
            assertEquals(original.id, updated.id)
            assertEquals(original.document, updated.document)
            back()

            field("Buscar jogada", "ausente-$suffix")
            exists("Nenhuma jogada encontrada")
            field("Buscar jogada", renamed)
            button("Editar jogada")
            assertCourt("Quadra inteira")
            exists(renamed)
            back()
            button("Duplicar")
            val duplicate = waitForSaved("Cópia de $renamed")
            assertEquals(updated.document, duplicate.document)
            field("Buscar jogada", duplicate.name)
            button("Excluir")
            button("Excluir", scroll = false, useLast = true)
            compose.waitUntil(15_000) { runBlocking { repository.tactics.find(duplicate.id) == null } }
        } finally {
            removePlays(suffix)
        }
    }

    @Test fun opensFromBasketballRosterWithTeamPreselectedAndAssignsPlayer() {
        val suffix = System.nanoTime().toString().takeLast(8)
        val teamName = "Quadra $suffix"
        val playerName = "Alex $suffix"
        val playName = "Zona $suffix"
        val team = runBlocking {
            assertEquals(WriteResult.SAVED, repository.saveTeam(null, TeamInput(teamName, Sport.BASKETBALL, "", "")))
            val id = repository.observeTeams().first().single { it.name == teamName }.id
            assertEquals(WriteResult.SAVED, repository.createPlayer(PlayerInput(playerName, id, "8", null, false)))
            id
        }
        try {
            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            compose.onNode(hasText("Equipes") and tab).performSemanticsAction(SemanticsActions.OnClick) { it() }
            field("Buscar equipes", teamName, substring = true)
            button(teamName)
            button("Quadro tático")
            button("Nova jogada", scroll = false)
            exists(teamName)
            button("Equipe e marcadores")
            dropdown("Jogador do elenco")
            button("Nº 8 · $playerName", scroll = false)
            button("Posicionar bola ao lado")
            save(playName)
            val stored = waitForSaved(playName)
            assertEquals(team, stored.teamId)
            val marker = TacticalDocumentCodec.decode(stored.document)!!.markers.first()
            val roster = runBlocking { repository.observeRoster(team).first()!!.players.single() }
            assertEquals(roster.id, marker.playerId)
            assertEquals(8, roster.jerseyNumber)
        } finally {
            removePlays(suffix)
            runBlocking {
                repository.deleteTeam(team)
                repository.observeDirectory().first().filter { it.player.name == playerName }.forEach { repository.deletePlayer(it.player.id) }
            }
        }
    }

    private fun save(name: String) {
        button("Salvar jogada")
        field("Nome da jogada", name, scroll = false)
        button("Salvar", scroll = false)
    }

    private fun assertCourt(description: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("tactical-court")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tactical-court").assertContentDescriptionEquals(description)
    }

    private fun drag(from: BoardPoint, to: BoardPoint, presenting: Boolean = false) {
        val node = compose.onNodeWithTag(if (presenting) "tactical-presentation-court" else "tactical-court")
        if (!presenting) node.performScrollTo()
        node.performTouchInput {
            val view = CourtViewport(width.toFloat(), height.toFloat(), CourtMode.FULL, false)
            swipe(Offset(view.x(from), view.y(from)), Offset(view.x(to), view.y(to)), durationMillis = 460)
        }
    }

    private fun tap(point: BoardPoint) {
        compose.onNodeWithTag("tactical-court").performScrollTo().performTouchInput {
            val view = CourtViewport(width.toFloat(), height.toFloat(), CourtMode.FULL, false)
            click(Offset(view.x(point), view.y(point)))
        }
    }

    private fun waitForSaved(name: String): TacticalPlay {
        fun read() = runBlocking {
            repository.tactics.list().first().find { it.name == name }?.let { repository.tactics.find(it.id) }
        }
        compose.waitUntil(15_000) { read() != null }
        compose.waitForIdle()
        return checkNotNull(read())
    }

    private fun button(label: String, scroll: Boolean = true, useLast: Boolean = false) {
        val matcher = hasText(label) and hasClickAction() and !hasSetTextAction()
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val node = if (useLast) compose.onAllNodes(matcher)[compose.onAllNodes(matcher).fetchSemanticsNodes().lastIndex]
        else compose.onNode(matcher)
        if (scroll) node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun dropdown(label: String) {
        val caption = hasText(label)
        val matcher = hasClickAction() and (caption or hasAnyDescendant(caption))
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun field(label: String, value: String, scroll: Boolean = true, substring: Boolean = false) {
        val caption = hasText(label, substring)
        val matcher = hasSetTextAction() and (caption or hasAnyDescendant(caption))
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNode(matcher)
        if (scroll) node.performScrollTo()
        node.performTextReplacement(value)
        node.performImeAction()
    }

    private fun exists(label: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun back() = compose.onNodeWithContentDescription("Voltar").performSemanticsAction(SemanticsActions.OnClick) { it() }

    private fun removePlays(suffix: String) = runBlocking {
        repository.tactics.list().first().filter { it.name.endsWith(suffix) }.forEach { repository.tactics.delete(it.id) }
    }
}
