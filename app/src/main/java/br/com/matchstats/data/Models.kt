package br.com.matchstats.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Sport { FOOTBALL, BASKETBALL }

enum class Position(val sport: Sport) {
    GOALKEEPER(Sport.FOOTBALL),
    CENTER_BACK(Sport.FOOTBALL),
    FULL_BACK(Sport.FOOTBALL),
    MIDFIELDER(Sport.FOOTBALL),
    FORWARD(Sport.FOOTBALL),
    POINT_GUARD(Sport.BASKETBALL),
    SHOOTING_GUARD(Sport.BASKETBALL),
    SMALL_FORWARD(Sport.BASKETBALL),
    POWER_FORWARD(Sport.BASKETBALL),
    CENTER(Sport.BASKETBALL),
}

@Entity(tableName = "teams")
data class Team(
    val name: String,
    val sport: Sport,
    val category: String?,
    val coachName: String?,
    val createdAt: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(tableName = "players")
data class Player(
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
)

@Entity(
    tableName = "team_memberships",
    primaryKeys = ["playerId", "teamId"],
    foreignKeys = [
        ForeignKey(entity = Player::class, parentColumns = ["id"], childColumns = ["playerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Team::class, parentColumns = ["id"], childColumns = ["teamId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["teamId", "jerseyNumber"], unique = true)],
)
data class TeamMembership(
    val playerId: Long,
    val teamId: Long,
    val jerseyNumber: Int?,
    val position: Position?,
    val captain: Boolean,
)

data class TeamSummary(@Embedded val team: Team, val playerCount: Int)

data class RosterPlayer(
    @Embedded val player: Player,
    val jerseyNumber: Int?,
    val position: Position?,
    val captain: Boolean,
) {
    val id get() = player.id
    val name get() = player.name
}

data class TeamRoster(val team: Team, val players: List<RosterPlayer>, val hasMatchHistory: Boolean)

data class DashboardCounts(val teams: Int, val players: Int, val matches: Int = 0)
