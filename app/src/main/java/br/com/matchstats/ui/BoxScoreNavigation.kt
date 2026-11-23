package br.com.matchstats.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import br.com.matchstats.R
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.Sport
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.RecordUnavailable
import br.com.matchstats.ui.screens.BoxConfigurationScreen
import br.com.matchstats.ui.screens.BoxScoreScreen
import br.com.matchstats.ui.screens.EditBoxPlayerScreen

internal const val BOX_SCORE = "partidas/box-score/{matchId}"
internal const val BOX_DEFAULTS = "partidas/box-score/padrao"
private const val BOX_CONFIG = "partidas/box-score/{matchId}/configurar"
private const val BOX_EDIT_PLAYER = "partidas/box-score/{matchId}/jogador/{playerId}"
private val fixtureArgs = listOf(navArgument("matchId") { type = NavType.LongType })

internal fun boxScoreRoute(matchId: Long) = "partidas/box-score/$matchId"
internal fun boxConfigRoute(matchId: Long) = "partidas/box-score/$matchId/configurar"

internal fun NavGraphBuilder.boxScoreRoutes(repository: MatchStatsRepository, nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    composable(BOX_DEFAULTS) {
        var sport by rememberSaveable { mutableStateOf(Sport.BASKETBALL) }
        val state = rememberSaveableStateHolder()
        val flow = remember(repository, sport) { repository.observeBoxConfiguration(sport) }
        state.SaveableStateProvider(sport.name) {
            ObserveRecords(flow) { config ->
                if (config == null) RecordUnavailable(back)
                else BoxConfigurationScreen(
                    config = config,
                    onSport = { sport = it },
                    save = { columns, custom, archived -> repository.saveBoxConfiguration(sport, null, columns, custom, archived) },
                    onSaved = back,
                    onCancel = back,
                )
            }
        }
    }
    composable(BOX_SCORE, arguments = fixtureArgs) { entry ->
        val id = entry.arguments?.getLong("matchId") ?: return@composable
        val flow = remember(repository, id) { repository.observeBoxScore(id) }
        ObserveRecords(flow) { score ->
            if (score == null) RecordUnavailable(back)
            else BoxScoreScreen(score, { nav.navigate(boxConfigRoute(id)) }) {
                nav.navigate("partidas/box-score/$id/jogador/$it")
            }
        }
    }
    composable(BOX_CONFIG, arguments = fixtureArgs) { entry ->
        val id = entry.arguments?.getLong("matchId") ?: return@composable
        val flow = remember(repository, id) { repository.observeMatch(id) }
        ObserveRecords(flow) { fixture ->
            if (fixture == null) {
                RecordUnavailable(back)
            } else {
                val sport = fixture.team.sport
                val configFlow = remember(repository, id, sport) { repository.observeBoxConfiguration(sport, id) }
                ObserveRecords(configFlow) { config ->
                    if (config == null) RecordUnavailable(back)
                    else BoxConfigurationScreen(
                        config = config,
                        matchName = stringResource(R.string.match_stats_scope, fixture.team.name, fixture.match.opponent),
                        onSport = {},
                        save = { columns, _, _ -> repository.saveBoxConfiguration(sport, id, columns) },
                        onSaved = back,
                        onCancel = back,
                    )
                }
            }
        }
    }
    composable(BOX_EDIT_PLAYER, arguments = fixtureArgs + navArgument("playerId") { type = NavType.LongType }) { entry ->
        val id = entry.arguments?.getLong("matchId") ?: return@composable
        val playerId = entry.arguments?.getLong("playerId") ?: return@composable
        val flow = remember(repository, id) { repository.observeBoxScore(id) }
        ObserveRecords(flow) { score ->
            val player = score?.players?.firstOrNull { it.player.id == playerId }
            if (score == null || player == null) RecordUnavailable(back)
            else EditBoxPlayerScreen(score, player, { repository.saveBoxPlayer(id, playerId, it) }, back, back)
        }
    }
}
