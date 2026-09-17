package com.lexis.reader.data

import android.content.ContentValues
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

class HistoryRepository(private val dbHelper: AppDatabase) {

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items

    init {
        refresh()
    }

    fun refresh() {
        val db = dbHelper.readableDatabase
        val out = ArrayList<HistoryItem>()
        db.query("history", null, null, null, null, null, "last_at DESC", "2000").use { c ->
            while (c.moveToNext()) out.add(read(c))
        }
        _items.value = out
    }

    private fun read(c: android.database.Cursor) = HistoryItem(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        word = c.getString(c.getColumnIndexOrThrow("word")),
        dictId = c.getColumnIndexOrThrow("dict_id").let { if (c.isNull(it)) null else c.getLong(it) },
        snippet = c.getString(c.getColumnIndexOrThrow("snippet")),
        firstAt = c.getLong(c.getColumnIndexOrThrow("first_at")),
        lastAt = c.getLong(c.getColumnIndexOrThrow("last_at")),
        count = c.getInt(c.getColumnIndexOrThrow("count")),
    )

    /** Record a lookup; an existing entry for the same word moves to the top. */
    suspend fun record(word: String, dictId: Long?, snippet: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        val existing = db.query("history", arrayOf("id", "count", "snippet"), "word = ?", arrayOf(word), null, null, null).use { c ->
            if (c.moveToFirst()) Triple(c.getLong(0), c.getInt(1), c.getString(2)) else null
        }
        if (existing != null) {
            val cv = ContentValues().apply {
                put("last_at", now)
                put("count", existing.second + 1)
                if (snippet.isNotEmpty()) put("snippet", snippet)
                if (dictId != null) put("dict_id", dictId)
            }
            db.update("history", cv, "id = ?", arrayOf(existing.first.toString()))
        } else {
            val cv = ContentValues().apply {
                put("word", word)
                if (dictId != null) put("dict_id", dictId)
                put("snippet", snippet)
                put("first_at", now)
                put("last_at", now)
                put("count", 1)
            }
            db.insert("history", null, cv)
        }
        refresh()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("history", "id = ?", arrayOf(id.toString()))
        refresh()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("history", null, null)
        refresh()
    }

    /** Items looked up (first or again) after the given time, oldest first. */
    fun since(timestamp: Long): List<HistoryItem> =
        _items.value.filter { it.lastAt > timestamp }.sortedBy { it.lastAt }

    fun all(): List<HistoryItem> = _items.value.sortedBy { it.lastAt }
}
