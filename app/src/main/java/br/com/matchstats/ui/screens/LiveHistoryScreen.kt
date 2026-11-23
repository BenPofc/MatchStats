package br.com.matchstats.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import br.com.matchstats.R
import br.com.matchstats.data.LiveAction
import br.com.matchstats.data.LiveActionInfo
import br.com.matchstats.data.LiveEvent
import br.com.matchstats.data.LiveGame
import br.com.matchstats.data.LiveGameStore
import br.com.matchstats.data.LiveOperation
import br.com.matchstats.data.LiveSide
import br.com.matchstats.data.SCORE_ADJUSTMENT
import br.com.matchstats.data.Sport
import br.com.matchstats.data.WriteResult
import br.com.matchstats.data.nonNegativeInt
import br.com.matchstats.ui.components.FormButtons
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun LiveRecentEvents(game: LiveGame) {
    SectionHeading(stringResource(R.string.live_recent), R.drawable.ic_history)
    if (game.events.isEmpty()) Text(stringResource(R.string.live_no_events), color = MatchStatsColors.Muted)
    game.events.takeLast(3).asReversed().forEach { event ->
        LiveHistoryCard(game, event, game.events.firstOrNull { it.previousId == event.id }?.operation)
    }
}

@Composable
internal fun LiveHistoryScreen(
    game: LiveGame,
    tracking: LiveGameStore,
    onCorrect: (String) -> Unit,
    onScore: () -> Unit,
) {
    val id = game.fixture.match.id
    val successors = game.events.mapNotNull { entry -> entry.previousId?.let { it to entry.operation } }.toMap()
    val active = game.activeEvents.mapTo(hashSetOf()) { it.id }
    val action = rememberRecordAction()
    var undoId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = action.running) {}

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = MatchStatsSpacing.ContentMax).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = MatchStatsSpacing.Screen, vertical = MatchStatsSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            item {
                SportsBanner(
                    stringResource(R.string.live_history),
                    stringResource(R.string.match_stats_scope, game.fixture.team.name, game.fixture.match.opponent),
                )
                game.session?.let {
                    Text(
                        stringResource(R.string.live_score, it.homeScore, it.awayScore),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = MatchStatsSpacing.Small),
                    )
                }
                MatchStatsSecondaryButton(stringResource(R.string.live_adjust_score), onScore, Modifier.fillMaxWidth(), !action.running)
                RecordError(action.error)
                if (game.events.isEmpty()) Text(stringResource(R.string.live_no_events), color = MatchStatsColors.Muted)
            }
            items(game.events.asReversed(), key = LiveEvent::id) { event ->
                val editable = event.id in active
                LiveHistoryCard(game, event, successors[event.id]) {
                    if (editable) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                            MatchStatsSecondaryButton(
                                stringResource(R.string.live_correct), { onCorrect(event.id) }, enabled = !action.running,
                            )
                            MatchStatsSecondaryButton(
                                stringResource(R.string.live_undo), { undoId = event.id },
                                enabled = !action.running, destructive = true,
                            )
                        }
                    }
                }
            }
        }
    }

    undoId?.let { event ->
        LiveConfirmationDialog(
            stringResource(R.string.live_undo), stringResource(R.string.live_undo_confirm),
            stringResource(R.string.live_undo), destructive = true,
            onDismiss = { undoId = null },
            onConfirm = {
                undoId = null
                val request = liveRequestId()
                action.launch({ tracking.undo(id, request, event) }) {}
            },
        )
    }
}

@Composable
private fun LiveHistoryCard(
    game: LiveGame,
    event: LiveEvent,
    successor: LiveOperation?,
    actions: @Composable () -> Unit = {},
) {
    val sport = game.fixture.team.sport
    val title = event.label()
    val kind = when (event.operation) {
        LiveOperation.RECORD -> title
        LiveOperation.CORRECT -> stringResource(R.string.live_correct_entry, title)
        LiveOperation.UNDO -> stringResource(R.string.live_undo_entry, title)
    }
    val state = when (successor) {
        LiveOperation.CORRECT -> stringResource(R.string.live_corrected)
        LiveOperation.UNDO -> stringResource(R.string.live_undone)
        else -> null
    }
    val team = if (event.side == LiveSide.HOME) game.fixture.team.name else game.fixture.match.opponent
    val jersey = event.jersey?.let { stringResource(R.string.jersey_short, it) }
    val actor = listOfNotNull(jersey, event.playerName).joinToString(" ").ifBlank {
        stringResource(R.string.live_for_team)
    }
    val wallTime = Instant.ofEpochMilli(event.createdAt).atZone(ZoneId.systemDefault()).format(MatchTimeFormat)
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(MatchStatsSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
        ) {
            Text(
                listOfNotNull(kind, state).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                color = if (state != null) MatchStatsColors.Muted else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOf(liveTime(event.clockMs, sport == Sport.BASKETBALL), livePeriod(sport, event.period), team, actor)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(wallTime, style = MaterialTheme.typography.labelSmall, color = MatchStatsColors.Muted)
            actions()
        }
    }
}

