package br.com.matchstats.data

import androidx.room.withTransaction
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class TeamInput(
    val name: String,
    val sport: Sport,
    val category: String,
    val coachName: String,
) {
    fun validate(): WriteResult? = if (name.isBlank()) WriteResult.NAME_REQUIRED else null
}

data class PlayerInput(
    val name: String,
    val teamId: Long?,
    val jersey: String,
    val position: Position?,
    val captain: Boolean,
) {
    fun validate(): WriteResult? = when {
        name.isBlank() -> WriteResult.NAME_REQUIRED
        teamId != null && jersey.isNotBlank() && (jersey.nonNegativeInt()?.let { it in 0..99 } != true) -> WriteResult.INVALID_JERSEY
        else -> null
    }
}

data class MembershipInput(
    val teamId: Long?,
    val jersey: String,
    val position: Position?,
    val captain: Boolean,
)

enum class WriteResult {
    SAVED,
    NAME_REQUIRED,
    TEAM_REQUIRED,
    INVALID_JERSEY,
    DUPLICATE_JERSEY,
    INVALID_POSITION,
    TEAM_MISSING,
    RECORD_MISSING,
    OPPONENT_REQUIRED,
    DATE_REQUIRED,
    TIME_REQUIRED,
    INVALID_MATCH_TIME,
    INVALID_TEAM_SCORE,
    INVALID_OPPONENT_SCORE,
    MATCH_ALREADY_FINISHED,
    MATCH_HAS_STATS,
    TEAM_HAS_MATCHES,
    DUPLICATE_MEMBERSHIP,
    MATCH_NOT_FINISHED,
    INVALID_MATCH_PLAYER,
    INVALID_STATS,
    INVALID_BOX_METRIC,
    INVALID_BOX_PAIR,
    LIVE_ACTIVE,
    LIVE_SCORE_AUDIT,
    LIVE_EVENT_TOTALS,
    INVALID_LIVE_DURATION,
    INVALID_LIVE_CLOCK,
    INVALID_LIVE_EVENT,
    INVALID_LIVE_TOTAL,
    LIVE_EVENT_ALREADY_CHANGED,
    GOALS_EXCEED_SCORE,
    SCORE_BELOW_RECORDED_GOALS,
    PRACTICE_PLAYERS_REQUIRED,
    PRACTICE_SPORT_LOCKED,
    PRACTICE_PLAYER_HAS_BATCHES,
    PRACTICE_CLOSED,
    PRACTICE_DUPLICATE_DRILL,
    PRACTICE_SUBMISSION_CONFLICT,
    INVALID_TACTICAL_PLAY,
    TACTICAL_PLAY_CHANGED,
    FAILED,
}

class MatchStatsRepository(private val database: MatchStatsDatabase) {
    private val teams = database.teams()
    private val players = database.players()
    private val matches = database.matches()
    private val stats = database.matchStats()
    private val boxDao = database.boxScores()
    private val boxStore = BoxScoreStore(database)
    private val liveDao = database.liveGames()
    private val liveStatistics = LiveStatistics(database)
    internal val live = LiveGameStore(database)
    internal val practices = PracticeStore(database)
    internal val tactics = TacticalPlayStore(database)

    fun observeBoxScore(matchId: Long) = boxStore.observeScore(matchId)

    internal suspend fun readMatchReport(matchId: Long): BoxScore? =
        boxStore.score(matchId)?.takeIf { it.fixture.match.hasFinalReport }

