package br.com.matchstats.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.RecordUnavailable
import br.com.matchstats.ui.screens.PracticeDraft
import br.com.matchstats.ui.screens.PracticeEntryScreen
import br.com.matchstats.ui.screens.PracticeFormScreen
import br.com.matchstats.ui.screens.PracticeHistoryScreen
import br.com.matchstats.ui.screens.PracticePending

internal const val PRACTICES_SECTION = "showPractices"
internal const val PRACTICES_COMPLETED = "showCompletedPractices"
internal const val NEW_PRACTICE = "partidas/praticas/nova"
internal const val PRACTICE_DETAILS = "partidas/praticas/detalhes/{practiceId}"
internal const val EDIT_PRACTICE = "partidas/praticas/editar/{practiceId}"
internal const val PRACTICE_HISTORY = "partidas/praticas/historico/{practiceId}"
private val practiceArgument = listOf(navArgument("practiceId") { type = NavType.LongType })

internal fun practiceDetails(id: Long) = "partidas/praticas/detalhes/$id"

internal fun NavGraphBuilder.practiceRoutes(repository: MatchStatsRepository, nav: NavHostController) {
    val store = repository.practices
    val back: () -> Unit = { nav.popBackStack() }
    composable(NEW_PRACTICE) {
        val candidates = remember(store) { store.candidates() }
        ObserveRecords(candidates) { choices ->
            var newId: Long? = null
            PracticeFormScreen(null, choices, { store.save(null, it).also { result -> newId = result.second }.first }, {
                newId?.let { id ->
                    nav.getBackStackEntry(MATCHES_LIST).savedStateHandle[PRACTICES_COMPLETED] = false
                    nav.navigate(practiceDetails(id)) { popUpTo(NEW_PRACTICE) { inclusive = true } }
                }
            }, back)
        }
    }
    composable(PRACTICE_DETAILS, practiceArgument) { entry ->
        val id = entry.arguments?.getLong("practiceId") ?: return@composable
        val details = remember(store, id) { store.details(id) }
        val listState = remember(entry) { nav.getBackStackEntry(MATCHES_LIST).savedStateHandle }
        val draftKey = "practiceDrafts_$id"
        val pendingKey = "practicePending_$id"
        val drafts by remember(listState, id) { listState.getStateFlow(draftKey, ArrayList<PracticeDraft>()) }
            .collectAsStateWithLifecycle()
        val pending by remember(listState, id) { listState.getStateFlow<PracticePending?>(pendingKey, null) }
            .collectAsStateWithLifecycle()
        ObserveRecords(details) { record ->
            if (record == null) RecordUnavailable(back)
            else PracticeEntryScreen(
                record, store, drafts, pending,
                onDrafts = { listState[draftKey] = ArrayList(it) },
                onPending = { request ->
                    val existing = listState.get<PracticePending>(pendingKey)
                    val current = listState.get<ArrayList<PracticeDraft>>(draftKey).orEmpty()
                    if (request == null || existing == null && current.containsAll(request.drafts)) {
                        listState[pendingKey] = request
                    }
                },
                onEdit = { nav.navigate("partidas/praticas/editar/$id") { launchSingleTop = true } },
                onHistory = { nav.navigate("partidas/praticas/historico/$id") { launchSingleTop = true } },
                onCompleted = { listState[PRACTICES_COMPLETED] = true },
                onDeleted = {
                    listState.remove<ArrayList<PracticeDraft>>(draftKey)
                    listState.remove<PracticePending>(pendingKey)
                    back()
                },
            )
        }
    }
    composable(EDIT_PRACTICE, practiceArgument) { entry ->
        val id = entry.arguments?.getLong("practiceId") ?: return@composable
        val details = remember(store, id) { store.details(id) }
        val candidates = remember(store) { store.candidates() }
        ObserveRecords(details) { record ->
            if (record == null) RecordUnavailable(back)
            else ObserveRecords(candidates) { choices ->
                PracticeFormScreen(record, choices, { store.save(id, it).first }, back, back)
            }
        }
    }
    composable(PRACTICE_HISTORY, practiceArgument) { entry ->
        val id = entry.arguments?.getLong("practiceId") ?: return@composable
        val history = remember(store, id) { store.history(id) }
        ObserveRecords(history) { PracticeHistoryScreen(it, store) }
    }
}
