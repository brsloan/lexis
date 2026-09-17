package com.lexis.reader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class QuotationsMode { COLLAPSED, FIRST, EXPANDED }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ExportFormat { HTML, TEXT }

/** Where the dictionary's abbreviations are expanded inline; tapping an abbreviation always shows its expansion. */
enum class AbbreviationMode { TAP, DEFINITIONS, EVERYWHERE }

data class AppSettings(
    val fontScale: Float = 1f,
    val quotations: QuotationsMode = QuotationsMode.COLLAPSED,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val serif: Boolean = true,
    val lastExportAt: Long = 0L,
    val exportFormat: ExportFormat = ExportFormat.HTML,
    val exportQuotations: Boolean = true,
    val abbreviations: AbbreviationMode = AbbreviationMode.TAP,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("lexis", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun load(): AppSettings = AppSettings(
        fontScale = prefs.getFloat("fontScale", 1f),
        quotations = enumOr(prefs.getString("quotations", null), QuotationsMode.COLLAPSED),
        theme = enumOr(prefs.getString("theme", null), ThemeMode.SYSTEM),
        serif = prefs.getBoolean("serif", true),
        lastExportAt = prefs.getLong("lastExportAt", 0L),
        exportFormat = enumOr(prefs.getString("exportFormat", null), ExportFormat.HTML),
        exportQuotations = prefs.getBoolean("exportQuotations", true),
        abbreviations = enumOr(prefs.getString("abbreviations", null), AbbreviationMode.TAP),
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        prefs.edit()
            .putFloat("fontScale", s.fontScale)
            .putString("quotations", s.quotations.name)
            .putString("theme", s.theme.name)
            .putBoolean("serif", s.serif)
            .putLong("lastExportAt", s.lastExportAt)
            .putString("exportFormat", s.exportFormat.name)
            .putBoolean("exportQuotations", s.exportQuotations)
            .putString("abbreviations", s.abbreviations.name)
            .apply()
        _state.value = s
    }
}
