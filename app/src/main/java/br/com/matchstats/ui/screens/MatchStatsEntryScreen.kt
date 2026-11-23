package br.com.matchstats.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.MatchStatsSheet
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.Sport
import br.com.matchstats.data.StatMetric
import br.com.matchstats.data.StatValues
import br.com.matchstats.data.WriteResult
import br.com.matchstats.data.nonNegativeInt
import br.com.matchstats.ui.components.FormButtons
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.NavyBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

private val statsDraftSaver = mapSaver<SnapshotStateMap<String, String>>(
    save = { it.toMap() },
    restore = { saved ->
        mutableStateMapOf<String, String>().apply {
            saved.forEach { (name, value) -> put(name, value as String) }
        }
    },
)

private fun draftKey(playerId: Long, metric: StatMetric) = "$playerId/${metric.name}"

@Composable
fun MatchStatsEntryScreen(
    sheet: MatchStatsSheet,
    saveStats: suspend (Map<Long, StatValues>) -> WriteResult,
    onBack: () -> Unit,
) {
    val fixture = sheet.fixture
    val metrics = StatMetric.forSport(fixture.team.sport)
    val saved = sheet.saved.associate { it.playerId to it.values }
    val draft = rememberSaveable(saver = statsDraftSaver) { mutableStateMapOf<String, String>() }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current

    fun input(playerId: Long, metric: StatMetric): String =
        draft[draftKey(playerId, metric)] ?: (saved[playerId]?.get(metric) ?: 0).toString()

    BackHandler(enabled = action.running) {}
    MatchStatsScreen {
        NavyBanner {
            Column(
                modifier = Modifier.fillMaxWidth(0.81f).padding(MatchStatsSpacing.Screen),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
            ) {
                Text(
                    stringResource(R.string.match_stats_scope, fixture.team.name, fixture.match.opponent),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(fixture.team.sport.label), color = MatchStatsColors.OnNavyMuted)
            }
        }
        if (fixture.match.status != MatchStatus.FINISHED || sheet.roster.isEmpty()) {
            val finished = fixture.match.status == MatchStatus.FINISHED
            MatchStatsEmptyState(
                stringResource(if (finished) R.string.empty_roster else R.string.match_stats),
                stringResource(if (finished) R.string.match_stats_empty_hint else R.string.error_match_not_finished),
                R.drawable.ic_stats,
            )
            MatchStatsSecondaryButton(stringResource(R.string.back), onBack, Modifier.fillMaxWidth())
            return@MatchStatsScreen
        }

        Text(stringResource(R.string.match_stats_intro), color = MatchStatsColors.Muted)
        val teamScore = fixture.match.teamScore
        if (fixture.team.sport == Sport.FOOTBALL && teamScore != null) {
            Text(
                stringResource(R.string.stats_goal_limit, teamScore),
                style = MaterialTheme.typography.bodySmall,
                color = MatchStatsColors.Muted,
            )
            if (sheet.saved.sumOf { it.values.goals.toLong() } > teamScore) {
                Text(
                    stringResource(R.string.stats_legacy_goal_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MatchStatsColors.Error,
                )
            }
        }
        for (player in sheet.roster) {
            key(player.id) {
                MatchStatsCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(MatchStatsSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                    ) {
                        Text(player.name, style = MaterialTheme.typography.titleMedium)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth >= 295.dp && LocalDensity.current.fontScale <= 1.35f) 2 else 1
                            Column(verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                                for (row in metrics.chunked(columns)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                                        for (metric in row) {
                                            val value = input(player.id, metric)
                                            MatchStatsTextField(
                                                value,
                                                onValueChange = {
                                                    draft[draftKey(player.id, metric)] = it
                                                    action.clearError()
                                                },
                                                modifier = Modifier.weight(1f),
                                                label = stringResource(metric.label),
                                                error = if (showErrors && value.isNotBlank() && value.nonNegativeInt() == null)
                                                    stringResource(R.string.error_match_score) else null,
                                                enabled = !action.running,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FormButtons(
            action = action,
            onSave = save@{
                focus.clearFocus()
                showErrors = true
                val updates = mutableMapOf<Long, StatValues>()
                for (player in sheet.roster) {
                    if (metrics.none { draft.containsKey(draftKey(player.id, it)) }) continue
                    val values = mutableMapOf<StatMetric, Int>()
                    for (metric in metrics) {
                        val raw = input(player.id, metric)
                        val number = if (raw.isBlank()) 0 else raw.nonNegativeInt() ?: return@save
                        values[metric] = number
                    }
                    updates[player.id] = StatValues.from(values)
                }
                action.launch({ saveStats(updates) }, onBack)
            },
            onCancel = onBack,
        )
    }
}
