package br.com.matchstats.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.Match
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.MatchWithTeam
import br.com.matchstats.ui.components.IconTile
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val MatchLocale: Locale = Locale.forLanguageTag("pt-BR")
internal val MatchDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", MatchLocale)
internal val MatchTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", MatchLocale)
private val fixtureDate = DateTimeFormatter.ofPattern("EEE, dd 'de' MMM 'de' uuuu · HH:mm", MatchLocale)

internal fun Match.localStart(): LocalDateTime = Instant.ofEpochMilli(startsAt).atZone(ZoneId.systemDefault()).toLocalDateTime()

@Composable
internal fun Match.scoreLabel(): String {
    val home = teamScore
    val away = opponentScore
    return if (home != null && away != null) stringResource(R.string.scored, home, away)
    else stringResource(R.string.match_pending_marker)
}

@Composable
internal fun MatchCard(fixture: MatchWithTeam, onClick: (() -> Unit)? = null) {
    val match = fixture.match
    val team = fixture.team
    val details = stringResource(R.string.view_details)
    val click = if (onClick == null) Modifier else Modifier.clickable(onClickLabel = details, onClick = onClick)
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().then(click).padding(MatchStatsSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(team.sport.icon)
                Column(Modifier.weight(1f)) {
                    Text(match.localStart().format(fixtureDate), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(team.sport.label), color = MatchStatsColors.Muted, style = MaterialTheme.typography.bodySmall)
                    match.location?.let {
                        Text(it, color = MatchStatsColors.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                val live = fixture.live != null && match.status == MatchStatus.SCHEDULED
                MatchStatusLabel(if (live) R.string.live_active_game else match.status.label, Modifier.weight(0.55f, fill = false))
            }
            MatchScoreboard(fixture)
        }
    }
}

@Composable
private fun MatchStatusLabel(label: Int, modifier: Modifier) {
    Text(
        stringResource(label),
        modifier = modifier.background(MatchStatsColors.SoftGreen, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = MatchStatsSpacing.Small, vertical = MatchStatsSpacing.Tiny),
        color = MatchStatsColors.GreenInk,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MatchScoreboard(fixture: MatchWithTeam) {
    val live = fixture.live?.takeIf { fixture.match.status == MatchStatus.SCHEDULED }
    val score = if (live == null) fixture.match.scoreLabel()
    else stringResource(R.string.live_score, live.homeScore, live.awayScore)
    val finished = fixture.match.status == MatchStatus.FINISHED || live != null
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 285.dp || LocalDensity.current.fontScale > 1.35f || score.length > 12) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                Text(fixture.team.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                ScoreText(score, finished)
                Text(fixture.match.opponent, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Participant(fixture.team.name, Modifier.weight(1f)) { TeamBadge(fixture.team.sport, 37.dp) }
                ScoreText(score, finished, Modifier.weight(0.72f, fill = false))
                Participant(fixture.match.opponent, Modifier.weight(1f)) { IconTile(R.drawable.ic_shield, size = 34.dp) }
            }
        }
    }
}

@Composable
private fun Participant(name: String, modifier: Modifier, badge: @Composable () -> Unit) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
    ) {
        badge()
        Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScoreText(text: String, isFinished: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = if (isFinished) modifier.background(MatchStatsColors.SoftGreen, MaterialTheme.shapes.small)
            .padding(horizontal = MatchStatsSpacing.Small, vertical = MatchStatsSpacing.Tiny) else modifier,
        color = if (isFinished) MatchStatsColors.GreenInk else MatchStatsColors.Muted,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
    )
}
