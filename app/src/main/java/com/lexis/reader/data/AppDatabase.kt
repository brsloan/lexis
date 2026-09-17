package com.lexis.reader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) : SQLiteOpenHelper(context, "lexis.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE dictionaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                dir TEXT NOT NULL,
                dict_file TEXT NOT NULL,
                sts TEXT,
                word_count INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                added_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE words (
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
        db.execSQL("CREATE INDEX idx_words_norm ON words(norm)")
        db.execSQL("CREATE INDEX idx_words_key ON words(key)")
        db.execSQL("CREATE INDEX idx_words_dict ON words(dict_id)")
        db.execSQL(
            """CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL UNIQUE,
                dict_id INTEGER,
                snippet TEXT NOT NULL DEFAULT '',
                first_at INTEGER NOT NULL,
                last_at INTEGER NOT NULL,
                count INTEGER NOT NULL DEFAULT 1
            )"""
        )
        db.execSQL("CREATE INDEX idx_history_last ON history(last_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // First schema version; nothing to migrate yet.
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }
}
