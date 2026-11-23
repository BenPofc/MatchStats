package br.com.matchstats.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.matchstats.R
import br.com.matchstats.data.Sport
import br.com.matchstats.data.WriteResult
import br.com.matchstats.ui.errorLabel
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun MatchStatsSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (destructive) MatchStatsColors.Error else MatchStatsColors.GreenInk,
        ),
        border = BorderStroke(1.dp, if (enabled) MatchStatsColors.Outline else MatchStatsColors.Border),
    ) { Text(label, textAlign = TextAlign.Center) }
}

@Composable
fun MatchStatsConfirmDelete(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    @StringRes confirmLabel: Int = R.string.delete,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        icon = { MatchStatsIcon(R.drawable.ic_delete, tint = MatchStatsColors.Error) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            MatchStatsButton(
                label = stringResource(confirmLabel),
                onClick = onConfirm,
                destructive = true,
            )
        },
        dismissButton = {
            MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss)
        },
    )
}

@Composable
fun SportSelector(selected: Sport, onSelect: (Sport) -> Unit, enabled: Boolean) {
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MatchStatsSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
        ) {
            Text(stringResource(R.string.sport), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                Sport.entries.forEach { sport ->
                    MatchStatsFilterChip(
                        label = stringResource(sport.label),
                        icon = sport.icon,
                        selected = sport == selected,
                        onClick = { onSelect(sport) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun FormButtons(
    action: RecordAction,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    showError: Boolean = true,
) {
    if (showError) RecordError(action.error)
    MatchStatsButton(
        label = stringResource(if (action.running) R.string.saving else R.string.save),
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
        enabled = !action.running,
    )
    MatchStatsSecondaryButton(
        label = stringResource(R.string.cancel),
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        enabled = !action.running,
    )
}

@Composable
fun RecordError(error: WriteResult?) {
    error?.errorLabel?.let {
        Text(stringResource(it), color = MatchStatsColors.Error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Stable
class RecordAction(private val scope: CoroutineScope) {
    var running by mutableStateOf(false)
        private set
    var error by mutableStateOf<WriteResult?>(null)
        private set

    fun clearError() { error = null }

    fun launch(operation: suspend () -> WriteResult, onSuccess: () -> Unit) {
        if (running) return
        error = null
        running = true
        scope.launch(Dispatchers.Main.immediate) {
            val result = try {
                operation()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                WriteResult.FAILED
            } finally {
                running = false
            }
            if (result == WriteResult.SAVED) onSuccess() else error = result
        }
    }
}

@Composable
fun rememberRecordAction(): RecordAction {
    val scope = rememberCoroutineScope()
    return remember(scope) { RecordAction(scope) }
}

private sealed interface RecordLoad<out T> {
    data object Loading : RecordLoad<Nothing>
    data object Failed : RecordLoad<Nothing>
    data class Ready<T>(val value: T) : RecordLoad<T>
}

@Composable
fun <T> ObserveRecords(flow: Flow<T>, content: @Composable (T) -> Unit) {
    var attempt by remember(flow) { mutableIntStateOf(0) }
    val source = remember(flow) {
        flow.map<T, RecordLoad<T>> { RecordLoad.Ready(it) }.catch { emit(RecordLoad.Failed) }
    }
    key(attempt) {
        val state by source.collectAsStateWithLifecycle(initialValue = RecordLoad.Loading)
        when (val result = state) {
            RecordLoad.Loading -> MatchStatsScreen {
                Row(
                    Modifier.fillMaxWidth().padding(MatchStatsSpacing.Screen),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.loading))
                }
            }
            RecordLoad.Failed -> MatchStatsScreen {
                MatchStatsEmptyState(
                    stringResource(R.string.read_error),
                    stringResource(R.string.read_error_hint),
                    R.drawable.ic_info,
                )
                MatchStatsButton(stringResource(R.string.retry), { attempt++ })
            }
            is RecordLoad.Ready -> content(result.value)
        }
    }
}

@Composable
fun RecordUnavailable(onBack: () -> Unit) {
    MatchStatsScreen {
        MatchStatsEmptyState(
            stringResource(R.string.error_record_missing),
            stringResource(R.string.record_missing_hint),
            R.drawable.ic_info,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            MatchStatsSecondaryButton(stringResource(R.string.back), onBack)
        }
    }
}
