package br.com.matchstats.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardType
import br.com.matchstats.R
import br.com.matchstats.data.LiveGame
import br.com.matchstats.data.LiveInput
import br.com.matchstats.data.LiveSide
import br.com.matchstats.data.Sport
import br.com.matchstats.data.nonNegativeInt
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsFilterChip
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing
import java.io.Serializable
import java.text.Normalizer
import java.util.Locale

internal data class LiveSelection(
    val side: LiveSide = LiveSide.HOME,
    val homePlayer: Long? = null,
    val jersey: String = "",
    val awayName: String = "",
) : Serializable {
    fun input(action: String, value: Int = 1) = LiveInput(
        side, action, homePlayer.takeIf { side == LiveSide.HOME },
        jersey.nonNegativeInt().takeIf { side == LiveSide.AWAY },
        awayName.takeIf { side == LiveSide.AWAY },
        value,
    )

    fun identified(game: LiveGame): Boolean =
        if (side == LiveSide.HOME) game.players.any { it.id == homePlayer } else jersey.nonNegativeInt()?.let { it in 0..99 } == true
}

@Composable
internal fun LiveSideSelector(side: LiveSide, select: (LiveSide) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
        MatchStatsFilterChip(
            stringResource(R.string.live_home), R.drawable.ic_teams, side == LiveSide.HOME,
            { select(LiveSide.HOME) }, Modifier.weight(1f),
        )
        MatchStatsFilterChip(
            stringResource(R.string.live_away), R.drawable.ic_shield, side == LiveSide.AWAY,
            { select(LiveSide.AWAY) }, Modifier.weight(1f),
        )
    }
}

@Composable
internal fun LivePlayerPicker(game: LiveGame, selection: LiveSelection, onChange: (LiveSelection) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var enterName by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val football = game.fixture.team.sport == Sport.FOOTBALL

    MatchStatsCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(MatchStatsSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
        ) {
            LiveSideSelector(selection.side) {
                focus.clearFocus()
                onChange(selection.copy(side = it))
            }
            if (selection.side == LiveSide.HOME) {
                val selected = game.players.firstOrNull { it.id == selection.homePlayer }
                val visible = game.players.filter {
                    it.name.normalized().contains(query.trim().normalized()) ||
                        it.jerseyNumber?.toString()?.contains(query.trim()) == true
                }.sortedWith(compareBy({ it.jerseyNumber == null }, { it.jerseyNumber }, { it.name }))
                MatchStatsFilterChip(
                    stringResource(R.string.live_select_player), R.drawable.ic_search, searching,
                    {
                        searching = !searching
                        if (!searching) { query = ""; focus.clearFocus() }
                    },
                )
                if (searching) {
                    MatchStatsTextField(
                        query, { query = it }, label = stringResource(R.string.live_search_player),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                ) {
                    if (football) {
                        MatchStatsFilterChip(
                            stringResource(R.string.live_team_event), R.drawable.ic_teams, selected == null,
                            { focus.clearFocus(); onChange(selection.copy(homePlayer = null)) },
                        )
                    }
                    for (player in visible) {
                        val label = if (searching || player.jerseyNumber == null) player.name
                        else stringResource(R.string.jersey_short, player.jerseyNumber)
                        MatchStatsFilterChip(label, R.drawable.ic_person, selected?.id == player.id, {
                            focus.clearFocus()
                            onChange(selection.copy(homePlayer = player.id))
                        })
                    }
                }
                if (selected != null) {
                    val jersey = selected.jerseyNumber?.let { stringResource(R.string.jersey_short, it) }
                    Text(
                        listOfNotNull(jersey, selected.name).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                        color = MatchStatsColors.GreenInk,
                    )
                } else if (game.players.isEmpty() && !football) {
                    Text(stringResource(R.string.live_no_players), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val guests = game.activeEvents.filter { it.side == LiveSide.AWAY && it.jersey != null }
                    .groupBy { requireNotNull(it.jersey) }.toSortedMap()
                if (guests.isNotEmpty() || football) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small),
                    ) {
                        if (football) {
                            MatchStatsFilterChip(
                                stringResource(R.string.live_team_event), R.drawable.ic_teams, selection.jersey.isBlank(),
                                { focus.clearFocus(); onChange(selection.copy(jersey = "", awayName = "")) },
                            )
                        }
                        guests.forEach { (number, history) ->
                            MatchStatsFilterChip(
                                stringResource(R.string.jersey_short, number), R.drawable.ic_person,
                                selection.jersey.nonNegativeInt() == number,
                                {
                                    focus.clearFocus()
                                    onChange(selection.copy(
                                        jersey = number.toString(),
                                        awayName = history.asReversed().firstNotNullOfOrNull { it.playerName }.orEmpty(),
                                    ))
                                },
                            )
                        }
                    }
                }
                MatchStatsTextField(
                    selection.jersey,
                    { text ->
                        if (text.length <= 2 && text.all(Char::isDigit)) {
                            val known = text.nonNegativeInt()?.let { guests[it] }?.asReversed()?.firstNotNullOfOrNull { it.playerName }
                            onChange(selection.copy(jersey = text, awayName = known.orEmpty()))
                        }
                    },
                    label = stringResource(R.string.live_opponent_jersey),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                )
                MatchStatsFilterChip(
                    stringResource(R.string.live_add_name), R.drawable.ic_person, enterName, { enterName = !enterName },
                )
                if (enterName) {
                    MatchStatsTextField(
                        selection.awayName, { onChange(selection.copy(awayName = it.take(60))) },
                        label = stringResource(R.string.live_opponent_name),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    )
                } else if (selection.awayName.isNotBlank()) {
                    Text(selection.awayName, style = MaterialTheme.typography.titleSmall, color = MatchStatsColors.GreenInk)
                }
            }
        }
    }
}

private val accents = Regex("\\p{M}+")
private fun String.normalized() =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(accents, "").lowercase(Locale.ROOT)
