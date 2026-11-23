package br.com.matchstats.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.DirectoryPlayer
import br.com.matchstats.data.Sport
import br.com.matchstats.data.StatMetric
import br.com.matchstats.data.Team
import br.com.matchstats.ui.components.IconTile
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.text.Collator

private enum class DirectorySort(val label: Int, val sport: Sport? = null, val metric: StatMetric? = null) {
    NAME(R.string.sort_player_name),
    GAMES(R.string.recorded_games),
    GOALS(R.string.stat_goals, Sport.FOOTBALL, StatMetric.GOALS),
    FOOTBALL_ASSISTS(R.string.stat_assists, Sport.FOOTBALL, StatMetric.ASSISTS),
    POINTS(R.string.stat_points, Sport.BASKETBALL, StatMetric.POINTS),
    REBOUNDS(R.string.stat_rebounds, Sport.BASKETBALL, StatMetric.REBOUNDS),
}

@Composable
internal fun TeamPlayerSelector(playersSelected: Boolean, onSelect: (Boolean) -> Unit) {
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MatchStatsSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            MatchStatsFilterChip(
                stringResource(R.string.nav_teams), R.drawable.ic_teams, !playersSelected,
                { onSelect(false) }, Modifier.weight(1f),
            )
            MatchStatsFilterChip(
                stringResource(R.string.players), R.drawable.ic_person, playersSelected,
                { onSelect(true) }, Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun PlayerDirectoryScreen(
    directory: List<DirectoryPlayer>,
    onSection: (Boolean) -> Unit,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf<Sport?>(null) }
    var sort by rememberSaveable { mutableStateOf(DirectorySort.NAME) }
    val options = DirectorySort.entries.filter { it.sport == null || it.sport == sport }
    val sortLabels = options.associateWith { stringResource(it.label) }
    val visible = remember(directory, query, sport, sort) {
        val names = Collator.getInstance(MatchLocale)
        val nameOrder = compareBy<DirectoryPlayer, String>(names) { it.player.name }.thenBy { it.player.id }
        val metric = sort.metric
        val selectedSport = sport
        val order = when {
            sort == DirectorySort.GAMES -> compareByDescending<DirectoryPlayer> { it.games(sport) }.then(nameOrder)
            metric != null && selectedSport != null -> compareByDescending<DirectoryPlayer> { it.totals(selectedSport)?.get(metric) ?: 0L }
                .then(nameOrder)
            else -> nameOrder
        }
        directory.filter { (sport?.let(it::plays) != false) && it.matches(query, sport) }.sortedWith(order)
    }
    val focus = LocalFocusManager.current

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MatchStatsSpacing.ContentMax).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(MatchStatsSpacing.Screen, MatchStatsSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
            ) {
                item {
                    SportsBanner(stringResource(R.string.players), stringResource(R.string.player_directory_intro))
                }
                item { TeamPlayerSelector(true) { focus.clearFocus(); onSection(it) } }
                item { PlayerSearch(query, { query = it }) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                        MatchStatsFilterChip(
                            stringResource(R.string.all_players), R.drawable.ic_grid, sport == null,
                            onClick = {
                                sport = null
                                if (sort.sport != null) sort = DirectorySort.NAME
                            },
                        )
                        Sport.entries.forEach { option ->
                            MatchStatsFilterChip(stringResource(option.label), option.icon, sport == option, {
                                sport = option
                                if (sort.sport != null && sort.sport != option) sort = DirectorySort.NAME
                            })
                        }
                    }
                }
                item {
                    MatchStatsDropdown(
                        stringResource(R.string.sort_players), sort, options,
                        optionLabel = { sortLabels.getValue(it) }, onSelect = { sort = it },
                    )
                }
                item {
                    SectionHeading(stringResource(R.string.saved_players, visible.size), R.drawable.ic_teams)
                }
                if (visible.isEmpty()) item { PlayerListEmpty(directory.isEmpty()) }
                items(visible, key = { it.player.id }) { entry ->
                    DirectoryCard(entry, sport) {
                        focus.clearFocus()
                        onOpen(entry.player.id)
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().padding(horizontal = MatchStatsSpacing.Screen, vertical = MatchStatsSpacing.Small),
                contentAlignment = Alignment.CenterEnd,
            ) {
                MatchStatsButton(stringResource(R.string.new_player), {
                    focus.clearFocus()
                    onNew()
                })
            }
        }
    }
}

