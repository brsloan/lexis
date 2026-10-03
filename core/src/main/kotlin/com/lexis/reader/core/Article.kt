package com.lexis.reader.core

enum class SenseKind { BRANCH, SENSE, SUB, SUBSUB, OTHER }

/** A block-level element of a parsed dictionary article. [indent] is a logical indentation level, 0-based. */
sealed class Block {
    abstract val indent: Int

    data class Headword(val text: Rich) : Block() {
        override val indent = 0
    }

    /** "▪ II." style section header for a homograph (e.g. the verb vs. the noun). */
    data class HomographHeader(val label: String, val text: Rich) : Block() {
        override val indent = 0
    }

    data class Pronunciation(val text: Rich, override val indent: Int) : Block()

    data class Etymology(val text: Rich, override val indent: Int) : Block()

    data class Sense(
        /** Markers preceding the label, e.g. "†" for obsolete, "‖" for alien, "Add:" for additions. */
        val markers: String,
        val label: String,
        val kind: SenseKind,
        val text: Rich,
        override val indent: Int,
    ) : Block()

    data class Paragraph(val text: Rich, override val indent: Int) : Block()

    data class Quotations(val items: List<Citation>, override val indent: Int) : Block() {
        val firstDate: String? get() = items.firstNotNullOfOrNull { it.date }
        val lastDate: String? get() = items.asReversed().firstNotNullOfOrNull { it.date }
    }

    data object Rule : Block() {
        override val indent = 0
    }
}

/**
 * One quotation. [before] is the bibliographic part (date, author, work, reference),
 * [quote] the quoted text and [after] any trailing material such as a closing bracket.
 */
data class Citation(val before: Rich, val quote: Rich, val after: Rich) {
    /** Year-ish text of the leading bold run(s), e.g. "c 1225", "1375", "13..". */
    val date: String?
        get() {
            val sb = StringBuilder()
            var prefix = 0
            for (r in before) {
                if (r.bold) {
                    sb.append(r.text)
                } else if (sb.isNotEmpty()) {
                    break
                } else {
                    // short non-bold prefixes such as "α [", "β " or "(a) " may precede the date
                    prefix += r.text.trim().length
                    if (prefix > 8) break
                }
            }
            val s = sb.toString().trim()
            return if (s.any { it.isDigit() }) s else null
        }
}

data class Article(
    val headword: String,
    val blocks: List<Block>,
    val dictionaryName: String = "",
    val dictionaryId: Long = 0,
) {
    val hasHomographs: Boolean get() = blocks.any { it is Block.HomographHeader }
    val quotationCount: Int get() = blocks.sumOf { if (it is Block.Quotations) it.items.size else 0 }

    /** Short text for lists: the first substantial sense or paragraph text. */
    fun snippet(max: Int = 140): String {
        for (b in blocks) {
            val t = when (b) {
                is Block.Sense -> b.text.plain()
                is Block.Paragraph -> b.text.plain()
                else -> null
            } ?: continue
            val s = t.replace('\n', ' ').trim()
            if (s.length < 12) continue
            return if (s.length <= max) s else s.substring(0, max - 1).trimEnd() + "…"
        }
        return ""
    }
}

data class OutlineItem(val blockIndex: Int, val label: String, val snippet: String, val level: Int)

/** Navigable outline: homograph headers, branches and numbered senses. */
fun Article.outline(): List<OutlineItem> {
    val out = ArrayList<OutlineItem>()
    val homographs = hasHomographs
    blocks.forEachIndexed { i, b ->
        when (b) {
            is Block.HomographHeader ->
                out.add(OutlineItem(i, b.label, b.text.plain().trim().take(80), 0))
            is Block.Sense -> if (b.kind == SenseKind.BRANCH || b.kind == SenseKind.SENSE) {
                val lvl = (if (homographs) 1 else 0) + (if (b.kind == SenseKind.SENSE) 1 else 0)
                out.add(
                    OutlineItem(
                        i,
                        (b.markers + " " + b.label).trim(),
                        b.text.plain().replace('\n', ' ').trim().take(80),
                        lvl,
                    )
                )
            }
            else -> {}
        }
    }
    return out
}
