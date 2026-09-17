package com.lexis.reader.core

/**
 * Support for a dictionary's own abbreviation glossary: the dictionary may contain `<dtrn>` entries such as
 * "Obs." -> "obsolete" or "a." -> "adjective, adoption of, adopted from, (in dates) ante".
 */
object Abbreviations {

    /** True when raw entry text is a glossary entry rather than a normal article. */
    fun isGlossaryEntry(entryText: String): Boolean = entryText.trimStart().startsWith("<dtrn>")

    /** Plain text of a glossary entry. */
    fun expansionText(entryText: String): String = Xdxf.stripTags(entryText).trim()

    /**
     * The single reading suitable for inline substitution, or null when the expansion lists
     * several readings (e.g. "noun; neuter") and only a human can pick the right one.
     * A quoted gloss such as "confer, ‘compare’" yields the gloss ("compare").
     */
    fun primaryReading(expansion: String): String? {
        // "(in author names) Cibber" -> "Cibber": a leading parenthesis is context, not a reading
        val parts = expansion.split(';', ',')
            .map { it.trim().replace(Regex("^\\([^)]*\\)\\s*"), "") }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val glosses = parts.filter { it.startsWith('‘') || it.startsWith('\'') || it.startsWith('"') }
        val readings = parts.filter { it !in glosses }
        if (readings.size != 1) return null
        val gloss = glosses.firstOrNull()?.trim('‘', '’', '\'', '"')
        return if (!gloss.isNullOrEmpty()) gloss else readings[0]
    }

    /** All distinct abbreviation texts used in the blocks. */
    fun collect(blocks: List<Block>): Set<String> {
        val out = LinkedHashSet<String>()
        fun add(r: Rich) {
            for (run in r) if (run.abbr) {
                val t = run.text.trim()
                if (t.isNotEmpty()) out.add(t)
            }
        }
        for (b in blocks) when (b) {
            is Block.Headword -> add(b.text)
            is Block.HomographHeader -> add(b.text)
            is Block.Pronunciation -> add(b.text)
            is Block.Etymology -> add(b.text)
            is Block.Sense -> add(b.text)
            is Block.Paragraph -> add(b.text)
            is Block.Quotations -> for (c in b.items) {
                add(c.before)
                add(c.quote)
                add(c.after)
            }
            Block.Rule -> {}
        }
        return out
    }

    /**
     * Replace abbreviation runs with their inline reading. [readings] maps abbreviation text to
     * the reading to substitute; abbreviations absent from the map are left untouched.
     * Quotation blocks are only touched when [includeQuotations] is true.
     */
    fun expand(blocks: List<Block>, readings: Map<String, String>, includeQuotations: Boolean): List<Block> {
        if (readings.isEmpty()) return blocks
        fun rich(r: Rich): Rich = r.map { run ->
            if (!run.abbr) return@map run
            val t = run.text
            val trimmed = t.trim()
            val rep = readings[trimmed] ?: return@map run
            val lead = t.substring(0, t.length - t.trimStart().length)
            val trail = t.substring(t.trimEnd().length)
            run.copy(text = lead + rep + trail)
        }
        return blocks.map { b ->
            when (b) {
                is Block.Headword -> b
                is Block.HomographHeader -> b.copy(text = rich(b.text))
                is Block.Pronunciation -> b.copy(text = rich(b.text))
                is Block.Etymology -> b.copy(text = rich(b.text))
                is Block.Sense -> b.copy(text = rich(b.text))
                is Block.Paragraph -> b.copy(text = rich(b.text))
                is Block.Quotations -> if (includeQuotations) {
                    b.copy(items = b.items.map { c -> Citation(rich(c.before), rich(c.quote), rich(c.after)) })
                } else b
                Block.Rule -> b
            }
        }
    }
}
