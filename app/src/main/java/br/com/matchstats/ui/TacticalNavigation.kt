package br.com.matchstats.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import br.com.matchstats.R
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.TacticalBoard
import br.com.matchstats.data.TacticalDocumentCodec
import br.com.matchstats.data.TacticalSnapshot
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.RecordUnavailable
import br.com.matchstats.ui.screens.TacticalDirectoryScreen
import br.com.matchstats.ui.screens.TacticalEditorScreen
import kotlinx.coroutines.flow.flow

internal const val TACTICAL_CONTEXT = "tacticalTeam"
internal const val TACTICAL_DIRECTORY = "quadra/{$TACTICAL_CONTEXT}"
internal const val TACTICAL_EDITOR = "quadra/{$TACTICAL_CONTEXT}/jogada/{playId}"
internal const val TACTICAL_PRESENTING = "tacticalPresenting"

internal fun tacticalDirectory(team: Long = 0) = "quadra/$team"
private fun tacticalEditor(team: Long, id: Long = 0) = "quadra/$team/jogada/$id"

internal fun NavGraphBuilder.tacticalRoutes(repository: MatchStatsRepository, nav: NavHostController) {
    val context = navArgument(TACTICAL_CONTEXT) { type = NavType.LongType }
    val back: () -> Unit = { nav.popBackStack() }
    composable(TACTICAL_DIRECTORY, listOf(context)) { entry ->
        val teamId = entry.arguments?.getLong(TACTICAL_CONTEXT) ?: 0L
        val source = remember(repository) { repository.tactics.list() }
        ObserveRecords(source) { plays ->
            TacticalDirectoryScreen(
                plays, repository.tactics,
                onNew = { nav.navigate(tacticalEditor(teamId)) { launchSingleTop = true } },
                onOpen = { id -> nav.navigate(tacticalEditor(teamId, id)) { launchSingleTop = true } },
            )
        }
    }
    composable(TACTICAL_EDITOR, listOf(context, navArgument("playId") { type = NavType.LongType })) { entry ->
        val team = entry.arguments?.getLong(TACTICAL_CONTEXT)?.takeIf { it > 0 }
        val id = entry.arguments?.getLong("playId")?.takeIf { it > 0 }
        val presenting by entry.savedStateHandle.getStateFlow(TACTICAL_PRESENTING, false).collectAsStateWithLifecycle()
        val onPresent: (Boolean) -> Unit = { entry.savedStateHandle[TACTICAL_PRESENTING] = it }
        val source = remember(repository, id) { flow { emit(id?.let { repository.tactics.find(it) }) } }
        ObserveRecords(source) { record ->
            if (id != null && record == null) {
                RecordUnavailable(back)
            } else {
                val board = remember(record) {
                    if (record == null) TacticalBoard() else TacticalDocumentCodec.decode(record.document)?.takeIf { it.mode == record.mode }
                }
                if (board == null) MatchStatsScreen {
                    MatchStatsEmptyState(stringResource(R.string.read_error), stringResource(R.string.tactical_document_invalid), R.drawable.ic_info)
                } else {
                    TacticalEditorScreen(
                        repository, record, TacticalSnapshot(board, if (record == null) team else record.teamId),
                        presenting, onPresent, back,
                    )
                }
            }
        }
    }
}
