package br.com.matchstats.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.PracticeCandidates
import br.com.matchstats.data.PracticeDetails
import br.com.matchstats.data.PracticeInput
import br.com.matchstats.data.PracticePlayer
import br.com.matchstats.data.PracticeStatus
import br.com.matchstats.data.PracticeSummary
import br.com.matchstats.data.Sport
import br.com.matchstats.data.forPlayerSearch
import br.com.matchstats.data.practiceOrMatchStart
import br.com.matchstats.ui.components.FormButtons
import br.com.matchstats.ui.components.IconTile
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.SportSelector
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal val PracticeStatus.label: Int get() = when (this) {
    PracticeStatus.SCHEDULED -> R.string.practice_scheduled
    PracticeStatus.ACTIVE -> R.string.practice_active
    PracticeStatus.COMPLETED -> R.string.practice_completed
}

@Composable
internal fun PracticeContextTabs(practices: Boolean, gamesLabel: Int, onSelect: (Boolean) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MatchStatsFilterChip(stringResource(gamesLabel), R.drawable.ic_calendar, !practices, { onSelect(false) }, Modifier.weight(1f))
        MatchStatsFilterChip(stringResource(R.string.practices), R.drawable.ic_bolt, practices, { onSelect(true) }, Modifier.weight(1f))
    }
}

@Composable
internal fun PracticeListScreen(
    sessions: List<PracticeSummary>,
    completed: Boolean,
    onCompleted: (Boolean) -> Unit,
    onContext: (Boolean) -> Unit,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    MatchStatsScreen(footer = { MatchStatsButton(stringResource(R.string.practice_new), onNew, Modifier.fillMaxWidth()) }) {
        SportsBanner(stringResource(R.string.practices), stringResource(R.string.practice_intro))
        PracticeContextTabs(true, R.string.nav_matches, onContext)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MatchStatsFilterChip(stringResource(R.string.practice_upcoming), R.drawable.ic_calendar, !completed, { onCompleted(false) })
            MatchStatsFilterChip(stringResource(R.string.practice_completed_list), R.drawable.ic_trophy, completed, { onCompleted(true) })
        }
        if (sessions.isEmpty()) MatchStatsEmptyState(
            stringResource(if (completed) R.string.practice_empty_completed else R.string.practice_empty_upcoming),
            stringResource(if (completed) R.string.practice_completed_hint else R.string.practice_empty_hint),
            R.drawable.ic_calendar,
        )
        sessions.forEach { record -> PracticeCard(record) { onOpen(record.session.id) } }
    }
}

@Composable
internal fun PracticeCard(record: PracticeSummary, onClick: () -> Unit) {
    val session = record.session
    MatchStatsCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(session.sport.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium)
                Text(practiceDateTime(session.startsAt), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.practice_scope, stringResource(session.sport.label),
                    record.teamName ?: stringResource(R.string.practice_no_team)), color = MatchStatsColors.Muted)
                session.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(session.status.label), style = MaterialTheme.typography.labelLarge, color = MatchStatsColors.GreenInk)
            }
        }
    }
}

