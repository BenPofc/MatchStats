package br.com.matchstats.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import br.com.matchstats.R
import br.com.matchstats.data.PerformanceSummary
import br.com.matchstats.data.Sport
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.text.NumberFormat

internal fun Long.statNumber(): String = NumberFormat.getIntegerInstance(MatchLocale).format(this)

@Composable
internal fun PerformanceCard(performance: PerformanceSummary, sport: Sport, title: String) {
    val football = sport == Sport.FOOTBALL
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(MatchStatsSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            SectionHeading(title, R.drawable.ic_trophy)
            Row(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                PerformanceValue(R.string.stats_completed_matches, performance.completed.toLong().statNumber(), Modifier.weight(1f))
                PerformanceValue(R.string.stats_win_rate, stringResource(R.string.stats_win_rate_value, performance.winPercentage), Modifier.weight(1f))
            }
            HorizontalDivider(color = MatchStatsColors.Border)
            Row(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                PerformanceValue(R.string.stats_wins, performance.wins.toLong().statNumber(), Modifier.weight(1f))
                if (football || performance.draws > 0) {
                    PerformanceValue(R.string.stats_draws, performance.draws.toLong().statNumber(), Modifier.weight(1f))
                }
                PerformanceValue(R.string.stats_losses, performance.losses.toLong().statNumber(), Modifier.weight(1f))
            }
            HorizontalDivider(color = MatchStatsColors.Border)
            Row(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                PerformanceValue(
                    if (football) R.string.stats_goals_scored else R.string.stats_points_scored,
                    performance.scored.statNumber(), Modifier.weight(1f),
                )
                PerformanceValue(
                    if (football) R.string.stats_goals_conceded else R.string.stats_points_conceded,
                    performance.conceded.statNumber(), Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PerformanceValue(label: Int, value: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MatchStatsColors.Muted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = MatchStatsColors.GreenInk)
    }
}
