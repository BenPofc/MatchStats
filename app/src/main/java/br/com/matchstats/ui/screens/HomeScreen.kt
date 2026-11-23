package br.com.matchstats.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.HomeDashboard
import br.com.matchstats.ui.MatchStatsTab
import br.com.matchstats.ui.components.IconTile
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeScreen(
    dashboard: HomeDashboard,
    onNavigate: (MatchStatsTab) -> Unit,
    onOpenMatch: (Long) -> Unit,
    onOpenPractice: (Long) -> Unit,
    onNewPractice: () -> Unit,
    onViewTeams: () -> Unit,
    onViewPlayers: () -> Unit,
    onAddPlayer: () -> Unit,
    onTacticalBoard: () -> Unit,
) {
    val counts = dashboard.counts
    MatchStatsScreen {
        SportsBanner(stringResource(R.string.home_welcome), stringResource(R.string.home_intro))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 310.dp || LocalDensity.current.fontScale > 1.4f) 1 else 2
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                val item = if (columns == 2) Modifier.weight(1f).fillMaxRowHeight() else Modifier.fillMaxWidth()
                SummaryCard(R.drawable.ic_teams, stringResource(R.string.nav_teams), stringResource(R.string.record_count, counts.teams), item)
                SummaryCard(R.drawable.ic_person, stringResource(R.string.players), stringResource(R.string.record_count, counts.players), item)
                SummaryCard(R.drawable.ic_calendar, stringResource(R.string.nav_matches), stringResource(R.string.record_count, counts.matches), item)
                SummaryCard(R.drawable.ic_bolt, stringResource(R.string.practices), stringResource(R.string.record_count, dashboard.practiceCount), item)
                SummaryCard(
                    R.drawable.ic_trophy,
                    stringResource(R.string.latest_results),
                    dashboard.results.firstOrNull()?.match?.scoreLabel() ?: stringResource(R.string.no_results_short),
                    item,
                    compactValue = true,
                )
            }
        }
        val upcoming = dashboard.upcoming
        if (upcoming == null) {
            HomeEmptySection(R.string.next_match, R.string.no_next_match, R.drawable.ic_calendar, tintedHeader = true)
        } else {
            SectionHeading(stringResource(R.string.next_match), R.drawable.ic_calendar)
            MatchCard(upcoming) { onOpenMatch(upcoming.match.id) }
        }
        val practice = dashboard.nextPractice
        if (practice == null) {
            HomeEmptySection(R.string.practice_next, R.string.practice_empty_upcoming, R.drawable.ic_bolt)
        } else {
            SectionHeading(stringResource(R.string.practice_next), R.drawable.ic_bolt)
            PracticeCard(practice) { onOpenPractice(practice.session.id) }
        }
        MatchStatsCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(MatchStatsSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
            ) {
                SectionHeading(stringResource(R.string.quick_actions), R.drawable.ic_bolt)
                QuickActions(onNavigate, onViewTeams, onViewPlayers, onAddPlayer, onNewPractice, onTacticalBoard)
            }
        }
        if (dashboard.results.isEmpty()) {
            HomeEmptySection(R.string.recent_results, R.string.empty_results, R.drawable.ic_trophy)
        } else {
            SectionHeading(stringResource(R.string.recent_results), R.drawable.ic_trophy)
            dashboard.results.forEach { result ->
                MatchCard(result) { onOpenMatch(result.match.id) }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    modifier: Modifier,
    compactValue: Boolean = false,
) {
    MatchStatsCard(modifier.semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(MatchStatsSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    value,
                    style = if (compactValue) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineSmall,
                    color = MatchStatsColors.GreenInk,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(
    onNavigate: (MatchStatsTab) -> Unit,
    onViewTeams: () -> Unit,
    onViewPlayers: () -> Unit,
    onAddPlayer: () -> Unit,
    onNewPractice: () -> Unit,
    onTacticalBoard: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 275.dp || LocalDensity.current.fontScale > 1.4f) 1 else 3
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            val actions = listOf(
                Triple(R.drawable.ic_teams, R.string.view_teams, onViewTeams),
                Triple(R.drawable.ic_person, R.string.view_players, onViewPlayers),
                Triple(R.drawable.ic_person, R.string.add_player, onAddPlayer),
                Triple(R.drawable.ic_calendar, R.string.view_matches) { onNavigate(MatchStatsTab.Matches) },
                Triple(R.drawable.ic_stats, R.string.view_stats) { onNavigate(MatchStatsTab.Stats) },
                Triple(R.drawable.ic_bolt, R.string.practice_new, onNewPractice),
                Triple(R.drawable.ic_basketball, R.string.tactical_board, onTacticalBoard),
            )
            actions.forEach { (icon, label, action) ->
                Surface(
                    onClick = action,
                    modifier = Modifier.weight(1f).fillMaxRowHeight(),
                    shape = MaterialTheme.shapes.small,
                    color = MatchStatsColors.SoftGreen,
                    contentColor = MatchStatsColors.GreenInk,
                ) {
                    Column(
                        modifier = Modifier.heightIn(min = 68.dp).padding(MatchStatsSpacing.Small),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny, Alignment.CenterVertically),
                    ) {
                        MatchStatsIcon(icon)
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeEmptySection(
    title: Int,
    message: Int,
    @DrawableRes icon: Int,
    tintedHeader: Boolean = false,
) {
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Surface(color = if (tintedHeader) MatchStatsColors.SoftGreen else MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxWidth().padding(MatchStatsSpacing.Medium)) {
                SectionHeading(stringResource(title), icon)
            }
        }
        Text(
            stringResource(message),
            modifier = Modifier.padding(
                start = MatchStatsSpacing.Medium,
                end = MatchStatsSpacing.Medium,
                bottom = MatchStatsSpacing.Medium,
                top = if (tintedHeader) MatchStatsSpacing.Medium else 0.dp,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
