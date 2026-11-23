package br.com.matchstats.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.matchstats.R
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.PracticeFact
import br.com.matchstats.data.PracticeMeasure
import br.com.matchstats.data.PracticePerformance
import br.com.matchstats.data.PracticeStore
import br.com.matchstats.data.Sport
import br.com.matchstats.data.forPlayerSearch
import br.com.matchstats.data.practicePerformance
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.ObserveRecords
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportSelector
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.theme.MatchStatsColors

private data class DrillFilter(val name: String, val measure: PracticeMeasure) {
    val key get() = "${measure.name}:${name.forPlayerSearch()}"
}
private data class PracticeRankingRow(val id: Long, val name: String, val performance: PracticePerformance)

@Composable
internal fun PlayerPracticeStatistics(playerId: Long, store: PracticeStore, onPractice: (Long) -> Unit) {
    var sport by rememberSaveable { mutableStateOf(Sport.FOOTBALL) }
    SportSelector(sport, { sport = it }, enabled = true)
    val flow = remember(store, sport, playerId) { store.facts(sport, playerId = playerId) }
    key(flow) {
        val records by flow.collectAsStateWithLifecycle(initialValue = null)
        val facts = records
        when {
            facts == null -> Text(stringResource(R.string.loading), color = MatchStatsColors.Muted)
            facts.isEmpty() -> PracticeStatsEmpty()
            else -> {
                val performance = facts.practicePerformance()
                PracticeStatsCard {
                    Text(stringResource(R.string.practice_attendance, performance.sessions))
                    Text(stringResource(R.string.practice_drills_total, performance.drills))
                    PracticePerformanceValues(performance, null)
                }
                val drills = facts.filter { it.drillName != null && it.measure != null }
                    .groupBy { DrillFilter(requireNotNull(it.drillName), requireNotNull(it.measure)).key }
                if (drills.isNotEmpty()) SectionHeading(stringResource(R.string.practice_by_drill), R.drawable.ic_stats)
                drills.values.forEach { drillFacts ->
                    val first = drillFacts.first()
                    PracticeStatsCard {
                        Text(first.drillName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        PracticePerformanceValues(drillFacts.practicePerformance(), first.measure)
                    }
                }
                SectionHeading(stringResource(R.string.practice_individual_history), R.drawable.ic_history)
                facts.groupBy { it.practiceId }.values.forEach { history ->
                    val first = history.first()
                    PracticeStatsCard(Modifier.clickable { onPractice(first.practiceId) }) {
                        Text(first.title, style = MaterialTheme.typography.titleMedium)
                        Text(practiceDateTime(first.startsAt), color = MatchStatsColors.Muted)
                        Text(stringResource(R.string.practice_drills_total, history.practicePerformance().drills))
                    }
                }
            }
        }
    }
}

@Composable
internal fun PracticeStatisticsScreen(
    repository: MatchStatsRepository,
    onDevelopment: (Long, Sport, Long?, Boolean) -> Unit,
    onContext: (Boolean) -> Unit,
) {
    var sport by rememberSaveable { mutableStateOf(Sport.FOOTBALL) }
    var teamId by rememberSaveable { mutableStateOf(0L) }
    var drillKey by rememberSaveable { mutableStateOf("") }
    val teamsFlow = remember(repository) { repository.observeTeams() }
    ObserveRecords(teamsFlow) { teams ->
        val allTeams = stringResource(R.string.stats_all_teams)
        val eligible = teams.filter { it.sport == sport }
        val teamLabels = eligible.associate { team ->
            team.id to if (eligible.count { it.name.equals(team.name, ignoreCase = true) } == 1) team.name
            else stringResource(R.string.stats_duplicate_team_option, team.name, team.id)
        } + (0L to allTeams)
        val selectedTeam = teamId.takeIf { it in teamLabels } ?: 0L
        val factsFlow = remember(repository, sport, selectedTeam) {
            repository.practices.facts(sport, teamId = selectedTeam.takeUnless { it == 0L })
        }
        key(factsFlow) {
            val records by factsFlow.collectAsStateWithLifecycle(initialValue = null)
            MatchStatsScreen {
                SportsBanner(stringResource(R.string.nav_stats), stringResource(R.string.practice_intro))
                PracticeContextTabs(true, R.string.player_games, onContext)
                SportSelector(sport, { if (sport != it) { sport = it; teamId = 0; drillKey = "" } }, enabled = true)
                MatchStatsDropdown(stringResource(R.string.stats_team_filter), selectedTeam,
                    teamLabels.keys.sortedBy { it != 0L }, { teamLabels.getValue(it) }, { teamId = it; drillKey = "" })
                val facts = records
                if (facts == null) {
                    Text(stringResource(R.string.loading), color = MatchStatsColors.Muted)
                } else {
                    val drills = facts.mapNotNull { fact ->
                        fact.drillName?.let { name -> fact.measure?.let { DrillFilter(name, it) } }
                    }.distinctBy { it.key }.sortedBy { it.name.forPlayerSearch() }
                    val selected = drills.find { it.key == drillKey }
                    val all = stringResource(R.string.practice_all_drills)
                    val count = stringResource(R.string.practice_measure_count)
                    val pair = stringResource(R.string.practice_measure_pair)
                    val drillLabels = drills.associate { drill ->
                        drill.key to if (drills.count { it.name.equals(drill.name, true) } == 1) drill.name
                        else stringResource(R.string.practice_scope, drill.name, if (drill.measure == PracticeMeasure.COUNT) count else pair)
                    } + ("" to all)
                    MatchStatsDropdown(stringResource(R.string.practice_drill), selected?.key.orEmpty(),
                        listOf("") + drills.map { it.key }, { drillLabels.getValue(it) }, { drillKey = it })
                    val measured = facts.filter { fact ->
                        val name = fact.drillName
                        val measure = fact.measure
                        name != null && measure != null && (selected == null || DrillFilter(name, measure).key == selected.key)
                    }
                    SectionHeading(stringResource(R.string.practice_rankings), R.drawable.ic_trophy)
                    if (measured.isEmpty()) PracticeStatsEmpty() else {
                        val order = when (selected?.measure) {
                            PracticeMeasure.COUNT -> compareByDescending<PracticeRankingRow> { it.performance.count }
                            PracticeMeasure.MAKES_AND_ATTEMPTS -> compareByDescending<PracticeRankingRow> { it.performance.accuracy }
                                .thenByDescending { it.performance.attempts }
                            null -> compareByDescending<PracticeRankingRow> { it.performance.drills }.thenByDescending { it.performance.sessions }
                        }.thenBy { it.name.forPlayerSearch() }.thenBy { it.id }
                        val rankings = measured.groupBy { it.playerId }.map { (id, rows) ->
                            PracticeRankingRow(id, rows.first().playerName, rows.practicePerformance())
                        }.sortedWith(order)
                        rankings.forEachIndexed { index, row ->
                            PracticeStatsCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.practice_rank, index + 1, row.name),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    IconButton(onClick = {
                                        onDevelopment(row.id, sport, selectedTeam.takeUnless { it == 0L }, true)
                                    }) {
                                        MatchStatsIcon(
                                            R.drawable.ic_chevron,
                                            tint = MatchStatsColors.GreenInk,
                                            description = stringResource(R.string.development_show_player, row.name),
                                        )
                                    }
                                }
                                if (selected == null) Text(stringResource(R.string.practice_drills_total, row.performance.drills))
                                PracticePerformanceValues(row.performance, selected?.measure)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeStatsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    MatchStatsCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun PracticePerformanceValues(values: PracticePerformance, measure: PracticeMeasure?) {
    if (measure != PracticeMeasure.COUNT) Text(
        stringResource(R.string.practice_pair_total, values.makes, values.attempts, practicePercent(values.accuracy)),
        color = MatchStatsColors.GreenInk,
    )
    if (measure != PracticeMeasure.MAKES_AND_ATTEMPTS) Text(
        stringResource(R.string.practice_count_total, values.count), color = MatchStatsColors.GreenInk,
    )
}

@Composable
private fun PracticeStatsEmpty() {
    MatchStatsEmptyState(stringResource(R.string.practice_no_facts), stringResource(R.string.practice_facts_hint), R.drawable.ic_stats)
}
