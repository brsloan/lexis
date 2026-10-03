package com.lexis.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lexis.desktop.data.ThemeMode
import com.lexis.desktop.ui.AppState
import com.lexis.desktop.ui.LexisMain
import com.lexis.desktop.ui.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Visual smoke test: renders the main window offscreen with real dictionaries and writes PNGs.
 * Runs only when LEXIS_SCREENSHOTS (output folder) and DICT_DIR (StarDict folder) are set;
 * the index is built once in LEXIS_SCREENSHOTS/home and reused on later runs.
 */
@OptIn(ExperimentalComposeUiApi::class)
class Screenshots {
    private val out = System.getenv("LEXIS_SCREENSHOTS")?.let(::File)
    private val dictDir = System.getenv("DICT_DIR")?.let(::File)

    @Test
    fun render() {
        assumeTrue(out != null && dictDir != null && dictDir.isDirectory)
        out!!.mkdirs()
        System.setProperty("lexis.home", File(out, "home").absolutePath)
        val app = LexisDesktop()
        if (app.dictionaries.dictionaries.value.isEmpty()) {
            val t0 = System.currentTimeMillis()
            runBlocking { app.dictionaries.addFolder(dictDir!!) { println(it.message + " " + (it.fraction ?: "")) } }
            println("indexed in ${System.currentTimeMillis() - t0} ms")
        }
        val state = AppState(app, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        fun shot(name: String, w: Int = 1400, h: Int = 900, setup: (UiState) -> Unit = {}) {
            val ui = UiState().also(setup)
            val scene = ImageComposeScene(w, h, Density(1.25f)) {
                val settings by state.settings.collectAsState()
                LexisMain(state, settings, ui, null)
            }
            var t = 0L
            repeat(40) {
                scene.render(t)
                t += 50_000_000L
                Thread.sleep(50)
            }
            val png = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$name.png").writeBytes(png)
            scene.close()
        }

        shot("01-welcome")
        state.query = "lexic"
        Thread.sleep(400)
        shot("02-suggestions")
        state.query = ""
        state.openWord("quotation")
        shot("03-entry-quotation")
        state.openWord("set")
        shot("04-entry-set")
        app.settings.update { it.copy(theme = ThemeMode.DARK) }
        state.openWord("serendipity")
        shot("05-entry-dark")
        app.settings.update { it.copy(theme = ThemeMode.LIGHT) }
        shot("06-dictionaries", setup = { it.showDictionaries = true })
        shot("07-settings", setup = { it.showSettings = true })
        shot("08-narrow", w = 900, h = 700)
        app.close()
    }
}
