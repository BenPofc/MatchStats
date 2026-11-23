package br.com.matchstats.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.PracticeClockAction
import br.com.matchstats.data.PracticeDetails
import br.com.matchstats.data.PracticeMeasure
import br.com.matchstats.data.PracticePlayer
import br.com.matchstats.data.PracticeRow
import br.com.matchstats.data.PracticeSession
import br.com.matchstats.data.PracticeStatus
import br.com.matchstats.data.PracticeStore
import br.com.matchstats.data.PracticeTotals
import br.com.matchstats.data.WriteResult
import br.com.matchstats.data.forPlayerSearch
import br.com.matchstats.data.nonNegativeInt
import br.com.matchstats.data.practiceAccuracy
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsConfirmDelete
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.NavyBanner
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.io.Serializable
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal data class PracticeDraft(val drillId: Long, val playerId: Long, val value: String, val attempts: String) : Serializable {
    val hasInput get() = value.isNotBlank() || attempts.isNotBlank()

    fun parse(measure: PracticeMeasure): PracticeRow? {
        val number = value.nonNegativeInt() ?: return null
        val total = if (measure == PracticeMeasure.COUNT) null else attempts.nonNegativeInt() ?: return null
        return PracticeRow(playerId, number, total).takeIf { it.validFor(measure) }
    }
}

internal data class PracticePending(
    val id: String,
    val drillId: Long,
    val drafts: ArrayList<PracticeDraft>,
) : Serializable

