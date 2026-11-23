package br.com.matchstats.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import br.com.matchstats.R
import br.com.matchstats.data.BoxBuiltin
import br.com.matchstats.data.BoxColumn
import br.com.matchstats.data.BoxConfiguration
import br.com.matchstats.data.BoxCustomMetric
import br.com.matchstats.data.BoxMetric
import br.com.matchstats.data.BoxMetricKind
import br.com.matchstats.data.Sport
import br.com.matchstats.data.WriteResult
import br.com.matchstats.data.defaultBoxColumns
import br.com.matchstats.data.normalizeBoxColumns
import br.com.matchstats.ui.components.IconTile
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsConfirmDelete
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportSelector
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@Composable
internal fun BoxMetric.title(): String = builtin?.let { stringResource(it.nameRes) } ?: requireNotNull(custom).name

@Composable
internal fun BoxMetric.shortLabel(): String = builtin?.let { stringResource(it.shortRes) } ?: requireNotNull(custom).abbreviation

@Composable
internal fun BoxConfigurationScreen(
    config: BoxConfiguration,
    matchName: String? = null,
    onSport: (Sport) -> Unit,
    save: suspend (List<BoxColumn>, List<BoxCustomMetric>, Set<String>) -> WriteResult,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val defaultsMode = matchName == null
    var columns by rememberSaveable { mutableStateOf(ArrayList(config.columns)) }
    var newMetrics by rememberSaveable { mutableStateOf(arrayListOf<BoxCustomMetric>()) }
    var archived by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    val action = rememberRecordAction()
    val catalog = config.metrics.filterKeys { it !in archived } +
        newMetrics.associate { it.id to BoxMetric(it.id, it.kind, null, it) }
    val ordered = normalizeBoxColumns(columns, catalog.keys)
    val visible = ordered.filter(BoxColumn::enabled).map { catalog.getValue(it.metricId) }
    BackHandler(enabled = action.running) {}

    MatchStatsScreen(footer = {
        Column(Modifier.fillMaxWidth()) {
            RecordError(action.error)
            MatchStatsButton(
                stringResource(if (action.running) R.string.saving else R.string.box_save_config),
                { action.launch({ save(ordered, newMetrics, archived.toSet()) }, onSaved) },
                Modifier.fillMaxWidth(),
                !action.running,
            )
        }
    }) {
        SportsBanner(stringResource(R.string.box_config), stringResource(R.string.box_config_intro))
        if (defaultsMode) SportSelector(config.sport, onSport, enabled = !action.running)
        Text(
            matchName ?: stringResource(R.string.box_scope_default),
            style = MaterialTheme.typography.titleMedium,
        )
        MatchStatsCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(MatchStatsSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                SectionHeading(stringResource(R.string.box_preview), R.drawable.ic_stats)
                if (visible.isEmpty()) {
                    Text(stringResource(R.string.box_no_columns), color = MatchStatsColors.Muted)
                } else {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                    ) {
                        visible.forEach { metric ->
                            androidx.compose.material3.Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MatchStatsColors.SoftGreen,
                                contentColor = MatchStatsColors.GreenInk,
                            ) {
                                Text(metric.shortLabel(), Modifier.padding(MatchStatsSpacing.Small))
                            }
                        }
                    }
                }
            }
        }
        MatchStatsCard(Modifier.fillMaxWidth()) {
            ordered.forEachIndexed { index, column ->
                if (index > 0) androidx.compose.material3.HorizontalDivider()
                val metric = catalog.getValue(column.metricId)
                BoxConfigRow(
                    metric = metric,
                    selected = column.enabled,
                    enabled = !action.running,
                    canArchive = defaultsMode && metric.custom != null,
                    canUp = index > 0,
                    canDown = index < ordered.lastIndex,
                    sport = config.sport,
                    onToggle = {
                        columns = ArrayList(ordered).apply { set(index, column.copy(enabled = it)) }
                        action.clearError()
                    },
                    onMove = { offset ->
                        columns = ArrayList(ordered).apply { add(index + offset, removeAt(index)) }
                        action.clearError()
                    },
                    onArchive = { confirming = column.metricId },
                )
            }
        }
        if (defaultsMode) {
            MatchStatsSecondaryButton(
                stringResource(R.string.box_add_metric), { adding = true }, Modifier.fillMaxWidth(), !action.running,
            )
        }
        MatchStatsSecondaryButton(
            stringResource(R.string.box_restore),
            {
                val reset = if (defaultsMode) defaultBoxColumns(config.sport) else config.defaults
                columns = ArrayList(normalizeBoxColumns(reset, catalog.keys))
                action.clearError()
            },
            Modifier.fillMaxWidth(),
            !action.running,
        )
        MatchStatsSecondaryButton(stringResource(R.string.cancel), onCancel, Modifier.fillMaxWidth(), !action.running)
    }

    if (adding) {
        val reserved = catalog.values.map { it.shortLabel() }
        NewBoxMetricDialog(config.sport, reserved, onDismiss = { adding = false }) { metric ->
            newMetrics = ArrayList(newMetrics + metric)
            columns = ArrayList(ordered + BoxColumn(metric.id, true))
            action.clearError()
            adding = false
        }
    }
    val toArchive = confirming?.let(catalog::get)
    if (toArchive != null) {
        MatchStatsConfirmDelete(
            title = stringResource(R.string.box_archive_metric),
            message = stringResource(R.string.box_archive_confirm, toArchive.title()),
            onConfirm = {
                if (toArchive.id in config.metrics) archived = ArrayList(archived + toArchive.id)
                newMetrics = ArrayList(newMetrics.filterNot { it.id == toArchive.id })
                columns = ArrayList(ordered.filterNot { it.metricId == toArchive.id })
                action.clearError()
                confirming = null
            },
            onDismiss = { confirming = null },
            confirmLabel = R.string.box_archive,
        )
    }
}

