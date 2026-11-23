package br.com.matchstats.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import br.com.matchstats.R
import br.com.matchstats.data.Match
import br.com.matchstats.data.MatchInput
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.Team
import br.com.matchstats.data.WriteResult
import br.com.matchstats.ui.components.FormButtons
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.errorLabel
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.time.LocalDate
import java.time.LocalTime

private enum class MatchPicker { DATE, TIME }

@Composable
fun MatchFormScreen(
    teams: List<Team>,
    match: Match?,
    saveMatch: suspend (MatchInput) -> WriteResult,
    onSaved: (MatchStatus) -> Unit,
    onBack: () -> Unit,
) {
    val originalStart = match?.localStart()
    var teamId by rememberSaveable { mutableStateOf(match?.teamId) }
    var opponent by rememberSaveable { mutableStateOf(match?.opponent.orEmpty()) }
    var dateDay by rememberSaveable { mutableStateOf(originalStart?.toLocalDate()?.toEpochDay()) }
    var timeMinute by rememberSaveable { mutableStateOf(originalStart?.toLocalTime()?.toSecondOfDay()?.div(60)) }
    var location by rememberSaveable { mutableStateOf(match?.location.orEmpty()) }
    var status by rememberSaveable { mutableStateOf(match?.status ?: MatchStatus.SCHEDULED) }
    var teamScore by rememberSaveable { mutableStateOf(match?.teamScore?.toString().orEmpty()) }
    var opponentScore by rememberSaveable { mutableStateOf(match?.opponentScore?.toString().orEmpty()) }
    var picker by rememberSaveable { mutableStateOf<MatchPicker?>(null) }

    val date = dateDay?.let(LocalDate::ofEpochDay)
    val time = timeMinute?.let { LocalTime.ofSecondOfDay(it * 60L) }
    val team = teams.firstOrNull { it.id == teamId }
    val sportLabels = teams.map { it.sport }.distinct().associateWith { stringResource(it.label) }
    val isFinished = status == MatchStatus.FINISHED
    val wasFinished = match?.status == MatchStatus.FINISHED
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current
    val fieldError = when (action.error) {
        WriteResult.TEAM_REQUIRED, WriteResult.TEAM_MISSING, WriteResult.MATCH_HAS_STATS -> R.string.team_required_label
        WriteResult.OPPONENT_REQUIRED -> R.string.match_opponent
        WriteResult.DATE_REQUIRED -> R.string.match_date
        WriteResult.TIME_REQUIRED, WriteResult.INVALID_MATCH_TIME -> R.string.match_time
        WriteResult.INVALID_TEAM_SCORE, WriteResult.SCORE_BELOW_RECORDED_GOALS -> R.string.team_score
        WriteResult.INVALID_OPPONENT_SCORE -> R.string.opponent_score
        else -> null
    }
    val error = action.error?.errorLabel?.let { stringResource(it) }

    BackHandler(enabled = action.running) {}
    MatchStatsScreen {
        SportsBanner(
            stringResource(if (match == null) R.string.match_form_new else R.string.match_form_edit),
            stringResource(R.string.match_form_intro),
        )
        if (teams.isEmpty()) {
            MatchStatsEmptyState(
                stringResource(R.string.empty_teams),
                stringResource(R.string.match_no_teams_hint),
                R.drawable.ic_teams,
            )
            MatchStatsSecondaryButton(stringResource(R.string.cancel), onBack, Modifier.fillMaxWidth())
            return@MatchStatsScreen
        }

        MatchStatsCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(MatchStatsSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
            ) {
                Text(stringResource(R.string.match_status), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                    MatchStatus.entries.forEach { option ->
                        MatchStatsFilterChip(
                            stringResource(option.label),
                            option.icon,
                            status == option,
                            onClick = {
                                status = option
                                action.clearError()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !action.running && !(wasFinished && option == MatchStatus.SCHEDULED),
                        )
                    }
                }
                if (wasFinished) {
                    Text(
                        stringResource(R.string.finished_lock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MatchStatsColors.Muted,
                    )
                }
            }
        }
        MatchStatsDropdown(
            label = stringResource(R.string.team_required_label),
            selected = team,
            options = teams,
            optionLabel = { listOfNotNull(it.name, sportLabels.getValue(it.sport), it.category).joinToString(" · ") },
            selectedLabel = Team::name,
            onSelect = {
                teamId = it.id
                action.clearError()
            },
            error = error.takeIf { fieldError == R.string.team_required_label },
            enabled = !action.running,
            leadingIcon = { MatchStatsIcon(R.drawable.ic_teams, tint = MatchStatsColors.GreenInk) },
        )
        team?.let {
            Text(
                sportLabels.getValue(it.sport),
                color = MatchStatsColors.GreenInk,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        MatchStatsTextField(
            value = opponent,
            onValueChange = {
                opponent = it
                action.clearError()
            },
            label = stringResource(R.string.match_opponent),
            leadingIcon = { MatchStatsIcon(R.drawable.ic_shield, tint = MatchStatsColors.GreenInk) },
            error = error.takeIf { fieldError == R.string.match_opponent },
            enabled = !action.running,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
        ) {
            MatchPickerField(
                stringResource(R.string.match_date),
                date?.format(MatchDateFormat).orEmpty(),
                stringResource(R.string.match_date_placeholder),
                R.drawable.ic_calendar,
                onClick = {
                    focus.clearFocus()
                    picker = MatchPicker.DATE
                },
                enabled = !action.running,
                error = error.takeIf { fieldError == R.string.match_date },
                modifier = Modifier.weight(1f),
            )
            MatchPickerField(
                stringResource(R.string.match_time),
                time?.format(MatchTimeFormat).orEmpty(),
                stringResource(R.string.match_time_placeholder),
                R.drawable.ic_history,
                onClick = {
                    focus.clearFocus()
                    picker = MatchPicker.TIME
                },
                enabled = !action.running,
                error = error.takeIf { fieldError == R.string.match_time },
                modifier = Modifier.weight(1f),
            )
        }
        MatchStatsTextField(
            value = location,
            onValueChange = { location = it },
            label = stringResource(R.string.match_location),
            leadingIcon = { MatchStatsIcon(R.drawable.ic_grid, tint = MatchStatsColors.GreenInk) },
            enabled = !action.running,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        MatchStatsCard(Modifier.fillMaxWidth(), containerColor = MatchStatsColors.SoftGreen) {
            Column(
                modifier = Modifier.padding(MatchStatsSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
            ) {
                Text(stringResource(R.string.match_score), style = MaterialTheme.typography.titleMedium)
                if (!isFinished) {
                    Text(stringResource(R.string.scheduled_score_hint), color = MatchStatsColors.Muted)
                } else {
                    MatchStatsTextField(
                        value = teamScore,
                        onValueChange = {
                            teamScore = it
                            action.clearError()
                        },
                        label = stringResource(R.string.team_score),
                        error = error.takeIf { fieldError == R.string.team_score },
                        enabled = !action.running,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                    MatchStatsTextField(
                        value = opponentScore,
                        onValueChange = {
                            opponentScore = it
                            action.clearError()
                        },
                        label = stringResource(R.string.opponent_score),
                        error = error.takeIf { fieldError == R.string.opponent_score },
                        enabled = !action.running,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                }
            }
        }
        FormButtons(
            action = action,
            onSave = {
                focus.clearFocus()
                val input = MatchInput(team?.id, opponent, date, time, location, status, teamScore, opponentScore)
                action.launch({ saveMatch(input) }, { onSaved(input.status) })
            },
            onCancel = onBack,
            showError = fieldError == null,
        )
    }

    when (picker) {
        MatchPicker.DATE -> MatchDateDialog(date, onSelected = {
            dateDay = it.toEpochDay()
            picker = null
            action.clearError()
        }, onDismiss = { picker = null })
        MatchPicker.TIME -> MatchTimeDialog(time, onSelected = {
            timeMinute = it.toSecondOfDay() / 60
            picker = null
            action.clearError()
        }, onDismiss = { picker = null })
        null -> Unit
    }
}