    internal fun observeDevelopment(playerId: Long, sport: Sport) =
        database.invalidationTracker.createFlow(
            "players", "team_memberships", "teams", "matches", "player_match_stats",
            "box_measurements", "box_custom_metrics", "practice_sessions", "practice_participants",
            "practice_drills", "practice_batches",
        ).map {
            database.withTransaction {
                val player = players.find(playerId) ?: return@withTransaction null
                val measurements = boxDao.playerMeasurements(playerId, sport)
                val recorded = measurements.mapTo(hashSetOf()) { it.metricId }
                val memberships = players.memberships(playerId).filter { it.team.sport == sport }
                val games = stats.playerGames(playerId).filter { it.sport == sport }
                val practices = database.practices().readFacts(sport, playerId, null)
                val relevantTeams = (memberships.map { it.team.id } + games.map { it.match.teamId } +
                    practices.mapNotNull { it.teamId }).toSet()
                PlayerDevelopment(
                    player, sport, teams.forSport(sport).filter { it.id in relevantTeams }, games,
                    measurements.groupBy { it.matchId }.mapValues { (_, values) ->
                        values.associate { it.metricId to it.measurement() }
                    },
                    boxDao.metrics(sport).filter { it.id in recorded }, practices,
                )
            }
        }

    fun observeBoxConfiguration(sport: Sport, matchId: Long? = null) =
        boxStore.observeConfiguration(sport, matchId)

    suspend fun saveBoxConfiguration(
        sport: Sport,
        matchId: Long?,
        columns: List<BoxColumn>,
        custom: List<BoxCustomMetric> = emptyList(),
        archiveIds: Set<String> = emptySet(),
    ) = boxStore.saveConfiguration(sport, matchId, columns, custom, archiveIds)

    fun observeDashboard() = database.dashboard().observeCounts()
    fun observeTeamSummaries() = teams.observeSummaries()
    fun observeTeams() = teams.observeTeams()
    fun observeRoster(id: Long) =
        database.invalidationTracker.createFlow("teams", "team_memberships", "players", "matches").map {
            database.withTransaction {
                val team = teams.find(id) ?: return@withTransaction null
                TeamRoster(team, players.roster(id), matches.containsTeam(id))
            }
        }

    private fun playerChanges() =
        database.invalidationTracker.createFlow("teams", "team_memberships", "players", "matches", "player_match_stats")

    fun observeDirectory() = playerChanges().map {
        database.withTransaction {
            val memberships = players.allMemberships().groupBy { it.playerId }
            val totals = stats.playerTotals(null).groupBy { it.playerId }
            players.all().map { DirectoryPlayer(it, memberships[it.id].orEmpty(), totals[it.id].orEmpty()) }
        }
    }

    fun observePlayer(id: Long) = playerChanges().map {
        database.withTransaction {
            val player = players.find(id) ?: return@withTransaction null
            PlayerDetails(DirectoryPlayer(player, players.memberships(id), stats.playerTotals(id)), stats.playerGames(id))
        }
    }
    fun observeMatches(status: MatchStatus) = matches.observeByStatus(status)
    fun observeMatch(id: Long) = matches.observeDetails(id)

    fun observeSportStatistics(sport: Sport) =
        database.invalidationTracker.createFlow("teams", "matches", "players", "player_match_stats").map {
            database.withTransaction {
                SportStatistics(teams.forSport(sport), matches.scoresForSport(sport), stats.totalsForSport(sport))
            }
        }

    fun observeTeamPerformance(teamId: Long) =
        matches.observeScoresForTeam(teamId).map(::summarizePerformance)

    internal fun observeHome() = combine(
        database.invalidationTracker.createFlow("teams", "players", "matches", "live_sessions", "practice_sessions"),
        flow {
            while (true) {
                emit(Unit)
                delay(30_000)
            }
        },
    ) { _, _ ->
        database.withTransaction {
            val now = System.currentTimeMillis()
            HomeDashboard(
                database.dashboard().counts(), matches.upcoming(now), matches.recentResults(),
                database.practices().count(), database.practices().next(now),
            )
        }
    }

    fun observeMatchSheet(matchId: Long) =
        database.invalidationTracker.createFlow("matches", "teams", "players", "team_memberships", "player_match_stats").map {
            database.withTransaction {
                val fixture = matches.details(matchId) ?: return@withTransaction null
                MatchStatsSheet(fixture, stats.eligiblePlayers(matchId, fixture.team.id), stats.forMatch(matchId))
            }
        }

