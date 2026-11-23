package br.com.matchstats.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun MatchPickerField(
    label: String,
    value: String,
    placeholder: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny)) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (error == null) MatchStatsColors.Outline else MatchStatsColors.Error),
        ) {
            Row(
                modifier = Modifier.padding(MatchStatsSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                MatchStatsIcon(icon, tint = MatchStatsColors.GreenInk)
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MatchStatsColors.Muted)
                    Text(
                        value.ifEmpty { placeholder },
                        color = if (value.isEmpty()) MatchStatsColors.Muted else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (error != null) Text(error, color = MatchStatsColors.Error, style = MaterialTheme.typography.bodySmall)
    }
}

private val dateHeadlineFormat = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", MatchLocale)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MatchDateDialog(date: LocalDate?, onSelected: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    PortuguesePicker {
        val initial = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                MatchStatsButton(
                    stringResource(R.string.accept_match_date),
                    onClick = {
                        state.selectedDateMillis?.let {
                            onSelected(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate())
                        }
                    },
                    enabled = state.selectedDateMillis != null,
                )
            },
            dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss) },
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(
                    state = state,
                    title = {
                        Text(
                            stringResource(R.string.select_match_date),
                            Modifier.padding(horizontal = MatchStatsSpacing.ExtraLarge, vertical = MatchStatsSpacing.Medium),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    headline = {
                        val selected = state.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate()
                        }
                        Text(
                            selected?.format(dateHeadlineFormat) ?: stringResource(R.string.match_date_placeholder),
                            Modifier.padding(horizontal = MatchStatsSpacing.ExtraLarge),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    },
                    showModeToggle = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MatchTimeDialog(time: LocalTime?, onSelected: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    PortuguesePicker {
        val initial = time ?: LocalTime.now()
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.select_match_time)) },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = state)
                }
            },
            confirmButton = {
                MatchStatsButton(stringResource(R.string.accept_match_time), {
                    onSelected(LocalTime.of(state.hour, state.minute))
                })
            },
            dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss) },
        )
    }
}

@Composable
private fun PortuguesePicker(content: @Composable () -> Unit) {
    val current = LocalConfiguration.current
    val context = LocalContext.current
    val configuration = remember(current) { Configuration(current).apply { setLocale(MatchLocale) } }
    val localized = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration, content = content)
}