@Composable
internal fun PracticeEntryScreen(
    data: PracticeDetails,
    store: PracticeStore,
    drafts: List<PracticeDraft>,
    pending: PracticePending?,
    onDrafts: (List<PracticeDraft>) -> Unit,
    onPending: (PracticePending?) -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onCompleted: () -> Unit,
    onDeleted: () -> Unit,
) {
    val session = data.summary.session
    val active = session.status == PracticeStatus.ACTIVE
    val completed = session.status == PracticeStatus.COMPLETED
    var chosenDrill by rememberSaveable(session.id) { mutableStateOf(0L) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(R.string.practice_sort_name) }
    var pendingOnly by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(0) }
    var saveNotice by rememberSaveable { mutableStateOf(false) }
    var invalidBatch by rememberSaveable { mutableStateOf(false) }
    var retryAttempt by rememberSaveable { mutableStateOf(0) }
    var submissionError by rememberSaveable { mutableStateOf<WriteResult?>(null) }
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val drill = data.drills.find { it.id == chosenDrill } ?: data.drills.firstOrNull()
    val totals = data.totals.filter { it.drillId == drill?.id }.associateBy { it.playerId }
    val currentDrafts = drafts.filter { it.drillId == drill?.id }
    val pendingRows = currentDrafts.associateBy { it.playerId }
    val byName = compareBy<PracticePlayer> { it.name.forPlayerSearch() }.thenBy { it.playerId }
    val comparator = when (sort) {
        R.string.practice_sort_jersey -> compareBy<PracticePlayer> { it.jerseyNumber ?: Int.MAX_VALUE }.then(byName)
        R.string.practice_sort_performance -> compareByDescending<PracticePlayer> {
            val total = totals[it.playerId]
            if (drill?.measure == PracticeMeasure.COUNT) total?.value?.toDouble() else total?.value?.practiceAccuracy(total.attempts)
        }.thenByDescending { totals[it.playerId]?.attempts }.then(byName)
        else -> byName
    }
    val visible = data.players.filter { it.matches(query) && (!pendingOnly || it.playerId in pendingRows) }.sortedWith(comparator)
    val busy = action.running || pending != null

    fun submit(rows: List<PracticeDraft>) {
        if (busy || drill == null || rows.isEmpty()) return
        saveNotice = false
        action.clearError()
        submissionError = null
        invalidBatch = rows.any { it.parse(drill.measure) == null }
        focus.clearFocus()
        keyboard?.hide()
        if (!invalidBatch) {
            onPending(PracticePending(UUID.randomUUID().toString(), drill.id, ArrayList(rows)))
        }
    }

    LaunchedEffect(pending?.id, retryAttempt) {
        val request = pending ?: return@LaunchedEffect
        val measurement = data.drills.find { it.id == request.drillId }?.measure
        val rows = measurement?.let { type -> request.drafts.mapNotNull { it.parse(type) } }.orEmpty()
        if (rows.size != request.drafts.size || rows.isEmpty()) {
            invalidBatch = true
            onPending(null)
            return@LaunchedEffect
        }
        val result = try {
            store.submit(session.id, request.drillId, request.id, rows)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            WriteResult.FAILED
        }
        if (result == WriteResult.SAVED) {
            onDrafts(drafts - request.drafts.toSet())
            saveNotice = true
        } else {
            submissionError = result
        }
        if (result != WriteResult.FAILED) onPending(null)
    }

    val runningClock = store.isRunning(session)
    LaunchedEffect(session.id, active, runningClock) {
        if (!active) return@LaunchedEffect
        try {
            if (session.runningSince != null && !runningClock) store.checkpoint(session.id)
            while (runningClock) {
                delay(15_000)
                store.checkpoint(session.id)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            submissionError = WriteResult.FAILED
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MatchStatsSpacing.ContentMax).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("practice-entry-list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    NavyBanner {
                        Column(Modifier.fillMaxWidth(0.9f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(session.title, style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.practice_scope, stringResource(session.sport.label),
                                data.summary.teamName ?: stringResource(R.string.practice_no_team)), color = MatchStatsColors.OnNavyMuted)
                            Text(stringResource(R.string.practice_scope, stringResource(session.status.label), practiceDateTime(session.startsAt)),
                                style = MaterialTheme.typography.bodySmall)
                            session.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            session.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (session.status == PracticeStatus.SCHEDULED) {
                            MatchStatsButton(stringResource(R.string.practice_start),
                                { action.launch({ store.start(session.id) }) {} }, enabled = !busy)
                        }
                        MatchStatsSecondaryButton(stringResource(R.string.practice_edit), onEdit, enabled = !busy)
                        if (active || completed) MatchStatsSecondaryButton(stringResource(R.string.practice_history), onHistory, enabled = !busy)
                    }
                }
                if (active || session.elapsedMs > 0) item {
                    PracticeTimer(session, store, !busy) { operation ->
                        action.launch({ store.clock(session.id, operation) }) {}
                    }
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (drill != null) MatchStatsDropdown(
                            stringResource(R.string.practice_drill), drill, data.drills, { it.name },
                            { chosenDrill = it.id; invalidBatch = false; saveNotice = false; submissionError = null; action.clearError() },
                            modifier = Modifier.weight(1f), enabled = !busy,
                        )
                        if (!completed) TextButton(onClick = { dialog = R.string.practice_add_drill },
                            modifier = Modifier.align(Alignment.CenterVertically), enabled = !busy) {
                            Text(stringResource(if (drill == null) R.string.practice_add_drill else R.string.practice_add_drill_short))
                        }
                    }
                    drill?.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (drill == null) Text(stringResource(R.string.practice_empty_drills))
                    else if (active) Text(stringResource(R.string.practice_measure_hint), style = MaterialTheme.typography.bodySmall)
                }
                if (drill != null) {
                    item {
                        PracticeSearch(query) { query = it }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { showFilters = !showFilters }) {
                                Text(stringResource(if (showFilters) R.string.practice_hide_filters else R.string.practice_show_filters))
                            }
                            if (query.isNotEmpty() || pendingOnly || sort != R.string.practice_sort_name) {
                                TextButton(onClick = { query = ""; pendingOnly = false; sort = R.string.practice_sort_name }) {
                                    Text(stringResource(R.string.practice_clear_filters))
                                }
                            }
                        }
                        if (showFilters) {
                            val sorts = listOf(R.string.practice_sort_name, R.string.practice_sort_jersey, R.string.practice_sort_performance)
                                .associateWith { stringResource(it) }
                            MatchStatsDropdown(stringResource(R.string.practice_sort), sort, sorts.keys.toList(), { sorts.getValue(it) }, { sort = it })
                            if (active) MatchStatsFilterChip(stringResource(R.string.practice_pending), R.drawable.ic_teams, pendingOnly, { pendingOnly = !pendingOnly })
                        }
                        if (visible.isEmpty()) Text(stringResource(R.string.practice_player_none))
                    }
                    items(visible, key = { it.playerId }) { player ->
                        val draft = pendingRows[player.playerId] ?: PracticeDraft(drill.id, player.playerId, "", "")
                        PracticeEntryRow(
                            player, draft, totals[player.playerId], drill.measure, active, !busy,
                            onChange = { edited ->
                                invalidBatch = false
                                saveNotice = false
                                val previous = drafts.filterNot { it.drillId == edited.drillId && it.playerId == edited.playerId }
                                onDrafts(if (edited.hasInput) previous + edited else previous)
                            },
                            onSave = { submit(listOf(draft)) },
                        )
                    }
                }
                item {
                    if (saveNotice) Text(stringResource(R.string.practice_saved), color = MatchStatsColors.GreenInk)
                    RecordError(action.error)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (drafts.isNotEmpty()) TextButton(onClick = { dialog = R.string.practice_discard }, enabled = !busy) {
                            Text(stringResource(R.string.practice_clear_draft))
                        }
                        if (active) MatchStatsSecondaryButton(
                            stringResource(R.string.practice_complete), { dialog = R.string.practice_complete }, enabled = !busy,
                        )
                        TextButton(onClick = { dialog = R.string.practice_delete }, enabled = !busy) {
                            Text(stringResource(R.string.practice_delete), color = MatchStatsColors.Error)
                        }
                    }
                }
            }
            if (active && drill != null && WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    if (invalidBatch) Text(stringResource(R.string.practice_invalid_values), color = MatchStatsColors.Error)
                    RecordError(submissionError)
                    if (submissionError == WriteResult.FAILED && pending != null) {
                        MatchStatsButton(stringResource(R.string.retry), { submissionError = null; retryAttempt++ }, Modifier.fillMaxWidth())
                    } else {
                        MatchStatsButton(stringResource(R.string.practice_save_bulk, currentDrafts.size), { submit(currentDrafts) },
                            Modifier.fillMaxWidth(), enabled = currentDrafts.isNotEmpty() && !busy)
                    }
                }
            }
        }
    }
    when (dialog) {
        R.string.practice_add_drill -> PracticeDrillDialog(session, store, { chosenDrill = it; dialog = 0 }, { dialog = 0 })
        R.string.practice_delete -> MatchStatsConfirmDelete(
            stringResource(dialog), stringResource(R.string.practice_confirm_delete),
            { dialog = 0; action.launch({ store.delete(session.id) }) { onDrafts(emptyList()); onDeleted() } },
            { dialog = 0 },
        )
        R.string.practice_complete -> {
            if (drafts.isNotEmpty()) AlertDialog(
                onDismissRequest = { dialog = 0 },
                title = { Text(stringResource(R.string.practice_complete)) },
                text = { Text(stringResource(R.string.practice_finish_draft)) },
                confirmButton = { TextButton(onClick = { dialog = 0 }) { Text(stringResource(R.string.cancel)) } },
            ) else PracticeConfirm(dialog, R.string.practice_confirm_complete,
                { dialog = 0; action.launch({ store.complete(session.id) }, onCompleted) }, { dialog = 0 })
        }
        R.string.practice_discard -> PracticeConfirm(dialog, R.string.practice_confirm_discard,
            { onDrafts(emptyList()); invalidBatch = false; dialog = 0 }, { dialog = 0 })
    }
}