    suspend fun saveTeam(id: Long?, input: TeamInput): WriteResult {
        input.validate()?.let { return it }
        return database.withTransaction {
            val previous = if (id == null) null else {
                teams.find(id) ?: return@withTransaction WriteResult.RECORD_MISSING
            }
            if (previous != null && previous.sport != input.sport && matches.containsTeam(previous.id)) {
                return@withTransaction WriteResult.TEAM_HAS_MATCHES
            }
            val team = Team(
                name = input.name.trim(),
                sport = input.sport,
                category = input.category.trim().ifEmpty { null },
                coachName = input.coachName.trim().ifEmpty { null },
                createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                id = previous?.id ?: 0,
            )
            if (previous == null) {
                teams.insert(team)
            } else {
                teams.update(team)
                if (previous.sport != team.sport) players.clearPositions(team.id)
            }
            WriteResult.SAVED
        }
    }

    suspend fun createPlayer(input: PlayerInput): WriteResult {
        input.validate()?.let { return it }
        return database.withTransaction {
            if (input.teamId == null) {
                players.insert(Player(input.name.trim()))
                return@withTransaction WriteResult.SAVED
            }
            val membership = MembershipInput(input.teamId, input.jersey, input.position, input.captain)
            writeMembership(null, input.name.trim(), membership, editing = false)
        }
    }

    suspend fun renamePlayer(id: Long, name: String): WriteResult {
        if (name.isBlank()) return WriteResult.NAME_REQUIRED
        return database.withTransaction {
            val player = players.find(id) ?: return@withTransaction WriteResult.RECORD_MISSING
            players.update(player.copy(name = name.trim()))
            WriteResult.SAVED
        }
    }

    suspend fun saveMembership(playerId: Long, input: MembershipInput, editing: Boolean): WriteResult =
        database.withTransaction {
            if (players.find(playerId) == null) return@withTransaction WriteResult.RECORD_MISSING
            writeMembership(playerId, null, input, editing)
        }

    // The caller holds the transaction so validation and writes are atomic.
    private suspend fun writeMembership(
        playerId: Long?,
        newName: String?,
        input: MembershipInput,
        editing: Boolean,
    ): WriteResult {
        val teamId = input.teamId ?: return WriteResult.TEAM_REQUIRED
        val team = teams.find(teamId) ?: return WriteResult.TEAM_MISSING
        if (input.position != null && input.position.sport != team.sport) return WriteResult.INVALID_POSITION
        val jersey = input.jersey.nonNegativeInt()
        if (input.jersey.isNotBlank() && (jersey == null || jersey !in 0..99)) return WriteResult.INVALID_JERSEY
        val exists = playerId != null && players.hasMembership(playerId, teamId)
        if (exists && !editing) return WriteResult.DUPLICATE_MEMBERSHIP
        if (!exists && editing) return WriteResult.RECORD_MISSING
        if (jersey != null && players.jerseyAssigned(teamId, jersey, playerId ?: 0)) {
            return WriteResult.DUPLICATE_JERSEY
        }
        val id = playerId ?: players.insert(Player(requireNotNull(newName)))
        val membership = TeamMembership(id, teamId, jersey, input.position, input.captain)
        if (editing) players.updateMembership(membership) else players.insertMembership(membership)
        return WriteResult.SAVED
    }

    suspend fun removeMembership(playerId: Long, teamId: Long): WriteResult =
        if (players.detach(playerId, teamId) == 1) WriteResult.SAVED else WriteResult.RECORD_MISSING

