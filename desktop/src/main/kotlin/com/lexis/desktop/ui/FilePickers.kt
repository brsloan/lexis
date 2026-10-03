package com.lexis.desktop.ui

import com.formdev.flatlaf.util.SystemFileChooser
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native-looking file choosers. All must be called on the UI thread; they block until closed. */
object FilePickers {

    /** Native folder picker on all three systems (Windows Explorer dialog, Finder sheet, GTK). */
    fun chooseFolder(parent: Frame?, title: String, start: String?): File? {
        val chooser = SystemFileChooser(start ?: System.getProperty("user.home")).apply {
            dialogTitle = title
            fileSelectionMode = SystemFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(parent) == SystemFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun chooseIfo(parent: Frame?, start: String?): File? {
        val d = FileDialog(parent, "Choose a dictionary's .ifo file", FileDialog.LOAD)
        start?.let { d.directory = it }
        d.file = "*.ifo" // filter on Windows
        d.setFilenameFilter { _, name -> name.endsWith(".ifo", ignoreCase = true) } // filter on macOS / Linux
        d.isVisible = true
        val name = d.file ?: return null
        return File(d.directory, name)
    }

    fun chooseSaveFile(parent: Frame?, title: String, start: String?, defaultName: String): File? {
        val d = FileDialog(parent, title, FileDialog.SAVE)
        d.directory = start ?: File(System.getProperty("user.home"), "Documents").takeIf { it.isDirectory }?.path ?: System.getProperty("user.home")
        d.file = defaultName
        d.isVisible = true
        val name = d.file ?: return null
        var f = File(d.directory, name)
        val ext = defaultName.substringAfterLast('.', "")
        if (ext.isNotEmpty() && !f.name.contains('.')) f = File(f.parentFile, f.name + "." + ext)
        return f
    }
}