@Composable
private fun PracticeEntryRow(
    player: PracticePlayer,
    draft: PracticeDraft,
    total: PracticeTotals?,
    measure: PracticeMeasure,
    editable: Boolean,
    enabled: Boolean,
    onChange: (PracticeDraft) -> Unit,
    onSave: () -> Unit,
) {
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(player.displayName(), style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        total == null -> stringResource(R.string.practice_no_totals)
                        total.attempts == null -> stringResource(R.string.practice_saved_count, total.value)
                        else -> stringResource(R.string.practice_saved_pair, total.value, total.attempts,
                            practicePercent(total.value.practiceAccuracy(total.attempts)))
                    },
                    style = MaterialTheme.typography.bodySmall, color = MatchStatsColors.GreenInk,
                )
                if (editable && measure == PracticeMeasure.MAKES_AND_ATTEMPTS) {
                    val next = draft.parse(measure)
                    if (next != null) Text(
                        stringResource(R.string.practice_draft_percent,
                            practicePercent(next.value.toLong().practiceAccuracy(next.attempts?.toLong()))),
                        style = MaterialTheme.typography.bodySmall, color = MatchStatsColors.Muted,
                    )
                }
            }
            if (editable) BoxWithConstraints(Modifier.fillMaxWidth()) {
                val large = LocalDensity.current.fontScale > 1.4f
                val pair = measure == PracticeMeasure.MAKES_AND_ATTEMPTS
                val separate = pair && (maxWidth < 290.dp || large)
                val save: @Composable (Modifier) -> Unit = { modifier ->
                    TextButton(onClick = onSave, enabled = enabled && draft.hasInput,
                        modifier = modifier.testTag("practice-save-${player.playerId}")) {
                        Text(stringResource(R.string.practice_save_row))
                    }
                }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PracticeNumberField(draft.value, { onChange(draft.copy(value = it)) },
                            if (pair) R.string.practice_makes else R.string.practice_count,
                            Modifier.weight(1f).testTag("practice-value-${player.playerId}"), enabled)
                        if (pair) {
                            PracticeNumberField(draft.attempts, { onChange(draft.copy(attempts = it)) }, R.string.practice_attempts,
                                Modifier.weight(1f).testTag("practice-attempts-${player.playerId}"), enabled)
                        }
                        if (!separate) save(Modifier)
                    }
                    if (separate) save(Modifier.align(Alignment.End))
                }
            }
        }
    }
}

