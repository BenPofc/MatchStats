package br.com.matchstats.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.RecordUnavailable
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.screens.MatchDetailsScreen
import br.com.matchstats.ui.screens.MatchFormScreen
import br.com.matchstats.ui.screens.MatchStatsEntryScreen
import br.com.matchstats.ui.screens.MatchesScreen
import br.com.matchstats.ui.screens.PracticeListScreen

internal const val MATCHES_LIST = "partidas/lista"
internal const val MATCH_DETAILS = "partidas/detalhes/{matchId}"
internal const val MATCH_STATS = "partidas/estatisticas/{matchId}"
private const val NEW_MATCH = "partidas/nova"
private const val EDIT_MATCH = "partidas/editar/{matchId}"
private const val MATCH_FILTER = "matchFilter"
private val matchArgument = listOf(navArgument("matchId") { type = NavType.LongType })

internal fun matchDetails(id: Long) = "partidas/detalhes/$id"

internal fun NavGraphBuilder.matchesGraph(repository: MatchStatsRepository, nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    val saveStatus: (MatchStatus) -> Unit = {
        nav.getBackStackEntry(MATCHES_LIST).savedStateHandle[MATCH_FILTER] = it.name
        back()
    }
    navigation(startDestination = MATCHES_LIST, route = MatchStatsTab.Matches.route) {
        practiceRoutes(repository, nav)
        boxScoreRoutes(repository, nav)
        liveRoutes(repository, nav) { saveStatus(MatchStatus.FINISHED) }
        composable(MATCHES_LIST) { entry ->
            val showPractices by remember(entry) {
                entry.savedStateHandle.getStateFlow(PRACTICES_SECTION, false)
            }.collectAsStateWithLifecycle()
            val completed by remember(entry) {
                entry.savedStateHandle.getStateFlow(PRACTICES_COMPLETED, false)
            }.collectAsStateWithLifecycle()
            val selectContext: (Boolean) -> Unit = { entry.savedStateHandle[PRACTICES_SECTION] = it }
            if (showPractices) {
                val practiceFlow = remember(repository, completed) { repository.practices.list(completed) }
                ObserveRecords(practiceFlow) { sessions ->
                    PracticeListScreen(
                        sessions, completed, { entry.savedStateHandle[PRACTICES_COMPLETED] = it }, selectContext,
                        { nav.navigate(NEW_PRACTICE) { launchSingleTop = true } },
                        { id -> nav.navigate(practiceDetails(id)) { launchSingleTop = true } },
                    )
                }
                return@composable
            }
            val filter by remember(entry) {
                entry.savedStateHandle.getStateFlow(MATCH_FILTER, MatchStatus.SCHEDULED.name)
            }.collectAsStateWithLifecycle()
            val status = MatchStatus.valueOf(filter)
            val flow = remember(repository, status) { repository.observeMatches(status) }
            key(status) {
                ObserveRecords(flow) { fixtures ->
                    MatchesScreen(
                        fixtures = fixtures,
                        status = status,
                        onStatusChange = { entry.savedStateHandle[MATCH_FILTER] = it.name },
                        onNew = { nav.navigate(NEW_MATCH) { launchSingleTop = true } },
                        onOpen = { id -> nav.navigate(matchDetails(id)) { launchSingleTop = true } },
                        onConfigureBox = { nav.navigate(BOX_DEFAULTS) { launchSingleTop = true } },
                        onConfigureLive = { nav.navigate(LIVE_DEFAULTS) { launchSingleTop = true } },
                        onContext = selectContext,
                    )
                }
            }
        }
        composable(NEW_MATCH) {
            val flow = remember(repository) { repository.observeTeams() }
            ObserveRecords(flow) { teams ->
                MatchFormScreen(teams, null, { repository.saveMatch(null, it) }, saveStatus, back)
            }
        }
        composable(MATCH_DETAILS, matchArgument) { entry ->
            val id = entry.arguments?.getLong("matchId") ?: return@composable
            val flow = remember(repository, id) { repository.observeMatch(id) }
            val deletion = rememberRecordAction()
            ObserveRecords(flow) { fixture ->
                if (fixture == null) {
                    RecordUnavailable(back)
                } else {
                    MatchDetailsScreen(
                        fixture,
                        deletion,
                        onEdit = { nav.navigate("partidas/editar/$id") { launchSingleTop = true } },
                        onStats = { nav.navigate("partidas/estatisticas/$id") { launchSingleTop = true } },
                        onBoxScore = { nav.navigate(boxScoreRoute(id)) { launchSingleTop = true } },
                        onConfigureBox = { nav.navigate(boxConfigRoute(id)) { launchSingleTop = true } },
                        onLive = { nav.navigate(liveGameRoute(id)) { launchSingleTop = true } },
                        onLiveHistory = { nav.navigate(liveHistoryRoute(id)) { launchSingleTop = true } },
                        onReadReport = { repository.readMatchReport(id) },
                        onDelete = { repository.deleteMatch(id) },
                        onDeleted = back,
                    )
                }
            }
        }
        composable(EDIT_MATCH, matchArgument) { entry ->
            val id = entry.arguments?.getLong("matchId") ?: return@composable
            val teamsFlow = remember(repository) { repository.observeTeams() }
            val matchFlow = remember(repository, id) { repository.observeMatch(id) }
            ObserveRecords(matchFlow) { fixture ->
                if (fixture == null) {
                    RecordUnavailable(back)
                } else {
                    ObserveRecords(teamsFlow) { teams ->
                        MatchFormScreen(teams, fixture.match, { repository.saveMatch(id, it) }, saveStatus, back)
                    }
                }
            }
        }
        composable(MATCH_STATS, matchArgument) { entry ->
            val id = entry.arguments?.getLong("matchId") ?: return@composable
            val flow = remember(repository, id) { repository.observeMatchSheet(id) }
            ObserveRecords(flow) { sheet ->
                if (sheet == null) {
                    RecordUnavailable(back)
                } else {
                    MatchStatsEntryScreen(sheet, { repository.saveMatchStats(id, it) }, back)
                }
            }
        }
    }
}
