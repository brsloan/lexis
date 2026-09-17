package com.lexis.reader.core

/** One exported word with every article found for it. */
data class ExportEntry(val word: String, val articles: List<Article>)

/** Renders looked-up words and their articles as a self-contained HTML document or as plain text. */
object Exporter {

    fun toHtml(entries: List<ExportEntry>, includeQuotations: Boolean, title: String, subtitle: String): String {
        val sb = StringBuilder(entries.size * 4096)
        sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        sb.append("<title>").append(esc(title)).append("</title>\n<style>\n").append(CSS).append("</style></head>\n<body>\n")
        sb.append("<h1>").append(esc(title)).append("</h1>\n")
        if (subtitle.isNotEmpty()) sb.append("<p class=\"sub\">").append(esc(subtitle)).append("</p>\n")
        if (entries.size > 1) {
            sb.append("<p class=\"toc\">")
            entries.forEachIndexed { i, e ->
                if (i > 0) sb.append(" · ")
                sb.append("<a href=\"#w").append(i).append("\">").append(esc(e.word)).append("</a>")
            }
            sb.append("</p>\n")
        }
        entries.forEachIndexed { i, e ->
            sb.append("<section class=\"word\" id=\"w").append(i).append("\">\n")
            sb.append("<h2>").append(esc(e.word)).append("</h2>\n")
            if (e.articles.isEmpty()) sb.append("<p class=\"missing\">No entry found.</p>\n")
            for (a in e.articles) {
                sb.append("<article>\n")
                if (a.dictionaryName.isNotEmpty()) sb.append("<div class=\"dict\">").append(esc(a.dictionaryName)).append("</div>\n")
                for (b in a.blocks) appendBlockHtml(sb, b, a.headword, e.word, includeQuotations)
                sb.append("</article>\n")
            }
            sb.append("</section>\n")
        }
        sb.append("</body></html>\n")
        return sb.toString()
    }

    private fun appendBlockHtml(sb: StringBuilder, b: Block, headword: String, word: String, includeQuotations: Boolean) {
        val style = if (b.indent > 0) " style=\"margin-left:${b.indent * 1.25}em\"" else ""
        when (b) {
            is Block.Headword -> {
                val t = b.text.plain()
                if (!t.equals(word, ignoreCase = true)) sb.append("<h3 class=\"hw\">").append(esc(t)).append("</h3>\n")
            }
            is Block.HomographHeader -> sb.append("<h3 class=\"hom\"><span class=\"lab\">").append(esc(b.label))
                .append("</span> ").append(rich(b.text)).append("</h3>\n")
            is Block.Pronunciation -> sb.append("<p class=\"pron\"").append(style).append(">").append(rich(b.text)).append("</p>\n")
            is Block.Etymology -> sb.append("<p class=\"etym\"").append(style).append(">").append(rich(b.text)).append("</p>\n")
            is Block.Sense -> {
                sb.append("<p class=\"sense\"").append(style).append(">")
                if (b.markers.isNotEmpty()) sb.append("<span class=\"mark\">").append(esc(b.markers)).append("</span> ")
                sb.append("<b class=\"lab\">").append(esc(b.label)).append("</b> ").append(rich(b.text)).append("</p>\n")
            }
            is Block.Paragraph -> sb.append("<p").append(style).append(">").append(rich(b.text)).append("</p>\n")
            is Block.Quotations -> {
                if (includeQuotations) {
                    sb.append("<ul class=\"quotes\"").append(style).append(">\n")
                    for (c in b.items) {
                        sb.append("<li><span class=\"cite\">").append(rich(c.before)).append("</span> ")
                        sb.append("<span class=\"q\">").append(rich(c.quote)).append("</span>")
                        if (c.after.isNotEmpty()) sb.append(" ").append(rich(c.after))
                        sb.append("</li>\n")
                    }
                    sb.append("</ul>\n")
                } else {
                    sb.append("<p class=\"qsum\"").append(style).append(">").append(esc(summary(b))).append("</p>\n")
                }
            }
            Block.Rule -> sb.append("<hr>\n")
        }
    }

    fun summary(q: Block.Quotations): String {
        val n = q.items.size
        val range = listOfNotNull(q.firstDate, q.lastDate).distinct().joinToString("–")
        val label = if (n == 1) "1 quotation" else "$n quotations"
        return if (range.isEmpty()) label else "$label, $range"
    }

