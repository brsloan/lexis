package com.lexis.reader

import android.app.Application
import com.lexis.reader.data.AppDatabase
import com.lexis.reader.data.DictionaryRepository
import com.lexis.reader.data.ExportService
import com.lexis.reader.data.HistoryRepository
import com.lexis.reader.data.Settings

class LexisApp : Application() {
    val database: AppDatabase by lazy { AppDatabase(this) }
    val settings: Settings by lazy { Settings(this) }
    val dictionaries: DictionaryRepository by lazy { DictionaryRepository(this, database) }
    val history: HistoryRepository by lazy { HistoryRepository(database) }
    val exporter: ExportService by lazy { ExportService(this, dictionaries) }
}
