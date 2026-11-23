package br.com.matchstats.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.com.matchstats.R
import br.com.matchstats.data.BoxScore
import br.com.matchstats.data.hasFinalReport
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.MatchWithTeam
import br.com.matchstats.data.WriteResult
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.NavyBanner
import br.com.matchstats.ui.components.RecordAction
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@Composable
fun MatchesScreen(
    fixtures: List<MatchWithTeam>,
    status: MatchStatus,
    onStatusChange: (MatchStatus) -> Unit,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onConfigureBox: () -> Unit,
    onConfigureLive: () -> Unit,
    onContext: (Boolean) -> Unit,
) {
    val results = status == MatchStatus.FINISHED
    MatchStatsScreen(
        footer = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small, Alignment.End),
            ) {
                MatchStatsSecondaryButton(stringResource(R.string.box_config), onConfigureBox)
                MatchStatsButton(stringResource(R.string.new_match), onNew)
            }
        },
    ) {
        SportsBanner(stringResource(R.string.nav_matches))
        PracticeContextTabs(false, R.string.nav_matches, onContext)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            MatchStatsFilterChip(
                stringResource(R.string.upcoming_matches), R.drawable.ic_calendar,
                !results, { onStatusChange(MatchStatus.SCHEDULED) }, Modifier.weight(1f),
            )
            MatchStatsFilterChip(
                stringResource(R.string.results), R.drawable.ic_trophy,
                results, { onStatusChange(MatchStatus.FINISHED) }, Modifier.weight(1f),
            )
        }
        if (fixtures.isEmpty()) {
            MatchStatsEmptyState(
                stringResource(if (results) R.string.empty_results else R.string.empty_schedule),
                stringResource(if (results) R.string.empty_results_hint else R.string.empty_schedule_hint),
                status.icon,
            )
        }
        fixtures.forEach { fixture ->
            key(fixture.match.id) { MatchCard(fixture) { onOpen(fixture.match.id) } }
        }
        MatchStatsSecondaryButton(stringResource(R.string.live_config), onConfigureLive, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun MatchDetailsScreen(
    fixture: MatchWithTeam,
    deletion: RecordAction,
    onEdit: () -> Unit,
    onStats: () -> Unit,
    onBoxScore: () -> Unit,
    onConfigureBox: () -> Unit,
    onLive: () -> Unit,
    onLiveHistory: () -> Unit,
    onReadReport: suspend () -> BoxScore?,
    onDelete: suspend () -> WriteResult,
    onDeleted: () -> Unit,
) {
    MatchStatsScreen {
        NavyBanner {
            Column(
                modifier = Modifier.fillMaxWidth(0.8f).padding(MatchStatsSpacing.Screen),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
            ) {
                Text(
                    stringResource(R.string.match_stats_scope, fixture.team.name, fixture.match.opponent),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(fixture.team.sport.label), color = MatchStatsColors.OnNavyMuted)
            }
        }
        MatchCard(fixture)
        val active = fixture.match.status == MatchStatus.SCHEDULED && fixture.live != null
        if (fixture.match.status == MatchStatus.FINISHED) {
            MatchStatsButton(
                stringResource(R.string.match_stats), onStats, Modifier.fillMaxWidth(), enabled = !deletion.running,
            )
            MatchStatsSecondaryButton(
                stringResource(R.string.box_score), onBoxScore, Modifier.fillMaxWidth(), enabled = !deletion.running,
            )
            if (fixture.live != null) {
                MatchStatsSecondaryButton(stringResource(R.string.live_history), onLiveHistory, Modifier.fillMaxWidth())
            }
            if (fixture.match.hasFinalReport) {
                MatchReportActions(enabled = !deletion.running, readBoxScore = onReadReport)
            }
        } else {
            MatchStatsButton(
                stringResource(if (active) R.string.live_resume_session else R.string.live_start_session),
                onLive, Modifier.fillMaxWidth(), enabled = !deletion.running,
            )
            if (!active) MatchStatsSecondaryButton(
                stringResource(R.string.box_config), onConfigureBox, Modifier.fillMaxWidth(), enabled = !deletion.running,
            )
        }
        if (active) return@MatchStatsScreen
        MatchStatsSecondaryButton(
            stringResource(R.string.edit_match), onEdit, Modifier.fillMaxWidth(), enabled = !deletion.running,
        )
        DeleteRecordButton(
            stringResource(R.string.delete_match),
            stringResource(R.string.confirm_delete_match, fixture.match.opponent),
            deletion, onDelete, onDeleted,
        )
    }
}
