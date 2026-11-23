package br.com.matchstats.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import br.com.matchstats.R
import br.com.matchstats.data.BoxBuiltin
import br.com.matchstats.data.BoxMeasurement
import br.com.matchstats.data.BoxMetric
import br.com.matchstats.data.BoxMetricKind
import br.com.matchstats.data.BoxPlayer
import br.com.matchstats.data.BoxScore
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.WriteResult
import br.com.matchstats.data.cell
import br.com.matchstats.data.nonNegativeInt
import br.com.matchstats.ui.components.FormButtons
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.NavyBanner
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.text.NumberFormat

@Composable
internal fun BoxScoreScreen(score: BoxScore, onConfigure: () -> Unit, onEdit: (Long) -> Unit) {
    val columns = score.config.visible
    var away by rememberSaveable { mutableStateOf(false) }
    val roster = if (away) score.opponents else score.players
    val finished = score.fixture.match.status == MatchStatus.FINISHED
    val canEdit = !away && finished && columns.any { it.kind != BoxMetricKind.PERCENT }
    val hasData = roster.any { it.canonical != null || it.measurements.isNotEmpty() }
    val horizontal = rememberScrollState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = MatchStatsSpacing.ContentMax).fillMaxSize().padding(MatchStatsSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            NavyBanner {
                Column(Modifier.fillMaxWidth(0.87f).padding(MatchStatsSpacing.Medium)) {
                    Text(
                        stringResource(R.string.match_stats_scope, score.fixture.team.name, score.fixture.match.opponent),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(score.fixture.team.sport.label) + " · " +
                            (score.fixture.live?.let { stringResource(R.string.live_score, it.homeScore, it.awayScore) }
                                ?: score.fixture.match.scoreLabel()),
                        color = MatchStatsColors.OnNavyMuted,
                    )
                }
            }
            MatchStatsSecondaryButton(stringResource(R.string.box_config), onConfigure, Modifier.fillMaxWidth())
            if (score.fixture.live != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                    MatchStatsFilterChip(stringResource(R.string.live_home), R.drawable.ic_teams, !away, { away = false })
                    MatchStatsFilterChip(stringResource(R.string.live_away), R.drawable.ic_shield, away, { away = true })
                }
            }
            if (!finished && !away) Text(stringResource(R.string.live_box_readonly), style = MaterialTheme.typography.bodySmall)
            when {
                roster.isEmpty() -> MatchStatsEmptyState(
                    stringResource(R.string.box_empty),
                    stringResource(if (away) R.string.live_no_opponents else R.string.box_empty_roster),
                    R.drawable.ic_teams,
                )
                columns.isEmpty() -> MatchStatsEmptyState(
                    stringResource(R.string.box_no_columns), stringResource(R.string.box_no_columns_hint), R.drawable.ic_grid,
                )
                else -> {
                    if (!hasData) {
                        Text(stringResource(R.string.box_empty), style = MaterialTheme.typography.titleSmall)
                        if (canEdit) Text(stringResource(R.string.box_empty_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!canEdit && finished && !away) Text(stringResource(R.string.box_no_editable), style = MaterialTheme.typography.bodySmall)
                    if (horizontal.maxValue in 1 until Int.MAX_VALUE) {
                        Text(
                            stringResource(R.string.box_scroll_hint),
                            color = MatchStatsColors.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    BoxScoreTable(
                        roster, columns, canEdit, horizontal, onEdit,
                        Modifier.weight(1f, fill = false).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScoreTable(
    players: List<BoxPlayer>,
    columns: List<BoxMetric>,
    canEdit: Boolean,
    horizontal: ScrollState,
    onEdit: (Long) -> Unit,
    modifier: Modifier,
) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    val playerWidth = 144.dp * scale
    val widths = columns.map { metric ->
        (when {
            metric.builtin == BoxBuiltin.PASSES -> 108.dp
            metric.kind == BoxMetricKind.PAIR -> 80.dp
            else -> 67.dp
        }) * scale
    }
    val surface = MaterialTheme.colorScheme.surface
    val pinned = Modifier.offset { IntOffset(horizontal.value, 0) }.zIndex(1f)
    MatchStatsCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tableWidth = maxOf(maxWidth, playerWidth + widths.fold(0.dp) { total, width -> total + width })
            Column(Modifier.fillMaxWidth().horizontalScroll(horizontal).width(tableWidth)) {
                BoxTableRow(MatchStatsColors.SoftGreen) {
                    BoxPlayerCell(stringResource(R.string.box_player), null, playerWidth, pinned, MatchStatsColors.SoftGreen, true)
                    columns.forEachIndexed { index, metric ->
                        val title = metric.title()
                        BoxTextCell(metric.shortLabel(), widths[index], true, Modifier.semantics { contentDescription = title })
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    items(players, key = { it.player.id }) { player ->
                        BoxTableRow(
                            surface,
                            Modifier.clickable(enabled = canEdit) { onEdit(player.player.id) },
                        ) {
                            BoxPlayerCell(
                                player.player.name.ifBlank { stringResource(R.string.live_away_player) },
                                player.jersey?.let { stringResource(R.string.jersey_short, it) } ?: stringResource(R.string.box_missing),
                                playerWidth, pinned, surface,
                            )
                            columns.forEachIndexed { index, metric ->
                                BoxTextCell(metric.formattedCell(listOf(player)), widths[index], false)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                HorizontalDivider()
                BoxTableRow(MatchStatsColors.SoftGreen) {
                    BoxPlayerCell(
                        stringResource(R.string.box_team_total), null, playerWidth, pinned, MatchStatsColors.SoftGreen, true,
                    )
                    columns.forEachIndexed { index, metric ->
                        BoxTextCell(metric.formattedCell(players), widths[index], true)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxTableRow(color: Color, modifier: Modifier = Modifier, cells: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().background(color).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = cells,
    )
}

@Composable
private fun BoxPlayerCell(title: String, jersey: String?, width: Dp, pinned: Modifier, color: Color, emphasis: Boolean = false) {
    Column(
        pinned.width(width).heightIn(min = 48.dp).background(color).padding(MatchStatsSpacing.Small),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasis) MatchStatsColors.GreenInk else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (jersey != null) Text(jersey, style = MaterialTheme.typography.labelSmall, color = MatchStatsColors.Muted)
    }
}

@Composable
private fun BoxTextCell(text: String, width: Dp, emphasis: Boolean, modifier: Modifier = Modifier) {
    Text(
        text, modifier.width(width).padding(horizontal = MatchStatsSpacing.Tiny, vertical = MatchStatsSpacing.Small),
        textAlign = TextAlign.Center,
        style = if (emphasis) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        color = if (emphasis) MatchStatsColors.GreenInk else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun BoxMetric.formattedCell(players: List<BoxPlayer>): String {
    val cell = cell(players) ?: return stringResource(R.string.box_missing)
    val rate = cell.percentage?.let { value ->
        val formatted = NumberFormat.getNumberInstance(MatchLocale).apply { maximumFractionDigits = 1 }.format(value)
        stringResource(R.string.box_percent_value, formatted)
    }
    return when (kind) {
        BoxMetricKind.TOTAL -> cell.value.statNumber()
        BoxMetricKind.PERCENT -> rate ?: stringResource(R.string.box_missing)
        BoxMetricKind.PAIR -> {
            val value = cell.value.statNumber()
            val attempts = requireNotNull(cell.attempts).statNumber()
            if (builtin == BoxBuiltin.PASSES && rate != null) stringResource(R.string.box_pair_with_rate, value, attempts, rate)
            else stringResource(R.string.box_pair_value, value, attempts)
        }
    }
}

@Composable
internal fun EditBoxPlayerScreen(
    score: BoxScore,
    player: BoxPlayer,
    save: suspend (Map<String, BoxMeasurement?>) -> WriteResult,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val editable = score.config.visible.filter { it.kind != BoxMetricKind.PERCENT }
    var drafts by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    var clientError by rememberSaveable { mutableStateOf<WriteResult?>(null) }
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current
    BackHandler(enabled = action.running) {}

    fun change(key: String, value: String) {
        drafts = HashMap(drafts).apply { put(key, value) }
        clientError = null
        action.clearError()
    }

    MatchStatsScreen {
        SportsBanner(stringResource(R.string.box_edit_player), player.player.name)
        if (editable.isEmpty()) Text(stringResource(R.string.box_no_editable))
        else Text(stringResource(R.string.box_edit_hint), color = MatchStatsColors.Muted, style = MaterialTheme.typography.bodySmall)
        editable.forEach { metric ->
            val stored = player.measurement(metric)
            val value = drafts[metric.id] ?: stored?.value?.toString().orEmpty()
            val title = metric.title()
            if (metric.kind == BoxMetricKind.TOTAL) {
                MatchStatsTextField(
                    value, { change(metric.id, it) }, label = title, enabled = !action.running, keyboardOptions = boxNumberKeyboard,
                )
            } else {
                val attemptKey = metric.id + "_attempts"
                val attempts = drafts[attemptKey] ?: stored?.attempts?.toString().orEmpty()
                MatchStatsCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(MatchStatsSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                    ) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                            MatchStatsTextField(
                                value, { change(metric.id, it) }, modifier = Modifier.weight(1f), label = stringResource(R.string.box_made),
                                enabled = !action.running, keyboardOptions = boxNumberKeyboard,
                            )
                            MatchStatsTextField(
                                attempts, { change(attemptKey, it) }, modifier = Modifier.weight(1f),
                                label = stringResource(R.string.box_attempted), enabled = !action.running, keyboardOptions = boxNumberKeyboard,
                            )
                        }
                    }
                }
            }
        }
        RecordError(clientError)
        FormButtons(
            action,
            onSave = {
                focus.clearFocus()
                val updates = mutableMapOf<String, BoxMeasurement?>()
                for (metric in editable) {
                    val attemptsKey = metric.id + "_attempts"
                    if (metric.id !in drafts && attemptsKey !in drafts) continue
                    val original = player.measurement(metric)
                    val value = drafts[metric.id] ?: original?.value?.toString().orEmpty()
                    val attempts = drafts[attemptsKey] ?: original?.attempts?.toString().orEmpty()
                    if (value.isBlank() && attempts.isBlank()) {
                        updates[metric.id] = null
                        continue
                    }
                    val parsed = value.nonNegativeInt()?.let { BoxMeasurement(it, attempts.nonNegativeInt()) }
                    if (parsed == null || !parsed.validFor(metric.kind)) {
                        clientError = if (metric.kind == BoxMetricKind.PAIR) WriteResult.INVALID_BOX_PAIR else WriteResult.INVALID_STATS
                        break
                    }
                    updates[metric.id] = parsed
                }
                if (clientError == null) action.launch({ save(updates) }, onSaved)
            },
            onCancel = onCancel,
        )
    }
}

private val boxNumberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
