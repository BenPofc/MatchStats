package br.com.matchstats.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.ui.MatchStatsTab
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@Composable
fun MatchStatsScaffold(
    selected: MatchStatsTab,
    onTabSelected: (MatchStatsTab) -> Unit,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    showBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = { if (showBars) MatchStatsTopBar(title = title, onBack = onBack) },
        bottomBar = { if (showBars) MatchStatsBottomNavigation(selected, onTabSelected) },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            content()
        }
    }
}

@Composable
fun MatchStatsTopBar(title: String? = null, onBack: (() -> Unit)? = null) {
    Surface(color = MatchStatsColors.Navy, contentColor = MatchStatsColors.White) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.widthIn(max = MatchStatsSpacing.ContentMax)
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(horizontal = MatchStatsSpacing.Large, vertical = MatchStatsSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        MatchStatsIcon(R.drawable.ic_back, description = stringResource(R.string.back))
                    }
                } else {
                    BrandMark(Modifier.size(width = 34.dp, height = 38.dp))
                }
                Column {
                    Text(
                        title ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (title == null) {
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MatchStatsColors.OnNavyMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val barWidth = size.width * 0.25f
        val radius = CornerRadius(barWidth / 2)
        for (index in 0..2) {
            val top = size.height * (0.54f - index * 0.22f)
            val bottom = size.height * if (index == 2) 0.82f else 0.97f
            drawRoundRect(
                color = MatchStatsColors.BrightGreen,
                topLeft = Offset(size.width * index * 0.36f, top),
                size = Size(barWidth, bottom - top),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
fun MatchStatsBottomNavigation(selected: MatchStatsTab, onTabSelected: (MatchStatsTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalDivider(color = MatchStatsColors.Border)
            val largeText = LocalDensity.current.fontScale > 1.4f
            Row(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .widthIn(max = MatchStatsSpacing.ContentMax)
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalAlignment = Alignment.Top,
            ) {
                MatchStatsTab.entries.forEach { tab ->
                    val isSelected = tab == selected
                    val color = if (isSelected) MatchStatsColors.GreenInk else MatchStatsColors.Muted
                    val tabWeight = when {
                        !largeText -> 1f
                        tab == MatchStatsTab.Stats -> 1.3f
                        tab == MatchStatsTab.Home || tab == MatchStatsTab.Profile -> 0.85f
                        else -> 1f
                    }
                    Column(
                        modifier = Modifier.weight(tabWeight)
                            .heightIn(min = 64.dp)
                            .selectable(selected = isSelected, role = Role.Tab, onClick = { onTabSelected(tab) })
                            .padding(horizontal = 2.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny),
                    ) {
                        Box(
                            modifier = Modifier.widthIn(max = 62.dp)
                                .fillMaxWidth()
                                .height(30.dp)
                                .background(
                                    if (isSelected) MatchStatsColors.SoftGreen else MaterialTheme.colorScheme.surface,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            MatchStatsIcon(tab.icon, tint = color)
                        }
                        Text(
                            stringResource(tab.label),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}
