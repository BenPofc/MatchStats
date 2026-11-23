package br.com.matchstats.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.LiveAction
import br.com.matchstats.data.LiveActionInfo
import br.com.matchstats.data.LiveClock
import br.com.matchstats.data.LiveGame
import br.com.matchstats.data.LiveGameStore
import br.com.matchstats.data.LiveSession
import br.com.matchstats.data.LiveSide
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.Sport
import br.com.matchstats.data.WriteResult
import br.com.matchstats.data.nonNegativeInt
import br.com.matchstats.ui.components.FormButtons
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.NavyBanner
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.util.UUID
import kotlinx.coroutines.delay

internal fun liveRequestId(): String = UUID.randomUUID().toString()

@Composable
internal fun LiveActionInfo.label(): String = labelRes?.let { stringResource(it) } ?: customName.orEmpty()

@Composable
internal fun LiveActionInfo.short(): String = shortRes?.let { stringResource(it) } ?: customShort.orEmpty()

@Composable
internal fun liveTime(ms: Long, countdown: Boolean): String {
    val seconds = if (countdown) (ms + 999) / 1000 else ms / 1000
    return stringResource(R.string.live_clock_format, seconds / 60, seconds % 60)
}

@Composable
internal fun livePeriod(sport: Sport, period: Int) =
    stringResource(if (sport == Sport.BASKETBALL) R.string.live_period else R.string.live_half, period)

private enum class LiveConfirmation { NEXT, FINISH, DISCARD }