    suspend fun saveMatch(id: Long?, input: MatchInput, zone: ZoneId = ZoneId.systemDefault()): WriteResult {
        input.validate(zone)?.let { return it }
        val teamId = input.teamId ?: return WriteResult.TEAM_REQUIRED
        val startsAt = input.startsAt(zone) ?: return WriteResult.INVALID_MATCH_TIME
        return database.withTransaction {
            val team = teams.find(teamId) ?: return@withTransaction WriteResult.TEAM_MISSING
            val previous = if (id == null) null else {
                matches.find(id) ?: return@withTransaction WriteResult.RECORD_MISSING
            }
            if (previous != null && liveDao.session(previous.id) != null) {
                if (previous.status == MatchStatus.SCHEDULED) return@withTransaction WriteResult.LIVE_ACTIVE
                if (previous.teamId != teamId || input.teamScore.nonNegativeInt() != previous.teamScore ||
                    input.opponentScore.nonNegativeInt() != previous.opponentScore
                ) return@withTransaction WriteResult.LIVE_SCORE_AUDIT
            }
            if (previous?.status == MatchStatus.FINISHED && input.status == MatchStatus.SCHEDULED) {
                return@withTransaction WriteResult.MATCH_ALREADY_FINISHED
            }
            if (previous != null && previous.teamId != teamId && stats.containsMatch(previous.id)) {
                return@withTransaction WriteResult.MATCH_HAS_STATS
            }

            val finished = input.status == MatchStatus.FINISHED
            val teamScore = if (finished) input.teamScore.nonNegativeInt() else null
            if (team.sport == Sport.FOOTBALL &&
                previous?.status == MatchStatus.FINISHED &&
                teamScore != null &&
                teamScore != previous.teamScore &&
                stats.totalGoals(previous.id) > teamScore
            ) {
                return@withTransaction WriteResult.SCORE_BELOW_RECORDED_GOALS
            }
            val match = Match(
                teamId = teamId,
                opponent = input.opponent.trim(),
                startsAt = startsAt,
                location = input.location.trim().ifEmpty { null },
                status = input.status,
                teamScore = teamScore,
                opponentScore = if (finished) input.opponentScore.nonNegativeInt() else null,
                id = previous?.id ?: 0,
                createdAt = previous?.createdAt ?: System.currentTimeMillis(),
            )
            if (previous == null) {
                boxStore.snapshotDefaults(matches.insert(match), team.sport)
            } else {
                val previousSport = if (previous.teamId == teamId) team.sport else teams.find(previous.teamId)?.sport
                matches.update(match)
                if (previousSport != team.sport) boxStore.snapshotDefaults(match.id, team.sport)
            }
            WriteResult.SAVED
        }
    }

    suspend fun saveMatchStats(matchId: Long, updates: Map<Long, StatValues>): WriteResult =
        database.withTransaction {
            val fixture = matches.details(matchId) ?: return@withTransaction WriteResult.RECORD_MISSING
            if (fixture.match.status != MatchStatus.FINISHED) {
                return@withTransaction if (fixture.live != null) WriteResult.LIVE_ACTIVE else WriteResult.MATCH_NOT_FINISHED
            }
            if (updates.values.any { !it.validate(fixture.team.sport) }) return@withTransaction WriteResult.INVALID_STATS
            val playerIds = stats.eligiblePlayers(matchId, fixture.team.id).mapTo(hashSetOf()) { it.id }
            if (!playerIds.containsAll(updates.keys)) return@withTransaction WriteResult.INVALID_MATCH_PLAYER
            if (fixture.live != null && !liveStatistics.keepsEventContributions(matchId, updates)) {
                return@withTransaction WriteResult.LIVE_EVENT_TOTALS
            }
            validateGoalTotal(fixture, updates)?.let { return@withTransaction it }
            persistCanonicalRows(stats, boxDao, matchId, updates)
            WriteResult.SAVED
        }

