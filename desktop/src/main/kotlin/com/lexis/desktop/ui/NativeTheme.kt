package com.lexis.desktop.ui

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.FlatSystemProperties
import com.lexis.desktop.data.AppPaths
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Window

/**
 * Light/dark for the parts of the window that Compose doesn't draw: the OS title bar and the
 * Swing menu bar (and Swing dialogs). On Linux the title bar belongs to the window manager and
 * already follows the desktop theme.
 */
object NativeTheme {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private var swingDark: Boolean? = null

    /** Call once at startup, before any window is created. */
    fun init(dark: Boolean) {
        if (AppPaths.isMac) {
            // Title bar and screen menu bar follow the macOS appearance.
            System.setProperty("apple.awt.application.appearance", "system")
            return
        }
        // Keep the native title bar (with Windows snap layouts); only the menus are FlatLaf.
        System.setProperty(FlatSystemProperties.USE_WINDOW_DECORATIONS, "false")
        applySwing(dark)
    }

    /** Re-themes Swing components and the title bar of [window]. */
    fun apply(window: Window, dark: Boolean) {
        if (AppPaths.isMac) return
        applySwing(dark)
        if (isWindows) runCatching { WindowsTitleBar.setDark(window, dark) }
    }

    private fun applySwing(dark: Boolean) {
        if (swingDark == dark) return
        val first = swingDark == null
        swingDark = dark
        if (dark) FlatDarkLaf.setup() else FlatLightLaf.setup()
        if (!first) FlatLaf.updateUI()
    }
}

/** Dark title bar via DwmSetWindowAttribute(DWMWA_USE_IMMERSIVE_DARK_MODE), Windows 10 1809+. */
private object WindowsTitleBar {
    @Suppress("FunctionName")
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntArray, size: Int): Int
    }

    @Suppress("FunctionName")
    private interface User32 : Library {
        fun SetWindowPos(hwnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
    }

    private val dwm by lazy { Native.load("dwmapi", Dwm::class.java) }
    private val user32 by lazy { Native.load("user32", User32::class.java) }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19 // Windows 10 before 20H1

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020

    fun setDark(window: Window, dark: Boolean) {
        val hwnd = Native.getWindowPointer(window) ?: return
        val value = intArrayOf(if (dark) 1 else 0)
        if (dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4) != 0) {
            dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, value, 4)
        }
        // Make Windows repaint the frame now rather than on the next focus change.
        user32.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED)
    }
}
