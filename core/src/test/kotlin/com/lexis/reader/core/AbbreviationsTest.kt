package com.lexis.reader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbbreviationsTest {

    @Test
    fun glossaryDetection() {
        assertTrue(Abbreviations.isGlossaryEntry("<dtrn>obsolete</dtrn>"))
        assertFalse(Abbreviations.isGlossaryEntry("<k>obs</k>\n<b>obs</b>"))
        assertEquals("(in Shakespeare) 1 Henry IV", Abbreviations.expansionText("<dtrn>(in Shakespeare) 1 Henry IV</dtrn>"))
    }

    @Test
    fun primaryReading() {
        assertEquals("obsolete", Abbreviations.primaryReading("obsolete"))
        assertEquals("Old French", Abbreviations.primaryReading("Old French"))
        assertNull(Abbreviations.primaryReading("adjective, adoption of, adopted from, (in dates) ante"))
        assertNull(Abbreviations.primaryReading("noun; neuter"))
        assertEquals("compare", Abbreviations.primaryReading("confer, ‘compare’"))
        assertEquals("for example", Abbreviations.primaryReading("exempli gratia, ‘for example’"))
        assertEquals("Cibber", Abbreviations.primaryReading("(in author names) Cibber"))
        assertNull(Abbreviations.primaryReading("Scottish, Scots"))
    }

    @Test
    fun collectAndExpand() {
        val blocks = EntryParser.parse(
            "<k>x</k>\n" +
                "<blockquote><blockquote><c c=\"gray\">[a. <abr>OFr.</abr> <i>x</i>]</c></blockquote></blockquote>\n" +
                "<blockquote><blockquote><b><c c=\"indigo\">1.</c></b> Sense. <i><abr>Obs.</abr></i></blockquote></blockquote>\n" +
                "<blockquote><blockquote><blockquote><blockquote><blockquote><blockquote><ex><b>1500</b> <abr>Shakes.</abr> <i>Ham.</i> <c c=\"darkmagenta\">Q.</c></ex></blockquote></blockquote></blockquote></blockquote></blockquote></blockquote>"
        )
        assertEquals(setOf("OFr.", "Obs.", "Shakes."), Abbreviations.collect(blocks))
        val readings = mapOf("OFr." to "Old French", "Obs." to "obsolete", "Shakes." to "Shakespeare")

        val defs = Abbreviations.expand(blocks, readings, includeQuotations = false)
        assertEquals("[a. Old French x]", defs.filterIsInstance<Block.Etymology>().single().text.plain())
        val sense = defs.filterIsInstance<Block.Sense>().single()
        assertEquals("Sense. obsolete", sense.text.plain())
        assertTrue(sense.text.last().abbr) // still tappable
        assertEquals("1500 Shakes. Ham.", defs.filterIsInstance<Block.Quotations>().single().items[0].before.plain())

        val all = Abbreviations.expand(blocks, readings, includeQuotations = true)
        assertEquals("1500 Shakespeare Ham.", all.filterIsInstance<Block.Quotations>().single().items[0].before.plain())

        // untouched when nothing to expand
        assertTrue(Abbreviations.expand(blocks, emptyMap(), true) === blocks)
    }
}