@Composable
internal fun LiveGameScreen(
    game: LiveGame,
    tracking: LiveGameStore,
    onBox: () -> Unit,
    onConfigure: () -> Unit,
    onHistory: () -> Unit,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val id = game.fixture.match.id
    val sport = game.fixture.team.sport
    val session = game.session
    if (session == null) {
        LiveSetupScreen(game, { minutes, shot -> tracking.start(id, minutes, shot) }, onBack)
        return
    }
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current
    val latest by rememberUpdatedState(session)
    val finished = game.fixture.match.status == MatchStatus.FINISHED
    val clock by produceState(tracking.clock(session), session, finished) {
        value = tracking.clock(session)
        while (!finished && value.running) {
            delay(180)
            value = tracking.clock(session)
        }
    }
    LaunchedEffect(id, finished, tracking.clock(session).running) {
        while (!finished && tracking.clock(latest).running) {
            delay(15_000)
            tracking.checkpoint(id)
        }
    }
    var picked by rememberSaveable { mutableStateOf(LiveSelection()) }
    var confirmation by rememberSaveable { mutableStateOf<LiveConfirmation?>(null) }
    val identified = picked.identified(game)
    val basketball = sport == Sport.BASKETBALL
    val next = if (basketball) R.string.live_next_period else R.string.live_next_half
    val runLabel = when {
        clock.running -> R.string.live_pause_clock
        clock.gameMs < session.periodSeconds * 1000L -> R.string.live_resume_clock
        else -> R.string.live_start_clock
    }
    BackHandler(enabled = action.running) {}

    MatchStatsScreen {
        LiveScoreboard(game, session, clock, finished)
        if (!finished) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
            ) {
                MatchStatsButton(
                    stringResource(runLabel), { action.launch({ tracking.setRunning(id, !clock.running) }) {} },
                    Modifier.weight(1f), enabled = !action.running && clock.gameMs > 0L,
                )
                if (basketball || session.period == 1) {
                    MatchStatsSecondaryButton(stringResource(next), {
                        if (clock.gameMs == 0L) action.launch({ tracking.nextPeriod(id) }) {}
                        else confirmation = LiveConfirmation.NEXT
                    }, Modifier.weight(1f), enabled = !action.running)
                }
                if (basketball) {
                    MatchStatsSecondaryButton(
                        stringResource(R.string.live_shot_reset, session.shotSeconds),
                        { action.launch({ tracking.resetShot(id, false) }) {} },
                        Modifier.weight(1f), enabled = !action.running,
                    )
                    if (session.shotSeconds != 14) {
                        MatchStatsSecondaryButton(
                            stringResource(R.string.live_shot_reset, 14),
                            { action.launch({ tracking.resetShot(id, true) }) {} },
                            Modifier.weight(1f), enabled = !action.running,
                        )
                    }
                }
            }
            LivePlayerPicker(game, picked) { picked = it; action.clearError() }
            SectionHeading(stringResource(R.string.live_actions), R.drawable.ic_bolt)
            MatchStatsSecondaryButton(
                stringResource(R.string.live_config), onConfigure, Modifier.fillMaxWidth(), enabled = !action.running,
            )
            if (game.config.visible.isEmpty()) {
                Text(stringResource(R.string.live_empty_actions), color = MatchStatsColors.Muted)
            } else {
                if (basketball && !identified) {
                    Text(stringResource(R.string.live_playing_hint), style = MaterialTheme.typography.bodySmall)
                }
                LiveActionGrid(
                    game.config.visible,
                    enabled = { !action.running && (identified || !basketball && it.customName == null) },
                    select = { info ->
                        focus.clearFocus()
                        val request = liveRequestId()
                        val input = picked.input(info.id)
                        action.launch({ tracking.record(id, request, input) }) {}
                    },
                )
            }
            RecordError(action.error)
        }
        LiveRecentEvents(game)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            MatchStatsSecondaryButton(stringResource(R.string.live_history), onHistory, Modifier.weight(1f))
            MatchStatsSecondaryButton(stringResource(R.string.box_score), onBox, Modifier.weight(1f))
        }
        if (!finished) {
            MatchStatsButton(
                stringResource(R.string.live_finish), { confirmation = LiveConfirmation.FINISH },
                Modifier.fillMaxWidth(), enabled = !action.running,
            )
            MatchStatsSecondaryButton(
                stringResource(R.string.live_discard), { confirmation = LiveConfirmation.DISCARD },
                Modifier.fillMaxWidth(), enabled = !action.running, destructive = true,
            )
        }
    }
    confirmation?.let { operation ->
        val title = when (operation) {
            LiveConfirmation.NEXT -> stringResource(next)
            LiveConfirmation.FINISH -> stringResource(R.string.live_finish)
            LiveConfirmation.DISCARD -> stringResource(R.string.live_discard)
        }
        val message = when (operation) {
            LiveConfirmation.NEXT -> stringResource(R.string.live_next_confirm)
            LiveConfirmation.FINISH -> stringResource(R.string.live_finish_confirm, session.homeScore, session.awayScore)
            LiveConfirmation.DISCARD -> stringResource(R.string.live_discard_confirm)
        }
        LiveConfirmationDialog(
            title = title, message = message,
            confirm = if (operation == LiveConfirmation.NEXT) stringResource(R.string.live_continue) else title,
            destructive = operation == LiveConfirmation.DISCARD,
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                when (operation) {
                    LiveConfirmation.NEXT -> action.launch({ tracking.nextPeriod(id) }) {}
                    LiveConfirmation.FINISH -> action.launch({ tracking.finish(id) }, onFinished)
                    LiveConfirmation.DISCARD -> action.launch({ tracking.discard(id) }, onBack)
                }
            },
        )
    }
}

@Composable
private fun LiveSetupScreen(game: LiveGame, start: suspend (Int, Int) -> WriteResult, onBack: () -> Unit) {
    val basketball = game.fixture.team.sport == Sport.BASKETBALL
    var minutes by rememberSaveable { mutableStateOf(if (basketball) "10" else "45") }
    var shot by rememberSaveable { mutableStateOf("24") }
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current
    BackHandler(enabled = action.running) {}
    MatchStatsScreen {
        SportsBanner(
            stringResource(R.string.live_start_session),
            stringResource(R.string.match_stats_scope, game.fixture.team.name, game.fixture.match.opponent),
        )
        MatchStatsTextField(
            minutes, { minutes = it; action.clearError() },
            label = stringResource(if (basketball) R.string.live_period_minutes else R.string.live_half_minutes),
            keyboardOptions = liveNumberKeyboard, enabled = !action.running,
        )
        if (basketball) MatchStatsTextField(
            shot, { shot = it; action.clearError() }, label = stringResource(R.string.live_shot_seconds),
            keyboardOptions = liveNumberKeyboard, enabled = !action.running,
        )
        RecordError(action.error)
        MatchStatsButton(
            stringResource(R.string.live_start_confirm), {
                focus.clearFocus()
                action.launch({ start(minutes.nonNegativeInt() ?: 0, shot.nonNegativeInt() ?: 0) }) {}
            }, Modifier.fillMaxWidth(), enabled = !action.running,
        )
        MatchStatsSecondaryButton(stringResource(R.string.cancel), onBack, Modifier.fillMaxWidth(), enabled = !action.running)
    }
}