@Composable
internal fun ExistingPlayerScreen(team: Team, available: List<DirectoryPlayer>, onSelect: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    MatchStatsScreen {
        SportsBanner(stringResource(R.string.add_existing_player), team.name)
        PlayerSearch(query, { query = it })
        val visible = available.filter { it.matches(query) }
        if (visible.isEmpty()) {
            MatchStatsEmptyState(
                stringResource(if (available.isEmpty()) R.string.no_players_to_link else R.string.no_matching_players),
                stringResource(if (available.isEmpty()) R.string.no_players_to_link_hint else R.string.player_search_hint),
                R.drawable.ic_person,
            )
        }
        visible.forEach { entry ->
            DirectoryCard(entry, team.sport) {
                focus.clearFocus()
                onSelect(entry.player.id)
            }
        }
    }
}

@Composable
private fun PlayerSearch(query: String, onQuery: (String) -> Unit) {
    val focus = LocalFocusManager.current
    MatchStatsTextField(
        value = query,
        onValueChange = onQuery,
        label = stringResource(R.string.player_search),
        leadingIcon = { MatchStatsIcon(R.drawable.ic_search, tint = MatchStatsColors.GreenInk) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQuery("") }) {
                    MatchStatsIcon(R.drawable.ic_close, description = stringResource(R.string.clear_search))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

@Composable
private fun PlayerListEmpty(noPlayers: Boolean) {
    MatchStatsEmptyState(
        stringResource(if (noPlayers) R.string.empty_player_directory else R.string.no_matching_players),
        stringResource(if (noPlayers) R.string.empty_player_directory_hint else R.string.player_search_hint),
        R.drawable.ic_person,
    )
}

@Composable
private fun DirectoryCard(entry: DirectoryPlayer, sport: Sport?, onClick: () -> Unit) {
    val team = entry.primaryTeam(sport)
    val membership = entry.teams.firstOrNull { it.team.id == team?.id }
    val displaySport = sport ?: team?.sport ?: entry.totals.singleOrNull()?.sport
    MatchStatsCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            Modifier.padding(MatchStatsSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                IconTile(displaySport?.icon ?: R.drawable.ic_person)
                Column(Modifier.weight(1f)) {
                    Text(entry.player.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        team?.name ?: stringResource(R.string.no_team),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MatchStatsColors.Muted,
                    )
                    val info = listOfNotNull(
                        team?.let { stringResource(it.sport.label) },
                        membership?.jerseyNumber?.let { stringResource(R.string.jersey_short, it) },
                        membership?.position?.let { stringResource(it.label) },
                    )
                    if (info.isNotEmpty()) Text(info.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                MatchStatsIcon(R.drawable.ic_chevron, tint = MatchStatsColors.GreenInk)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlayerStat(R.string.recorded_games, entry.games(sport).toLong())
                if (displaySport != null) {
                    val totals = entry.totals(displaySport)
                    val metrics = if (displaySport == Sport.FOOTBALL) listOf(StatMetric.GOALS, StatMetric.ASSISTS)
                        else listOf(StatMetric.POINTS, StatMetric.REBOUNDS)
                    metrics.forEach { PlayerStat(it.label, totals?.get(it) ?: 0L) }
                }
            }
        }
    }
}

@Composable
internal fun PlayerStat(label: Int, value: Long) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.small,
        color = MatchStatsColors.SoftGreen,
        contentColor = MatchStatsColors.GreenInk,
    ) {
        Row(
            Modifier.padding(horizontal = MatchStatsSpacing.Small, vertical = MatchStatsSpacing.Tiny),
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
            Text(value.statNumber(), style = MaterialTheme.typography.labelLarge)
        }
    }
}
