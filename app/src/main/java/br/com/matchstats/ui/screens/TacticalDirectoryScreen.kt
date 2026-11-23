package br.com.matchstats.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.data.CourtMode
import br.com.matchstats.data.TacticalPlayStore
import br.com.matchstats.data.TacticalPlaySummary
import br.com.matchstats.data.forPlayerSearch
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsConfirmDelete
import br.com.matchstats.ui.components.MatchStatsEmptyState
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.RecordError
import br.com.matchstats.ui.components.SectionHeading
import br.com.matchstats.ui.components.SportsBanner
import br.com.matchstats.ui.components.rememberRecordAction
import br.com.matchstats.ui.theme.MatchStatsColors

internal val CourtMode.label get() = if (this == CourtMode.HALF) R.string.tactical_half else R.string.tactical_full

@Composable
internal fun TacticalDirectoryScreen(
    plays: List<TacticalPlaySummary>,
    store: TacticalPlayStore,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val action = rememberRecordAction()
    val focus = LocalFocusManager.current
    val needle = query.trim().forPlayerSearch()
    val visible = plays.filter { it.name.forPlayerSearch().contains(needle) }

    MatchStatsScreen(footer = {
        MatchStatsButton(stringResource(R.string.tactical_new), onNew, Modifier.fillMaxWidth(), enabled = !action.running)
    }) {
        SportsBanner(stringResource(R.string.tactical_board), stringResource(R.string.tactical_intro))
        MatchStatsTextField(
            query, { query = it }, label = stringResource(R.string.tactical_search),
            leadingIcon = { MatchStatsIcon(R.drawable.ic_search, tint = MatchStatsColors.GreenInk) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )
        RecordError(action.error)
        SectionHeading(stringResource(R.string.tactical_directory), R.drawable.ic_basketball)
        if (visible.isEmpty()) MatchStatsEmptyState(
            stringResource(if (plays.isEmpty()) R.string.tactical_empty else R.string.tactical_no_match),
            stringResource(if (plays.isEmpty()) R.string.tactical_empty_hint else R.string.tactical_search_hint),
            R.drawable.ic_basketball,
        )
        visible.forEach { play ->
            val duplicateName = stringResource(R.string.tactical_duplicate_name, play.name)
            MatchStatsCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(play.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.practice_scope, stringResource(play.mode.label),
                            play.teamName ?: stringResource(R.string.tactical_no_team),
                        ),
                        color = MatchStatsColors.GreenInk,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(stringResource(R.string.tactical_updated, practiceDateTime(play.updatedAt)),
                        style = MaterialTheme.typography.bodySmall, color = MatchStatsColors.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MatchStatsSecondaryButton(stringResource(R.string.tactical_edit), { onOpen(play.id) }, enabled = !action.running)
                        MatchStatsSecondaryButton(stringResource(R.string.tactical_duplicate), {
                            action.launch({ store.duplicate(play.id, duplicateName) }) { query = "" }
                        }, enabled = !action.running)
                        MatchStatsSecondaryButton(stringResource(R.string.delete), { deleting = play.id }, enabled = !action.running)
                    }
                }
            }
        }
    }

    plays.find { it.id == deleting }?.let { play ->
        MatchStatsConfirmDelete(
            stringResource(R.string.tactical_delete), stringResource(R.string.tactical_confirm_delete, play.name),
            onConfirm = { deleting = null; action.launch({ store.delete(play.id) }) {} },
            onDismiss = { deleting = null },
        )
    }
}
