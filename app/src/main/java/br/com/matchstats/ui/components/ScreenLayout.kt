package br.com.matchstats.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.matchstats.R
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsSpacing

@Composable
fun MatchStatsScreen(
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MatchStatsSpacing.ContentMax).fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = MatchStatsSpacing.Screen,
                        vertical = MatchStatsSpacing.Medium,
                    ),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Medium),
                content = content,
            )
            if (footer != null) {
                Box(Modifier.fillMaxWidth().padding(
                    horizontal = MatchStatsSpacing.Screen,
                    vertical = MatchStatsSpacing.Small,
                )) { footer() }
            }
        }
    }
}

@Composable
fun SportsBanner(title: String, subtitle: String? = null) {
    NavyBanner {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val showArt = maxWidth >= 295.dp && LocalDensity.current.fontScale <= 1.4f
            Column(
                modifier = Modifier.fillMaxWidth(if (showArt) 0.64f else 0.94f)
                    .heightIn(min = if (subtitle == null) 92.dp else 118.dp)
                    .padding(horizontal = MatchStatsSpacing.Screen, vertical = MatchStatsSpacing.Medium)
                    .align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(MatchStatsSpacing.Tiny, Alignment.CenterVertically),
            ) {
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MatchStatsColors.OnNavyMuted)
                }
            }
            if (showArt) {
                Image(
                    painter = painterResource(R.drawable.sports_balls),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = MatchStatsSpacing.Small, bottom = 3.dp)
                        .width(minOf(maxWidth * 0.37f, if (subtitle == null) 124.dp else 152.dp)),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}

@Composable
fun NavyBanner(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MatchStatsColors.Navy,
        contentColor = MatchStatsColors.White,
    ) {
        Box(Modifier.fillMaxWidth()) {
            val largeText = LocalDensity.current.fontScale > 1.4f
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.horizontalGradient(listOf(MatchStatsColors.Navy, MatchStatsColors.BannerNavy)))
                val accentStart = if (largeText || size.width < 295.dp.toPx()) 0.92f else 0.60f
                val diagonal = Path().apply {
                    moveTo(size.width * accentStart, size.height)
                    lineTo(size.width * (accentStart + (1f - accentStart) * 0.7f), size.height * 0.45f)
                    lineTo(size.width * 0.96f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(
                    diagonal,
                    Brush.linearGradient(listOf(MatchStatsColors.Green, MatchStatsColors.BannerGreen)),
                )
                val accent = Path().apply {
                    moveTo(size.width * (accentStart + (1f - accentStart) * 0.55f), size.height)
                    lineTo(size.width, size.height * 0.30f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(accent, MatchStatsColors.BrightGreen.copy(alpha = 0.65f))
            }
            content()
        }
    }
}