@Composable
private fun BoxConfigRow(
    metric: BoxMetric,
    selected: Boolean,
    enabled: Boolean,
    canArchive: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    sport: Sport,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onArchive: () -> Unit,
) {
    val title = metric.title()
    val kind = when (metric.kind) {
        BoxMetricKind.TOTAL -> R.string.box_total_kind
        BoxMetricKind.PAIR -> R.string.box_pair_kind
        BoxMetricKind.PERCENT -> R.string.box_percentage_kind
    }
    val icon = when (metric.builtin) {
        BoxBuiltin.MINUTES -> R.drawable.ic_history
        BoxBuiltin.GOALS, BoxBuiltin.POINTS -> R.drawable.ic_trophy
        BoxBuiltin.FOOTBALL_ASSISTS, BoxBuiltin.BASKET_ASSISTS, BoxBuiltin.PASSES -> R.drawable.ic_teams
        BoxBuiltin.TACKLES, BoxBuiltin.INTERCEPTIONS, BoxBuiltin.BLOCKS, BoxBuiltin.SAVES -> R.drawable.ic_shield
        BoxBuiltin.YELLOW, BoxBuiltin.RED, BoxBuiltin.FOULS, BoxBuiltin.PERSONAL_FOULS -> R.drawable.ic_info
        null -> R.drawable.ic_grid
        else -> if (metric.kind == BoxMetricKind.PERCENT) R.drawable.ic_stats else sport.icon
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MatchStatsSpacing.Small, vertical = MatchStatsSpacing.Tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
    ) {
        IconTile(icon, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            val caption = if (metric.custom?.archived == true) R.string.box_archived else kind
            Text(
                metric.shortLabel() + " · " + stringResource(caption),
                style = MaterialTheme.typography.bodySmall,
                color = MatchStatsColors.Muted,
            )
        }
        IconButton(onClick = { onMove(-1) }, modifier = Modifier.width(40.dp), enabled = enabled && canUp) {
            MatchStatsIcon(R.drawable.ic_chevron, Modifier.rotate(-90f), description = stringResource(R.string.box_move_up, title))
        }
        IconButton(onClick = { onMove(1) }, modifier = Modifier.width(40.dp), enabled = enabled && canDown) {
            MatchStatsIcon(R.drawable.ic_chevron, Modifier.rotate(90f), description = stringResource(R.string.box_move_down, title))
        }
        if (canArchive) {
            IconButton(onClick = onArchive, modifier = Modifier.width(40.dp), enabled = enabled) {
                MatchStatsIcon(R.drawable.ic_delete, description = stringResource(R.string.box_archive_metric))
            }
        }
        Switch(
            checked = selected, onCheckedChange = onToggle, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun NewBoxMetricDialog(
    sport: Sport,
    reserved: List<String>,
    onDismiss: () -> Unit,
    onAdd: (BoxCustomMetric) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var abbreviation by rememberSaveable { mutableStateOf("") }
    var pair by rememberSaveable { mutableStateOf(false) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.box_add_metric)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                MatchStatsTextField(name, { name = it; invalid = false }, label = stringResource(R.string.box_metric_name))
                MatchStatsTextField(
                    abbreviation, { abbreviation = it; invalid = false },
                    label = stringResource(R.string.box_metric_abbr),
                )
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                    MatchStatsFilterChip(
                        stringResource(R.string.box_total_kind), R.drawable.ic_grid, !pair, { pair = false },
                    )
                    MatchStatsFilterChip(
                        stringResource(R.string.box_pair_kind), R.drawable.ic_stats, pair, { pair = true },
                    )
                }
                if (invalid) RecordError(WriteResult.INVALID_BOX_METRIC)
            }
        },
        confirmButton = {
            MatchStatsButton(stringResource(R.string.save), {
                val metric = BoxCustomMetric.create(sport, name, abbreviation, if (pair) BoxMetricKind.PAIR else BoxMetricKind.TOTAL)
                invalid = metric.name.length !in 1..60 || metric.abbreviation.length !in 1..8 ||
                    metric.abbreviation.any(Char::isWhitespace) || reserved.any { it.equals(metric.abbreviation, ignoreCase = true) }
                if (!invalid) onAdd(metric)
            })
        },
        dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss) },
    )
}

@Composable
private fun Modifier.verticalScrollForBoxDialog(): Modifier =
    this.then(Modifier.verticalScroll(rememberScrollState()))
