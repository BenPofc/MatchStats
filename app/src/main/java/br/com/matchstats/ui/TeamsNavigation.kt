package br.com.matchstats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import br.com.matchstats.R
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.MembershipInput
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.RecordUnavailable
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.screens.ExistingPlayerScreen
import br.com.matchstats.ui.screens.PlayerDetailsScreen
import br.com.matchstats.ui.screens.PlayerDirectoryScreen
import br.com.matchstats.ui.screens.PlayerFormScreen
import br.com.matchstats.ui.screens.TeamDetailsScreen
import br.com.matchstats.ui.screens.TeamFormScreen
import br.com.matchstats.ui.screens.TeamsScreen
import kotlinx.coroutines.flow.catch

internal const val TEAMS_LIST = "equipes/lista"
internal const val TEAM_DETAILS = "equipes/detalhes/{teamId}"
internal const val PLAYER_DETAILS = "equipes/jogador/{playerId}"
private const val NEW_TEAM = "equipes/nova"
private const val EDIT_TEAM = "equipes/editar/{teamId}"
internal const val GLOBAL_NEW_PLAYER = "equipes/novo-jogador"
internal const val PLAYERS_SECTION = "show_players"
private const val NEW_PLAYER = "equipes/adicionar-jogador/{teamId}"
private const val PICK_EXISTING = "equipes/jogador-existente/{teamId}"
private const val ASSIGN_PICKED = "equipes/adicionar-vinculo/{playerId}/{teamId}"
private const val ASSIGN_FROM_PROFILE = "equipes/adicionar-vinculo/{playerId}"
private const val EDIT_MEMBERSHIP = "equipes/editar-vinculo/{playerId}/{teamId}"
private const val EDIT_PLAYER = "equipes/editar-jogador/{playerId}"

private fun teamDetails(id: Long) = "equipes/detalhes/$id"
private fun editTeam(id: Long) = "equipes/editar/$id"
private fun newPlayer(id: Long) = "equipes/adicionar-jogador/$id"
private fun playerDetails(id: Long) = "equipes/jogador/$id"
private fun editPlayer(id: Long) = "equipes/editar-jogador/$id"

private val teamArgument = listOf(navArgument("teamId") { type = NavType.LongType })
private val playerArgument = listOf(navArgument("playerId") { type = NavType.LongType })

