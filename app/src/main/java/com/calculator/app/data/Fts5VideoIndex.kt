package com.calculator.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/**
 * FTS5 index for videos already saved in the encrypted Room/SQLCipher database.
 * It is an auxiliary virtual table, so the Room entity schema and existing encrypted
 * database migration remain unchanged. If the bundled SQLite lacks FTS5, the repository
 * falls back to a bounded LIKE query rather than breaking database startup.
 */
internal object Fts5VideoIndex {
    @Volatile private var available = false

    fun install(db: SupportSQLiteDatabase) {
        available = false
        try {
            val existed = db.query(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='video_search_fts' LIMIT 1"
            ).use { it.moveToFirst() }
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS video_search_fts USING fts5(" +
                    "title, tags, sourceSite, content='videos', content_rowid='rowid')"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS videos_fts_ai AFTER INSERT ON videos BEGIN " +
                    "INSERT INTO video_search_fts(rowid, title, tags, sourceSite) " +
                    "VALUES (new.rowid, new.title, new.tags, new.sourceSite); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS videos_fts_ad AFTER DELETE ON videos BEGIN " +
                    "INSERT INTO video_search_fts(video_search_fts, rowid, title, tags, sourceSite) " +
                    "VALUES ('delete', old.rowid, old.title, old.tags, old.sourceSite); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS videos_fts_au AFTER UPDATE ON videos BEGIN " +
                    "INSERT INTO video_search_fts(video_search_fts, rowid, title, tags, sourceSite) " +
                    "VALUES ('delete', old.rowid, old.title, old.tags, old.sourceSite); " +
                    "INSERT INTO video_search_fts(rowid, title, tags, sourceSite) " +
                    "VALUES (new.rowid, new.title, new.tags, new.sourceSite); END"
            )
            if (!existed) db.execSQL("INSERT INTO video_search_fts(video_search_fts) VALUES ('rebuild')")
            available = true
        } catch (_: Exception) {
            // Some SQLite builds omit FTS5; normal Room/SQLCipher storage must still work.
            available = false
        }
    }

    fun isAvailable(): Boolean = available

    /** OR-based terms improve recall; all punctuation is stripped before FTS syntax is built. */
    fun matchExpression(raw: String): String {
        val tokens = Regex("[\\p{L}\\p{N}]+")
            .findAll(raw.lowercase(Locale.ROOT))
            .map { it.value }
            .filter { it.length >= 2 }
            .distinct()
            .take(24)
            .toList()
        return tokens.joinToString(" OR ") { token -> "\"$token\"" }
    }
}