@Composable
internal fun PracticeFormScreen(
    details: PracticeDetails?,
    candidates: PracticeCandidates,
    onSave: suspend (PracticeInput) -> br.com.matchstats.data.WriteResult,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val original = details?.summary?.session
    val initial = original?.startsAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        ?: LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0)
    var title by rememberSaveable { mutableStateOf(original?.title.orEmpty()) }
    var sport by rememberSaveable { mutableStateOf(original?.sport ?: Sport.FOOTBALL) }
    var teamId by rememberSaveable { mutableStateOf(original?.teamId ?: 0L) }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime().toString()) }
    var location by rememberSaveable { mutableStateOf(original?.location.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(original?.notes.orEmpty()) }
    var selected by rememberSaveable { mutableStateOf(ArrayList(details?.players.orEmpty().map { it.playerId })) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var timePicker by rememberSaveable { mutableStateOf(false) }
    var playerPicker by rememberSaveable { mutableStateOf(false) }
    val action = rememberRecordAction()
    val localDate = LocalDate.parse(date)
    val localTime = LocalTime.parse(time)
    val locked = !details?.drills.isNullOrEmpty()
    val teams = candidates.teams.filter { !locked || it.sport == sport }
    val teamNames = teams.associate { it.id to it.name } + (0L to stringResource(R.string.practice_no_team))
    val eligibleTeam = teamId.takeIf { it in teamNames && it != 0L }
    val roster = candidates.memberships.filter { it.team.id == eligibleTeam }.associateBy { it.playerId }

    MatchStatsScreen {
        SportsBanner(stringResource(if (original == null) R.string.practice_new else R.string.practice_edit))
        MatchStatsTextField(title, { title = it }, label = stringResource(R.string.practice_title), enabled = !action.running)
        MatchStatsDropdown(
            stringResource(R.string.practice_optional_team), eligibleTeam ?: 0L, listOf(0L) + teams.map { it.id },
            { id -> teamNames.getValue(id) },
            { id ->
                if (id != teamId) {
                    teamId = id
                    teams.find { it.id == id }?.let { team ->
                        sport = team.sport
                        val recorded = details?.totals.orEmpty().map { it.playerId }
                        selected = ArrayList((recorded + candidates.memberships.filter { it.team.id == id }.map { it.playerId }).distinct())
                    }
                }
            }, enabled = !action.running,
        )
        SportSelector(sport, { sport = it }, eligibleTeam == null && !locked && !action.running)
        MatchPickerField(stringResource(R.string.match_date), localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), "",
            R.drawable.ic_calendar, { datePicker = true }, !action.running, null)
        MatchPickerField(stringResource(R.string.match_time), localTime.format(DateTimeFormatter.ofPattern("HH:mm")), "",
            R.drawable.ic_calendar, { timePicker = true }, !action.running, null)
        MatchStatsTextField(location, { location = it }, label = stringResource(R.string.practice_location), enabled = !action.running)
        MatchStatsTextField(notes, { notes = it }, label = stringResource(R.string.practice_notes), singleLine = false, enabled = !action.running)
        MatchStatsSecondaryButton(
            stringResource(R.string.practice_selected, selected.size), { playerPicker = true }, Modifier.fillMaxWidth(), enabled = !action.running,
        )
        FormButtons(action, onSave = {
            action.launch({
                onSave(PracticeInput(title, sport, eligibleTeam,
                    practiceOrMatchStart(localDate, localTime, ZoneId.systemDefault()), location, notes, selected.toSet()))
            }, onSaved)
        }, onCancel = onBack)
    }
    if (datePicker) MatchDateDialog(localDate, { date = it.toString(); datePicker = false }, { datePicker = false })
    if (timePicker) MatchTimeDialog(localTime, { time = it.toString(); timePicker = false }, { timePicker = false })
    if (playerPicker) PracticeParticipantDialog(
        players = candidates.players.map { PracticePlayer(it.id, it.name, roster[it.id]?.jerseyNumber) },
        roster = roster.keys,
        initial = selected.toSet(),
        locked = details?.totals.orEmpty().map { it.playerId }.toSet(),
        onSelected = { selected = ArrayList(it); playerPicker = false },
        onDismiss = { playerPicker = false },
    )
}

@Composable
private fun PracticeParticipantDialog(
    players: List<PracticePlayer>,
    roster: Set<Long>,
    initial: Set<Long>,
    locked: Set<Long>,
    onSelected: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(ArrayList(initial)) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(R.string.practice_all_players) }
    val visible = players.filter { it.matches(query) && when (filter) {
        R.string.practice_selected -> it.playerId in selected
        R.string.practice_roster -> it.playerId in roster
        else -> true
    } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.practice_players)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PracticeSearch(query, { query = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (listOf(R.string.practice_all_players, R.string.practice_selected) +
                        if (roster.isEmpty()) emptyList() else listOf(R.string.practice_roster)).forEach { label ->
                        MatchStatsFilterChip(
                            if (label == R.string.practice_selected) stringResource(label, selected.size) else stringResource(label),
                            R.drawable.ic_teams, filter == label, { filter = label },
                        )
                    }
                }
                MatchStatsSecondaryButton(stringResource(R.string.practice_clear_selection), { selected = ArrayList(locked) })
                if (visible.isEmpty()) Text(stringResource(if (players.isEmpty()) R.string.practice_player_hint else R.string.practice_player_none))
                LazyColumn(Modifier.heightIn(max = 256.dp)) {
                    items(visible, key = { it.playerId }) { player ->
                        val checked = player.playerId in selected
                        val enabled = player.playerId !in locked
                        val toggle = {
                            selected = ArrayList(if (checked) selected - player.playerId else selected + player.playerId)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = toggle).padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, { toggle() }, enabled = enabled)
                            Text(player.displayName())
                        }
                    }
                }
            }
        },
        confirmButton = { MatchStatsButton(stringResource(R.string.save), { onSelected(selected.toSet()) }) },
        dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss) },
    )
}

@Composable
internal fun PracticeSearch(query: String, onQuery: (String) -> Unit) {
    val focus = LocalFocusManager.current
    MatchStatsTextField(query, onQuery, label = stringResource(R.string.practice_find_player),
        leadingIcon = { MatchStatsIcon(R.drawable.ic_search, tint = MatchStatsColors.GreenInk) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
}

internal fun PracticePlayer.matches(query: String): Boolean {
    val normalized = query.trim().removePrefix("#").forPlayerSearch()
    val number = normalized.toIntOrNull()
    return name.forPlayerSearch().contains(normalized) || number != null && jerseyNumber == number
}

@Composable
internal fun PracticePlayer.displayName(): String = jerseyNumber?.let {
    stringResource(R.string.practice_scope, stringResource(R.string.practice_number, it), name)
} ?: name

internal fun practiceDateTime(at: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.forLanguageTag("pt-BR")).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))