@Composable
private fun LiveEvent.label(): String {
    if (actionId == SCORE_ADJUSTMENT) {
        return stringResource(R.string.live_score_adjusted, stringResource(R.string.live_score_delta, value))
    }
    val builtin = LiveAction.byId[actionId]
    return if (builtin != null) stringResource(builtin.labelRes)
    else listOf(actionName ?: actionId, value.toLong().statNumber()).joinToString(" · ")
}

@Composable
internal fun LiveCorrectionScreen(
    game: LiveGame,
    tracking: LiveGameStore,
    previous: LiveEvent?,
    scoreCorrection: Boolean,
    onBack: () -> Unit,
) {
    val session = requireNotNull(game.session)
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current
    BackHandler(enabled = action.running) {}
    var selection by rememberSaveable {
        mutableStateOf(LiveSelection(
            side = previous?.side ?: LiveSide.HOME,
            homePlayer = previous?.playerId,
            jersey = previous?.jersey?.takeIf { previous.side == LiveSide.AWAY }?.toString().orEmpty(),
            awayName = previous?.playerName?.takeIf { previous.side == LiveSide.AWAY }.orEmpty(),
        ))
    }
    var actionId by rememberSaveable { mutableStateOf(previous?.actionId.orEmpty()) }
    fun scoreFor(side: LiveSide) = if (side == LiveSide.HOME) session.homeScore else session.awayScore
    var value by rememberSaveable {
        mutableStateOf(if (scoreCorrection) scoreFor(selection.side).toString() else (previous?.value ?: 1).toString())
    }
    val options = buildList {
        addAll(game.config.catalog.values)
        if (previous != null && previous.actionName != null && previous.actionId !in game.config.catalog) {
            add(LiveActionInfo(previous.actionId, null, null, previous.actionName, previous.actionName))
        }
    }
    val labels = options.associate { it.id to it.label() }
    val chosen = options.firstOrNull { it.id == actionId }
    MatchStatsScreen {
        SportsBanner(stringResource(if (scoreCorrection) R.string.live_adjust_score else R.string.live_correct_event))
        if (scoreCorrection) {
            LiveSideSelector(selection.side) {
                focus.clearFocus()
                selection = selection.copy(side = it)
                value = scoreFor(it).toString()
                action.clearError()
            }
            Text(
                stringResource(R.string.live_score, session.homeScore, session.awayScore),
                style = MaterialTheme.typography.headlineSmall, color = MatchStatsColors.GreenInk,
            )
            MatchStatsTextField(
                value, { value = it; action.clearError() }, label = stringResource(R.string.live_score_value),
                keyboardOptions = liveNumberKeyboard, enabled = !action.running,
            )
        } else {
            LivePlayerPicker(game, selection) { selection = it; action.clearError() }
            MatchStatsDropdown(
                label = stringResource(R.string.live_action_type),
                selected = chosen,
                options = options,
                optionLabel = { labels.getValue(it.id) },
                onSelect = { actionId = it.id; action.clearError() },
                enabled = !action.running,
            )
            if (chosen?.customName != null) MatchStatsTextField(
                value, { value = it; action.clearError() }, label = stringResource(R.string.live_event_value),
                keyboardOptions = liveNumberKeyboard, enabled = !action.running,
            )
        }
        FormButtons(
            action,
            onSave = {
                focus.clearFocus()
                val request = liveRequestId()
                val matchId = game.fixture.match.id
                action.launch({
                    if (scoreCorrection) tracking.adjustScore(matchId, request, selection.side, value.nonNegativeInt() ?: -1, previous?.id)
                    else if (chosen == null || previous == null) WriteResult.INVALID_LIVE_EVENT
                    else tracking.record(
                        matchId, request,
                        selection.input(chosen.id, if (chosen.customName == null) 1 else value.nonNegativeInt() ?: 0),
                        previous.id,
                    )
                }, onBack)
            },
            onCancel = onBack,
        )
    }
}
