package com.lexis.reader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures under `src/test/resources` are synthetic: invented headwords, senses and
 * quotations written to reproduce the markup conventions of richly structured XDXF
 * dictionaries. No real dictionary content is bundled with this project.
 */
class EntryParserTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes().toString(Charsets.UTF_8)

    @Test
    fun simpleEntry() {
        val blocks = EntryParser.parse(resource("simple-entry.xdxf"))
        val article = Article("quinceberry", blocks)
        assertTrue(blocks[0] is Block.Headword)
        assertEquals("quinceberry", (blocks[0] as Block.Headword).text.plain())
        // the bare "<b>quinceberry</b>" line is a plain paragraph, then pronunciation
        val pron = blocks.filterIsInstance<Block.Pronunciation>().single()
        assertEquals("(ˈkwɪnsbɛrɪ)", pron.text.plain())
        // etymology spans two lines: "[f. OFr. ..." and "... glossaries.]"
        val etym = blocks.filterIsInstance<Block.Etymology>()
        assertEquals(2, etym.size)
        assertTrue(etym[1].text.plain().endsWith("glossaries.]"))
        val quotes = blocks.filterIsInstance<Block.Quotations>()
        assertEquals(2, quotes.size)
        assertEquals(6, quotes[0].items.size)
        assertEquals("1601", quotes[0].items[0].date)
        assertEquals("1994", quotes[0].items[5].date)
        assertEquals("1601", quotes[0].firstDate)
        assertEquals("1994", quotes[0].lastDate)
        assertTrue(quotes[0].items[0].quote.plain().startsWith("The quinceberrie is"))
        assertTrue(quotes[0].items[0].before.plain().contains("J. Gerard"))
        assertEquals(2, quotes[1].items.size)
        assertEquals(8, article.quotationCount)
        assertTrue(article.snippet().startsWith("The fruit of a hedge shrub"))
        assertEquals(0, article.outline().size)
    }

    @Test
    fun homographsAndSenses() {
        val blocks = EntryParser.parse(resource("homographs.xdxf"))
        val article = Article("gallimond", blocks)
        assertTrue(article.hasHomographs)
        val homs = blocks.filterIsInstance<Block.HomographHeader>()
        assertEquals(listOf("I.", "II.", "III.", "IV.", "V."), homs.map { it.label })
        assertTrue(homs[0].text.plain().contains("adv."))
        assertEquals("†", homs[0].text.first { it.abbr }.text.trim())

        val senses = blocks.filterIsInstance<Block.Sense>()
        assertTrue(senses.size > 10)
        val first = senses[0]
        assertEquals("1.", first.label)
        assertEquals(SenseKind.SENSE, first.kind)
        assertEquals(0, first.indent)
        assertTrue(first.text.plain().startsWith("In a state of disorder"))
        // obsolete sense marker
        val obs = senses.first { it.markers.contains("†") }
        assertEquals("1.", obs.label)
        // branches in the verb
        assertTrue(senses.any { it.kind == SenseKind.BRANCH && it.label == "II." })
        // sub-senses get indented
        val sub = senses.first { it.kind == SenseKind.SUB }
        assertEquals(1, sub.indent)

        // quotation with editorial bracket inside the quote stays one citation
        val q = blocks.filterIsInstance<Block.Quotations>().first()
        assertEquals(1, q.items.size)
        assertEquals("c 1275", q.items[0].date)
        assertTrue(q.items[0].quote.plain().contains("[Ashby MS. galimaund]"))

        // quotation block with three citations
        val q2 = blocks.filterIsInstance<Block.Quotations>()[1]
        assertEquals(3, q2.items.size)
        assertEquals(listOf("a 1400", "c 1400", "1487"), q2.items.map { it.date })
        assertTrue(q2.items[2].before.plain().contains("T. Marlow"))

        val outline = article.outline()
        assertTrue(outline.first().label == "I.")
        assertTrue(outline.any { it.label == "2." })

        // pronunciation of the noun
        val pron = blocks.filterIsInstance<Block.Pronunciation>().first()
        assertEquals("(ˈgælɪmɒnd)", pron.text.plain())

        // cross reference link present
        val link = blocks.filterIsInstance<Block.Etymology>().flatMap { it.text }.firstOrNull { it.link != null }
        assertNotNull(link)
        assertEquals("gallimand", link!!.link)
    }

    @Test
    fun denseEntrySmoke() {
        val blocks = EntryParser.parse(resource("dense.xdxf"))
        assertTrue(blocks.filterIsInstance<Block.Sense>().isNotEmpty())
        assertTrue(blocks.filterIsInstance<Block.Quotations>().all { q -> q.items.all { it.quote.isNotEmpty() || it.before.isNotEmpty() } })
    }

    @Test
    fun citationSplitting() {
        val runs = Xdxf.toRuns(
            "α [<b><i>c</i> 950</b> <i>N. Gloss.</i> vi. 15 <c c=\"darkmagenta\">First.</c>] " +
                "<b><i>c</i> 1175</b> <i>Ashby Hom.</i> 1 <c c=\"darkmagenta\">Second.</c> " +
                "<b>Ibid.</b> 2 <c c=\"darkmagenta\">Third.</c>"
        )
        val cites = EntryParser.parseCitations(runs)
        assertEquals(3, cites.size)
        assertEquals("c 950", cites[0].date)
        assertEquals("]", cites[0].after.plain())
        assertTrue(cites[0].before.plain().startsWith("α ["))
        assertEquals("c 1175", cites[1].date)
        assertEquals("Second.", cites[1].quote.plain())
        assertEquals(null, cites[2].date)
        assertEquals("Ibid. 2", cites[2].before.plain())
    }

    @Test
    fun citationWithoutDate() {
        val runs = Xdxf.toRuns("<i>Old Northern Verse</i> 123 <c c=\"darkmagenta\">Listen now.</c>")
        val cites = EntryParser.parseCitations(runs)
        assertEquals(1, cites.size)
        assertEquals(null, cites[0].date)
        assertEquals("Old Northern Verse 123", cites[0].before.plain())
    }

    @Test
    fun senseKinds() {
        assertEquals(SenseKind.BRANCH, EntryParser.senseKind("I."))
        assertEquals(SenseKind.SENSE, EntryParser.senseKind("12."))
        assertEquals(SenseKind.SUB, EntryParser.senseKind("b."))
        assertEquals(SenseKind.SUBSUB, EntryParser.senseKind("(a)"))
        assertEquals(SenseKind.SENSE, EntryParser.senseKind("1. a."))
        assertEquals(SenseKind.SUB, EntryParser.senseKind("[2.] b."))
        assertEquals(SenseKind.BRANCH, EntryParser.senseKind("A."))
    }

    @Test
    fun ruleAndPlain() {
        val blocks = EntryParser.parse("<k>x</k>\n<blockquote><blockquote>______________</blockquote></blockquote>\n<blockquote><blockquote>Hello</blockquote></blockquote>")
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is Block.Rule)
        assertEquals("Hello", (blocks[2] as Block.Paragraph).text.plain())
    }

    @Test
    fun exporterProducesHtmlAndText() {
        val article = Article("quinceberry", EntryParser.parse(resource("simple-entry.xdxf")), "Dictionary")
        val html = Exporter.toHtml(listOf(ExportEntry("quinceberry", listOf(article))), true, "Words", "sub")
        assertTrue(html.contains("<h2>quinceberry</h2>"))
        assertTrue(html.contains("J. Gerard"))
        val htmlNoQ = Exporter.toHtml(listOf(ExportEntry("quinceberry", listOf(article))), false, "Words", "")
        assertTrue(htmlNoQ.contains("6 quotations, 1601–1994"))
        val text = Exporter.toText(listOf(ExportEntry("quinceberry", listOf(article))), true, "Words", "")
        assertTrue(text.contains("QUINCEBERRY"))
        assertTrue(text.contains("1601 J. Gerard"))
    }
}
