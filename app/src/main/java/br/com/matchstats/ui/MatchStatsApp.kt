package br.com.matchstats.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.matchstats.R
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.ui.components.MatchStatsScaffold
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.screens.HomeScreen
import br.com.matchstats.ui.screens.ProfileScreen
import br.com.matchstats.ui.screens.StatsScreen

enum class MatchStatsTab(
    val route: String,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    Home("inicio", R.string.nav_home, R.drawable.ic_home),
    Teams("equipes", R.string.nav_teams, R.drawable.ic_teams),
    Matches("partidas", R.string.nav_matches, R.drawable.ic_calendar),
    Stats("estatisticas", R.string.nav_stats, R.drawable.ic_stats),
    Profile("perfil", R.string.nav_profile, R.drawable.ic_person),
}

@Composable
fun MatchStatsApp(repository: MatchStatsRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val tactical = destination?.route == TACTICAL_DIRECTORY || destination?.route == TACTICAL_EDITOR
    val teamContext = backStackEntry?.arguments?.getLong(TACTICAL_CONTEXT) ?: 0L
    val presenting = if (destination?.route == TACTICAL_EDITOR) {
        backStackEntry?.savedStateHandle?.getStateFlow(TACTICAL_PRESENTING, false)
            ?.collectAsStateWithLifecycle()?.value == true
    } else false
    val selected = if (tactical) {
        if (teamContext > 0) MatchStatsTab.Teams else MatchStatsTab.Home
    } else if (destination?.route == STATS_DEVELOPMENT) MatchStatsTab.Stats
    else MatchStatsTab.entries.firstOrNull { tab ->
        destination?.hierarchy?.any { it.route == tab.route } == true
    } ?: MatchStatsTab.Home

    val navigateTo: (MatchStatsTab) -> Unit = { tab ->
        if (navController.currentDestination?.hierarchy?.any { it.route == tab.route } != true) {
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val openTacticalBoard: (Long) -> Unit = { team ->
        navController.navigate(tacticalDirectory(team)) { launchSingleTop = true }
    }
    val openMatch: (Long) -> Unit = { id ->
        navigateTo(MatchStatsTab.Matches)
        navController.popBackStack(MATCHES_LIST, inclusive = false)
        navController.getBackStackEntry(MATCHES_LIST).savedStateHandle[PRACTICES_SECTION] = false
        navController.navigate(matchDetails(id)) { launchSingleTop = true }
    }
    val openPractice: (Long?) -> Unit = { id ->
        navigateTo(MatchStatsTab.Matches)
        navController.popBackStack(MATCHES_LIST, inclusive = false)
        val state = navController.getBackStackEntry(MATCHES_LIST).savedStateHandle
        state[PRACTICES_SECTION] = true
        if (id == null) state[PRACTICES_COMPLETED] = false
        navController.navigate(id?.let(::practiceDetails) ?: NEW_PRACTICE) { launchSingleTop = true }
    }
    val openTeamSection: (Boolean, Boolean) -> Unit = { showPlayers, create ->
        navigateTo(MatchStatsTab.Teams)
        navController.popBackStack(TEAMS_LIST, inclusive = false)
        navController.getBackStackEntry(TEAMS_LIST).savedStateHandle[PLAYERS_SECTION] = showPlayers
        if (create) navController.navigate(GLOBAL_NEW_PLAYER)
    }
    val title = when (destination?.route) {
        TEAM_DETAILS -> stringResource(R.string.team_details)
        PLAYER_DETAILS -> stringResource(R.string.player_details)
        TEAMS_DEVELOPMENT, STATS_DEVELOPMENT -> stringResource(R.string.development_title)
        MATCH_DETAILS -> stringResource(R.string.match_details)
        MATCH_STATS -> stringResource(R.string.match_stats)
        BOX_SCORE -> stringResource(R.string.box_score)
        LIVE_GAME -> stringResource(R.string.live_title)
        LIVE_HISTORY -> stringResource(R.string.live_history)
        PRACTICE_DETAILS -> stringResource(R.string.practices)
        PRACTICE_HISTORY -> stringResource(R.string.practice_history)
        TACTICAL_EDITOR -> stringResource(R.string.tactical_board)
        else -> null
    }
    val isNested = when (selected) {
        MatchStatsTab.Teams -> destination?.route != TEAMS_LIST
        MatchStatsTab.Matches -> destination?.route != MATCHES_LIST
        MatchStatsTab.Stats -> destination?.route == STATS_DEVELOPMENT
        else -> false
    }
    val onBack: (() -> Unit)? = if (tactical) {
        { if (dispatcher != null) dispatcher.onBackPressed() else navController.popBackStack() }
    } else if (isNested) {
        { navController.popBackStack() }
    } else {
        null
    }

    MatchStatsScaffold(selected = selected, onTabSelected = navigateTo, title = title, onBack = onBack, showBars = !presenting) {
        NavHost(
            navController = navController,
            startDestination = MatchStatsTab.Home.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(MatchStatsTab.Home.route) {
                val dashboard = remember(repository) { repository.observeHome() }
                ObserveRecords(dashboard) {
                    HomeScreen(
                        dashboard = it,
                        onNavigate = navigateTo,
                        onOpenMatch = openMatch,
                        onOpenPractice = openPractice,
                        onNewPractice = { openPractice(null) },
                        onViewTeams = { openTeamSection(false, false) },
                        onViewPlayers = { openTeamSection(true, false) },
                        onAddPlayer = { openTeamSection(true, true) },
                        onTacticalBoard = { openTacticalBoard(0) },
                    )
                }
            }
            teamsGraph(repository, navController, openMatch, openPractice, openTacticalBoard)
            matchesGraph(repository, navController)
            tacticalRoutes(repository, navController)
            composable(MatchStatsTab.Stats.route) {
                StatsScreen(repository) { id, sport, team, practice ->
                    navController.navigate(developmentPath(true, id, sport, team, practice)) { launchSingleTop = true }
                }
            }
            developmentRoute(STATS_DEVELOPMENT, repository, navController)
            composable(MatchStatsTab.Profile.route) { ProfileScreen() }
        }
    }
}
