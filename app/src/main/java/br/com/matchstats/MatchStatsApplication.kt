package br.com.matchstats

import android.app.Application
import br.com.matchstats.data.MatchStatsDatabase
import br.com.matchstats.data.MatchStatsRepository

class MatchStatsApplication : Application() {
    val repository by lazy { MatchStatsRepository(MatchStatsDatabase.open(this)) }
}
