package br.com.matchstats.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.matchstats.R

@Entity(
    tableName = "live_sessions",
    foreignKeys = [ForeignKey(entity = Match::class, parentColumns = ["id"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE)],
)
data class LiveSession(
    @PrimaryKey val matchId: Long,
    val periodSeconds: Int,
    val shotSeconds: Int,
    val period: Int = 1,
    val gameMs: Long = periodSeconds * 1000L,
    val shotMs: Long = shotSeconds * 1000L,
    val homeScore: Int = 0,
    val awayScore: Int = 0,
    val runningFrom: Long? = null,
    val clockOwner: String? = null,
    // Preserve explicitly measured zeroes when the last live contribution is undone.
    val measuredZeroes: String = "",
)

data class LiveClock(val gameMs: Long, val shotMs: Long, val running: Boolean) {
    fun position(session: LiveSession, sport: Sport): Long =
        if (sport == Sport.BASKETBALL) gameMs else session.period * session.periodSeconds * 1000L - gameMs
}

internal fun LiveSession.clock(now: Long, owner: String): LiveClock {
    val elapsed = runningFrom?.takeIf { clockOwner == owner }?.let { (now - it).coerceIn(0L, gameMs) } ?: 0L
    val remaining = gameMs - elapsed
    return LiveClock(remaining, (shotMs - elapsed).coerceAtLeast(0L), remaining > 0 && runningFrom != null && clockOwner == owner)
}

internal fun LiveSession.checkpoint(now: Long, owner: String, run: Boolean? = null): LiveSession {
    val clock = clock(now, owner)
    val keepRunning = (run ?: clock.running) && clock.gameMs > 0
    return copy(
        gameMs = clock.gameMs,
        shotMs = clock.shotMs,
        runningFrom = now.takeIf { keepRunning },
        clockOwner = owner.takeIf { keepRunning },
    )
}

enum class LiveSide { HOME, AWAY }
enum class LiveOperation { RECORD, CORRECT, UNDO }

@Entity(
    tableName = "live_events",
    foreignKeys = [
        ForeignKey(entity = LiveSession::class, parentColumns = ["matchId"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Player::class, parentColumns = ["id"], childColumns = ["playerId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["matchId", "sequence"], unique = true), Index("playerId"), Index(value = ["previousId"], unique = true)],
)
data class LiveEvent(
    @PrimaryKey val id: String,
    val matchId: Long,
    val sequence: Long,
    val previousId: String?,
    val operation: LiveOperation,
    val side: LiveSide,
    val playerId: Long?,
    val jersey: Int?,
    val playerName: String?,
    val actionId: String,
    val metricId: String?,
    val actionName: String?,
    val period: Int,
    val clockMs: Long,
    val value: Int,
    val createdAt: Long,
)

data class LiveInput(
    val side: LiveSide,
    val actionId: String,
    val playerId: Long? = null,
    val jersey: Int? = null,
    val opponentName: String? = null,
    val value: Int = 1,
)

data class LiveActionInfo(
    val id: String,
    val labelRes: Int?,
    val shortRes: Int?,
    val customName: String? = null,
    val customShort: String? = null,
)

data class LiveConfiguration(
    val sport: Sport,
    val columns: List<BoxColumn>,
    val catalog: Map<String, LiveActionInfo>,
    val defaults: List<BoxColumn>,
) {
    val visible get() = columns.filter { it.enabled }.mapNotNull { catalog[it.metricId] }
}

data class LiveGame(
    val fixture: MatchWithTeam,
    val session: LiveSession?,
    val players: List<RosterPlayer>,
    val events: List<LiveEvent>,
    val config: LiveConfiguration,
) {
    val activeEvents get() = events.activeLiveEvents()
}

internal const val SCORE_ADJUSTMENT = "score_adjustment"

internal fun List<LiveEvent>.activeLiveEvents(): List<LiveEvent> {
    val superseded = mapNotNullTo(hashSetOf()) { it.previousId }
    return filter { it.operation != LiveOperation.UNDO && it.id !in superseded }
}

internal data class LiveDelta(val value: Long = 0L, val attempts: Long = 0L) {
    fun times(sign: Int) = LiveDelta(value * sign, attempts * sign)
    operator fun plus(other: LiveDelta) = LiveDelta(value + other.value, attempts + other.attempts)
}

internal data class LiveEffect(val score: Long = 0L, val metrics: Map<String, LiveDelta> = emptyMap())

internal enum class LiveAction(
    val id: String,
    val sport: Sport,
    val labelRes: Int,
    val shortRes: Int,
    val primary: BoxBuiltin,
    private val points: Int = 0,
    private val shot: Boolean = false,
    private val extra: BoxBuiltin? = null,
) {
    FREE_MADE("bk_ft_made", Sport.BASKETBALL, R.string.live_free_made, R.string.live_plus_one, BoxBuiltin.FREE, 1, true),
    TWO_MADE("bk_two_made", Sport.BASKETBALL, R.string.live_two_made, R.string.live_plus_two, BoxBuiltin.TWO, 2, true),
    THREE_MADE("bk_three_made", Sport.BASKETBALL, R.string.live_three_made, R.string.live_plus_three, BoxBuiltin.THREE, 3, true),
    FREE_MISSED("bk_ft_missed", Sport.BASKETBALL, R.string.live_free_missed, R.string.live_ft_missed_short, BoxBuiltin.FREE, shot = true),
    TWO_MISSED("bk_two_missed", Sport.BASKETBALL, R.string.live_two_missed, R.string.live_two_missed_short, BoxBuiltin.TWO, shot = true),
    THREE_MISSED("bk_three_missed", Sport.BASKETBALL, R.string.live_three_missed, R.string.live_three_missed_short, BoxBuiltin.THREE, shot = true),
    OFFENSIVE("bk_reb_off", Sport.BASKETBALL, R.string.box_offensive, R.string.box_oreb_short, BoxBuiltin.OFFENSIVE, extra = BoxBuiltin.REBOUNDS),
    DEFENSIVE("bk_reb_def", Sport.BASKETBALL, R.string.box_defensive, R.string.box_dreb_short, BoxBuiltin.DEFENSIVE, extra = BoxBuiltin.REBOUNDS),
    BASKET_ASSIST("bk_assist", Sport.BASKETBALL, R.string.stat_assists, R.string.box_ast_short, BoxBuiltin.BASKET_ASSISTS),
    STEAL("bk_steal", Sport.BASKETBALL, R.string.box_steals, R.string.box_stl_short, BoxBuiltin.STEALS),
    BLOCK("bk_block", Sport.BASKETBALL, R.string.box_blocks, R.string.box_blk_short, BoxBuiltin.BLOCKS),
    TURNOVER("bk_turnover", Sport.BASKETBALL, R.string.box_turnovers, R.string.box_to_short, BoxBuiltin.TURNOVERS),
    BASKET_FOUL("bk_foul", Sport.BASKETBALL, R.string.box_personal_fouls, R.string.box_pf_short, BoxBuiltin.PERSONAL_FOULS),
    GOAL("fb_goal", Sport.FOOTBALL, R.string.live_goal, R.string.live_goal, BoxBuiltin.GOALS, 1),
    FOOTBALL_ASSIST("fb_assist", Sport.FOOTBALL, R.string.stat_assists, R.string.box_ast_short, BoxBuiltin.FOOTBALL_ASSISTS),
    SHOT("fb_shot", Sport.FOOTBALL, R.string.box_shots, R.string.box_fin_short, BoxBuiltin.SHOTS),
    ON_TARGET("fb_shot_target", Sport.FOOTBALL, R.string.box_on_target, R.string.box_target_short, BoxBuiltin.ON_TARGET, extra = BoxBuiltin.SHOTS),
    TACKLE("fb_tackle", Sport.FOOTBALL, R.string.box_tackles, R.string.box_tackle_short, BoxBuiltin.TACKLES),
    INTERCEPT("fb_intercept", Sport.FOOTBALL, R.string.box_interceptions, R.string.box_int_short, BoxBuiltin.INTERCEPTIONS),
    FOOTBALL_FOUL("fb_foul", Sport.FOOTBALL, R.string.box_fouls, R.string.box_foul_short, BoxBuiltin.FOULS),
    YELLOW("fb_yellow_card", Sport.FOOTBALL, R.string.stat_yellow_cards, R.string.box_yellow_short, BoxBuiltin.YELLOW),
    RED("fb_red_card", Sport.FOOTBALL, R.string.stat_red_cards, R.string.box_red_short, BoxBuiltin.RED),
    CORNER("fb_corner", Sport.FOOTBALL, R.string.live_corner, R.string.live_corner_short, BoxBuiltin.CORNERS),
    SAVE("fb_save", Sport.FOOTBALL, R.string.box_saves, R.string.box_save_short, BoxBuiltin.SAVES);

    fun effect() = LiveEffect(points.toLong(), buildMap {
        put(primary.id, if (shot) LiveDelta(if (points > 0) 1 else 0, 1) else LiveDelta(1))
        if (shot && points > 0) put(BoxBuiltin.POINTS.id, LiveDelta(points.toLong()))
        extra?.let { put(it.id, LiveDelta(1)) }
    })

    companion object {
        val byId = entries.associateBy(LiveAction::id)
    }
}

internal fun LiveEvent.effect(): LiveEffect = when {
    actionId == SCORE_ADJUSTMENT -> LiveEffect(value.toLong())
    actionId in LiveAction.byId -> LiveAction.byId.getValue(actionId).effect()
    metricId != null -> LiveEffect(metrics = mapOf(metricId to LiveDelta(value.toLong())))
    else -> LiveEffect()
}

internal fun defaultLiveColumns(sport: Sport) =
    LiveAction.entries.filter { it.sport == sport }.map { BoxColumn(it.id, true) }

internal fun List<LiveEvent>.opponentBox(sport: Sport): List<BoxPlayer> =
    activeLiveEvents().filter { it.side == LiveSide.AWAY && it.jersey != null }.groupBy { requireNotNull(it.jersey) }
        .toSortedMap().map { (jersey, events) ->
            val totals = mutableMapOf<String, LiveDelta>()
            for (event in events) for ((id, delta) in event.effect().metrics) {
                totals[id] = (totals[id] ?: LiveDelta()) + delta
            }
            val builtins = BoxBuiltin.entries.filter { it.sport == sport }.associateBy { it.id }
            val fields = totals.mapNotNull { (id, value) ->
                builtins[id]?.canonical?.let { it to value.value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
            }.toMap()
            val extras = totals.filterKeys { builtins[it]?.canonical == null }.mapValues { (id, value) ->
                BoxMeasurement(
                    value.value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    value.attempts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().takeIf { builtins[id]?.kind == BoxMetricKind.PAIR },
                )
            }
            BoxPlayer(
                // Display identity scoped to the opponent table. It is never inserted into players.
                Player(events.asReversed().firstNotNullOfOrNull { it.playerName }.orEmpty(), id = jersey.toLong()),
                jersey, StatValues.from(fields), extras,
            )
        }
