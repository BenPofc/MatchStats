package br.com.matchstats.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import br.com.matchstats.data.DEVELOPMENT_ALL_TEAMS
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.Sport
import br.com.matchstats.ui.screens.PlayerDevelopmentScreen

internal const val TEAMS_DEVELOPMENT = "equipes/evolucao/{playerId}/{sport}/{teamId}/{practice}"
internal const val STATS_DEVELOPMENT = "estatisticas/evolucao/{playerId}/{sport}/{teamId}/{practice}"

internal fun developmentPath(fromStats: Boolean, playerId: Long, sport: Sport, teamId: Long?, practice: Boolean): String {
    val prefix = if (fromStats) "estatisticas" else "equipes"
    return "$prefix/evolucao/$playerId/${sport.name}/${teamId ?: DEVELOPMENT_ALL_TEAMS}/$practice"
}

internal fun NavGraphBuilder.developmentRoute(route: String, repository: MatchStatsRepository, nav: NavHostController) {
    composable(route, listOf(
        navArgument("playerId") { type = NavType.LongType },
        navArgument("sport") { type = NavType.StringType },
        navArgument("teamId") { type = NavType.LongType },
        navArgument("practice") { type = NavType.BoolType },
    )) { entry ->
        val arguments = entry.arguments ?: return@composable
        val sport = Sport.entries.find { it.name == arguments.getString("sport") } ?: Sport.FOOTBALL
        PlayerDevelopmentScreen(repository, arguments.getLong("playerId"), sport, arguments.getLong("teamId"),
            arguments.getBoolean("practice")) { nav.popBackStack() }
    }
}
