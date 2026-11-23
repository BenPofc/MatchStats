package br.com.matchstats.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.annotation.StringRes
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.Player
import br.com.matchstats.data.PlayerInput
import br.com.matchstats.data.PlayerTeam
import br.com.matchstats.data.Position
import br.com.matchstats.data.Sport
import br.com.matchstats.data.Team
import br.com.matchstats.data.TeamInput
import br.com.matchstats.data.WriteResult
import br.com.matchstats.ui.components.FormButtons
import br.com.matchstats.ui.components.IconTile
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsDropdown
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.SportSelector
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.errorLabel
import br.com.matchstats.ui.icon
import br.com.matchstats.ui.label
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@Composable
fun TeamFormScreen(
    team: Team?,
    hasPositions: Boolean = false,
    hasMatchHistory: Boolean = false,
    saveTeam: suspend (TeamInput) -> WriteResult,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(team?.name.orEmpty()) }
    var sport by rememberSaveable { mutableStateOf(team?.sport ?: Sport.FOOTBALL) }
    var category by rememberSaveable { mutableStateOf(team?.category.orEmpty()) }
    var coach by rememberSaveable { mutableStateOf(team?.coachName.orEmpty()) }
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current
    val nameError = action.error == WriteResult.NAME_REQUIRED
    BackHandler(enabled = action.running) {}

    MatchStatsScreen {
        SportsBanner(stringResource(if (team == null) R.string.new_team else R.string.edit_team))
        SportSelector(sport, { sport = it }, enabled = !action.running && !hasMatchHistory)
        if (hasMatchHistory) {
            Text(
                stringResource(R.string.error_team_has_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MatchStatsColors.Muted,
            )
        }
        MatchStatsTextField(
            value = name,
            onValueChange = { name = it; action.clearError() },
            label = stringResource(R.string.team_name),
            leadingIcon = { MatchStatsIcon(R.drawable.ic_teams, tint = MatchStatsColors.GreenInk) },
            error = if (nameError) stringResource(R.string.error_name_required) else null,
            enabled = !action.running,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next,
            ),
        )
        MatchStatsTextField(
            value = category,
            onValueChange = { category = it },
            label = stringResource(R.string.category),
            leadingIcon = { MatchStatsIcon(R.drawable.ic_grid, tint = MatchStatsColors.GreenInk) },
            enabled = !action.running,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next,
            ),
        )
        MatchStatsTextField(
            value = coach,
            onValueChange = { coach = it },
            label = stringResource(R.string.coach),
            leadingIcon = { MatchStatsIcon(R.drawable.ic_person, tint = MatchStatsColors.GreenInk) },
            enabled = !action.running,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done,
            ),
        )
        if (team != null && sport != team.sport && hasPositions) {
            Text(
                stringResource(R.string.team_positions_reset),
                style = MaterialTheme.typography.bodyMedium,
                color = MatchStatsColors.Muted,
            )
        }
        FormButtons(
            action = action,
            onSave = {
                focus.clearFocus()
                val input = TeamInput(name, sport, category, coach)
                action.launch({ saveTeam(input) }, onSaved)
            },
            onCancel = onCancel,
            showError = !nameError,
        )
    }
}

