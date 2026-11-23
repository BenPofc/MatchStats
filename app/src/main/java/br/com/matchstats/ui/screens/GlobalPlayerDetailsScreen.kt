package br.com.matchstats.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import br.com.matchstats.data.PracticeStore
import br.com.matchstats.data.PlayerDetails
import br.com.matchstats.data.PlayerTeam
import br.com.matchstats.data.Sport
import br.com.matchstats.data.StatMetric
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
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@Composable
internal fun PlayerDetailsScreen(
    details: PlayerDetails,
    deletion: RecordAction,
    onEdit: () -> Unit,
    onAddMembership: () -> Unit,
    onEditMembership: (Long) -> Unit,
    onRemoveMembership: suspend (Long) -> WriteResult,
    onOpenMatch: (Long) -> Unit,
    practiceStore: PracticeStore,
    onOpenPractice: (Long) -> Unit,
    onDevelopment: (Sport, Boolean) -> Unit,
    onDelete: suspend () -> WriteResult,
    onDeleted: () -> Unit,
) {
    val entry = details.entry
    val sports = Sport.entries.filter(entry::plays)
    var practices by rememberSaveable { mutableStateOf(false) }
    var confirmingTeamId by rememberSaveable { mutableStateOf<Long?>(null) }
    val detach = rememberRecordAction()
    val enabled = !detach.running && !deletion.running

    MatchStatsScreen {
        NavyBanner {
            Row(
                modifier = Modifier.fillMaxWidth(0.86f).heightIn(min = 106.dp).padding(MatchStatsSpacing.Screen),
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(R.drawable.ic_person, size = 54.dp)
                Column(verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny)) {
                    Text(entry.player.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (sports.isEmpty()) stringResource(R.string.no_team)
                        else sports.map { stringResource(it.label) }.joinToString(" · "),
                        color = MatchStatsColors.OnNavyMuted,
                    )
                }
            }
        }
        MatchStatsButton(stringResource(R.string.edit_player), onEdit, Modifier.fillMaxWidth(), enabled = enabled)
        MatchStatsSecondaryButton(stringResource(R.string.development_title),
            { onDevelopment(sports.firstOrNull() ?: Sport.FOOTBALL, practices) }, Modifier.fillMaxWidth(), enabled = enabled)
        SectionHeading(stringResource(R.string.current_teams), R.drawable.ic_teams)
        if (entry.teams.isEmpty()) Text(stringResource(R.string.no_team), color = MatchStatsColors.Muted)
        entry.teams.forEach { membership ->
            MembershipCard(
                membership,
                enabled,
                onEdit = { onEditMembership(membership.team.id) },
                onRemove = { confirmingTeamId = membership.team.id },
            )
        }
        RecordError(detach.error)
        MatchStatsSecondaryButton(
            stringResource(R.string.link_team), onAddMembership, Modifier.fillMaxWidth(), enabled = enabled,
        )

        PracticeContextTabs(practices, R.string.player_games) { practices = it }
        if (practices) {
            PlayerPracticeStatistics(entry.player.id, practiceStore, onOpenPractice)
        } else {
            SectionHeading(stringResource(R.string.player_game_statistics), R.drawable.ic_stats)
            sports.forEach { sport ->
                val totals = entry.totals(sport)
                MatchStatsCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(MatchStatsSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                    ) {
                        SectionHeading(stringResource(sport.label), sport.icon)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
                        ) {
                            PlayerStat(R.string.recorded_games, totals?.games?.toLong() ?: 0)
                            StatMetric.forSport(sport).forEach { PlayerStat(it.label, totals?.get(it) ?: 0L) }
                        }
                    }
                }
            }
            SectionHeading(stringResource(R.string.player_match_history), R.drawable.ic_history)
            if (details.games.isEmpty()) {
                Text(stringResource(R.string.no_player_match_history), color = MatchStatsColors.Muted)
            }
            details.games.forEach { game ->
                MatchStatsCard(Modifier.fillMaxWidth().clickable(enabled = enabled) { onOpenMatch(game.match.id) }) {
                    Column(
                        Modifier.padding(MatchStatsSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
                    ) {
                        Text(
                            stringResource(R.string.player_history_fixture, game.teamName, game.match.opponent),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            listOf(
                                game.match.localStart().format(MatchDateFormat),
                                stringResource(game.sport.label),
                                game.match.scoreLabel(),
                            ).joinToString(" · "),
                            color = MatchStatsColors.Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
                        ) {
                            StatMetric.forSport(game.sport).filter { game.values[it] != 0 }.forEach {
                                PlayerStat(it.label, game.values[it].toLong())
                            }
                        }
                    }
                }
            }
        }
        DeleteRecordButton(
            stringResource(R.string.delete_player),
            stringResource(R.string.confirm_delete_player, entry.player.name),
            deletion, onDelete, onDeleted, enabled = !detach.running,
        )
    }

    val confirming = entry.teams.firstOrNull { it.team.id == confirmingTeamId }
    if (confirming != null) {
        MatchStatsConfirmDelete(
            stringResource(R.string.remove_membership),
            stringResource(R.string.confirm_remove_membership, entry.player.name, confirming.team.name),
            onConfirm = {
                confirmingTeamId = null
                detach.launch({ onRemoveMembership(confirming.team.id) }) {}
            },
            onDismiss = { confirmingTeamId = null },
            confirmLabel = R.string.remove,
        )
    }
}

@Composable
private fun MembershipCard(membership: PlayerTeam, enabled: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(MatchStatsSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                IconTile(membership.team.sport.icon)
                Column(Modifier.weight(1f)) {
                    Text(membership.team.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(
                            stringResource(membership.team.sport.label),
                            membership.jerseyNumber?.let { stringResource(R.string.jersey_short, it) },
                            stringResource(membership.position?.label ?: R.string.no_position),
                            if (membership.captain) stringResource(R.string.captain) else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MatchStatsColors.Muted,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                MatchStatsSecondaryButton(stringResource(R.string.edit_membership), onEdit, enabled = enabled)
                MatchStatsSecondaryButton(stringResource(R.string.remove_membership), onRemove, enabled = enabled)
            }
        }
    }
}
