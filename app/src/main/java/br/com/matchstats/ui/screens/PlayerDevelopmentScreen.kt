package br.com.matchstats.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.DEVELOPMENT_ALL_TEAMS
import br.com.matchstats.data.DEVELOPMENT_UNASSIGNED
import br.com.matchstats.data.DevelopmentMetric
import br.com.matchstats.data.DevelopmentPeriod
import br.com.matchstats.data.DevelopmentPoint
import br.com.matchstats.data.DevelopmentSeries
import br.com.matchstats.data.DevelopmentValue
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.Sport
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.NavyBanner
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.RecordUnavailable
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportSelector
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private val developmentLocale = Locale.forLanguageTag("pt-BR")
private val shortDateFormat = DateTimeFormatter.ofPattern("dd/MM", developmentLocale)
private val fullDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", developmentLocale)

@Composable
internal fun PlayerDevelopmentScreen(
    repository: MatchStatsRepository,
    playerId: Long,
    initialSport: Sport,
    initialTeam: Long,
    initialPractice: Boolean,
    onBack: () -> Unit,
) {
    var sport by rememberSaveable { mutableStateOf(initialSport) }
    var practices by rememberSaveable { mutableStateOf(initialPractice) }
    var teamId by rememberSaveable { mutableLongStateOf(initialTeam) }
    var period by rememberSaveable { mutableStateOf(DevelopmentPeriod.ALL) }
    var drillKey by rememberSaveable { mutableStateOf("") }
    var metricKey by rememberSaveable { mutableStateOf("") }
    val matchLabel = stringResource(R.string.development_versus)
    val feed = remember(repository, playerId, sport) { repository.observeDevelopment(playerId, sport) }

    ObserveRecords(feed) { data ->
        if (data == null) {
            RecordUnavailable(onBack)
            return@ObserveRecords
        }
        val allTeams = stringResource(R.string.stats_all_teams)
        val unassigned = stringResource(R.string.development_no_team)
        val teamNames = data.teams.associate { it.id to it.name }
        val teams = buildMap {
            put(DEVELOPMENT_ALL_TEAMS, allTeams)
            putAll(teamNames)
            if (practices && data.practices.any { it.teamId == null }) put(DEVELOPMENT_UNASSIGNED, unassigned)
        }
        val team = teamId.takeIf(teams::containsKey) ?: DEVELOPMENT_ALL_TEAMS
        val drillOptions = remember(data, team) { data.drills(team) }
        val drill = drillOptions.find { it.key == drillKey }
        val drillNames = mapOf("" to stringResource(R.string.development_all_drills)) + drillOptions.associate { option ->
            val name = if (drillOptions.count { it.name.equals(option.name, true) } > 1) {
                val measure = if (option.measure == br.com.matchstats.data.PracticeMeasure.COUNT) {
                    stringResource(R.string.practice_measure_count)
                } else stringResource(R.string.practice_measure_pair)
                stringResource(R.string.practice_scope, option.name, measure)
            } else option.name
            option.key to name
        }
        val metrics = remember(data, practices, drill) { if (practices) data.practiceMetrics(drill) else data.gameMetrics() }
        val names = metrics.associate { it.id to it.label() }
        val metric = metrics.find { it.id == metricKey } ?: metrics.first()
        val metricName = names.getValue(metric.id)
        val bounds = period.bounds(LocalDate.now(), ZoneId.systemDefault())
        val series = remember(data, practices, metric, team, bounds, drill, matchLabel) {
            if (practices) data.practiceSeries(metric, team, bounds, drill)
            else data.gameSeries(metric, team, bounds) { left, right ->
                String.format(developmentLocale, matchLabel, left, right)
            }
        }
        var selectedId by rememberSaveable(playerId, sport, practices, metric.id, team, period, drill?.key) {
            mutableStateOf<Long?>(null)
        }
        val selected = series.points.find { it.id == selectedId } ?: series.points.lastOrNull()

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = MatchStatsSpacing.ContentMax).fillMaxSize().testTag("development-screen"),
                contentPadding = PaddingValues(MatchStatsSpacing.Screen),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
            ) {
                item {
                    NavyBanner {
                        Column(Modifier.fillMaxWidth(.86f).padding(MatchStatsSpacing.Medium)) {
                            Text(data.player.name, style = MaterialTheme.typography.headlineSmall)
                            Text(teams.getValue(team), color = MatchStatsColors.OnNavyMuted)
                        }
                    }
                }
                item {
                    SportSelector(sport, {
                        sport = it
                        teamId = DEVELOPMENT_ALL_TEAMS
                        drillKey = ""
                        metricKey = ""
                    }, enabled = true)
                }
                item {
                    PracticeContextTabs(practices, R.string.player_games) {
                        if (practices != it) {
                            practices = it
                            metricKey = ""
                            if (teamId == DEVELOPMENT_UNASSIGNED) teamId = DEVELOPMENT_ALL_TEAMS
                        }
                    }
                }
                item {
                    MatchStatsDropdown(stringResource(R.string.stats_team_filter), team, teams.keys.toList(),
                        { teams.getValue(it) }, { teamId = it; drillKey = "" })
                }
                if (practices) item {
                    MatchStatsDropdown(stringResource(R.string.practice_drill), drill?.key.orEmpty(),
                        drillNames.keys.toList(), { drillNames.getValue(it) }, { drillKey = it })
                }
                item {
                    MatchStatsDropdown(stringResource(R.string.development_metric), metric.id, metrics.map { it.id },
                        { names.getValue(it) }, { metricKey = it })
                }
                item {
                    val periods = DevelopmentPeriod.entries.associateWith { stringResource(it.label) }
                    MatchStatsDropdown(stringResource(R.string.development_range), period, DevelopmentPeriod.entries,
                        { periods.getValue(it) }, { period = it })
                }
                if (selected == null) {
                    item {
                        MatchStatsEmptyState(stringResource(R.string.development_no_data),
                            stringResource(R.string.development_no_data_hint), R.drawable.ic_stats)
                    }
                } else {
                    item {
                        DevelopmentSummary(series, metric)
                    }
                    item {
                        DevelopmentChart(metricName, series.points, metric.value == DevelopmentValue.RATE, selected) {
                            selectedId = it
                        }
                    }
                    item { SectionHeading(stringResource(R.string.development_history), R.drawable.ic_history) }
                    items(series.points, key = { it.id }) { point ->
                        val active = point.id == selected.id
                        MatchStatsCard(Modifier.fillMaxWidth().clickable { selectedId = point.id },
                            containerColor = if (active) MatchStatsColors.SoftGreen else MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(point.label, style = MaterialTheme.typography.titleSmall)
                                val date = point.date(fullDateFormat)
                                val label = teamNames[point.teamId]?.let { stringResource(R.string.practice_scope, date, it) } ?: date
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MatchStatsColors.Muted)
                                Text(point.displayValue(), color = MatchStatsColors.GreenInk)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevelopmentMetric.label(): String = titleRes?.let { stringResource(it) } ?: title.orEmpty()

private fun DevelopmentPoint.date(formatter: DateTimeFormatter): String =
    Instant.ofEpochMilli(startsAt).atZone(ZoneId.systemDefault()).format(formatter)

private fun Long.localized(): String = NumberFormat.getIntegerInstance(developmentLocale).format(this)
private fun Double.localized(): String =
    NumberFormat.getNumberInstance(developmentLocale).apply { maximumFractionDigits = 1 }.format(this)

@Composable
private fun Double?.percentageText(): String = this?.let { stringResource(R.string.box_percent_value, it.localized()) }
    ?: stringResource(R.string.box_missing)

@Composable
private fun DevelopmentPoint.displayValue(): String {
    val attempts = measure.attempts ?: return measure.value.localized()
    return stringResource(R.string.development_pair_detail, measure.value.localized(), attempts.localized(),
        measure.percentage.percentageText())
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DevelopmentSummary(series: DevelopmentSeries, metric: DevelopmentMetric) {
    val rate = metric.value == DevelopmentValue.RATE
    val total = if (rate) stringResource(R.string.box_pair_value, series.total.localized(), series.attempts.localized())
        else series.total.localized()
    val items = buildList {
        add(R.string.development_total to total)
        add(R.string.development_observations to series.points.size.toLong().localized())
        if (rate) add(R.string.development_accuracy to series.accuracy.percentageText())
        else if (metric.value != DevelopmentValue.SESSIONS) {
            add(R.string.development_average to (series.average?.localized() ?: stringResource(R.string.box_missing)))
        }
    }
    val large = LocalDensity.current.fontScale > 1.35f
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = if (large) 1 else 3,
    ) {
        items.forEach { (label, value) ->
            MatchStatsCard(Modifier.weight(1f).fillMaxRowHeight()) {
                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                    Text(value, color = MatchStatsColors.GreenInk, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun DevelopmentChart(
    metric: String,
    points: List<DevelopmentPoint>,
    rate: Boolean,
    selected: DevelopmentPoint,
    onSelect: (Long) -> Unit,
) {
    val values = remember(points, rate) { points.map { it.plotted(rate) } }
    val limit = if (rate) 100.0 else (ceil((values.filterNotNull().maxOrNull() ?: 0.0) / 4) * 4).coerceAtLeast(4.0)
    val description = stringResource(R.string.development_chart_accessibility, metric)
    MatchStatsCard(Modifier.fillMaxWidth().testTag("development-chart-card")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(metric, color = MatchStatsColors.Navy, style = MaterialTheme.typography.titleLarge)
            if (values.any { it != null }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.height(184.dp).widthIn(min = 32.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        (4 downTo 0).forEach { step ->
                            val level = limit * step / 4
                            Text(if (rate) level.percentageText() else level.localized(),
                                style = MaterialTheme.typography.labelSmall, color = MatchStatsColors.Muted)
                        }
                    }
                    Canvas(Modifier.weight(1f).height(184.dp).testTag("development-chart")
                        .semantics { contentDescription = description }
                        .pointerInput(points) {
                            detectTapGestures { event ->
                                val padding = 7.dp.toPx()
                                val span = (size.width - padding * 2).coerceAtLeast(1f)
                                val location = ((event.x - padding) / span).coerceIn(0f, 1f)
                                val preferred = (location * points.lastIndex).roundToInt()
                                onSelect(points[preferred].id)
                            }
                        }) {
                        val inset = 7.dp.toPx()
                        val height = size.height - inset * 2
                        val width = size.width - inset * 2
                        fun position(index: Int, value: Double) = Offset(
                            x = if (points.size == 1) size.width / 2 else inset + width * index / points.lastIndex,
                            y = inset + height * (1 - value / limit).toFloat(),
                        )
                        for (line in 0..4) {
                            val y = inset + height * line / 4
                            drawLine(MatchStatsColors.Border, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                        }
                        var previous: Offset? = null
                        values.forEachIndexed { index, value ->
                            val next = value?.let { position(index, it) }
                            if (next != null) {
                                previous?.let { drawLine(MatchStatsColors.Green, it, next, 2.dp.toPx()) }
                                if (points[index].id == selected.id) {
                                    drawCircle(MatchStatsColors.SoftGreen, 7.dp.toPx(), next)
                                    drawCircle(MatchStatsColors.GreenInk, 4.dp.toPx(), next)
                                } else {
                                    drawCircle(MatchStatsColors.Green, if (points.size > 40) 1.8.dp.toPx() else 3.dp.toPx(), next)
                                }
                            }
                            previous = next
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().date(shortDateFormat), style = MaterialTheme.typography.labelSmall)
                    if (points.size > 1) Text(points.last().date(shortDateFormat), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(stringResource(R.string.development_no_values), color = MatchStatsColors.Muted)
            }
            MatchStatsCard(Modifier.fillMaxWidth().testTag("development-selection"), containerColor = MatchStatsColors.SoftGreen) {
                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(selected.label, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.practice_scope, selected.date(fullDateFormat), selected.displayValue()),
                        color = MatchStatsColors.GreenInk)
                }
            }
        }
    }
}
