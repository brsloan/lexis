package com.lexis.reader.core

/** Semantic colour roles used by the XDXF markup. */
enum class Tone { QUOTE, SENSE, ETYM, PRON }

/** One inline run of text with uniform styling. */
data class Run(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
    val abbr: Boolean = false,
    val tone: Tone? = null,
    /** Cross-reference target (headword to look up) when this run is a link. */
    val link: String? = null,
) {
    fun sameStyle(o: Run) =
        bold == o.bold && italic == o.italic && sup == o.sup && sub == o.sub &&
            abbr == o.abbr && tone == o.tone && link == o.link
}

typealias Rich = List<Run>

fun Rich.plain(): String = joinToString("") { it.text }

fun Rich.trimStartRuns(): Rich {
    if (isEmpty()) return this
    val out = ArrayList<Run>(size)
    var started = false
    for (r in this) {
        if (!started) {
            val t = r.text.trimStart()
            if (t.isEmpty()) continue
            started = true
            out.add(r.copy(text = t))
        } else out.add(r)
    }
    return out
}

fun Rich.trimEndRuns(): Rich {
    if (isEmpty()) return this
    val out = ArrayList(this)
    while (out.isNotEmpty()) {
        val last = out.last()
        val t = last.text.trimEnd()
        if (t.isEmpty()) {
            out.removeAt(out.size - 1)
        } else {
            out[out.size - 1] = last.copy(text = t)
            break
        }
    }
    return out
}

fun Rich.trimRuns(): Rich = trimStartRuns().trimEndRuns()

/** Merge adjacent runs that share a style. */
fun Rich.coalesce(): Rich {
    if (size < 2) return this
    val out = ArrayList<Run>(size)
    for (r in this) {
        if (r.text.isEmpty()) continue
        val last = out.lastOrNull()
        if (last != null && last.sameStyle(r)) {
            out[out.size - 1] = last.copy(text = last.text + r.text)
        } else {
            out.add(r)
        }
    }
    return out
}
