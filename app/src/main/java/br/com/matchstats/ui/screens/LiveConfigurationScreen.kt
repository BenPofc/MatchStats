package br.com.matchstats.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.BoxColumn
import br.com.matchstats.data.LiveConfiguration
import br.com.matchstats.data.Sport
import br.com.matchstats.data.WriteResult
import br.com.matchstats.data.defaultLiveColumns
import br.com.matchstats.data.normalizeBoxColumns
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportSelector
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@Composable
internal fun LiveConfigurationScreen(
    configuration: LiveConfiguration,
    scoped: Boolean,
    onSport: (Sport) -> Unit,
    onDefaults: () -> Unit,
    onSave: suspend (List<BoxColumn>) -> WriteResult,
    onBack: () -> Unit,
) {
    var layout by rememberSaveable { mutableStateOf(ArrayList(configuration.columns)) }
    val ordered = normalizeBoxColumns(layout, configuration.catalog.keys)
    val action = rememberRecordAction()
    BackHandler(enabled = action.running) {}
    MatchStatsScreen(footer = {
        Column(Modifier.fillMaxWidth()) {
            RecordError(action.error)
            MatchStatsButton(
                stringResource(R.string.box_save_config), { action.launch({ onSave(ordered) }, onBack) },
                Modifier.fillMaxWidth(), enabled = !action.running,
            )
        }
    }) {
        SportsBanner(stringResource(R.string.live_config), stringResource(R.string.live_actions_scope))
        if (!scoped) SportSelector(configuration.sport, onSport, enabled = !action.running)
        Text(
            stringResource(if (scoped) R.string.live_match_actions else R.string.live_default_actions),
            style = MaterialTheme.typography.titleMedium,
        )
        if (scoped) MatchStatsSecondaryButton(stringResource(R.string.live_default_actions), onDefaults, enabled = !action.running)
        MatchStatsCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(MatchStatsSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                SectionHeading(stringResource(R.string.live_actions), R.drawable.ic_bolt)
                val enabled = ordered.filter { it.enabled }
                if (enabled.isEmpty()) Text(stringResource(R.string.live_empty_actions))
                else Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                ) {
                    enabled.forEach { Text(configuration.catalog.getValue(it.metricId).short(), color = MatchStatsColors.GreenInk) }
                }
            }
        }
        MatchStatsCard(Modifier.fillMaxWidth()) {
            ordered.forEachIndexed { index, column ->
                if (index > 0) HorizontalDivider()
                val button = configuration.catalog.getValue(column.metricId)
                val title = button.label()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = MatchStatsSpacing.Small, vertical = MatchStatsSpacing.Tiny),
                    horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        if (button.short() != title) Text(button.short(), style = MaterialTheme.typography.labelSmall, color = MatchStatsColors.Muted)
                    }
                    IconButton(
                        onClick = { layout = ArrayList(ordered).apply { add(index - 1, removeAt(index)) } },
                        modifier = Modifier.width(40.dp), enabled = index > 0 && !action.running,
                    ) {
                        MatchStatsIcon(R.drawable.ic_chevron, Modifier.rotate(-90f), description = stringResource(R.string.box_move_up, title))
                    }
                    IconButton(
                        onClick = { layout = ArrayList(ordered).apply { add(index + 1, removeAt(index)) } },
                        modifier = Modifier.width(40.dp), enabled = index < ordered.lastIndex && !action.running,
                    ) {
                        MatchStatsIcon(R.drawable.ic_chevron, Modifier.rotate(90f), description = stringResource(R.string.box_move_down, title))
                    }
                    Switch(
                        checked = column.enabled,
                        onCheckedChange = { enabled -> layout = ArrayList(ordered).apply { set(index, column.copy(enabled = enabled)) } },
                        enabled = !action.running,
                        modifier = Modifier.semantics { contentDescription = title },
                    )
                }
            }
        }
        MatchStatsSecondaryButton(
            stringResource(R.string.box_restore),
            {
                val source = if (scoped) configuration.defaults else defaultLiveColumns(configuration.sport)
                layout = ArrayList(normalizeBoxColumns(source, configuration.catalog.keys))
                action.clearError()
            }, Modifier.fillMaxWidth(), enabled = !action.running,
        )
        MatchStatsSecondaryButton(stringResource(R.string.cancel), onBack, Modifier.fillMaxWidth(), enabled = !action.running)
    }
}