    private fun rich(r: Rich): String {
        val sb = StringBuilder()
        for (run in r) {
            var t = esc(run.text).replace("\n", "<br>")
            if (run.sup) t = "<sup>$t</sup>"
            if (run.sub) t = "<sub>$t</sub>"
            if (run.italic) t = "<i>$t</i>"
            if (run.bold) t = "<b>$t</b>"
            val cls = when (run.tone) {
                Tone.QUOTE -> "q"
                Tone.SENSE -> "lab"
                Tone.ETYM -> "etym"
                Tone.PRON -> "pron"
                null -> null
            }
            if (cls != null) t = "<span class=\"$cls\">$t</span>"
            if (run.link != null) t = "<span class=\"xref\">$t</span>"
            sb.append(t)
        }
        return sb.toString()
    }

    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (c in s) when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            else -> sb.append(c)
        }
        return sb.toString()
    }

    fun toText(entries: List<ExportEntry>, includeQuotations: Boolean, title: String, subtitle: String): String {
        val sb = StringBuilder(entries.size * 2048)
        sb.append(title).append('\n')
        if (subtitle.isNotEmpty()) sb.append(subtitle).append('\n')
        sb.append('\n')
        for (e in entries) {
            sb.append(e.word.uppercase()).append('\n')
            sb.append("=".repeat(maxOf(8, e.word.length))).append('\n')
            if (e.articles.isEmpty()) sb.append("(no entry found)\n")
            for (a in e.articles) {
                if (a.dictionaryName.isNotEmpty()) sb.append("[").append(a.dictionaryName).append("]\n")
                for (b in a.blocks) appendBlockText(sb, b, e.word, includeQuotations)
                sb.append('\n')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun appendBlockText(sb: StringBuilder, b: Block, word: String, includeQuotations: Boolean) {
        val pad = "  ".repeat(b.indent)
        when (b) {
            is Block.Headword -> {
                val t = b.text.plain()
                if (!t.equals(word, ignoreCase = true)) sb.append(t).append('\n')
            }
            is Block.HomographHeader -> sb.append('\n').append("▪ ").append(b.label).append(' ').append(b.text.plain()).append('\n')
            is Block.Pronunciation -> sb.append(pad).append(b.text.plain()).append('\n')
            is Block.Etymology -> sb.append(pad).append(b.text.plain()).append('\n')
            is Block.Sense -> {
                sb.append(pad)
                if (b.markers.isNotEmpty()) sb.append(b.markers).append(' ')
                sb.append(b.label).append(' ').append(b.text.plain()).append('\n')
            }
            is Block.Paragraph -> sb.append(pad).append(b.text.plain()).append('\n')
            is Block.Quotations -> {
                if (includeQuotations) {
                    for (c in b.items) {
                        sb.append(pad).append("  ")
                        val before = c.before.plain().trim()
                        if (before.isNotEmpty()) sb.append(before).append(": ")
                        sb.append(c.quote.plain().trim())
                        val after = c.after.plain().trim()
                        if (after.isNotEmpty()) sb.append(' ').append(after)
                        sb.append('\n')
                    }
                } else {
                    sb.append(pad).append("  [").append(summary(b)).append("]\n")
                }
            }
            Block.Rule -> sb.append("----\n")
        }
    }

    private const val CSS = """
body{font-family:Georgia,'Times New Roman',serif;max-width:46em;margin:1em auto;padding:0 1em;line-height:1.45;color:#1b1b1b}
h1{font-size:1.5em;margin-bottom:.1em}
.sub{color:#666;margin-top:0}
.toc{font-size:.95em;color:#444}
.toc a{color:#3f51b5;text-decoration:none}
section.word{margin-top:2em;border-top:2px solid #ddd;padding-top:.6em}
h2{font-size:1.6em;margin:.2em 0 .3em}
.dict{font-size:.8em;color:#777;text-transform:uppercase;letter-spacing:.04em;margin-bottom:.4em}
h3.hom{font-size:1.1em;margin:1.1em 0 .3em;border-bottom:1px solid #e5e5e5;padding-bottom:.2em}
h3.hw{font-size:1.2em;margin:.6em 0 .2em}
.lab{color:#4b0082;font-weight:bold}
.mark{color:#8a2d2d}
p{margin:.35em 0}
.pron{color:#2f4f4f}
.etym{color:#666;font-size:.93em}
.sense{margin-top:.6em}
ul.quotes{list-style:none;padding-left:1.25em;margin:.25em 0 .6em;border-left:2px solid #eee}
ul.quotes li{margin:.25em 0;font-size:.93em}
.cite{color:#555}
.cite b{color:#222}
.q{color:#6a1b6a}
.qsum{color:#888;font-size:.85em;font-style:italic}
.xref{color:#3f51b5}
hr{border:0;border-top:1px solid #ccc;margin:1em 0}
sup,sub{font-size:.75em}
"""
}
