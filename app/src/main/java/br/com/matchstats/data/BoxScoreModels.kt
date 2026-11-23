package br.com.matchstats.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import br.com.matchstats.R
import java.io.Serializable
import java.util.Locale
import java.util.UUID

enum class BoxMetricKind { TOTAL, PAIR, PERCENT }

enum class BoxBuiltin(
    val id: String,
    val sport: Sport,
    val nameRes: Int,
    val shortRes: Int,
    val canonical: StatMetric? = null,
    val kind: BoxMetricKind = BoxMetricKind.TOTAL,
) {
    MINUTES("bk_minutes", Sport.BASKETBALL, R.string.box_minutes, R.string.box_min_short),
    POINTS("bk_points", Sport.BASKETBALL, R.string.stat_points, R.string.box_pts_short, StatMetric.POINTS),
    REBOUNDS("bk_rebounds", Sport.BASKETBALL, R.string.stat_rebounds, R.string.box_reb_short, StatMetric.REBOUNDS),
    OFFENSIVE("bk_oreb", Sport.BASKETBALL, R.string.box_offensive, R.string.box_oreb_short),
    DEFENSIVE("bk_dreb", Sport.BASKETBALL, R.string.box_defensive, R.string.box_dreb_short),
    BASKET_ASSISTS("bk_assists", Sport.BASKETBALL, R.string.stat_assists, R.string.box_ast_short, StatMetric.ASSISTS),
    STEALS("bk_steals", Sport.BASKETBALL, R.string.box_steals, R.string.box_stl_short),
    BLOCKS("bk_blocks", Sport.BASKETBALL, R.string.box_blocks, R.string.box_blk_short),
    TURNOVERS("bk_turnovers", Sport.BASKETBALL, R.string.box_turnovers, R.string.box_to_short),
    PERSONAL_FOULS("bk_fouls", Sport.BASKETBALL, R.string.box_personal_fouls, R.string.box_pf_short),
    TWO("bk_two", Sport.BASKETBALL, R.string.box_two, R.string.box_two_short, kind = BoxMetricKind.PAIR),
    THREE("bk_three", Sport.BASKETBALL, R.string.box_three, R.string.box_three_short, kind = BoxMetricKind.PAIR),
    FREE("bk_free", Sport.BASKETBALL, R.string.box_free, R.string.box_free_short, kind = BoxMetricKind.PAIR),
    FIELD_PERCENT("bk_fg_pct", Sport.BASKETBALL, R.string.box_field_percent, R.string.box_fg_short, kind = BoxMetricKind.PERCENT),
    THREE_PERCENT("bk_three_pct", Sport.BASKETBALL, R.string.box_three_percent, R.string.box_three_pct_short, kind = BoxMetricKind.PERCENT),
    FREE_PERCENT("bk_free_pct", Sport.BASKETBALL, R.string.box_free_percent, R.string.box_free_pct_short, kind = BoxMetricKind.PERCENT),

    GOALS("fb_goals", Sport.FOOTBALL, R.string.stat_goals, R.string.box_g_short, StatMetric.GOALS),
    FOOTBALL_ASSISTS("fb_assists", Sport.FOOTBALL, R.string.stat_assists, R.string.box_ast_short, StatMetric.ASSISTS),
    SHOTS("fb_shots", Sport.FOOTBALL, R.string.box_shots, R.string.box_fin_short),
    ON_TARGET("fb_on_target", Sport.FOOTBALL, R.string.box_on_target, R.string.box_target_short),
    PASSES("fb_passes", Sport.FOOTBALL, R.string.box_passes, R.string.box_pass_short, kind = BoxMetricKind.PAIR),
    TACKLES("fb_tackles", Sport.FOOTBALL, R.string.box_tackles, R.string.box_tackle_short),
    INTERCEPTIONS("fb_interceptions", Sport.FOOTBALL, R.string.box_interceptions, R.string.box_int_short),
    FOULS("fb_fouls", Sport.FOOTBALL, R.string.box_fouls, R.string.box_foul_short),
    YELLOW("fb_yellow", Sport.FOOTBALL, R.string.stat_yellow_cards, R.string.box_yellow_short, StatMetric.YELLOW_CARDS),
    RED("fb_red", Sport.FOOTBALL, R.string.stat_red_cards, R.string.box_red_short, StatMetric.RED_CARDS),
    SAVES("fb_saves", Sport.FOOTBALL, R.string.box_saves, R.string.box_save_short),
    CORNERS("fb_corners", Sport.FOOTBALL, R.string.live_corners, R.string.live_corner_short);

    val percentageSources: List<BoxBuiltin>
        get() = when (this) {
            FIELD_PERCENT -> listOf(TWO, THREE)
            THREE_PERCENT -> listOf(THREE)
            FREE_PERCENT -> listOf(FREE)
            else -> emptyList()
        }
}

