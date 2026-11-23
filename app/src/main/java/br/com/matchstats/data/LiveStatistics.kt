package br.com.matchstats.data

internal data class LiveStatPlan(
    val canonical: Map<Long, StatValues>,
    val extras: List<BoxStoredMeasurement>,
    val removals: Map<Long, List<String>>,
    val zeroes: String,
)

internal class LiveStatistics(private val database: MatchStatsDatabase) {
    private val stats = database.matchStats()
    private val box = database.boxScores()

    suspend fun plan(
        session: LiveSession,
        differences: List<Pair<LiveEvent, Int>>,
        homeScore: Int,
        football: Boolean,
    ): LiveStatPlan? {
        val matchId = session.matchId
        val updates = differences.collectHomeDeltas()
        val existing = stats.forMatch(matchId).associate { it.playerId to it.values }
        val measured = box.measurements(matchId).associateBy { it.playerId to it.metricId }
        val builtins = BoxBuiltin.entries.associateBy { it.id }
        val zeroes = session.measuredZeroes.split(',').filterTo(mutableSetOf()) { it.isNotEmpty() }
        val canonical = mutableMapOf<Long, StatValues>()
        val extras = mutableListOf<BoxStoredMeasurement>()
        val removals = mutableMapOf<Long, List<String>>()

        for ((playerId, deltas) in updates) {
            val current = existing[playerId] ?: StatValues()
            val fields = StatMetric.entries.associateWithTo(mutableMapOf()) { current[it] }
            val removed = mutableListOf<String>()
            for ((id, delta) in deltas) {
                if (delta == LiveDelta()) continue
                val metric = builtins[id]
                val field = metric?.canonical
                if (field != null) {
                    fields[field] = safeCount(fields.getValue(field).toLong() + delta.value) ?: return null
                    continue
                }
                val previous = measured[playerId to id]
                val zeroKey = "$playerId:$id"
                if (previous == null) zeroes.remove(zeroKey)
                if (previous?.value == 0 && (previous.attempts ?: 0) == 0) zeroes.add(zeroKey)
                val value = safeCount((previous?.value ?: 0).toLong() + delta.value) ?: return null
                val isPair = metric?.kind == BoxMetricKind.PAIR
                val attempts = if (isPair) {
                    safeCount((previous?.attempts ?: 0).toLong() + delta.attempts) ?: return null
                } else null
                if (attempts != null && value > attempts) return null
                if (value == 0 && (attempts ?: 0) == 0 && zeroKey !in zeroes) {
                    removed.add(id)
                } else {
                    extras.add(BoxStoredMeasurement(matchId, playerId, id, value, attempts))
                }
            }
            canonical[playerId] = StatValues.from(fields)
            if (removed.isNotEmpty()) removals[playerId] = removed
        }
        val updated = existing + canonical
        if (football && updated.values.sumOf { it.goals.toLong() } > homeScore) return null
        return LiveStatPlan(canonical, extras, removals, zeroes.sorted().joinToString(","))
    }

    suspend fun apply(matchId: Long, plan: LiveStatPlan) {
        if (plan.extras.isNotEmpty()) {
            // The Phase 6 measurements use the canonical player/match row as their parent.
            val additions = plan.extras.map { it.playerId }.distinct()
            stats.upsert(additions.map { PlayerMatchStats(matchId, it, plan.canonical.getValue(it)) })
            box.upsertMeasurements(plan.extras)
        }
        for ((playerId, metricIds) in plan.removals) box.clearMeasurements(matchId, playerId, metricIds)
        persistCanonicalRows(stats, box, matchId, plan.canonical)
    }

    suspend fun keepsEventContributions(
        matchId: Long,
        updates: Map<Long, StatValues>,
        measurements: Map<String, BoxMeasurement?> = emptyMap(),
        measurementPlayer: Long? = null,
    ): Boolean {
        val events = database.liveGames().events(matchId).activeLiveEvents()
        if (events.isEmpty()) return true
        val contributions = events.map { it to 1 }.collectHomeDeltas()
        val fields = BoxBuiltin.entries.mapNotNull { metric -> metric.canonical?.let { metric.id to it } }.toMap()
        for ((playerId, values) in updates) {
            for ((id, delta) in contributions[playerId].orEmpty()) {
                val field = fields[id] ?: continue
                if (values[field] < delta.value) return false
            }
        }
        for ((id, value) in measurements) {
            val delta = contributions[measurementPlayer]?.get(id) ?: continue
            val baseline = (value?.value ?: 0) - delta.value
            val attempts = (value?.attempts ?: 0) - delta.attempts
            if (baseline < 0 || attempts < 0 || delta.attempts > 0 && baseline > attempts) return false
        }
        return true
    }

    private fun List<Pair<LiveEvent, Int>>.collectHomeDeltas(): Map<Long, Map<String, LiveDelta>> {
        val result = mutableMapOf<Long, MutableMap<String, LiveDelta>>()
        for ((event, sign) in this) {
            val player = event.playerId ?: continue
            if (event.side != LiveSide.HOME) continue
            val metrics = result.getOrPut(player) { mutableMapOf() }
            for ((metricId, delta) in event.effect().metrics) {
                metrics[metricId] = (metrics[metricId] ?: LiveDelta()) + delta.times(sign)
            }
        }
        return result
    }
}

internal fun safeCount(value: Long): Int? = value.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

internal suspend fun persistCanonicalRows(
    stats: MatchStatsDao,
    box: BoxScoreDao,
    matchId: Long,
    updates: Map<Long, StatValues>,
) {
    if (updates.isEmpty()) return
    val measured = box.measuredPlayers(matchId).toHashSet()
    val zero = StatValues()
    val rows = updates.filter { (id, values) -> values != zero || id in measured }
        .map { (id, values) -> PlayerMatchStats(matchId, id, values) }
    if (rows.isNotEmpty()) stats.upsert(rows)
    for ((id, values) in updates) {
        if (values == zero && id !in measured) stats.delete(matchId, id)
    }
}