@Composable
internal fun PracticeNumberField(value: String, onChange: (String) -> Unit, label: Int, modifier: Modifier = Modifier, enabled: Boolean = true) {
    MatchStatsTextField(value, onChange, modifier, label = stringResource(label), enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
}

@Composable
internal fun practicePercent(rate: Double?): String =
    rate?.let { NumberFormat.getPercentInstance(Locale.forLanguageTag("pt-BR")).format(it) }
        ?: stringResource(R.string.practice_dash)

@Composable
internal fun PracticeConfirm(title: Int, message: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(title)) }, text = { Text(stringResource(message)) },
        confirmButton = { MatchStatsButton(stringResource(title), onConfirm) },
        dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss) },
    )
}

@Composable
private fun PracticeTimer(
    session: PracticeSession,
    store: PracticeStore,
    enabled: Boolean,
    onAction: (PracticeClockAction) -> Unit,
) {
    val running = store.isRunning(session)
    val elapsed by produceState(store.elapsedFor(session), session) {
        do {
            value = store.elapsedFor(session)
            if (running) delay(250)
        } while (running)
    }
    val (operation, label) = when {
        running -> PracticeClockAction.PAUSE to R.string.practice_clock_pause
        elapsed > 0 && !session.timerStopped -> PracticeClockAction.RESUME to R.string.practice_clock_resume
        else -> PracticeClockAction.START to R.string.practice_clock_start
    }
    val seconds = elapsed / 1000
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.practice_scope, stringResource(R.string.practice_timer),
            stringResource(R.string.practice_clock_display, seconds / 3600, seconds / 60 % 60, seconds % 60)),
            Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
        if (session.status == PracticeStatus.ACTIVE) {
            TextButton(onClick = { onAction(operation) }, enabled = enabled) { Text(stringResource(label)) }
            if (running || elapsed > 0 && !session.timerStopped) {
                TextButton(onClick = { onAction(PracticeClockAction.STOP) }, enabled = enabled) {
                    Text(stringResource(R.string.practice_clock_stop))
                }
            }
        }
    }
}
