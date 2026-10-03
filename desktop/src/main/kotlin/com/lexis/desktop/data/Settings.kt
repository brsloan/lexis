package com.lexis.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Properties

enum class QuotationsMode { COLLAPSED, FIRST, EXPANDED }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ExportFormat { HTML, TEXT }

/** Where the dictionary's abbreviations are expanded inline; clicking an abbreviation always shows its expansion. */
enum class AbbreviationMode { TAP, DEFINITIONS, EVERYWHERE }

data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int, val maximized: Boolean)

data class AppSettings(
    val fontScale: Float = 1f,
    val quotations: QuotationsMode = QuotationsMode.COLLAPSED,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val serif: Boolean = true,
    val lastExportAt: Long = 0L,
    val exportFormat: ExportFormat = ExportFormat.HTML,
    val exportQuotations: Boolean = true,
    val abbreviations: AbbreviationMode = AbbreviationMode.TAP,
    val showOutline: Boolean = true,
    val lastAddFolder: String? = null,
    val lastExportFolder: String? = null,
    val window: WindowBounds? = null,
)

/** Settings persisted as a properties file in the data folder. */
class Settings(private val file: File) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun load(): AppSettings {
        val p = Properties()
        if (file.exists()) runCatching { file.inputStream().use { p.load(it) } }
        fun str(k: String) = p.getProperty(k)
        fun float(k: String, d: Float) = str(k)?.toFloatOrNull() ?: d
        fun long(k: String, d: Long) = str(k)?.toLongOrNull() ?: d
        fun bool(k: String, d: Boolean) = str(k)?.toBooleanStrictOrNull() ?: d
        val window = run {
            val parts = str("window")?.split(',')?.map { it.trim() } ?: return@run null
            if (parts.size != 5) return@run null
            val n = parts.take(4).map { it.toIntOrNull() ?: return@run null }
            WindowBounds(n[0], n[1], n[2], n[3], parts[4].toBoolean())
        }
        return AppSettings(
            fontScale = float("fontScale", 1f),
            quotations = enumOr(str("quotations"), QuotationsMode.COLLAPSED),
            theme = enumOr(str("theme"), ThemeMode.SYSTEM),
            serif = bool("serif", true),
            lastExportAt = long("lastExportAt", 0L),
            exportFormat = enumOr(str("exportFormat"), ExportFormat.HTML),
            exportQuotations = bool("exportQuotations", true),
            abbreviations = enumOr(str("abbreviations"), AbbreviationMode.TAP),
            showOutline = bool("showOutline", true),
            lastAddFolder = str("lastAddFolder"),
            lastExportFolder = str("lastExportFolder"),
            window = window,
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        if (s == _state.value) return
        _state.value = s
        val p = Properties()
        p["fontScale"] = s.fontScale.toString()
        p["quotations"] = s.quotations.name
        p["theme"] = s.theme.name
        p["serif"] = s.serif.toString()
        p["lastExportAt"] = s.lastExportAt.toString()
        p["exportFormat"] = s.exportFormat.name
        p["exportQuotations"] = s.exportQuotations.toString()
        p["abbreviations"] = s.abbreviations.name
        p["showOutline"] = s.showOutline.toString()
        s.lastAddFolder?.let { p["lastAddFolder"] = it }
        s.lastExportFolder?.let { p["lastExportFolder"] = it }
        s.window?.let { p["window"] = "${it.x},${it.y},${it.width},${it.height},${it.maximized}" }
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { p.store(it, "Lexis settings") }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
