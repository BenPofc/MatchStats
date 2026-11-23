package br.com.matchstats.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.Sport
import br.com.matchstats.data.TeamSummary
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.text.Normalizer

private val accents = Regex("\\p{M}+")

private fun String.forSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(accents, "").lowercase()

@Composable
fun TeamsScreen(
    repository: MatchStatsRepository,
    onNewTeam: () -> Unit,
    onOpen: (Long) -> Unit,
    onSection: (Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSport by rememberSaveable { mutableStateOf<Sport?>(null) }
    val focus = LocalFocusManager.current
    val flow = remember(repository) { repository.observeTeamSummaries() }

    ObserveRecords(flow) { teams ->
        val visibleTeams = remember(teams, query, selectedSport) {
            val search = query.trim().forSearch()
            teams.filter { summary ->
                (selectedSport == null || summary.team.sport == selectedSport) &&
                    summary.team.name.forSearch().contains(search)
            }
        }
        MatchStatsScreen(
            footer = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    MatchStatsButton(stringResource(R.string.new_team), onNewTeam)
                }
            },
        ) {
            SportsBanner(stringResource(R.string.nav_teams), stringResource(R.string.teams_intro))
            TeamPlayerSelector(false) {
                focus.clearFocus()
                onSection(it)
            }
            MatchStatsTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.team_search),
                leadingIcon = { MatchStatsIcon(R.drawable.ic_search, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = if (query.isEmpty()) null else {
                    {
                        IconButton(onClick = { query = "" }) {
                            MatchStatsIcon(R.drawable.ic_close, description = stringResource(R.string.clear_search))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                MatchStatsFilterChip(
                    stringResource(R.string.all_teams), R.drawable.ic_grid, selectedSport == null, { selectedSport = null },
                )
                Sport.entries.forEach { sport ->
                    MatchStatsFilterChip(stringResource(sport.label), sport.icon, sport == selectedSport, { selectedSport = sport })
                }
            }
            if (visibleTeams.isEmpty()) {
                val title = when {
                    query.isNotBlank() -> R.string.empty_team_search
                    selectedSport == Sport.FOOTBALL -> R.string.empty_soccer_teams
                    selectedSport == Sport.BASKETBALL -> R.string.empty_basketball_teams
                    else -> R.string.empty_teams
                }
                val message = if (query.isNotBlank()) R.string.empty_team_search_hint else R.string.empty_teams_hint
                MatchStatsEmptyState(stringResource(title), stringResource(message), R.drawable.ic_teams)
            }
            visibleTeams.forEach { summary ->
                TeamListCard(summary, onOpen = { onOpen(summary.team.id) })
            }
        }
    }
}

@Composable
private fun TeamListCard(summary: TeamSummary, onOpen: () -> Unit) {
    val team = summary.team
    val sport = stringResource(team.sport.label)
    val category = team.category
    val details = stringResource(R.string.view_details)
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = details, onClick = onOpen)
                .padding(MatchStatsSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamBadge(team.sport)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
            ) {
                Text(team.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (category == null) sport else stringResource(R.string.team_category_info, sport, category),
                    color = MatchStatsColors.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MatchStatsIcon(R.drawable.ic_teams, Modifier.size(16.dp), MatchStatsColors.GreenInk)
                    Text(
                        pluralStringResource(R.plurals.team_player_count, summary.playerCount, summary.playerCount),
                        color = MatchStatsColors.GreenInk,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            MatchStatsIcon(R.drawable.ic_chevron, tint = MatchStatsColors.GreenInk)
        }
    }
}

@Composable
fun TeamBadge(sport: Sport, size: Dp = 56.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        MatchStatsIcon(R.drawable.ic_shield, Modifier.size(size), MatchStatsColors.GreenInk)
        MatchStatsIcon(sport.icon, Modifier.size(size * 0.47f), MatchStatsColors.White)
    }
}