@Composable
private fun LiveScoreboard(game: LiveGame, session: LiveSession, clock: LiveClock, finished: Boolean) {
    val sport = game.fixture.team.sport
    val basketball = sport == Sport.BASKETBALL
    val inputs = if (basketball) game.activeEvents.filter { it.period == session.period && it.actionId == LiveAction.BASKET_FOUL.id }
    else emptyList()
    val status = when {
        finished -> R.string.match_finished
        clock.running -> R.string.live_running
        clock.gameMs == 0L -> R.string.live_end_period
        else -> R.string.live_paused
    }
    NavyBanner {
        Column(
            Modifier.fillMaxWidth().padding(MatchStatsSpacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            Text(
                livePeriod(sport, session.period) + " · " + stringResource(status),
                style = MaterialTheme.typography.labelLarge,
                color = MatchStatsColors.OnNavyMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                LiveScoreTile(game.fixture.team.name, session.homeScore, inputs.count { it.side == LiveSide.HOME }.takeIf { basketball }, Modifier.weight(1f))
                LiveScoreTile(game.fixture.match.opponent, session.awayScore, inputs.count { it.side == LiveSide.AWAY }.takeIf { basketball }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        liveTime(clock.position(session, sport), basketball), color = Color(0xFF9AFAB1),
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Text(stringResource(R.string.live_game_clock), style = MaterialTheme.typography.labelSmall, color = MatchStatsColors.OnNavyMuted)
                }
                if (basketball) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.live_seconds, (clock.shotMs + 999) / 1000),
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFFFFBEA7),
                        )
                        Text(stringResource(R.string.live_shot_clock), style = MaterialTheme.typography.labelSmall, color = MatchStatsColors.OnNavyMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveScoreTile(name: String, score: Int, fouls: Int?, modifier: Modifier) {
    Column(
        modifier.background(Color.Black.copy(alpha = 0.25f), MaterialTheme.shapes.medium).padding(MatchStatsSpacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            score.toLong().statNumber(), color = MatchStatsColors.White,
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
        )
        if (fouls != null) Text(stringResource(R.string.live_fouls, fouls), color = MatchStatsColors.OnNavyMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LiveActionGrid(actions: List<LiveActionInfo>, enabled: (LiveActionInfo) -> Boolean, select: (LiveActionInfo) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 315.dp || LocalDensity.current.fontScale > 1.35f) 2 else 3
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            for (item in actions) {
                val interactive = enabled(item)
                val title = item.label()
                Surface(
                    onClick = { select(item) }, enabled = interactive,
                    shape = MaterialTheme.shapes.medium,
                    color = if (interactive) MatchStatsColors.SoftGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (interactive) MatchStatsColors.GreenInk else MatchStatsColors.Muted,
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                ) {
                    Column(
                        Modifier.padding(horizontal = MatchStatsSpacing.Tiny, vertical = MatchStatsSpacing.Small),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val short = item.short()
                        Text(short, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        if (short != title) Text(title, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
internal fun LiveConfirmationDialog(
    title: String, message: String, confirm: String, destructive: Boolean = false,
    onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { MatchStatsButton(confirm, onConfirm, destructive = destructive) },
        dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss) },
    )
}

internal val liveNumberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
