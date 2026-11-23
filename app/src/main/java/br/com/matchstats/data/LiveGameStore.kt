package br.com.matchstats.data

import android.os.SystemClock
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.map

internal class LiveGameStore(
    private val database: MatchStatsDatabase,
    private val clockOwner: String = PROCESS_CLOCK_OWNER,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.liveGames()
    private val matches = database.matches()
    private val players = database.players()
    private val stats = database.matchStats()
    private val statistics = LiveStatistics(database)

    fun clock(session: LiveSession): LiveClock = session.clock(elapsed(), clockOwner)

    fun observe(matchId: Long) = database.invalidationTracker.createFlow(
        "matches", "teams", "players", "team_memberships", "player_match_stats", "live_sessions",
        "live_events", "live_preferences", "live_match_layouts", "box_custom_metrics",
    ).map {
        database.withTransaction {
            val fixture = matches.details(matchId) ?: return@withTransaction null
            val roster = players.roster(fixture.team.id)
            val rosterIds = roster.mapTo(hashSetOf()) { it.id }
            val former = stats.eligiblePlayers(matchId, fixture.team.id).filterNot { it.id in rosterIds }
                .map { RosterPlayer(it, null, null, false) }
            LiveGame(
                fixture, fixture.live, roster + former, dao.events(matchId), configuration(fixture.team.sport, matchId),
            )
        }
    }

    fun observeConfiguration(sport: Sport, matchId: Long?) =
        database.invalidationTracker.createFlow("live_preferences", "live_match_layouts", "box_custom_metrics", "matches", "teams")
            .map {
                database.withTransaction {
                    if (matchId != null && matches.details(matchId)?.team?.sport != sport) return@withTransaction null
                    configuration(sport, matchId)
                }
            }

    private suspend fun configuration(sport: Sport, matchId: Long?): LiveConfiguration {
        val catalog = buildMap {
            LiveAction.entries.filter { it.sport == sport }.forEach {
                put(it.id, LiveActionInfo(it.id, it.labelRes, it.shortRes))
            }
            database.boxScores().metrics(sport).filter { !it.archived && it.kind == BoxMetricKind.TOTAL }.forEach {
                put(it.id, LiveActionInfo(it.id, null, null, it.name, it.abbreviation))
            }
        }
        val defaults = normalizeBoxColumns(
            dao.preference(sport)?.decodeBoxLayout() ?: defaultLiveColumns(sport), catalog.keys,
        )
        val override = matchId?.let { dao.layout(it)?.takeIf { row -> row.sport == sport } }?.layout?.decodeBoxLayout()
        return LiveConfiguration(sport, normalizeBoxColumns(override ?: defaults, catalog.keys), catalog, defaults)
    }

    suspend fun saveConfiguration(sport: Sport, matchId: Long?, columns: List<BoxColumn>): WriteResult =
        database.withTransaction {
            if (matchId != null && matches.details(matchId)?.team?.sport != sport) {
                return@withTransaction WriteResult.RECORD_MISSING
            }
            val available = configuration(sport, matchId).catalog.keys
            if (columns.any { it.metricId !in available } || columns.distinctBy { it.metricId }.size != columns.size) {
                return@withTransaction WriteResult.INVALID_BOX_METRIC
            }
            val encoded = normalizeBoxColumns(columns, available).encodeBoxLayout()
            if (matchId == null) dao.savePreference(LivePreference(sport, encoded))
            else dao.saveLayout(LiveMatchLayout(matchId, sport, encoded))
            WriteResult.SAVED
        }

    suspend fun start(matchId: Long, minutes: Int, shotSeconds: Int): WriteResult = database.withTransaction {
        val fixture = matches.details(matchId) ?: return@withTransaction WriteResult.RECORD_MISSING
        if (fixture.match.status != MatchStatus.SCHEDULED) return@withTransaction WriteResult.MATCH_ALREADY_FINISHED
        if (fixture.live != null) return@withTransaction WriteResult.SAVED
        val sport = fixture.team.sport
        if (minutes !in 1..120 || sport == Sport.BASKETBALL && shotSeconds !in 1..120) {
            return@withTransaction WriteResult.INVALID_LIVE_DURATION
        }
        val baseline = stats.forMatch(matchId).sumOf {
            (if (sport == Sport.FOOTBALL) it.values.goals else it.values.points).toLong()
        }
        val initialHome = safeCount(maxOf(baseline, (fixture.match.teamScore ?: 0).toLong()))
            ?: return@withTransaction WriteResult.INVALID_STATS
        if (dao.layout(matchId)?.sport != sport) {
            val config = configuration(sport, null)
            dao.saveLayout(LiveMatchLayout(matchId, sport, config.columns.encodeBoxLayout()))
        }
        dao.saveSession(
            LiveSession(
                matchId, minutes * 60, if (sport == Sport.BASKETBALL) shotSeconds else 0,
                homeScore = initialHome, awayScore = fixture.match.opponentScore ?: 0,
            ),
        )
        WriteResult.SAVED
    }

    suspend fun setRunning(matchId: Long, running: Boolean) = changeClock(matchId) { session, _, now ->
        if (running && session.clock(now, clockOwner).gameMs == 0L) null
        else session.checkpoint(now, clockOwner, running)
    }

    suspend fun checkpoint(matchId: Long) = changeClock(matchId) { session, _, now ->
        if (session.runningFrom == null || session.clockOwner != clockOwner) session
        else session.checkpoint(now, clockOwner)
    }

    suspend fun resetShot(matchId: Long, short: Boolean) = changeClock(matchId) { session, sport, now ->
        if (sport != Sport.BASKETBALL) null
        else session.checkpoint(now, clockOwner).copy(shotMs = (if (short) 14 else session.shotSeconds) * 1000L)
    }

    suspend fun nextPeriod(matchId: Long) = changeClock(matchId) { session, sport, now ->
        if (sport == Sport.FOOTBALL && session.period >= 2 || session.period >= 99) null
        else session.checkpoint(now, clockOwner, false).copy(
            period = session.period + 1,
            gameMs = session.periodSeconds * 1000L,
            shotMs = session.shotSeconds * 1000L,
        )
    }

    private suspend fun changeClock(
        matchId: Long,
        transform: (LiveSession, Sport, Long) -> LiveSession?,
    ): WriteResult = database.withTransaction {
        val fixture = matches.details(matchId) ?: return@withTransaction WriteResult.RECORD_MISSING
        val session = fixture.live ?: return@withTransaction WriteResult.RECORD_MISSING
        if (fixture.match.status != MatchStatus.SCHEDULED) return@withTransaction WriteResult.MATCH_ALREADY_FINISHED
        val updated = transform(session, fixture.team.sport, elapsed()) ?: return@withTransaction WriteResult.INVALID_LIVE_CLOCK
        if (updated != session) dao.saveSession(updated)
        WriteResult.SAVED
    }

    suspend fun finish(matchId: Long): WriteResult = database.withTransaction {
        val fixture = matches.details(matchId) ?: return@withTransaction WriteResult.RECORD_MISSING
        val session = fixture.live ?: return@withTransaction WriteResult.RECORD_MISSING
        if (fixture.match.status == MatchStatus.FINISHED) return@withTransaction WriteResult.SAVED
        if (fixture.team.sport == Sport.FOOTBALL && stats.totalGoals(matchId) > session.homeScore) {
            return@withTransaction WriteResult.SCORE_BELOW_RECORDED_GOALS
        }
        dao.saveSession(session.checkpoint(elapsed(), clockOwner, false))
        matches.update(fixture.match.copy(
            status = MatchStatus.FINISHED, teamScore = session.homeScore, opponentScore = session.awayScore,
        ))
        WriteResult.SAVED
    }

    suspend fun discard(matchId: Long): WriteResult = database.withTransaction {
        val fixture = matches.details(matchId) ?: return@withTransaction WriteResult.RECORD_MISSING
        val session = fixture.live ?: return@withTransaction WriteResult.RECORD_MISSING
        if (fixture.match.status == MatchStatus.FINISHED) return@withTransaction WriteResult.MATCH_ALREADY_FINISHED
        val events = dao.events(matchId).activeLiveEvents()
        val homeBaseline = safeCount(session.homeScore.toLong() - events.filter { it.side == LiveSide.HOME }.sumOf { it.effect().score })
            ?: return@withTransaction WriteResult.INVALID_LIVE_TOTAL
        val plan = statistics.plan(session, events.map { it to -1 }, homeBaseline, fixture.team.sport == Sport.FOOTBALL)
            ?: return@withTransaction WriteResult.INVALID_LIVE_TOTAL
        statistics.apply(matchId, plan)
        dao.discard(matchId)
        WriteResult.SAVED
    }

    suspend fun record(matchId: Long, id: String, input: LiveInput, previousId: String? = null): WriteResult =
        mutate(matchId, id, previousId) { fixture, session, previous, sequence, now ->
            if (fixture.match.status == MatchStatus.FINISHED && previous == null) return@mutate null
            val sport = fixture.team.sport
            val builtin = LiveAction.byId[input.actionId]
            val catalog = configuration(sport, matchId)
            val custom = if (builtin == null) {
                database.boxScores().metrics(sport).firstOrNull { it.id == input.actionId && it.kind == BoxMetricKind.TOTAL }
                    ?.takeIf { !it.archived || previous?.actionId == it.id }
            } else null
            if (builtin?.sport != sport && custom == null || input.value !in 1..9999 || builtin != null && input.value != 1) {
                return@mutate null
            }
            if (previous == null && catalog.visible.none { it.id == input.actionId }) return@mutate null
            val home = input.side == LiveSide.HOME
            val player = if (home && input.playerId != null) {
                stats.eligiblePlayers(matchId, fixture.team.id).firstOrNull { it.id == input.playerId }
                    ?: return@mutate null
            } else null
            if (!home && input.playerId != null || home && input.jersey != null || input.jersey != null && input.jersey !in 0..99) {
                return@mutate null
            }
            if (sport == Sport.BASKETBALL && player == null && input.jersey == null || custom != null && player == null && input.jersey == null) {
                return@mutate null
            }
            val homeJersey = player?.let { p -> players.roster(fixture.team.id).firstOrNull { it.id == p.id }?.jerseyNumber }
            LiveEvent(
                id, matchId, sequence, previousId, if (previous == null) LiveOperation.RECORD else LiveOperation.CORRECT,
                input.side, player?.id, if (home) homeJersey ?: previous?.jersey?.takeIf { previous.playerId == player?.id } else input.jersey,
                player?.name ?: if (!home && input.jersey != null) input.opponentName?.trim()?.take(60)?.ifBlank { null } else null,
                input.actionId, builtin?.primary?.id ?: custom?.id, custom?.name,
                previous?.period ?: session.period,
                previous?.clockMs ?: session.clock(now, clockOwner).position(session, sport),
                input.value, wallClock(),
            )
        }

    suspend fun undo(matchId: Long, id: String, previousId: String): WriteResult =
        mutate(matchId, id, previousId) { _, _, previous, sequence, _ ->
            previous?.copy(id = id, sequence = sequence, previousId = previousId, operation = LiveOperation.UNDO, createdAt = wallClock())
        }

    suspend fun adjustScore(matchId: Long, id: String, side: LiveSide, score: Int, previousId: String? = null): WriteResult =
        mutate(matchId, id, previousId) { fixture, session, previous, sequence, now ->
            if (previous != null && previous.actionId != SCORE_ADJUSTMENT) return@mutate null
            val existing = if (side == LiveSide.HOME) session.homeScore else session.awayScore
            val reversed = previous?.value?.toLong()?.takeIf { previous.side == side } ?: 0L
            val difference = score.toLong() - (existing - reversed)
            if (score < 0 || difference !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() || difference == 0L && previous == null) {
                return@mutate null
            }
            LiveEvent(
                id, matchId, sequence, previousId, if (previous == null) LiveOperation.RECORD else LiveOperation.CORRECT,
                side, null, null, null, SCORE_ADJUSTMENT, null, null,
                previous?.period ?: session.period,
                previous?.clockMs ?: session.clock(now, clockOwner).position(session, fixture.team.sport),
                difference.toInt(), wallClock(),
            )
        }

    private suspend fun mutate(
        matchId: Long,
        id: String,
        previousId: String?,
        create: suspend (MatchWithTeam, LiveSession, LiveEvent?, Long, Long) -> LiveEvent?,
    ): WriteResult = database.withTransaction {
        if (id.isBlank() || id.length > 160) return@withTransaction WriteResult.INVALID_LIVE_EVENT
        val duplicate = dao.event(id)
        if (duplicate != null) {
            return@withTransaction if (duplicate.matchId == matchId && duplicate.previousId == previousId) WriteResult.SAVED
            else WriteResult.INVALID_LIVE_EVENT
        }
        val fixture = matches.details(matchId) ?: return@withTransaction WriteResult.RECORD_MISSING
        val session = fixture.live ?: return@withTransaction WriteResult.RECORD_MISSING
        val log = dao.events(matchId)
        val active = log.activeLiveEvents()
        val previous = if (previousId == null) null else {
            active.firstOrNull { it.id == previousId } ?: return@withTransaction WriteResult.LIVE_EVENT_ALREADY_CHANGED
        }
        val now = elapsed()
        val event = create(fixture, session, previous, (log.lastOrNull()?.sequence ?: 0) + 1, now)
            ?: return@withTransaction WriteResult.INVALID_LIVE_EVENT
        val visibleEvent = event.takeUnless { it.operation == LiveOperation.UNDO }
        val changes = listOfNotNull(previous?.let { it to -1 }, visibleEvent?.let { it to 1 })
        fun scoreDelta(side: LiveSide) = changes.filter { it.first.side == side }.sumOf { (value, sign) -> value.effect().score * sign }
        val home = safeCount(session.homeScore + scoreDelta(LiveSide.HOME))
            ?: return@withTransaction WriteResult.INVALID_LIVE_TOTAL
        val away = safeCount(session.awayScore + scoreDelta(LiveSide.AWAY))
            ?: return@withTransaction WriteResult.INVALID_LIVE_TOTAL
        val football = fixture.team.sport == Sport.FOOTBALL
        if (football) {
            val remaining = active.filterNot { it.id == previousId } + listOfNotNull(visibleEvent)
            val assigned = remaining.filter { it.side == LiveSide.AWAY && it.jersey != null }
                .sumOf { it.effect().metrics[BoxBuiltin.GOALS.id]?.value ?: 0L }
            if (assigned > away) return@withTransaction WriteResult.INVALID_LIVE_TOTAL
        }
        val plan = statistics.plan(session, changes, home, football)
            ?: return@withTransaction WriteResult.INVALID_LIVE_TOTAL
        statistics.apply(matchId, plan)
        dao.insertEvent(event)
        val finished = fixture.match.status == MatchStatus.FINISHED
        dao.saveSession(
            session.checkpoint(now, clockOwner, false.takeIf { finished })
                .copy(homeScore = home, awayScore = away, measuredZeroes = plan.zeroes),
        )
        if (finished) matches.update(fixture.match.copy(teamScore = home, opponentScore = away))
        WriteResult.SAVED
    }

    private companion object {
        val PROCESS_CLOCK_OWNER = UUID.randomUUID().toString()
    }
}