fun NavGraphBuilder.teamsGraph(
    repository: MatchStatsRepository,
    nav: NavHostController,
    onOpenMatch: (Long) -> Unit,
    onOpenPractice: (Long) -> Unit,
    onTacticalBoard: (Long) -> Unit,
) {
    val back: () -> Unit = { nav.popBackStack() }
    navigation(startDestination = TEAMS_LIST, route = MatchStatsTab.Teams.route) {
        developmentRoute(TEAMS_DEVELOPMENT, repository, nav)
        composable(TEAMS_LIST) { entry ->
            val showPlayers by entry.savedStateHandle.getStateFlow(PLAYERS_SECTION, false).collectAsStateWithLifecycle()
            val setSection: (Boolean) -> Unit = { entry.savedStateHandle[PLAYERS_SECTION] = it }
            val state = rememberSaveableStateHolder()
            state.SaveableStateProvider(showPlayers) {
                if (showPlayers) {
                    val directory = remember(repository) { repository.observeDirectory() }
                    ObserveRecords(directory) {
                        PlayerDirectoryScreen(
                            it, setSection, { nav.navigate(GLOBAL_NEW_PLAYER) }, { nav.navigate(playerDetails(it)) },
                        )
                    }
                } else {
                    TeamsScreen(
                        repository,
                        onNewTeam = { nav.navigate(NEW_TEAM) },
                        onOpen = { nav.navigate(teamDetails(it)) },
                        onSection = setSection,
                    )
                }
            }
        }
        composable(NEW_TEAM) {
            TeamFormScreen(
                team = null,
                saveTeam = { repository.saveTeam(null, it) },
                onSaved = back,
                onCancel = back,
            )
        }
        composable(TEAM_DETAILS, teamArgument) { entry ->
            val teamId = entry.arguments?.getLong("teamId") ?: return@composable
            val rosterFlow = remember(repository, teamId) { repository.observeRoster(teamId) }
            val deletion = rememberRecordAction()
            ObserveRecords(rosterFlow) { roster ->
                if (roster == null) {
                    RecordUnavailable(back)
                } else {
                    val performanceFlow = remember(repository, teamId) {
                        repository.observeTeamPerformance(teamId).catch {}
                    }
                    val performance by performanceFlow.collectAsStateWithLifecycle(initialValue = null)
                    TeamDetailsScreen(
                        roster = roster,
                        performance = performance,
                        deletion = deletion,
                        onEdit = { nav.navigate(editTeam(teamId)) },
                        onAddPlayer = { nav.navigate(newPlayer(teamId)) },
                        onAddExisting = { nav.navigate("equipes/jogador-existente/$teamId") },
                        onOpenPlayer = { nav.navigate(playerDetails(it)) },
                        onTacticalBoard = { onTacticalBoard(teamId) },
                        onDelete = { repository.deleteTeam(teamId) },
                        onDeleted = back,
                    )
                }
            }
        }
        composable(EDIT_TEAM, teamArgument) { entry ->
            val teamId = entry.arguments?.getLong("teamId") ?: return@composable
            val rosterFlow = remember(repository, teamId) { repository.observeRoster(teamId) }
            ObserveRecords(rosterFlow) { roster ->
                if (roster == null) {
                    RecordUnavailable(back)
                } else {
                    TeamFormScreen(
                        team = roster.team,
                        hasPositions = roster.players.any { it.position != null },
                        hasMatchHistory = roster.hasMatchHistory,
                        saveTeam = { repository.saveTeam(teamId, it) },
                        onSaved = back,
                        onCancel = back,
                    )
                }
            }
        }
        composable(GLOBAL_NEW_PLAYER) {
            NewPlayer(repository, teamId = null, back)
        }
        composable(NEW_PLAYER, teamArgument) { entry ->
            val teamId = entry.arguments?.getLong("teamId") ?: return@composable
            NewPlayer(repository, teamId, back)
        }
        composable(PICK_EXISTING, teamArgument) { entry ->
            val teamId = entry.arguments?.getLong("teamId") ?: return@composable
            val teams = remember(repository) { repository.observeTeams() }
            val directory = remember(repository) { repository.observeDirectory() }
            ObserveRecords(teams) { allTeams ->
                val team = allTeams.firstOrNull { it.id == teamId }
                if (team == null) {
                    RecordUnavailable(back)
                } else {
                    ObserveRecords(directory) { players ->
                        ExistingPlayerScreen(team, players.filter { player -> player.teams.none { it.team.id == teamId } }) {
                            nav.navigate("equipes/adicionar-vinculo/$it/$teamId")
                        }
                    }
                }
            }
        }
        composable(ASSIGN_PICKED, playerArgument + teamArgument) { entry ->
            val playerId = entry.arguments?.getLong("playerId") ?: return@composable
            val teamId = entry.arguments?.getLong("teamId") ?: return@composable
            MembershipForm(repository, playerId, teamId, editing = false, back = back) {
                nav.popBackStack()
                nav.popBackStack()
            }
        }
        composable(ASSIGN_FROM_PROFILE, playerArgument) { entry ->
            val playerId = entry.arguments?.getLong("playerId") ?: return@composable
            MembershipForm(repository, playerId, null, editing = false, back, onSaved = back)
        }
        composable(EDIT_MEMBERSHIP, playerArgument + teamArgument) { entry ->
            val playerId = entry.arguments?.getLong("playerId") ?: return@composable
            val teamId = entry.arguments?.getLong("teamId") ?: return@composable
            MembershipForm(repository, playerId, teamId, editing = true, back, onSaved = back)
        }
        composable(PLAYER_DETAILS, playerArgument) { entry ->
            val playerId = entry.arguments?.getLong("playerId") ?: return@composable
            val playerFlow = remember(repository, playerId) { repository.observePlayer(playerId) }
            val deletion = rememberRecordAction()
            ObserveRecords(playerFlow) { details ->
                if (details == null) {
                    RecordUnavailable(back)
                } else {
                    PlayerDetailsScreen(
                        details = details,
                        deletion = deletion,
                        onEdit = { nav.navigate(editPlayer(playerId)) },
                        onAddMembership = { nav.navigate("equipes/adicionar-vinculo/$playerId") },
                        onEditMembership = { nav.navigate("equipes/editar-vinculo/$playerId/$it") },
                        onRemoveMembership = { repository.removeMembership(playerId, it) },
                        onOpenMatch = onOpenMatch,
                        practiceStore = repository.practices,
                        onOpenPractice = onOpenPractice,
                        onDevelopment = { sport, practice ->
                            nav.navigate(developmentPath(false, playerId, sport, null, practice)) {
                                launchSingleTop = true
                            }
                        },
                        onDelete = { repository.deletePlayer(playerId) },
                        onDeleted = back,
                    )
                }
            }
        }
        composable(EDIT_PLAYER, playerArgument) { entry ->
            val playerId = entry.arguments?.getLong("playerId") ?: return@composable
            val playerFlow = remember(repository, playerId) { repository.observePlayer(playerId) }
            ObserveRecords(playerFlow) { details ->
                if (details == null) {
                    RecordUnavailable(back)
                } else {
                    PlayerFormScreen(
                        teams = emptyList(),
                        player = details.player,
                        initialTeamId = null,
                        title = R.string.edit_player,
                        nameOnly = true,
                        savePlayer = { repository.renamePlayer(playerId, it.name) },
                        onSaved = back,
                        onCancel = back,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewPlayer(repository: MatchStatsRepository, teamId: Long?, back: () -> Unit) {
    val teamsFlow = remember(repository) { repository.observeTeams() }
    ObserveRecords(teamsFlow) { teams ->
        val available = if (teamId == null) teams else teams.filter { it.id == teamId }
        if (teamId != null && available.isEmpty()) {
            RecordUnavailable(back)
        } else {
            PlayerFormScreen(
                teams = available,
                player = null,
                initialTeamId = teamId,
                teamRequired = teamId != null,
                savePlayer = repository::createPlayer,
                onSaved = back,
                onCancel = back,
            )
        }
    }
}

@Composable
private fun MembershipForm(
    repository: MatchStatsRepository,
    playerId: Long,
    teamId: Long?,
    editing: Boolean,
    back: () -> Unit,
    onSaved: () -> Unit,
) {
    val playerFlow = remember(repository, playerId) { repository.observePlayer(playerId) }
    val teamsFlow = remember(repository) { repository.observeTeams() }
    ObserveRecords(playerFlow) { details ->
        if (details == null) {
            RecordUnavailable(back)
        } else {
            ObserveRecords(teamsFlow) { teams ->
                val member = details.entry.teams.firstOrNull { it.team.id == teamId }
                val available = when {
                    teamId != null -> teams.filter { it.id == teamId }
                    else -> teams.filter { team -> details.entry.teams.none { it.team.id == team.id } }
                }
                if (teamId != null && (available.isEmpty() || editing && member == null)) {
                    RecordUnavailable(back)
                } else {
                    PlayerFormScreen(
                        teams = available,
                        player = details.player,
                        initialTeamId = teamId,
                        membership = member.takeIf { editing },
                        title = if (editing) R.string.edit_membership else R.string.link_team,
                        teamRequired = true,
                        savePlayer = { input ->
                            repository.saveMembership(
                                playerId, MembershipInput(input.teamId, input.jersey, input.position, input.captain), editing,
                            )
                        },
                        onSaved = onSaved,
                        onCancel = back,
                    )
                }
            }
        }
    }
}
