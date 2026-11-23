package br.com.matchstats.ui

import br.com.matchstats.R
import br.com.matchstats.data.MatchStatus
import br.com.matchstats.data.StatMetric
import br.com.matchstats.data.Position
import br.com.matchstats.data.Sport
import br.com.matchstats.data.WriteResult

internal val Sport.label: Int
    get() = when (this) {
        Sport.FOOTBALL -> R.string.soccer
        Sport.BASKETBALL -> R.string.basketball
    }

internal val Sport.icon: Int
    get() = when (this) {
        Sport.FOOTBALL -> R.drawable.ic_soccer
        Sport.BASKETBALL -> R.drawable.ic_basketball
    }

internal val Position.label: Int
    get() = when (this) {
        Position.GOALKEEPER -> R.string.position_goalkeeper
        Position.CENTER_BACK -> R.string.position_center_back
        Position.FULL_BACK -> R.string.position_full_back
        Position.MIDFIELDER -> R.string.position_midfielder
        Position.FORWARD -> R.string.position_forward
        Position.POINT_GUARD -> R.string.position_point_guard
        Position.SHOOTING_GUARD -> R.string.position_shooting_guard
        Position.SMALL_FORWARD -> R.string.position_small_forward
        Position.POWER_FORWARD -> R.string.position_power_forward
        Position.CENTER -> R.string.position_center
    }

internal val WriteResult.errorLabel: Int?
    get() = when (this) {
        WriteResult.SAVED -> null
        WriteResult.NAME_REQUIRED -> R.string.error_name_required
        WriteResult.TEAM_REQUIRED -> R.string.error_team_required
        WriteResult.INVALID_JERSEY -> R.string.error_jersey_invalid
        WriteResult.DUPLICATE_JERSEY -> R.string.error_jersey_duplicate
        WriteResult.INVALID_POSITION -> R.string.error_position_invalid
        WriteResult.TEAM_MISSING -> R.string.error_team_missing
        WriteResult.RECORD_MISSING -> R.string.error_record_missing
        WriteResult.OPPONENT_REQUIRED -> R.string.error_opponent_required
        WriteResult.DATE_REQUIRED -> R.string.error_date_required
        WriteResult.TIME_REQUIRED -> R.string.error_time_required
        WriteResult.INVALID_MATCH_TIME -> R.string.error_match_time
        WriteResult.INVALID_TEAM_SCORE, WriteResult.INVALID_OPPONENT_SCORE -> R.string.error_match_score
        WriteResult.MATCH_ALREADY_FINISHED -> R.string.error_match_already_finished
        WriteResult.MATCH_HAS_STATS -> R.string.error_match_has_stats
        WriteResult.TEAM_HAS_MATCHES -> R.string.error_team_has_matches
        WriteResult.DUPLICATE_MEMBERSHIP -> R.string.error_duplicate_membership
        WriteResult.MATCH_NOT_FINISHED -> R.string.error_match_not_finished
        WriteResult.INVALID_MATCH_PLAYER -> R.string.error_invalid_match_player
        WriteResult.INVALID_BOX_METRIC -> R.string.box_invalid_metric
        WriteResult.INVALID_BOX_PAIR -> R.string.box_invalid_pair
        WriteResult.INVALID_STATS -> R.string.error_invalid_stats
        WriteResult.GOALS_EXCEED_SCORE -> R.string.goals_exceed_score
        WriteResult.SCORE_BELOW_RECORDED_GOALS -> R.string.score_below_recorded_goals
        WriteResult.LIVE_ACTIVE -> R.string.live_box_readonly
        WriteResult.LIVE_SCORE_AUDIT -> R.string.live_score_audit_hint
        WriteResult.LIVE_EVENT_TOTALS -> R.string.live_event_totals
        WriteResult.INVALID_LIVE_DURATION -> R.string.live_duration_error
        WriteResult.INVALID_LIVE_CLOCK -> R.string.live_clock_error
        WriteResult.INVALID_LIVE_EVENT -> R.string.live_event_error
        WriteResult.INVALID_LIVE_TOTAL -> R.string.live_total_error
        WriteResult.LIVE_EVENT_ALREADY_CHANGED -> R.string.live_event_changed
        WriteResult.PRACTICE_PLAYERS_REQUIRED -> R.string.practice_players_required
        WriteResult.PRACTICE_SPORT_LOCKED -> R.string.practice_sport_locked
        WriteResult.PRACTICE_PLAYER_HAS_BATCHES -> R.string.practice_player_has_batches
        WriteResult.PRACTICE_CLOSED -> R.string.practice_closed
        WriteResult.PRACTICE_DUPLICATE_DRILL -> R.string.practice_duplicate_drill
        WriteResult.PRACTICE_SUBMISSION_CONFLICT -> R.string.practice_conflict
        WriteResult.INVALID_TACTICAL_PLAY -> R.string.tactical_document_invalid
        WriteResult.TACTICAL_PLAY_CHANGED -> R.string.tactical_changed
        WriteResult.FAILED -> R.string.error_write_failed
    }

internal val MatchStatus.label: Int
    get() = when (this) {
        MatchStatus.SCHEDULED -> R.string.match_scheduled
        MatchStatus.FINISHED -> R.string.match_finished
    }

internal val MatchStatus.icon: Int
    get() = when (this) {
        MatchStatus.SCHEDULED -> R.drawable.ic_calendar
        MatchStatus.FINISHED -> R.drawable.ic_trophy
    }

internal val StatMetric.label: Int
    get() = when (this) {
        StatMetric.GOALS -> R.string.stat_goals
        StatMetric.ASSISTS -> R.string.stat_assists
        StatMetric.YELLOW_CARDS -> R.string.stat_yellow_cards
        StatMetric.RED_CARDS -> R.string.stat_red_cards
        StatMetric.POINTS -> R.string.stat_points
        StatMetric.REBOUNDS -> R.string.stat_rebounds
    }
