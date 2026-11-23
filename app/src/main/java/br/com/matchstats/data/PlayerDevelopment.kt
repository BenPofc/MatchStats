package br.com.matchstats.data

import br.com.matchstats.R
import java.time.LocalDate
import java.time.ZoneId

internal enum class DevelopmentPeriod(val label: Int) {
    WEEK(R.string.development_week), MONTH(R.string.development_month),
    QUARTER(R.string.development_quarter), YEAR(R.string.development_year), ALL(R.string.development_all);

    fun bounds(today: LocalDate, zone: ZoneId): LongRange? {
        val first = when (this) {
            WEEK -> today.minusDays(6)
            MONTH -> today.minusDays(29)
            QUARTER -> today.minusDays(89)
            YEAR -> today.minusYears(1).plusDays(1)
            ALL -> return null
        }
        return first.atStartOfDay(zone).toInstant().toEpochMilli() until
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

internal enum class DevelopmentValue { TOTAL, RATE, SESSIONS, MAKES, ATTEMPTS, REPETITIONS }

internal data class DevelopmentMetric(
    val id: String,
    val value: DevelopmentValue,
    val titleRes: Int? = null,
    val title: String? = null,
    val box: BoxMetric? = null,
)

internal data class DevelopmentDrill(val key: String, val name: String, val measure: PracticeMeasure)

internal data class DevelopmentPoint(
    val id: Long,
    val startsAt: Long,
    val label: String,
    val teamId: Long?,
    val measure: BoxCell,
) {
    fun plotted(rate: Boolean): Double? = if (rate) measure.percentage else measure.value.toDouble()
}

internal data class DevelopmentSeries(val points: List<DevelopmentPoint>) {
    val total: Long get() = points.sumOf { it.measure.value }
    val attempts: Long get() = points.sumOf { it.measure.attempts ?: 0 }
    val average: Double? get() = total.toDouble().takeIf { points.isNotEmpty() }?.div(points.size)
    val accuracy: Double? get() = BoxCell(total, attempts).percentage
}

internal const val DEVELOPMENT_ALL_TEAMS = 0L
internal const val DEVELOPMENT_UNASSIGNED = -1L

internal data class PlayerDevelopment(
    val player: Player,
    val sport: Sport,
    val teams: List<Team>,
    val games: List<PlayerGame>,
    val measurements: Map<Long, Map<String, BoxMeasurement>>,
    val custom: List<BoxCustomMetric>,
    val practices: List<PracticeFact>,
) {
    fun gameMetrics(): List<DevelopmentMetric> {
        val main = if (sport == Sport.BASKETBALL) listOf(
            BoxBuiltin.POINTS, BoxBuiltin.REBOUNDS, BoxBuiltin.BASKET_ASSISTS,
            BoxBuiltin.STEALS, BoxBuiltin.BLOCKS, BoxBuiltin.TURNOVERS,
            BoxBuiltin.FREE_PERCENT, BoxBuiltin.TWO, BoxBuiltin.THREE_PERCENT,
            BoxBuiltin.FIELD_PERCENT, BoxBuiltin.MINUTES,
        ) else listOf(BoxBuiltin.GOALS, BoxBuiltin.FOOTBALL_ASSISTS, BoxBuiltin.SHOTS, BoxBuiltin.ON_TARGET,
            BoxBuiltin.PASSES, BoxBuiltin.TACKLES, BoxBuiltin.INTERCEPTIONS, BoxBuiltin.SAVES)
        val builtins = (main + BoxBuiltin.entries.filter { it.kind == BoxMetricKind.TOTAL && it.sport == sport }).distinct()
        val catalog = boxCatalog(sport, custom)
        return (builtins.map { catalog.getValue(it.id) } + custom.map { catalog.getValue(it.id) }).map { box ->
            val titleRes = when (box.builtin) {
                BoxBuiltin.TWO -> R.string.development_two_percent
                BoxBuiltin.PASSES -> R.string.development_pass_percent
                else -> box.builtin?.nameRes
            }
            DevelopmentMetric(box.id, if (box.kind == BoxMetricKind.TOTAL) DevelopmentValue.TOTAL else DevelopmentValue.RATE,
                titleRes, box.custom?.name, box)
        }
    }

    fun drills(teamId: Long): List<DevelopmentDrill> =
        practices.filter { matchesTeam(it.teamId, teamId) }.mapNotNull(PracticeFact::developmentDrill)
            .distinctBy { it.key }.sortedBy { it.name.forPlayerSearch() }

    fun practiceMetrics(drill: DevelopmentDrill?): List<DevelopmentMetric> = buildList {
        add(DevelopmentMetric("sessions", DevelopmentValue.SESSIONS, R.string.development_practices_count))
        if (drill?.measure != PracticeMeasure.COUNT) {
            add(DevelopmentMetric("practice-rate", DevelopmentValue.RATE, R.string.development_accuracy))
            add(DevelopmentMetric("makes", DevelopmentValue.MAKES, R.string.practice_makes))
            add(DevelopmentMetric("attempts", DevelopmentValue.ATTEMPTS, R.string.practice_attempts))
        }
        if (drill?.measure != PracticeMeasure.MAKES_AND_ATTEMPTS) {
            add(DevelopmentMetric("repetitions", DevelopmentValue.REPETITIONS, R.string.development_repetitions))
        }
    }

    fun gameSeries(metric: DevelopmentMetric, teamId: Long, range: LongRange?, matchTitle: (String, String) -> String): DevelopmentSeries {
        val box = metric.box ?: return DevelopmentSeries(emptyList())
        val points = games.asSequence()
            .filter { it.sport == sport && inScope(it.match.teamId, it.match.startsAt, teamId, range) }
            .mapNotNull { game ->
                val match = game.match
                val row = BoxPlayer(player, null, game.values, measurements[match.id].orEmpty())
                val cell = box.cell(listOf(row)) ?: return@mapNotNull null
                DevelopmentPoint(match.id, match.startsAt, matchTitle(game.teamName, match.opponent), match.teamId, cell)
            }.toList()
        return chronological(points)
    }

    fun practiceSeries(metric: DevelopmentMetric, teamId: Long, range: LongRange?, drill: DevelopmentDrill?): DevelopmentSeries {
        val points = practices.filter {
            it.sport == sport && inScope(it.teamId, it.startsAt, teamId, range) &&
                (drill == null || it.developmentDrill()?.key == drill.key)
        }.groupBy { it.practiceId }.values.mapNotNull { group ->
            val first = group.first()
            val measure = when (metric.value) {
                DevelopmentValue.SESSIONS -> BoxCell(1, null)
                DevelopmentValue.REPETITIONS -> group.filter { it.measure == PracticeMeasure.COUNT }.takeIf { it.isNotEmpty() }
                    ?.let { BoxCell(it.sumOf { fact -> fact.value ?: 0 }, null) }
                DevelopmentValue.RATE, DevelopmentValue.MAKES, DevelopmentValue.ATTEMPTS -> {
                    val pairs = group.filter { it.measure == PracticeMeasure.MAKES_AND_ATTEMPTS && it.attempts != null }
                    if (pairs.isEmpty()) null
                    else {
                        val makes = pairs.sumOf { it.value ?: 0 }
                        val attempts = pairs.sumOf { it.attempts ?: 0 }
                        when (metric.value) {
                            DevelopmentValue.RATE -> BoxCell(makes, attempts)
                            DevelopmentValue.MAKES -> BoxCell(makes, null)
                            else -> BoxCell(attempts, null)
                        }
                    }
                }
                else -> null
            } ?: return@mapNotNull null
            DevelopmentPoint(first.practiceId, first.startsAt, first.title, first.teamId, measure)
        }
        return chronological(points)
    }
}

private fun chronological(points: List<DevelopmentPoint>) =
    DevelopmentSeries(points.sortedWith(compareBy({ it.startsAt }, { it.id })))

private fun inScope(team: Long?, start: Long, filter: Long, range: LongRange?) =
    matchesTeam(team, filter) && (range == null || start in range)

private fun matchesTeam(team: Long?, filter: Long) = when (filter) {
    DEVELOPMENT_ALL_TEAMS -> true
    DEVELOPMENT_UNASSIGNED -> team == null
    else -> team == filter
}

private fun PracticeFact.developmentDrill(): DevelopmentDrill? {
    val name = drillName ?: return null
    val kind = measure ?: return null
    return DevelopmentDrill("${kind.name}|${name.forPlayerSearch()}", name, kind)
}
