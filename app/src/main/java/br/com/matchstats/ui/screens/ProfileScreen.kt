package br.com.matchstats.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import br.com.matchstats.BuildConfig
import br.com.matchstats.R
import br.com.matchstats.ui.components.BrandMark
import br.com.matchstats.ui.components.IconTile
import br.com.matchstats.ui.components.MatchStatsButton
import br.com.matchstats.ui.components.MatchStatsCard
import br.com.matchstats.ui.components.MatchStatsIcon
import br.com.matchstats.ui.components.MatchStatsScreen
import br.com.matchstats.ui.components.MatchStatsSecondaryButton
import br.com.matchstats.ui.components.MatchStatsTextField
import br.com.matchstats.ui.components.NavyBanner
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@Composable
fun ProfileScreen() {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.getSharedPreferences("matchstats_profile", Context.MODE_PRIVATE) }
    var name by rememberSaveable { mutableStateOf(preferences.getString("name", "").orEmpty()) }
    var draft by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val appVersion = stringResource(R.string.version, BuildConfig.VERSION_NAME)

    MatchStatsScreen {
        NavyBanner {
            Row(
                modifier = Modifier.fillMaxWidth(0.84f).heightIn(min = 116.dp).padding(MatchStatsSpacing.Screen),
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(R.drawable.ic_person, size = 60.dp)
                Column(verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny)) {
                    Text(
                        stringResource(R.string.local_profile),
                        Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        name.ifEmpty { stringResource(R.string.no_profile) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MatchStatsColors.OnNavyMuted,
                    )
                }
            }
        }
        ProfileOption(stringResource(R.string.profile_edit_name), R.drawable.ic_person) {
            draft = name
            editing = true
        }
        ProfileOption(stringResource(R.string.about_app), R.drawable.ic_info) { showAbout = true }
        MatchStatsCard(Modifier.fillMaxWidth(), containerColor = MatchStatsColors.SoftGreen) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(MatchStatsSpacing.Screen),
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = MatchStatsColors.Navy, shape = MaterialTheme.shapes.small) {
                    BrandMark(Modifier.padding(11.dp).size(30.dp))
                }
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(appVersion, style = MaterialTheme.typography.bodyMedium, color = MatchStatsColors.Muted)
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.profile_edit_name)) },
            text = {
                MatchStatsTextField(
                    draft,
                    onValueChange = { draft = it },
                    label = stringResource(R.string.profile_name_label),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done,
                    ),
                )
            },
            confirmButton = {
                MatchStatsButton(stringResource(R.string.save), {
                    name = draft.trim()
                    preferences.edit().putString("name", name).apply()
                    editing = false
                })
            },
            dismissButton = { MatchStatsSecondaryButton(stringResource(R.string.cancel), { editing = false }) },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { MatchStatsIcon(R.drawable.ic_info, tint = MatchStatsColors.GreenInk) },
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Small)) {
                    Text(stringResource(R.string.about_description))
                    Text(appVersion)
                }
            },
            confirmButton = { MatchStatsButton(stringResource(R.string.close), { showAbout = false }) },
        )
    }
}

@Composable
private fun ProfileOption(label: String, icon: Int, onClick: () -> Unit) {
    MatchStatsCard(Modifier.fillMaxWidth()) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(MatchStatsSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(icon)
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                MatchStatsIcon(R.drawable.ic_chevron, tint = MatchStatsColors.Muted)
            }
        }
    }
}
