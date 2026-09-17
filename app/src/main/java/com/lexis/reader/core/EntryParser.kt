package com.lexis.reader.core

/**
 * Turns the raw entry markup of a richly structured XDXF StarDict entry into a list of [Block]s.
 *
 * The markup is line oriented: every line is either the headword (`<k>`), a homograph
 * header (`▪ I.`), or a paragraph wrapped in N `<blockquote>` pairs (N = 2, 4, 6, 8) where the
 * quotation paragraphs (`<ex>`) sit two levels deeper than the sense text they belong to.
 */
object EntryParser {

    private const val BQ = "<blockquote>"
    private const val BQE = "</blockquote>"
    private val MARKERS = setOf("†", "‖", "¶", "Add:", "*", "**", "***", "****")

    fun parse(markup: String, type: Char = 'x'): List<Block> = when (type) {
        'x' -> parseXdxf(markup)
        'h', 'g' -> parseHtmlish(markup)
        else -> parsePlain(markup)
    }

    fun parsePlain(text: String): List<Block> =
        text.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }
            .map { Block.Paragraph(listOf(Run(Xdxf.decodeEntities(it))), 0) }

    fun parseHtmlish(markup: String): List<Block> {
        val runs = Xdxf.toRuns(markup)
        val blocks = ArrayList<Block>()
        var cur = ArrayList<Run>()
        fun flush() {
            val t = cur.trimRuns()
            if (t.isNotEmpty()) blocks.add(Block.Paragraph(t, 0))
            cur = ArrayList()
        }
        for (r in runs) {
            var start = 0
            while (true) {
                val nl = r.text.indexOf('\n', start)
                if (nl < 0) {
                    if (start < r.text.length) cur.add(r.copy(text = r.text.substring(start)))
                    break
                }
                if (nl > start) cur.add(r.copy(text = r.text.substring(start, nl)))
                flush()
                start = nl + 1
            }
        }
        flush()
        return blocks
    }

    fun parseXdxf(markup: String): List<Block> {
        val lines = markup.split('\n')
        val blocks = ArrayList<Block>(lines.size)
        val hasHomographs = lines.any { it.startsWith("<b><c c=\"darkmagenta\">▪") }
        val baseLevel = if (hasHomographs) 2 else 1
        var etymOpenBrackets = 0
        var senseExtra = 0

        for (rawLine in lines) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue
            var depth = 0
            while (line.startsWith(BQ)) {
                depth++
                line = line.substring(BQ.length)
            }
            var trailing = 0
            while (trailing < depth && line.endsWith(BQE)) {
                trailing++
                line = line.substring(0, line.length - BQE.length)
            }
            line = line.trim()
            if (line.isEmpty()) continue
            val level = (depth + 1) / 2
            val rel = (level - baseLevel).coerceAtLeast(0)

            if (line.startsWith("<k>")) {
                val end = line.indexOf("</k>")
                val hw = Xdxf.stripTags(if (end > 0) line.substring(3, end) else line.substring(3))
                blocks.add(Block.Headword(listOf(Run(hw.trim(), bold = true))))
                val rest = if (end > 0) line.substring(end + 4).trim() else ""
                if (rest.isNotEmpty()) blocks.add(Block.Paragraph(Xdxf.toRuns(rest).trimRuns(), 0))
                etymOpenBrackets = 0
                continue
            }
            if (line.length >= 5 && line.all { it == '_' }) {
                blocks.add(Block.Rule)
                etymOpenBrackets = 0
                continue
            }
            if (line.startsWith("<ex>")) {
                val end = line.lastIndexOf("</ex>")
                val inner = if (end > 4) line.substring(4, end) else line.substring(4)
                val cites = parseCitations(Xdxf.toRuns(inner))
                if (cites.isNotEmpty()) blocks.add(Block.Quotations(cites, minOf(rel, 1) + senseExtra))
                continue
            }

            val runs = Xdxf.toRuns(line).trimRuns()
            if (runs.isEmpty()) continue
            val first = runs[0]

            // Homograph header: "▪ I.<tab>headword, pos"
            if (first.text.startsWith("▪")) {
                val plainAll = runs.plain()
                val tab = plainAll.indexOf('\t')
                val label = (if (tab >= 0) plainAll.substring(0, tab) else plainAll).removePrefix("▪").trim()
                val restMarkup = line.substringAfter('\t', "")
                val text = if (restMarkup.isNotEmpty()) Xdxf.toRuns(restMarkup).trimRuns() else emptyList()
                blocks.add(Block.HomographHeader(label, text))
                etymOpenBrackets = 0
                senseExtra = 0
                continue
            }

            // Sense: optional markers then a bold indigo label
            val sense = detectSense(runs)
            if (sense != null) {
                senseExtra = when (sense.kind) {
                    SenseKind.SUB -> 1
                    SenseKind.SUBSUB -> 2
                    else -> 0
                }
                blocks.add(sense.copy(indent = rel + senseExtra))
                etymOpenBrackets = 0
                continue
            }

            // Pronunciation: "(" followed by a darkslategray run
            if (first.text.startsWith("(") && runs.take(3).any { it.tone == Tone.PRON }) {
                blocks.add(Block.Pronunciation(runs, rel + senseExtra))
                continue
            }

            // Etymology: gray run starting with "[" (may continue on following lines until brackets balance)
            val plain = runs.plain()
            if (first.tone == Tone.ETYM && first.text.startsWith("[")) {
                etymOpenBrackets = bracketBalance(plain)
                blocks.add(Block.Etymology(runs, rel + senseExtra))
                continue
            }
            if (etymOpenBrackets > 0) {
                etymOpenBrackets += bracketBalance(plain)
                blocks.add(Block.Etymology(runs, rel + senseExtra))
                continue
            }

            blocks.add(Block.Paragraph(runs, rel + senseExtra))
        }
        return blocks
    }

    private fun bracketBalance(s: String): Int {
        var n = 0
        for (c in s) {
            if (c == '[') n++ else if (c == ']') n--
        }
        return n
    }

    private fun detectSense(runs: Rich): Block.Sense? {
        val markers = StringBuilder()
        var i = 0
        while (i < runs.size) {
            val r = runs[i]
            val t = r.text.trim()
            if (t.isEmpty()) {
                i++
                continue
            }
            if (t in MARKERS || (r.abbr && t.length <= 2 && !t[0].isLetterOrDigit())) {
                if (markers.isNotEmpty()) markers.append(' ')
                markers.append(t)
                i++
                continue
            }
            break
        }
        if (i >= runs.size) return null
        val labelRun = runs[i]
        if (!labelRun.bold || labelRun.tone != Tone.SENSE) return null
        val label = labelRun.text.trim()
        if (label.isEmpty() || label.length > 24) return null
        val text = runs.subList(i + 1, runs.size).trimStartRuns()
        return Block.Sense(markers.toString(), label, senseKind(label), text, 0)
    }

    /** Classify "I.", "1.", "a.", "(a)", "1. a.", "[2.] b." etc. */
    fun senseKind(label: String): SenseKind {
        val parts = label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return SenseKind.OTHER
        // Bracketed components such as "[2.]" only restate the enclosing sense for context.
        val own = parts.filter { !it.startsWith("[") }
        if (own.isEmpty()) return kindOf(parts.last().trim('[', ']'))
        // "1. a." starts numbered sense 1, so classify by the first unbracketed component.
        val first = kindOf(own.first())
        return if (first != SenseKind.OTHER) first else kindOf(own.last())
    }

    private fun kindOf(p: String): SenseKind {
        val core = p.trimEnd('.')
        return when {
            core.startsWith("(") && core.endsWith(")") -> SenseKind.SUBSUB
            core.isNotEmpty() && core.all { it in "IVXLC" } -> SenseKind.BRANCH
            core.isNotEmpty() && core.all { it.isDigit() } -> SenseKind.SENSE
            core.length == 1 && core[0].isLetter() && core[0].isLowerCase() -> SenseKind.SUB
            core.length == 1 && core[0].isLetter() && core[0].isUpperCase() -> SenseKind.BRANCH
            else -> SenseKind.OTHER
        }
    }

    /**
     * Split the runs of an `<ex>` block into citations. A citation ends after its quoted (darkmagenta)
     * text; the next bold run (a date such as "1375", or a label like "b." / "Ibid.") starts a new one.
     */
    fun parseCitations(runs: Rich): List<Citation> {
        val cites = ArrayList<Citation>()
        var before = ArrayList<Run>()
        var quote = ArrayList<Run>()
        var after = ArrayList<Run>()

        fun flush() {
            if (before.isEmpty() && quote.isEmpty() && after.isEmpty()) return
            cites.add(Citation(before.trimRuns(), quote.trimRuns(), after.trimRuns()))
            before = ArrayList()
            quote = ArrayList()
            after = ArrayList()
        }

        for (r in runs) {
            if (r.tone == Tone.QUOTE) {
                if (after.isNotEmpty()) {
                    // quote resumed after interleaved text: keep that text inside the quote
                    quote.addAll(after)
                    after.clear()
                }
                quote.add(r)
                continue
            }
            if (quote.isNotEmpty()) {
                if (r.bold && r.text.isNotBlank()) {
                    flush()
                    before.add(r)
                } else {
                    after.add(r)
                }
            } else {
                before.add(r)
            }
        }
        flush()
        return cites
    }
}
