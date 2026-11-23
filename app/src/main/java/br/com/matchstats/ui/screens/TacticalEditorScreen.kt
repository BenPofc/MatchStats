package br.com.matchstats.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.matchstats.R
import br.com.matchstats.data.CourtMode
import br.com.matchstats.data.MAX_STROKES
import br.com.matchstats.data.MatchStatsRepository
import br.com.matchstats.data.RosterPlayer
import br.com.matchstats.data.Sport
import br.com.matchstats.data.TacticalBoard
import br.com.matchstats.data.TacticalDocumentCodec
import br.com.matchstats.data.TacticalHistory
import br.com.matchstats.data.TacticalMarker
import br.com.matchstats.data.TacticalPlay
import br.com.matchstats.data.TacticalSide
import br.com.matchstats.data.TacticalSnapshot
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.NavyBanner
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

private val historySaver = listSaver<TacticalHistory, Any>(
    save = { listOf(TacticalDocumentCodec.encode(it.current.board), it.current.teamId ?: 0L) },
    restore = { cells -> TacticalDocumentCodec.decode(cells[0] as String)?.let { board ->
        TacticalHistory(TacticalSnapshot(board, (cells[1] as Long).takeUnless { it == 0L }))
    } },
)

@Composable
internal fun TacticalEditorScreen(
    repository: MatchStatsRepository,
    record: TacticalPlay?,
    initial: TacticalSnapshot,
    presenting: Boolean,
    onPresentation: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var history by rememberSaveable(record?.id, stateSaver = historySaver) { mutableStateOf(TacticalHistory(initial)) }
    var savedId by rememberSaveable { mutableStateOf(record?.id) }
    var version by rememberSaveable { mutableStateOf(record?.updatedAt) }
    var savedName by rememberSaveable { mutableStateOf(record?.name.orEmpty()) }
    var baseline by rememberSaveable { mutableStateOf(initial.signature()) }
    var message by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf(CourtTool.SELECT) }
    var selectedSide by rememberSaveable { mutableStateOf<TacticalSide?>(null) }
    var selectedNumber by rememberSaveable { mutableStateOf(0) }
    var attackNumber by rememberSaveable { mutableStateOf(1) }
    var showAssignments by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(0) }
    val action = rememberRecordAction()
    val snapshot = history.current
    val board = snapshot.board
    val signature = remember(snapshot) { snapshot.signature() }
    val dirty = baseline != signature
    val selection = board.markers.find { it.side == selectedSide && it.number == selectedNumber }
    val teamFlow = remember(repository) { repository.observeTeams() }
    val teams by teamFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val basketballTeams = teams.filter { it.sport == Sport.BASKETBALL }
    val teamName = basketballTeams.find { it.id == snapshot.teamId }?.name
    val name = savedName.ifBlank { stringResource(R.string.tactical_new) }

    fun change(newBoard: TacticalBoard, team: Long? = snapshot.teamId) {
        if (presenting || action.running) return
        history = history.edit(TacticalSnapshot(newBoard, team))
        message = false
    }

    BackHandler(enabled = presenting || dirty || action.running) {
        when {
            action.running -> Unit
            presenting -> onPresentation(false)
            else -> dialog = R.string.tactical_discard
        }
    }

    if (presenting) {
        Column(
            Modifier.fillMaxSize().background(MatchStatsColors.Navy).safeDrawingPadding().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = MatchStatsColors.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BasketballCourtCanvas(board, Modifier.fillMaxSize(), maxWidth > maxHeight, readOnly = true)
            }
            MatchStatsButton(stringResource(R.string.tactical_end_present), { onPresentation(false) })
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val contentWidth = (minOf(maxWidth, MatchStatsSpacing.ContentMax) - MatchStatsSpacing.Screen * 2).coerceAtLeast(1.dp)
        val split = maxWidth >= 560.dp && maxWidth > maxHeight
        val canvas: @Composable (Modifier) -> Unit = { modifier ->
            BasketballCourtCanvas(
                board, modifier.clip(MaterialTheme.shapes.medium), horizontal = split,
                readOnly = action.running, tool = tool, selection = selection,
                onSelect = { marker ->
                    selectedSide = marker?.side
                    selectedNumber = marker?.number ?: 0
                    if (marker?.side == TacticalSide.ATTACK) attackNumber = marker.number
                },
                onEdit = { change(it) },
            )
        }
        val heading: @Composable () -> Unit = {
            NavyBanner {
                Column(Modifier.fillMaxWidth(.89f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Text(teamName ?: stringResource(R.string.tactical_no_team), color = MatchStatsColors.OnNavyMuted)
                }
            }
        }
        val options: @Composable () -> Unit = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CourtMode.entries.forEach { mode ->
                    MatchStatsFilterChip(stringResource(mode.label), R.drawable.ic_grid, board.mode == mode,
                        { change(board.copy(mode = mode)) }, enabled = !action.running)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatchStatsFilterChip(stringResource(R.string.tactical_attack), R.drawable.ic_teams, board.showAttack,
                    { change(board.copy(showAttack = !board.showAttack)) }, enabled = !action.running)
                MatchStatsFilterChip(stringResource(R.string.tactical_defense), R.drawable.ic_shield, board.showDefense,
                    { change(board.copy(showDefense = !board.showDefense)) }, enabled = !action.running)
                MatchStatsFilterChip(stringResource(R.string.tactical_ball), R.drawable.ic_basketball, board.showBall,
                    { change(board.copy(showBall = !board.showBall)) }, enabled = !action.running)
            }
        }
        val controls: @Composable ColumnScope.() -> Unit = {
            TacticalToolbar(tool, history, action.running, { tool = it },
                { history = history.undo(); message = false },
                { history = history.redo(); message = false },
                { dialog = R.string.tactical_clear_title },
            )
            if (board.strokes.size == MAX_STROKES) Text(stringResource(R.string.tactical_limit), color = MatchStatsColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MatchStatsButton(stringResource(R.string.tactical_save),
                    { action.clearError(); dialog = R.string.tactical_save }, enabled = !action.running)
                MatchStatsSecondaryButton(stringResource(R.string.tactical_present), { onPresentation(true) }, enabled = !action.running)
                if (savedId != null) MatchStatsSecondaryButton(stringResource(R.string.tactical_rename),
                    { action.clearError(); dialog = R.string.tactical_rename }, enabled = !action.running)
            }
            if (message) Text(stringResource(R.string.tactical_saved), color = MatchStatsColors.GreenInk)
            RecordError(action.error)
            MatchStatsSecondaryButton(stringResource(if (showAssignments) R.string.tactical_hide_assignments else R.string.tactical_assignments),
                { showAssignments = !showAssignments }, enabled = !action.running)
            if (showAssignments) {
                val names = basketballTeams.associate { it.id to it.name } + (0L to stringResource(R.string.tactical_no_team))
                val team = snapshot.teamId?.takeIf { it in names }
                MatchStatsDropdown(stringResource(R.string.practice_optional_team), team ?: 0L,
                    listOf(0L) + basketballTeams.map { it.id }, { names.getValue(it) }, { id ->
                        val newTeam = id.takeUnless { it == 0L }
                        if (newTeam != snapshot.teamId) change(
                            board.copy(markers = board.markers.map { it.copy(playerId = null) }), newTeam,
                        )
                    }, enabled = !action.running)
                val markerNames = (1..5).associateWith { stringResource(R.string.tactical_marker_number, it) }
                MatchStatsDropdown(stringResource(R.string.tactical_marker), attackNumber, markerNames.keys.toList(),
                    { markerNames.getValue(it) }, { number ->
                        attackNumber = number
                        selectedSide = TacticalSide.ATTACK
                        selectedNumber = number
                    }, enabled = !action.running)
                if (team != null) key(team) {
                    val rosterFlow = remember(repository, team) { repository.observeRoster(team) }
                    val roster by rosterFlow.collectAsStateWithLifecycle(initialValue = null)
                    val marker = board.markers.first { it.side == TacticalSide.ATTACK && it.number == attackNumber }
                    TacticalRosterDropdown(roster?.players.orEmpty(), marker, !action.running) { id ->
                        change(board.assign(attackNumber, id))
                    }
                }
                MatchStatsSecondaryButton(stringResource(R.string.tactical_ball_to_player),
                    { change(board.attachBall(attackNumber)) }, enabled = !action.running && board.markers.any {
                        it.side == TacticalSide.ATTACK && it.number == attackNumber && board.shows(it)
                    })
                if (board.markers.any { !it.visible }) MatchStatsSecondaryButton(
                    stringResource(R.string.tactical_restore_markers),
                    { change(board.copy(markers = board.markers.map { it.copy(visible = true) })) },
                    enabled = !action.running,
                )
            }
        }

        if (split) {
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                canvas(Modifier.weight(.59f).fillMaxHeight())
                Column(
                    Modifier.weight(.41f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    heading()
                    options()
                    controls()
                }
            }
        } else {
            MatchStatsScreen {
                heading()
                options()
                val ideal = contentWidth * board.mode.length / 15f
                val height = minOf(ideal, maxOf(150.dp, viewportHeight * .68f))
                canvas(Modifier.fillMaxWidth().height(height))
                controls()
            }
        }
    }

    when (dialog) {
        R.string.tactical_save, R.string.tactical_rename -> TacticalNameDialog(
            dialog, savedName, action.running, action.error, onDismiss = { dialog = 0 },
            onSave = { value ->
                val submitted = snapshot
                var persisted: TacticalPlay? = null
                action.launch({
                    repository.tactics.save(savedId, version, value, submitted).also { persisted = it.second }.first
                }) {
                    persisted?.let { saved ->
                        savedId = saved.id
                        version = saved.updatedAt
                        savedName = saved.name
                        val stored = TacticalDocumentCodec.decode(saved.document) ?: submitted.board
                        val storedSnapshot = TacticalSnapshot(stored, saved.teamId)
                        history = history.edit(storedSnapshot)
                        baseline = storedSnapshot.signature()
                        message = true
                        dialog = 0
                    }
                }
            },
        )
        R.string.tactical_clear_title -> TacticalConfirm(
            R.string.tactical_clear_title, R.string.tactical_clear_confirm, R.string.tactical_clear,
            { change(board.copy(strokes = emptyList())); dialog = 0 }, { dialog = 0 },
        )
        R.string.tactical_discard -> TacticalConfirm(
            R.string.tactical_discard, R.string.tactical_discard_confirm, R.string.tactical_discard,
            onBack, { dialog = 0 },
        )
    }
}

