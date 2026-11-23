package br.com.matchstats.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.map

internal class BoxScoreStore(private val database: MatchStatsDatabase) {
    private val dao = database.boxScores()
    private val matches = database.matches()
    private val stats = database.matchStats()

    suspend fun configuration(sport: Sport, matchId: Long?): BoxConfiguration {
        val all = dao.metrics(sport)
        val active = all.filterNot { it.archived }
        val defaultCatalog = boxCatalog(sport, active)
        val defaults = normalizeBoxColumns(
            dao.defaults(sport)?.decodeBoxLayout() ?: defaultBoxColumns(sport), defaultCatalog.keys,
        )
        if (matchId == null) return BoxConfiguration(sport, defaults, defaultCatalog, defaults)

        val stored = dao.matchLayout(matchId)?.takeIf { it.sport == sport }
        val layout = stored?.layout?.decodeBoxLayout() ?: defaultBoxColumns(sport)
        val retained = layout.mapTo(hashSetOf()) { it.metricId }
        if (all.any { it.archived && it.id !in retained }) {
            dao.measurements(matchId).forEach { retained.add(it.metricId) }
            database.liveGames().recordedMetrics(matchId).forEach { retained.add(it) }
        }
        val catalog = boxCatalog(sport, all.filter { !it.archived || it.id in retained })
        return BoxConfiguration(sport, normalizeBoxColumns(layout, catalog.keys), catalog, defaults)
    }

    suspend fun snapshotDefaults(matchId: Long, sport: Sport) {
        val columns = configuration(sport, null).columns
        dao.upsertMatchLayout(BoxMatchLayout(matchId, sport, columns.encodeBoxLayout()))
    }

    fun observeConfiguration(sport: Sport, matchId: Long?) =
        database.invalidationTracker.createFlow("box_custom_metrics", "box_defaults", "box_match_layouts", "matches", "teams")
            .map {
                database.withTransaction {
                    if (matchId != null && matches.details(matchId)?.team?.sport != sport) return@withTransaction null
                    configuration(sport, matchId)
                }
            }

    fun observeScore(matchId: Long) = database.invalidationTracker.createFlow(
        "matches", "teams", "players", "team_memberships", "player_match_stats",
        "box_custom_metrics", "box_defaults", "box_match_layouts", "box_measurements", "live_sessions", "live_events",
    ).map { score(matchId) }

    suspend fun score(matchId: Long): BoxScore? =
        database.withTransaction {
            val fixture = matches.details(matchId) ?: return@withTransaction null
            if (fixture.match.status != MatchStatus.FINISHED && fixture.live == null) return@withTransaction null
            val saved = stats.forMatch(matchId).associateBy { it.playerId }
            val extras = dao.measurements(matchId).groupBy { it.playerId }
            val roster = database.players().roster(fixture.team.id).associateBy { it.id }
            val players = stats.eligiblePlayers(matchId, fixture.team.id).map { player ->
                BoxPlayer(
                    player, roster[player.id]?.jerseyNumber, saved[player.id]?.values,
                    extras[player.id].orEmpty().associate { it.metricId to it.measurement() },
                )
            }.sortedWith(compareBy<BoxPlayer> { it.jersey == null }.thenBy { it.jersey })
            val opponents = if (fixture.live == null) emptyList()
            else database.liveGames().events(matchId).opponentBox(fixture.team.sport)
            BoxScore(fixture, configuration(fixture.team.sport, matchId), players, opponents)
        }
    suspend fun saveConfiguration(
        sport: Sport,
        matchId: Long?,
        columns: List<BoxColumn>,
        custom: List<BoxCustomMetric>,
        archiveIds: Set<String>,
    ): WriteResult = database.withTransaction {
        val existing = configuration(sport, matchId)
        if (matchId != null) {
            if (matches.details(matchId)?.team?.sport != sport) return@withTransaction WriteResult.RECORD_MISSING
            if (custom.isNotEmpty() || archiveIds.isNotEmpty() || !validColumns(columns, existing.metrics.keys)) {
                return@withTransaction WriteResult.INVALID_BOX_METRIC
            }
            val ordered = normalizeBoxColumns(columns, existing.metrics.keys)
            dao.upsertMatchLayout(BoxMatchLayout(matchId, sport, ordered.encodeBoxLayout()))
            return@withTransaction WriteResult.SAVED
        }

        val saved = dao.metrics(sport).associateBy { it.id }
        val edits = custom.associateBy { it.id }
        val registered = if (edits.isEmpty()) emptyMap() else dao.definitions(edits.keys.toList()).associateBy { it.id }
        if (edits.size != custom.size || archiveIds.any { it !in saved } ||
            edits.keys.any { it in archiveIds } || custom.any { !it.canSaveFor(sport, registered[it.id]) }
        ) {
            return@withTransaction WriteResult.INVALID_BOX_METRIC
        }
        val active = (saved + edits).values.filter { !it.archived && it.id !in archiveIds }
        if (active.map { it.abbreviation.uppercase(java.util.Locale.ROOT) }.distinct().size != active.size) {
            return@withTransaction WriteResult.INVALID_BOX_METRIC
        }
        val available = boxCatalog(sport, active).keys
        if (!validColumns(columns, available)) return@withTransaction WriteResult.INVALID_BOX_METRIC
        val changed = custom + archiveIds.map { saved.getValue(it).copy(archived = true) }
        if (changed.isNotEmpty()) dao.upsertMetrics(changed)
        val ordered = normalizeBoxColumns(columns, available)
        dao.upsertDefault(BoxDefaultLayout(sport, ordered.encodeBoxLayout()))
        WriteResult.SAVED
    }

    private fun validColumns(columns: List<BoxColumn>, available: Set<String>): Boolean =
        columns.all { it.metricId in available } && columns.distinctBy { it.metricId }.size == columns.size

    private fun BoxCustomMetric.canSaveFor(sport: Sport, saved: BoxCustomMetric?): Boolean =
        id.matches(customId) && this.sport == sport && !archived && kind != BoxMetricKind.PERCENT &&
            name.isNotBlank() && name.length <= 60 &&
            abbreviation.isNotBlank() && abbreviation.length <= 8 && abbreviation.none(Char::isWhitespace) &&
            (saved == null || saved.sport == sport && !saved.archived && saved.kind == kind)

    private companion object {
        val customId = Regex("custom_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
