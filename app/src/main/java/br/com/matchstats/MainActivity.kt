package br.com.matchstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import br.com.matchstats.ui.MatchStatsApp
import br.com.matchstats.ui.theme.MatchStatsColors
import br.com.matchstats.ui.theme.MatchStatsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.light(
                Color.Transparent.toArgb(),
                MatchStatsColors.Navy.toArgb(),
            ),
        )
        val repository = (application as MatchStatsApplication).repository
        setContent {
            MatchStatsTheme {
                MatchStatsApp(repository)
            }
        }
    }
}