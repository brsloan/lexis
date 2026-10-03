package com.lexis.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class HistoryItem(
    val id: Long,
    val word: String,
    val dictId: Long?,
    val snippet: String,
    val firstAt: Long,
    val lastAt: Long,
    val count: Int,
)

class HistoryRepository(private val db: Database) {

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items

    init {
        refresh()
    }

    fun refresh() {
        val out = ArrayList<HistoryItem>()
        db.write { c ->
            c.query("SELECT id, word, dict_id, snippet, first_at, last_at, count FROM history ORDER BY last_at DESC LIMIT 2000") { rs ->
                out.add(
                    HistoryItem(
                        id = rs.getLong(1),
                        word = rs.getString(2),
                        dictId = rs.getLong(3).takeUnless { rs.wasNull() },
                        snippet = rs.getString(4),
                        firstAt = rs.getLong(5),
                        lastAt = rs.getLong(6),
                        count = rs.getInt(7),
                    )
                )
            }
        }
        _items.value = out
    }

    /** Record a lookup; an existing entry for the same word moves to the top. */
    suspend fun record(word: String, dictId: Long?, snippet: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.write { c ->
            val changed = c.update(
                "UPDATE history SET last_at = ?, count = count + 1, " +
                    "snippet = CASE WHEN ? <> '' THEN ? ELSE snippet END, dict_id = COALESCE(?, dict_id) WHERE word = ?",
                now, snippet, snippet, dictId, word,
            )
            if (changed == 0) {
                c.update(
                    "INSERT INTO history(word, dict_id, snippet, first_at, last_at, count) VALUES (?,?,?,?,?,1)",
                    word, dictId, snippet, now, now,
                )
            }
        }
        refresh()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.write { it.update("DELETE FROM history WHERE id = ?", id) }
        refresh()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.write { it.update("DELETE FROM history") }
        refresh()
    }

    /** Items looked up (first or again) after the given time, oldest first. */
    fun since(timestamp: Long): List<HistoryItem> =
        _items.value.filter { it.lastAt > timestamp }.sortedBy { it.lastAt }

    fun all(): List<HistoryItem> = _items.value.sortedBy { it.lastAt }
}