@Entity(tableName = "box_custom_metrics")
data class BoxCustomMetric(
    @PrimaryKey val id: String,
    val sport: Sport,
    val name: String,
    val abbreviation: String,
    val kind: BoxMetricKind,
    val archived: Boolean = false,
) : Serializable {
    companion object {
        fun create(sport: Sport, name: String, short: String, kind: BoxMetricKind) =
            BoxCustomMetric(
                "custom_${UUID.randomUUID()}", sport, name.trim(), short.trim().uppercase(Locale.ROOT), kind,
            )
    }
}

data class BoxMetric(val id: String, val kind: BoxMetricKind, val builtin: BoxBuiltin?, val custom: BoxCustomMetric?)

data class BoxColumn(val metricId: String, val enabled: Boolean) : Serializable

data class BoxConfiguration(
    val sport: Sport,
    val columns: List<BoxColumn>,
    val metrics: Map<String, BoxMetric>,
    val defaults: List<BoxColumn>,
) {
    val visible get() = columns.filter { it.enabled }.mapNotNull { metrics[it.metricId] }
}

data class BoxMeasurement(val value: Int, val attempts: Int? = null) {
    fun validFor(kind: BoxMetricKind): Boolean = value >= 0 && when (kind) {
        BoxMetricKind.TOTAL -> attempts == null
        BoxMetricKind.PAIR -> attempts != null && attempts >= value
        BoxMetricKind.PERCENT -> false
    }
}

data class BoxPlayer(
    val player: Player,
    val jersey: Int?,
    val canonical: StatValues?,
    val measurements: Map<String, BoxMeasurement>,
) {
    fun measurement(metric: BoxMetric): BoxMeasurement? {
        val canonicalField = metric.builtin?.canonical
        return if (canonicalField == null) measurements[metric.id] else canonical?.let { BoxMeasurement(it[canonicalField]) }
    }
}

data class BoxScore(
    val fixture: MatchWithTeam,
    val config: BoxConfiguration,
    val players: List<BoxPlayer>,
    val opponents: List<BoxPlayer> = emptyList(),
) {
    val hasData get() = players.any { it.canonical != null || it.measurements.isNotEmpty() }
}

data class BoxCell(val value: Long, val attempts: Long?) {
    val percentage: Double? get() = attempts?.takeIf { it > 0 }?.let { 100.0 * value / it }
}

fun BoxMetric.cell(players: List<BoxPlayer>): BoxCell? {
    val entries = if (kind == BoxMetricKind.PERCENT) {
        players.flatMap { player -> builtin?.percentageSources.orEmpty().mapNotNull { player.measurements[it.id] } }
    } else {
        players.mapNotNull { it.measurement(this) }
    }
    if (entries.isEmpty()) return null
    val attempts = if (kind == BoxMetricKind.TOTAL) null else entries.sumOf { it.attempts?.toLong() ?: 0L }
    return BoxCell(entries.sumOf { it.value.toLong() }, attempts)
}

internal fun defaultBoxColumns(sport: Sport) =
    BoxBuiltin.entries.filter { it.sport == sport }.map { BoxColumn(it.id, it != BoxBuiltin.CORNERS) }

internal fun boxCatalog(sport: Sport, custom: List<BoxCustomMetric>): Map<String, BoxMetric> = buildMap {
    BoxBuiltin.entries.filter { it.sport == sport }.forEach { put(it.id, BoxMetric(it.id, it.kind, it, null)) }
    custom.forEach { put(it.id, BoxMetric(it.id, it.kind, null, it)) }
}

internal fun normalizeBoxColumns(saved: List<BoxColumn>, available: Set<String>): List<BoxColumn> {
    val listed = saved.filter { it.metricId in available }.distinctBy { it.metricId }
    val included = listed.mapTo(hashSetOf()) { it.metricId }
    return listed + (available - included).map { BoxColumn(it, false) }
}
