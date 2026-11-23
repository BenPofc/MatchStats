package br.com.matchstats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.RosterPlayer
import br.com.matchstats.data.Sport
import br.com.matchstats.data.PerformanceSummary
import br.com.matchstats.data.TeamRoster
import br.com.matchstats.data.WriteResult
import br.com.matchstats.ui.components.IconTile
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsConfirmDelete
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.NavyBanner
import br.com.matchstats.ui.components.RecordAction
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@Composable
fun TeamDetailsScreen(
    roster: TeamRoster,
    performance: PerformanceSummary?,
    deletion: RecordAction,
    onEdit: () -> Unit,
    onAddPlayer: () -> Unit,
    onAddExisting: () -> Unit,
    onOpenPlayer: (Long) -> Unit,
    onTacticalBoard: () -> Unit,
    onDelete: suspend () -> WriteResult,
    onDeleted: () -> Unit,
) {
    val team = roster.team
    MatchStatsScreen {
        NavyBanner {
            Row(
                modifier = Modifier.fillMaxWidth(0.81f).heightIn(min = 115.dp).padding(MatchStatsSpacing.Screen),
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamBadge(team.sport, 66.dp)
                Column(verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny)) {
                    RecordTitle(team.name)
                    val sport = stringResource(team.sport.label)
                    val category = team.category
                    Text(
                        if (category == null) sport else stringResource(R.string.team_category_info, sport, category),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    team.coachName?.let {
                        Text(stringResource(R.string.coach_info, it), color = MatchStatsColors.OnNavyMuted)
                    }
                }
            }
        }
        MatchStatsCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(MatchStatsSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(R.drawable.ic_teams)
                Column {
                    Text(stringResource(R.string.players), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.record_count, roster.players.size),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MatchStatsColors.GreenInk,
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            MatchStatsSecondaryButton(
                stringResource(R.string.edit_team), onEdit, Modifier.weight(1f), enabled = !deletion.running,
            )
            MatchStatsButton(
                stringResource(R.string.add_player), onAddPlayer, Modifier.weight(1f), enabled = !deletion.running,
            )
        }
        MatchStatsSecondaryButton(
            stringResource(R.string.add_existing_player), onAddExisting, Modifier.fillMaxWidth(),
            enabled = !deletion.running,
        )
        if (team.sport == Sport.BASKETBALL) {
            MatchStatsSecondaryButton(
                stringResource(R.string.tactical_board), onTacticalBoard, Modifier.fillMaxWidth(),
                enabled = !deletion.running,
            )
        }
        MatchStatsCard(Modifier.fillMaxWidth()) {
            SectionHeading(
                stringResource(R.string.players),
                R.drawable.ic_teams,
                Modifier.padding(MatchStatsSpacing.Medium),
            )
            if (roster.players.isEmpty()) {
                Text(
                    stringResource(R.string.empty_roster),
                    modifier = Modifier.padding(
                        start = MatchStatsSpacing.Medium,
                        end = MatchStatsSpacing.Medium,
                        bottom = MatchStatsSpacing.Medium,
                    ),
                    color = MatchStatsColors.Muted,
                )
            }
            roster.players.sortedWith(compareBy({ it.jerseyNumber == null }, { it.jerseyNumber }, { it.name })).forEach { player ->
                HorizontalDivider(Modifier.padding(horizontal = MatchStatsSpacing.Medium))
                RosterRow(player, onClick = { onOpenPlayer(player.id) })
            }
        }
        if (performance != null && performance.completed > 0) {
            PerformanceCard(performance, team.sport, stringResource(R.string.stats_team_performance))
        }
        DeleteRecordButton(
            stringResource(R.string.delete_team),
            stringResource(R.string.confirm_delete_team, team.name),
            deletion,
            onDelete,
            onDeleted,
        )
    }
}

@Composable
private fun RosterRow(player: RosterPlayer, onClick: () -> Unit) {
    val details = stringResource(R.string.view_details)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = details, onClick = onClick)
            .heightIn(min = 64.dp).padding(MatchStatsSpacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(MatchStatsColors.SoftGreen, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            val number = player.jerseyNumber
            if (number == null) {
                MatchStatsIcon(R.drawable.ic_person, tint = MatchStatsColors.GreenInk)
            } else {
                Text(stringResource(R.string.record_count, number), color = MatchStatsColors.GreenInk, style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
        ) {
            Text(player.name, style = MaterialTheme.typography.titleMedium)
            player.position?.let {
                Text(stringResource(it.label), color = MatchStatsColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (player.captain) {
                Text(stringResource(R.string.captain), color = MatchStatsColors.GreenInk, style = MaterialTheme.typography.labelMedium)
            }
        }
        MatchStatsIcon(R.drawable.ic_chevron, tint = MatchStatsColors.Muted)
    }
}

@Composable
private fun RecordTitle(name: String) {
    Text(name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
}

@Composable
internal fun DeleteRecordButton(
    title: String,
    message: String,
    action: RecordAction,
    onDelete: suspend () -> WriteResult,
    onDeleted: () -> Unit,
    enabled: Boolean = true,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    RecordError(action.error)
    MatchStatsSecondaryButton(
        label = if (action.running) stringResource(R.string.deleting) else title,
        onClick = { confirming = true },
        enabled = enabled && !action.running,
        modifier = Modifier.fillMaxWidth(),
        destructive = true,
    )
    if (confirming) {
        MatchStatsConfirmDelete(
            title,
            message,
            onConfirm = {
                confirming = false
                action.launch(onDelete, onDeleted)
            },
            onDismiss = { confirming = false },
        )
    }
}
