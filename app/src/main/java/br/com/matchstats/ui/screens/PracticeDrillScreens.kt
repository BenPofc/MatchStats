package br.com.matchstats.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.PracticeHistoryRow
import br.com.matchstats.data.PracticeMeasure
import br.com.matchstats.data.PracticeSession
import br.com.matchstats.data.PracticeStore
import br.com.matchstats.data.Sport
import br.com.matchstats.data.WriteResult
import br.com.matchstats.data.practiceAccuracy
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.theme.MatchStatsColors

@Composable
internal fun PracticeDrillDialog(
    session: PracticeSession, store: PracticeStore, onSaved: (Long) -> Unit, onDismiss: () -> Unit,
) {
    val count = PracticeMeasure.COUNT
    val pair = PracticeMeasure.MAKES_AND_ATTEMPTS
    val examples = (if (session.sport == Sport.BASKETBALL) listOf(
        R.string.practice_template_free_throw to pair, R.string.practice_template_two to pair,
        R.string.practice_template_three to pair, R.string.practice_template_layup to pair,
        R.string.practice_template_rebounds to count, R.string.practice_template_dribble to count,
    ) else listOf(
        R.string.practice_template_shots to count, R.string.practice_template_on_target to pair,
        R.string.practice_template_penalty to pair, R.string.practice_template_passes to pair,
        R.string.practice_template_tackles to count, R.string.practice_template_carry to count,
    )).toMap()
    val templates = (listOf(R.string.practice_custom) + examples.keys).associateWith { stringResource(it) }
    val labels = mapOf(count to stringResource(R.string.practice_measure_count), pair to stringResource(R.string.practice_measure_pair))
    var template by rememberSaveable { mutableStateOf(R.string.practice_custom) }
    var name by rememberSaveable { mutableStateOf("") }
    var measure by rememberSaveable { mutableStateOf(count) }
    var notes by rememberSaveable { mutableStateOf("") }
    val action = rememberRecordAction()
    AlertDialog(
        onDismissRequest = { if (!action.running) onDismiss() },
        title = { Text(stringResource(R.string.practice_add_drill)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MatchStatsDropdown(stringResource(R.string.practice_template), template, templates.keys.toList(), { templates.getValue(it) },
                    { selected ->
                        template = selected
                        if (selected != R.string.practice_custom) {
                            name = templates.getValue(selected)
                            measure = examples.getValue(selected)
                        }
                    }, enabled = !action.running)
                MatchStatsTextField(name, { name = it }, label = stringResource(R.string.practice_drill_name), enabled = !action.running)
                MatchStatsDropdown(stringResource(R.string.practice_measure), measure, PracticeMeasure.entries, { labels.getValue(it) },
                    { measure = it }, enabled = !action.running)
                MatchStatsTextField(notes, { notes = it }, label = stringResource(R.string.practice_notes),
                    singleLine = false, enabled = !action.running)
                RecordError(action.error)
            }
        },
        confirmButton = {
            MatchStatsButton(stringResource(R.string.save), {
                var id: Long? = null
                action.launch({ store.addDrill(session.id, name, measure, notes).also { id = it.second }.first }) { id?.let(onSaved) }
            }, enabled = !action.running)
        },
        dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss, enabled = !action.running) },
    )
}

@Composable
internal fun PracticeHistoryScreen(rows: List<PracticeHistoryRow>, store: PracticeStore) {
    var selectedSubmission by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlayer by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = rows.find { it.batch.submissionId == selectedSubmission && it.batch.playerId == selectedPlayer }
    MatchStatsScreen {
        SportsBanner(stringResource(R.string.practice_history))
        if (rows.isEmpty()) MatchStatsEmptyState(
            stringResource(R.string.practice_empty_batches), stringResource(R.string.practice_measure_hint), R.drawable.ic_history,
        )
        rows.forEach { item ->
            val batch = item.batch
            MatchStatsCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.playerName, style = MaterialTheme.typography.titleMedium)
                    Text(item.drillName, style = MaterialTheme.typography.bodyMedium)
                    Text(practiceDateTime(batch.recordedAt), style = MaterialTheme.typography.bodySmall, color = MatchStatsColors.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(practiceMeasurement(batch.value.toLong(), batch.attempts?.toLong()), color = MatchStatsColors.GreenInk)
                        if (batch.revision > 0) Text(stringResource(R.string.practice_corrected))
                    }
                    if (batch.revision > 0) Text(stringResource(R.string.practice_original,
                        practiceMeasurement(batch.originalValue.toLong(), batch.originalAttempts?.toLong())),
                        style = MaterialTheme.typography.bodySmall)
                    MatchStatsSecondaryButton(stringResource(R.string.practice_correct), {
                        selectedSubmission = batch.submissionId
                        selectedPlayer = batch.playerId
                    })
                }
            }
        }
    }
    if (editing != null) PracticeCorrectionDialog(editing, store) {
        selectedSubmission = null
        selectedPlayer = null
    }
}

@Composable
private fun PracticeCorrectionDialog(item: PracticeHistoryRow, store: PracticeStore, onDismiss: () -> Unit) {
    val batch = item.batch
    val pair = item.measure == PracticeMeasure.MAKES_AND_ATTEMPTS
    var value by rememberSaveable { mutableStateOf(batch.value.toString()) }
    var attempts by rememberSaveable { mutableStateOf(batch.attempts?.toString().orEmpty()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val action = rememberRecordAction()
    AlertDialog(
        onDismissRequest = { if (!action.running) onDismiss() },
        title = { Text(stringResource(R.string.practice_correct)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.practice_scope, item.playerName, item.drillName))
                PracticeNumberField(value, { value = it; invalid = false }, if (pair) R.string.practice_makes else R.string.practice_count,
                    enabled = !action.running)
                if (pair) PracticeNumberField(attempts, { attempts = it; invalid = false }, R.string.practice_attempts, enabled = !action.running)
                if (invalid) Text(stringResource(R.string.practice_invalid_values), color = MatchStatsColors.Error)
                RecordError(action.error)
            }
        },
        confirmButton = {
            MatchStatsButton(stringResource(R.string.save), {
                val row = PracticeDraft(batch.drillId, batch.playerId, value, attempts).parse(item.measure)
                invalid = row == null
                if (row != null) action.launch({ store.correct(batch, row) }, onDismiss)
            }, enabled = !action.running)
        },
        dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss, enabled = !action.running) },
    )
}

@Composable
internal fun practiceMeasurement(value: Long, attempts: Long?): String =
    if (attempts == null) stringResource(R.string.practice_count_total, value)
    else stringResource(R.string.practice_pair_value, value, attempts, practicePercent(value.practiceAccuracy(attempts)))
