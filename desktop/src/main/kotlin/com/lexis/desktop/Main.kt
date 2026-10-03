package com.lexis.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lexis.desktop.data.AppPaths
import com.lexis.desktop.data.Database
import com.lexis.desktop.data.DictionaryRepository
import com.lexis.desktop.data.ExportService
import com.lexis.desktop.data.HistoryRepository
import com.lexis.desktop.data.Settings
import com.lexis.desktop.data.ThemeMode
import com.lexis.desktop.data.WindowBounds
import com.lexis.desktop.ui.AppState
import com.lexis.desktop.ui.LexisWindowContent
import com.lexis.desktop.ui.NativeTheme
import com.lexis.desktop.ui.theme.isDark
import kotlinx.coroutines.flow.collect
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JOptionPane
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme
import kotlin.system.exitProcess

/** Process-wide services, the desktop counterpart of the Android `LexisApp`. */
class LexisDesktop {
    val database = Database(AppPaths.database)
    val settings = Settings(AppPaths.settings)
    val dictionaries = DictionaryRepository(database)
    val history = HistoryRepository(database)
    val exporter = ExportService(dictionaries)

    fun close() {
        dictionaries.close()
        database.close()
    }
}

fun main(args: Array<String>) {
    if (AppPaths.isMac) {
        System.setProperty("apple.awt.application.name", "Lexis")
        System.setProperty("apple.laf.useScreenMenuBar", "true")
    }
    val app = try {
        LexisDesktop()
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(
            null,
            "Lexis could not open its data folder:\n${AppPaths.dataDir}\n\n${e.message ?: e.javaClass.name}",
            "Lexis",
            JOptionPane.ERROR_MESSAGE,
        )
        exitProcess(1)
    }
    // Menus and title bar start in the right theme; later changes are applied from the window.
    val startTheme = app.settings.state.value.theme
    NativeTheme.init(
        when (startTheme) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> currentSystemTheme == SystemTheme.DARK
        }
    )

    // `lexis <word>` opens straight to that word.
    val initialWord = args.joinToString(" ").trim().take(80)

    application {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(app, scope).also { if (initialWord.isNotEmpty()) it.openWord(initialWord) } }
        val settings by state.settings.collectAsState()

        val saved = remember { app.settings.state.value.window?.takeIf { it.isOnScreen() } }
        val windowState = rememberWindowState(
            placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = saved?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition(Alignment.Center),
            size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: defaultWindowSize(),
        )
        // Remember the last floating bounds, so a maximized window restores to a sensible size.
        val floating = remember { arrayOf(saved) }
        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.placement, windowState.position, windowState.size) }.collect { (placement, pos, size) ->
                if (placement == WindowPlacement.Floating && pos is WindowPosition.Absolute) {
                    floating[0] = WindowBounds(pos.x.value.toInt(), pos.y.value.toInt(), size.width.value.toInt(), size.height.value.toInt(), false)
                }
            }
        }

        val icon = remember {
            Thread.currentThread().contextClassLoader.getResourceAsStream("lexis-icon.png")?.use {
                BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
            }
        }

        Window(
            onCloseRequest = {
                val maximized = windowState.placement == WindowPlacement.Maximized
                floating[0]?.let { b -> app.settings.update { it.copy(window = b.copy(maximized = maximized)) } }
                app.close()
                exitApplication()
            },
            state = windowState,
            title = state.currentWord?.let { "$it — Lexis" } ?: "Lexis",
            icon = icon,
        ) {
            val dark = isDark(settings.theme)
            LaunchedEffect(dark) { NativeTheme.apply(window, dark) }
            LexisWindowContent(state, settings)
        }
    }
}

/** 1180 × 800, shrunk to fit the usable screen area (AWT bounds are in the same logical units as dp). */
private fun defaultWindowSize(): DpSize {
    val usable = runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }.getOrNull()
        ?: return DpSize(1180.dp, 800.dp)
    return DpSize(minOf(1180, usable.width * 92 / 100).dp, minOf(800, usable.height * 92 / 100).dp)
}

/** True when at least a usable part of the saved window lies on a connected screen. */
private fun WindowBounds.isOnScreen(): Boolean = runCatching {
    val r = Rectangle(x, y, width, height)
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { d ->
        val i = d.defaultConfiguration.bounds.intersection(r)
        !i.isEmpty && i.width >= 200 && i.height >= 100
    } && width >= 400 && height >= 300
}.getOrDefault(false)
