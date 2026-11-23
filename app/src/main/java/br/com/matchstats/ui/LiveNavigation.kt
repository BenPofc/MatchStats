package br.com.matchstats.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.SCORE_ADJUSTMENT
import br.com.matchstats.data.Sport
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.RecordUnavailable
import br.com.matchstats.ui.screens.LiveConfigurationScreen
import br.com.matchstats.ui.screens.LiveCorrectionScreen
import br.com.matchstats.ui.screens.LiveGameScreen
import br.com.matchstats.ui.screens.LiveHistoryScreen

internal const val LIVE_GAME = "partidas/ao-vivo/{matchId}"
internal const val LIVE_HISTORY = "partidas/ao-vivo/{matchId}/historico"
internal const val LIVE_DEFAULTS = "partidas/acoes-ao-vivo"
private const val LIVE_CONFIG = "partidas/ao-vivo/{matchId}/acoes"
private const val LIVE_SCORE_EDIT = "partidas/ao-vivo/{matchId}/placar"
private const val LIVE_EVENT_EDIT = "partidas/ao-vivo/{matchId}/evento/{eventId}"
private val fixtureArguments = listOf(navArgument("matchId") { type = NavType.LongType })

internal fun liveGameRoute(matchId: Long) = "partidas/ao-vivo/$matchId"
internal fun liveHistoryRoute(matchId: Long) = "partidas/ao-vivo/$matchId/historico"

internal fun NavGraphBuilder.liveRoutes(repository: MatchStatsRepository, nav: NavHostController, onFinished: () -> Unit) {
    val tracking = repository.live
    val back: () -> Unit = { nav.popBackStack() }
    composable(LIVE_GAME, fixtureArguments) { entry ->
        val id = entry.arguments?.getLong("matchId") ?: return@composable
        val flow = remember(tracking, id) { tracking.observe(id) }
        ObserveRecords(flow) { game ->
            if (game == null) RecordUnavailable(back)
            else LiveGameScreen(
                game, tracking,
                onBox = { nav.navigate(boxScoreRoute(id)) },
                onConfigure = { nav.navigate("partidas/ao-vivo/$id/acoes") },
                onHistory = { nav.navigate(liveHistoryRoute(id)) },
                onFinished = onFinished,
                onBack = back,
            )
        }
    }
    composable(LIVE_HISTORY, fixtureArguments) { entry ->
        val id = entry.arguments?.getLong("matchId") ?: return@composable
        val flow = remember(tracking, id) { tracking.observe(id) }
        ObserveRecords(flow) { game ->
            if (game?.session == null) RecordUnavailable(back)
            else LiveHistoryScreen(
                game, tracking,
                onCorrect = { nav.navigate("partidas/ao-vivo/$id/evento/$it") },
                onScore = { nav.navigate("partidas/ao-vivo/$id/placar") },
            )
        }
    }
    composable(LIVE_SCORE_EDIT, fixtureArguments) { entry ->
        val id = entry.arguments?.getLong("matchId") ?: return@composable
        val flow = remember(tracking, id) { tracking.observe(id) }
        ObserveRecords(flow) { game ->
            if (game?.session == null) RecordUnavailable(back)
            else LiveCorrectionScreen(game, tracking, null, scoreCorrection = true, onBack = back)
        }
    }
    composable(LIVE_EVENT_EDIT, fixtureArguments + navArgument("eventId") { type = NavType.StringType }) { entry ->
        val id = entry.arguments?.getLong("matchId") ?: return@composable
        val eventId = entry.arguments?.getString("eventId") ?: return@composable
        val flow = remember(tracking, id) { tracking.observe(id) }
        ObserveRecords(flow) { game ->
            val event = game?.activeEvents?.firstOrNull { it.id == eventId }
            if (game?.session == null || event == null) RecordUnavailable(back)
            else LiveCorrectionScreen(game, tracking, event, event.actionId == SCORE_ADJUSTMENT, back)
        }
    }
    composable(LIVE_DEFAULTS) {
        var sport by rememberSaveable { mutableStateOf(Sport.BASKETBALL) }
        val state = rememberSaveableStateHolder()
        val flow = remember(tracking, sport) { tracking.observeConfiguration(sport, null) }
        state.SaveableStateProvider(sport.name) {
            ObserveRecords(flow) { config ->
                if (config == null) RecordUnavailable(back)
                else LiveConfigurationScreen(
                    config, false, { sport = it }, {},
                    { tracking.saveConfiguration(sport, null, it) }, back,
                )
            }
        }
    }
    composable(LIVE_CONFIG, fixtureArguments) { entry ->
        val id = entry.arguments?.getLong("matchId") ?: return@composable
        val flow = remember(tracking, id) { tracking.observe(id) }
        ObserveRecords(flow) { game ->
            if (game == null) RecordUnavailable(back)
            else LiveConfigurationScreen(
                game.config, true, {}, { nav.navigate(LIVE_DEFAULTS) },
                { tracking.saveConfiguration(game.fixture.team.sport, id, it) }, back,
            )
        }
    }
}
