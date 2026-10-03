package com.lexis.reader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XdxfTest {

    @Test
    fun decodesEntities() {
        assertEquals("one's & <b> \"q\" é", Xdxf.decodeEntities("one&apos;s &amp; &lt;b&gt; &quot;q&quot; &#233;"))
        assertEquals("a & b", Xdxf.decodeEntities("a & b"))
        assertEquals("&unknown;", Xdxf.decodeEntities("&unknown;"))
    }

    @Test
    fun stylesAndTones() {
        val runs = Xdxf.toRuns("<b><c c=\"indigo\">1.</c></b> Plain <i>ital</i> <abr>Obs.</abr> <c c=\"darkmagenta\">quote</c>")
        assertEquals("1. Plain ital Obs. quote", runs.plain())
        val label = runs[0]
        assertTrue(label.bold)
        assertEquals(Tone.SENSE, label.tone)
        val ital = runs.first { it.text == "ital" }
        assertTrue(ital.italic)
        assertTrue(runs.first { it.text == "Obs." }.abbr)
        assertEquals(Tone.QUOTE, runs.last().tone)
        assertNull(runs.first { it.text == " Plain " }.tone)
    }

    @Test
    fun krefBecomesLink() {
        val runs = Xdxf.toRuns("See <kref>bandon</kref> <abr>n.</abr>")
        val link = runs.first { it.link != null }
        assertEquals("bandon", link.text)
        assertEquals("bandon", link.link)
        assertNull(runs[0].link)
    }

    @Test
    fun nestedColourKeepsOuterTone() {
        val runs = Xdxf.toRuns("<b><c c=\"darkmagenta\">▪ <c>I.</c></c></b>")
        assertEquals("▪ I.", runs.plain())
        assertTrue(runs.all { it.tone == Tone.QUOTE && it.bold })
    }

    @Test
    fun coalescesAdjacentRuns() {
        val runs = Xdxf.toRuns("a<b></b>b<i></i>c")
        assertEquals(1, runs.size)
        assertEquals("abc", runs[0].text)
    }

    @Test
    fun superscriptAndSubscript() {
        val runs = Xdxf.toRuns("x<sup>2</sup> H<sub>2</sub>O")
        assertTrue(runs.first { it.text == "2" }.sup)
        assertTrue(runs.first { it.sub }.text == "2")
    }
}