    suspend fun saveBoxPlayer(matchId: Long, playerId: Long, changes: Map<String, BoxMeasurement?>): WriteResult =
        database.withTransaction {
            val fixture = matches.details(matchId) ?: return@withTransaction WriteResult.RECORD_MISSING
            if (fixture.match.status != MatchStatus.FINISHED) {
                return@withTransaction if (fixture.live != null) WriteResult.LIVE_ACTIVE else WriteResult.MATCH_NOT_FINISHED
            }
            if (stats.eligiblePlayers(matchId, fixture.team.id).none { it.id == playerId }) {
                return@withTransaction WriteResult.INVALID_MATCH_PLAYER
            }
            val enabled = boxStore.configuration(fixture.team.sport, matchId).visible.associateBy { it.id }
            val saved = stats.forMatch(matchId).firstOrNull { it.playerId == playerId }
            val previous = saved?.values ?: StatValues()
            val canonical = StatMetric.entries.associateWithTo(mutableMapOf()) { previous[it] }
            val additions = mutableListOf<BoxStoredMeasurement>()
            val removals = mutableListOf<String>()
            for ((id, measurement) in changes) {
                val metric = enabled[id] ?: return@withTransaction WriteResult.INVALID_BOX_METRIC
                if (metric.kind == BoxMetricKind.PERCENT) return@withTransaction WriteResult.INVALID_BOX_METRIC
                if (measurement != null && !measurement.validFor(metric.kind)) {
                    return@withTransaction if (metric.kind == BoxMetricKind.PAIR) WriteResult.INVALID_BOX_PAIR
                    else WriteResult.INVALID_STATS
                }
                val field = metric.builtin?.canonical
                when {
                    field != null -> canonical[field] = measurement?.value ?: 0
                    measurement == null -> removals.add(id)
                    else -> additions.add(BoxStoredMeasurement(matchId, playerId, id, measurement.value, measurement.attempts))
                }
            }

            val next = StatValues.from(canonical)
            val update = mapOf(playerId to next)
            if (fixture.live != null && !liveStatistics.keepsEventContributions(matchId, update, changes, playerId)) {
                return@withTransaction WriteResult.LIVE_EVENT_TOTALS
            }
            if (next.goals != previous.goals) validateGoalTotal(fixture, update)?.let { return@withTransaction it }
            if (additions.isNotEmpty()) {
                if (saved == null) stats.upsert(listOf(PlayerMatchStats(matchId, playerId, next)))
                boxDao.upsertMeasurements(additions)
            }
            if (removals.isNotEmpty()) boxDao.clearMeasurements(matchId, playerId, removals)
            if (changes.isNotEmpty()) persistCanonicalRows(stats, boxDao, matchId, update)
            WriteResult.SAVED
        }

    private suspend fun validateGoalTotal(fixture: MatchWithTeam, updates: Map<Long, StatValues>): WriteResult? {
        if (fixture.team.sport != Sport.FOOTBALL) return null
        val previousGoals = stats.forMatch(fixture.match.id)
            .filterNot { it.playerId in updates }
            .sumOf { it.values.goals.toLong() }
        val proposedGoals = previousGoals + updates.values.sumOf { it.goals.toLong() }
        val score = fixture.match.teamScore ?: return WriteResult.INVALID_STATS
        return if (proposedGoals > score) WriteResult.GOALS_EXCEED_SCORE else null
    }

    suspend fun deleteMatch(id: Long): WriteResult = database.withTransaction {
        if (matches.find(id)?.status == MatchStatus.SCHEDULED && liveDao.session(id) != null) {
            return@withTransaction WriteResult.LIVE_ACTIVE
        }
        if (matches.delete(id) == 1) WriteResult.SAVED else WriteResult.RECORD_MISSING
    }

    suspend fun deleteTeam(id: Long): WriteResult = database.withTransaction {
        if (liveDao.activeForTeam(id)) return@withTransaction WriteResult.LIVE_ACTIVE
        if (teams.delete(id) == 1) WriteResult.SAVED else WriteResult.RECORD_MISSING
    }

    suspend fun deletePlayer(id: Long): WriteResult = database.withTransaction {
        if (liveDao.activeForPlayer(id)) return@withTransaction WriteResult.LIVE_ACTIVE
        if (players.delete(id) == 1) WriteResult.SAVED else WriteResult.RECORD_MISSING
    }
}
