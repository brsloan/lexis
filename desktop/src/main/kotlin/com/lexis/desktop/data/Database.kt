package com.lexis.desktop.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * SQLite store with the same schema as the Android app. Dictionaries are indexed in place, so
 * `dictionaries.dir` is the folder that holds the user's original files and `base` their common
 * file-name stem.
 *
 * Two connections are kept: reads (suggestions, lookups) use their own so they are not held up
 * while a large dictionary is being indexed on the write connection. Each is used by one thread
 * at a time.
 */
class Database(file: File) {
    private val url = "jdbc:sqlite:" + file.absolutePath.replace('\\', '/')
    private val writer: Connection = open()
    private val reader: Connection by lazy { open() }

    init {
        writer.createStatement().use { st ->
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS dictionaries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    dir TEXT NOT NULL,
                    base TEXT NOT NULL,
                    dict_file TEXT NOT NULL,
                    sts TEXT,
                    word_count INTEGER NOT NULL,
                    sort_order INTEGER NOT NULL,
                    added_at INTEGER NOT NULL
                )"""
            )
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    dict_id INTEGER NOT NULL,
                    word TEXT NOT NULL,
                    norm TEXT NOT NULL,
                    key TEXT NOT NULL,
                    data_offset INTEGER NOT NULL,
                    data_length INTEGER NOT NULL,
                    is_syn INTEGER NOT NULL DEFAULT 0
                )"""
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_words_norm ON words(norm)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_words_key ON words(key)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_words_dict ON words(dict_id)")
            st.executeUpdate(
                """CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    word TEXT NOT NULL UNIQUE,
                    dict_id INTEGER,
                    snippet TEXT NOT NULL DEFAULT '',
                    first_at INTEGER NOT NULL,
                    last_at INTEGER NOT NULL,
                    count INTEGER NOT NULL DEFAULT 1
                )"""
            )
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_history_last ON history(last_at)")
        }
    }

    private fun open(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection(url)
        c.createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute("PRAGMA synchronous=NORMAL")
            st.execute("PRAGMA busy_timeout=10000")
        }
        return c
    }

    fun <T> read(block: (Connection) -> T): T = synchronized(reader) { block(reader) }

    fun <T> write(block: (Connection) -> T): T = synchronized(writer) { block(writer) }

    /** Runs [block] in a transaction on the write connection. */
    fun <T> transaction(block: (Connection) -> T): T = write { c ->
        c.autoCommit = false
        try {
            val r = block(c)
            c.commit()
            r
        } catch (e: Throwable) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    fun close() {
        runCatching { writer.close() }
        runCatching { reader.close() }
    }
}

fun Connection.query(sql: String, vararg args: Any?, row: (ResultSet) -> Unit) {
    prepareStatement(sql).use { ps ->
        ps.bind(args)
        ps.executeQuery().use { rs -> while (rs.next()) row(rs) }
    }
}

fun Connection.update(sql: String, vararg args: Any?): Int =
    prepareStatement(sql).use { ps ->
        ps.bind(args)
        ps.executeUpdate()
    }

fun PreparedStatement.bind(args: Array<out Any?>) {
    args.forEachIndexed { i, a ->
        when (a) {
            null -> setObject(i + 1, null)
            is String -> setString(i + 1, a)
            is Int -> setInt(i + 1, a)
            is Long -> setLong(i + 1, a)
            is Boolean -> setInt(i + 1, if (a) 1 else 0)
            else -> setObject(i + 1, a)
        }
    }
}
