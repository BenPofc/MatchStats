package br.com.matchstats.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Move the dependent stats first so dropping the legacy player table cannot cascade into history.
        db.execSQL("CREATE TEMP TABLE legacy_match_stats AS SELECT * FROM player_match_stats")
        db.execSQL("CREATE TEMP TABLE legacy_players AS SELECT * FROM players")
        db.execSQL("CREATE TEMP TABLE legacy_player_sequence AS SELECT seq FROM sqlite_sequence WHERE name = 'players'")
        db.execSQL("DROP TABLE player_match_stats")
        db.execSQL("DROP TABLE players")
        db.execSQL("""
            CREATE TABLE players (
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
            )
        """.trimIndent())
        db.execSQL("INSERT INTO players (id, name, createdAt) SELECT id, name, createdAt FROM legacy_players")
        db.execSQL("""
            INSERT INTO sqlite_sequence (name, seq)
            SELECT 'players', seq FROM legacy_player_sequence
            WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = 'players')
        """.trimIndent())
        db.execSQL("""
            UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE((SELECT seq FROM legacy_player_sequence), 0))
            WHERE name = 'players'
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE team_memberships (
                playerId INTEGER NOT NULL,
                teamId INTEGER NOT NULL,
                jerseyNumber INTEGER,
                position TEXT,
                captain INTEGER NOT NULL,
                PRIMARY KEY (playerId, teamId),
                FOREIGN KEY (playerId) REFERENCES players(id) ON DELETE CASCADE,
                FOREIGN KEY (teamId) REFERENCES teams(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX index_team_memberships_teamId_jerseyNumber ON team_memberships (teamId, jerseyNumber)")
        db.execSQL("""
            INSERT INTO team_memberships (playerId, teamId, jerseyNumber, position, captain)
            SELECT id, teamId, jerseyNumber, position, captain FROM legacy_players
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE player_match_stats (
                matchId INTEGER NOT NULL,
                playerId INTEGER NOT NULL,
                goals INTEGER NOT NULL DEFAULT 0,
                assists INTEGER NOT NULL DEFAULT 0,
                yellowCards INTEGER NOT NULL DEFAULT 0,
                redCards INTEGER NOT NULL DEFAULT 0,
                points INTEGER NOT NULL DEFAULT 0,
                rebounds INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (matchId, playerId),
                FOREIGN KEY (matchId) REFERENCES matches(id) ON DELETE CASCADE,
                FOREIGN KEY (playerId) REFERENCES players(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_player_match_stats_playerId ON player_match_stats (playerId)")
        val columns = "matchId, playerId, goals, assists, yellowCards, redCards, points, rebounds"
        db.execSQL("INSERT INTO player_match_stats ($columns) SELECT $columns FROM legacy_match_stats")
        db.execSQL("DROP TABLE legacy_match_stats")
        db.execSQL("DROP TABLE legacy_players")
        db.execSQL("DROP TABLE legacy_player_sequence")
    }
}