private fun TacticalSnapshot.signature() = 31 * TacticalDocumentCodec.encode(board).hashCode() + (teamId?.hashCode() ?: 0)

@Composable
private fun TacticalToolbar(
    tool: CourtTool,
    history: TacticalHistory,
    busy: Boolean,
    onTool: (CourtTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CourtTool.entries.forEach { option ->
            val icon = when (option) {
                CourtTool.SELECT -> R.drawable.ic_person
                CourtTool.MOVEMENT -> R.drawable.ic_bolt
                CourtTool.PASS -> R.drawable.ic_chevron
                CourtTool.SCREEN -> R.drawable.ic_shield
                CourtTool.ERASE -> R.drawable.ic_delete
            }
            MatchStatsFilterChip(stringResource(option.label), icon, option == tool, { onTool(option) }, enabled = !busy)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MatchStatsSecondaryButton(stringResource(R.string.tactical_undo), onUndo, enabled = !busy && history.canUndo)
        MatchStatsSecondaryButton(stringResource(R.string.tactical_redo), onRedo, enabled = !busy && history.canRedo)
        MatchStatsSecondaryButton(stringResource(R.string.tactical_clear), onClear,
            enabled = !busy && history.current.board.strokes.isNotEmpty())
    }
}

@Composable
private fun TacticalRosterDropdown(players: List<RosterPlayer>, marker: TacticalMarker, enabled: Boolean, onSelect: (Long?) -> Unit) {
    val none = stringResource(R.string.tactical_no_player)
    val names = players.associate { player ->
        player.id to (player.jerseyNumber?.let { number ->
            stringResource(R.string.practice_scope, stringResource(R.string.practice_number, number), player.name)
        } ?: player.name)
    } + (0L to none)
    MatchStatsDropdown(stringResource(R.string.tactical_player), marker.playerId?.takeIf { it in names } ?: 0L,
        listOf(0L) + players.map { it.id }, { names.getValue(it) }, { onSelect(it.takeUnless { id -> id == 0L }) },
        enabled = enabled)
}

@Composable
private fun TacticalNameDialog(
    title: Int, initial: String, busy: Boolean, error: br.com.matchstats.data.WriteResult?,
    onDismiss: () -> Unit, onSave: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val focus = LocalFocusManager.current
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(title)) },
        text = { Column {
            MatchStatsTextField(value, { value = it }, label = stringResource(R.string.tactical_name),
                enabled = !busy, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            RecordError(error)
        } },
        confirmButton = {
            MatchStatsButton(stringResource(R.string.save), { onSave(value) }, enabled = !busy)
        },
        dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss, enabled = !busy) },
    )
}

@Composable
private fun TacticalConfirm(title: Int, message: Int, label: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { MatchStatsButton(stringResource(label), onConfirm, destructive = true) },
        dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), onDismiss) },
    )
}