@Composable
fun PlayerFormScreen(
    teams: List<Team>,
    player: Player?,
    initialTeamId: Long?,
    savePlayer: suspend (PlayerInput) -> WriteResult,
    onSaved: () -> Unit,
    membership: PlayerTeam? = null,
    @StringRes title: Int = R.string.add_player,
    nameOnly: Boolean = false,
    teamRequired: Boolean = false,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(player?.name.orEmpty()) }
    var teamId by rememberSaveable { mutableStateOf(membership?.team?.id ?: initialTeamId) }
    var jersey by rememberSaveable { mutableStateOf(membership?.jerseyNumber?.toString().orEmpty()) }
    var position by rememberSaveable { mutableStateOf(membership?.position) }
    var captain by rememberSaveable { mutableStateOf(membership?.captain == true) }
    val selectedTeam = teams.firstOrNull { it.id == teamId }
    val teamLabel = if (teamRequired) R.string.team_required_label else R.string.optional_team
    val selectedPosition = position?.takeIf { it.sport == selectedTeam?.sport }
    val positions: List<Position?> = if (selectedTeam == null) emptyList() else {
        listOf(null) + Position.entries.filter { it.sport == selectedTeam.sport }
    }
    val positionLabels = positions.associateWith { stringResource(it?.label ?: R.string.no_position) }
    val sportLabels = Sport.entries.associateWith { stringResource(it.label) }
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current
    val fieldError = when (action.error) {
        WriteResult.NAME_REQUIRED -> R.string.player_name
        WriteResult.INVALID_JERSEY, WriteResult.DUPLICATE_JERSEY -> R.string.jersey_number
        WriteResult.TEAM_REQUIRED, WriteResult.TEAM_MISSING, WriteResult.DUPLICATE_MEMBERSHIP -> teamLabel
        WriteResult.INVALID_POSITION -> R.string.position
        else -> null
    }
    val errorText = action.error?.errorLabel?.let { stringResource(it) }
    BackHandler(enabled = action.running) {}

    MatchStatsScreen {
        SportsBanner(stringResource(title), if (player != null && !nameOnly) player.name else null)
        selectedTeam?.let { team ->
            MatchStatsCard(Modifier.fillMaxWidth(), containerColor = MatchStatsColors.SoftGreen) {
                Row(
                    modifier = Modifier.padding(MatchStatsSpacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MatchStatsIcon(team.sport.icon, tint = MatchStatsColors.GreenInk)
                    Column {
                        Text(stringResource(R.string.sport), style = MaterialTheme.typography.labelMedium)
                        Text(sportLabels.getValue(team.sport), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (player == null || nameOnly) MatchStatsTextField(
            value = name,
            onValueChange = { name = it; action.clearError() },
            label = stringResource(R.string.player_name),
            error = errorText.takeIf { fieldError == R.string.player_name },
            leadingIcon = { MatchStatsIcon(R.drawable.ic_person, tint = MatchStatsColors.GreenInk) },
            enabled = !action.running,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next,
            ),
        )
        if (!nameOnly) {
            val noTeam = stringResource(R.string.no_team)
            MatchStatsDropdown(
                label = stringResource(teamLabel),
                selected = selectedTeam,
                options = if (teamRequired) teams else listOf(null) + teams,
                selectedLabel = { it?.name.orEmpty() },
                optionLabel = { team ->
                    if (team == null) noTeam else
                        listOfNotNull(team.name, sportLabels.getValue(team.sport), team.category).joinToString(" · ")
                },
                onSelect = { option ->
                    teamId = option?.id
                    position = position?.takeIf { it.sport == option?.sport }
                    action.clearError()
                },
                error = errorText.takeIf { fieldError == teamLabel },
                enabled = !action.running && (!teamRequired || initialTeamId == null),
                leadingIcon = { MatchStatsIcon(R.drawable.ic_teams, tint = MatchStatsColors.GreenInk) },
            )
            if (selectedTeam != null) {
                MatchStatsTextField(
                    value = jersey,
                    onValueChange = { jersey = it; action.clearError() },
                    label = stringResource(R.string.jersey_number),
                    leadingIcon = { MatchStatsIcon(R.drawable.ic_grid, tint = MatchStatsColors.GreenInk) },
                    error = errorText.takeIf { fieldError == R.string.jersey_number },
                    enabled = !action.running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
                MatchStatsDropdown(
                    label = stringResource(R.string.position),
                    selected = selectedPosition,
                    options = positions,
                    optionLabel = { positionLabels.getValue(it) },
                    onSelect = { position = it; action.clearError() },
                    error = errorText.takeIf { fieldError == R.string.position },
                    enabled = !action.running,
                    leadingIcon = { MatchStatsIcon(R.drawable.ic_grid, tint = MatchStatsColors.GreenInk) },
                )
                MatchStatsCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                            .toggleable(
                                value = captain, enabled = !action.running, role = Role.Switch,
                                onValueChange = { captain = it },
                            )
                            .padding(MatchStatsSpacing.Medium),
                        horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(R.drawable.ic_trophy)
                        Text(
                            stringResource(R.string.captain),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Switch(
                            checked = captain,
                            onCheckedChange = null,
                            enabled = !action.running,
                            colors = SwitchDefaults.colors(checkedTrackColor = MatchStatsColors.ActionGreen),
                        )
                    }
                }
            } else if (teamRequired && teams.isEmpty()) {
                Text(stringResource(R.string.no_available_teams), color = MatchStatsColors.Muted)
            }
        }
        FormButtons(
            action = action,
            onSave = {
                focus.clearFocus()
                val input = PlayerInput(name, if (nameOnly) null else teamId, jersey, selectedPosition, captain)
                action.launch({ savePlayer(input) }, onSaved)
            },
            onCancel = onCancel,
            showError = fieldError == null,
        )
    }
}
