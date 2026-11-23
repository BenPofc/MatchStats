package br.com.matchstats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.RankedPlayer
import br.com.matchstats.data.ScoredMatch
import br.com.matchstats.data.Sport
import br.com.matchstats.data.StatMetric
import br.com.matchstats.ui.components.IconTile
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class TeamFilter(val id: Long?, val name: String)

@Composable
fun StatsScreen(repository: MatchStatsRepository, onDevelopment: (Long, Sport, Long?, Boolean) -> Unit) {
    var practices by rememberSaveable { mutableStateOf(false) }
    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    holder.SaveableStateProvider(practices) {
        if (practices) PracticeStatisticsScreen(repository, onDevelopment) { practices = it }
        else GameStatisticsScreen(repository, onDevelopment) { practices = it }
    }
}

@Composable
private fun GameStatisticsScreen(
    repository: MatchStatsRepository,
    onDevelopment: (Long, Sport, Long?, Boolean) -> Unit,
    onContext: (Boolean) -> Unit,
) {
    var sport by rememberSaveable { mutableStateOf(Sport.FOOTBALL) }
    var teamId by rememberSaveable { mutableStateOf<Long?>(null) }
    val flow = remember(repository, sport) { repository.observeSportStatistics(sport) }
    key(sport) {
        ObserveRecords(flow) { snapshot ->
            val all = stringResource(R.string.stats_all_teams)
            val nameCounts = remember(snapshot.teams) {
                snapshot.teams.groupingBy { it.name.lowercase(MatchLocale) }.eachCount()
            }
            val options = listOf(TeamFilter(null, all)) + snapshot.teams.map { team ->
                val name = if (nameCounts.getValue(team.name.lowercase(MatchLocale)) == 1) {
                    team.name
                } else {
                    val qualifiedName = team.category?.let {
                        stringResource(R.string.team_category_info, team.name, it)
                    } ?: team.name
                    stringResource(R.string.stats_duplicate_team_option, qualifiedName, team.id)
                }
                TeamFilter(team.id, name)
            }
            val selected = options.firstOrNull { it.id == teamId } ?: options.first()
            val data = remember(snapshot, selected.id) { snapshot.forTeam(selected.id) }

            MatchStatsScreen {
                SportsBanner(stringResource(R.string.nav_stats), stringResource(R.string.stats_intro))
                PracticeContextTabs(false, R.string.player_games, onContext)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                ) {
                    Sport.entries.forEach { option ->
                        MatchStatsFilterChip(
                            stringResource(option.label),
                            option.icon,
                            sport == option,
                            onClick = {
                                if (sport != option) {
                                    sport = option
                                    teamId = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                MatchStatsDropdown(
                    label = stringResource(R.string.stats_team_filter),
                    selected = selected,
                    options = options,
                    optionLabel = TeamFilter::name,
                    onSelect = { teamId = it.id },
                    enabled = options.size > 1,
                    leadingIcon = { MatchStatsIcon(R.drawable.ic_teams, tint = MatchStatsColors.GreenInk) },
                )

                if (data.performance.completed == 0) {
                    MatchStatsEmptyState(
                        stringResource(if (sport == Sport.FOOTBALL) R.string.empty_soccer_stats else R.string.empty_basketball_stats),
                        stringResource(R.string.empty_stats_hint),
                        R.drawable.ic_stats,
                    )
                    return@MatchStatsScreen
                }

                val metrics = if (sport == Sport.FOOTBALL) listOf(StatMetric.GOALS, StatMetric.ASSISTS)
                else listOf(StatMetric.POINTS, StatMetric.REBOUNDS, StatMetric.ASSISTS)
                val rankings = remember(data, metrics) { metrics.associateWith(data::ranking) }
                Leaders(metrics, rankings, sport)
                PerformanceCard(data.performance, sport, stringResource(R.string.stats_performance))
                RecentScoreChart(data.recent, sport)
                PlayerRankings(sport, selected.id, metrics, rankings) { onDevelopment(it, sport, selected.id, false) }
            }
        }
    }
}

@Composable
private fun Leaders(
    metrics: List<StatMetric>,
    rankings: Map<StatMetric, List<RankedPlayer>>,
    sport: Sport,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
            metrics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                    row.forEach { metric ->
                        val leader = rankings.getValue(metric).firstOrNull()
                        MatchStatsCard(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(MatchStatsSpacing.Medium),
                                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
                            ) {
                                IconTile(if (metric == StatMetric.GOALS || metric == StatMetric.POINTS) sport.icon else R.drawable.ic_trophy)
                                Text(stringResource(metric.leaderTitle), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    leader?.playerName ?: stringResource(R.string.stats_no_player_value),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (leader == null) MatchStatsColors.Muted else MatchStatsColors.GreenInk,
                                )
                                if (leader != null) {
                                    Text(metric.quantity(leader.total), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerRankings(
    sport: Sport,
    teamId: Long?,
    metrics: List<StatMetric>,
    rankings: Map<StatMetric, List<RankedPlayer>>,
    onDevelopment: (Long) -> Unit,
) {
    var metric by rememberSaveable(sport, teamId) { mutableStateOf(metrics.first()) }
    var expanded by rememberSaveable(sport, teamId, metric) { mutableStateOf(false) }
    val ranking = rankings.getValue(metric)

    MatchStatsCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(MatchStatsSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            SectionHeading(stringResource(R.string.stats_rankings), R.drawable.ic_trophy)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                metrics.forEach { choice ->
                    MatchStatsFilterChip(
                        stringResource(choice.label), R.drawable.ic_stats, choice == metric, { metric = choice },
                    )
                }
            }

            if (ranking.isEmpty()) {
                Text(stringResource(R.string.stats_no_ranking), color = MatchStatsColors.Muted)
            }
            val visible = if (expanded) ranking else ranking.take(5)
            visible.forEachIndexed { index, player ->
                HorizontalDivider(color = MatchStatsColors.Border)
                Row(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (index + 1).toString(),
                        Modifier.background(MatchStatsColors.SoftGreen, MaterialTheme.shapes.small)
                            .padding(horizontal = MatchStatsSpacing.Small, vertical = MatchStatsSpacing.Tiny),
                        color = MatchStatsColors.GreenInk,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(player.playerName, style = MaterialTheme.typography.titleMedium)
                        if (teamId == null) {
                            Text(player.teamName, style = MaterialTheme.typography.bodySmall, color = MatchStatsColors.Muted)
                        }
                    }
                    Text(
                        player.total.statNumber(),
                        modifier = Modifier.weight(0.38f, fill = false),
                        color = MatchStatsColors.GreenInk,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { onDevelopment(player.playerId) }) {
                        MatchStatsIcon(
                            R.drawable.ic_chevron,
                            tint = MatchStatsColors.GreenInk,
                            description = stringResource(R.string.development_show_player, player.playerName),
                        )
                    }
                }
            }
            if (ranking.size > 5) {
                MatchStatsSecondaryButton(
                    stringResource(if (expanded) R.string.stats_show_less else R.string.stats_show_all),
                    { expanded = !expanded },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val chartDate = DateTimeFormatter.ofPattern("dd/MM", MatchLocale)

@Composable
private fun RecentScoreChart(matches: List<ScoredMatch>, sport: Sport) {
    val peak = matches.maxOfOrNull { it.scored }?.coerceAtLeast(1) ?: 1
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(MatchStatsSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            val title = if (sport == Sport.FOOTBALL) R.string.stats_goals_chart else R.string.stats_points_chart
            SectionHeading(stringResource(title), R.drawable.ic_stats)
            matches.forEach { match ->
                val date = Instant.ofEpochMilli(match.startsAt).atZone(ZoneId.systemDefault()).format(chartDate)
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.stats_chart_match, date, match.opponent),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(match.scored.toLong().statNumber(), color = MatchStatsColors.GreenInk, style = MaterialTheme.typography.titleSmall)
                    }
                    Box(Modifier.fillMaxWidth().padding(top = MatchStatsSpacing.Tiny).height(8.dp)
                        .background(MatchStatsColors.SoftGreen, CircleShape)) {
                        Box(Modifier.fillMaxWidth(match.scored.toFloat() / peak).height(8.dp)
                            .background(MatchStatsColors.ActionGreen, CircleShape))
                    }
                }
            }
        }
    }
}

private val StatMetric.leaderTitle: Int
    get() = when (this) {
        StatMetric.GOALS -> R.string.stats_leader_goals
        StatMetric.POINTS -> R.string.stats_leader_points
        StatMetric.REBOUNDS -> R.string.stats_leader_rebounds
        else -> R.string.stats_leader_assists
    }

@Composable
private fun StatMetric.quantity(total: Long): String {
    val resource = when (this) {
        StatMetric.GOALS -> R.plurals.stats_goal_quantity
        StatMetric.POINTS -> R.plurals.stats_point_quantity
        StatMetric.REBOUNDS -> R.plurals.stats_rebound_quantity
        else -> R.plurals.stats_assist_quantity
    }
    return pluralStringResource(resource, if (total == 1L) 1 else 2, total.statNumber())
}
