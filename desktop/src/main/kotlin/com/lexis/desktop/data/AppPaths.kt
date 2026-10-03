package com.lexis.desktop.data

import java.io.File

/** Per-user data folder, following each OS's convention. Override with -Dlexis.home=... */
object AppPaths {
    val dataDir: File by lazy {
        val override = System.getProperty("lexis.home")
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val dir = when {
            override != null -> File(override)
            os.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "Lexis")
            os.contains("mac") -> File(home, "Library/Application Support/Lexis")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "lexis")
        }
        dir.mkdirs()
        dir
    }

    val database: File get() = File(dataDir, "lexis.db")
    val settings: File get() = File(dataDir, "settings.properties")

    val isMac: Boolean get() = System.getProperty("os.name").lowercase().contains("mac")
}
