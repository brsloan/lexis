package com.lexis.reader.core

import java.text.Normalizer

/** Normalisation used for index keys and lookups so that "élan" matches "elan", "Æsthetic" matches "aesthetic", etc. */
object TextNormalizer {

    private val COMBINING = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")

    fun norm(s: String): String {
        val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
        val stripped = COMBINING.replace(decomposed, "")
        val sb = StringBuilder(stripped.length)
        for (c in stripped.lowercase()) {
            when (c) {
                'æ' -> sb.append("ae")
                'œ' -> sb.append("oe")
                'ð' -> sb.append('d')
                'þ' -> sb.append("th")
                'ß' -> sb.append("ss")
                'ø' -> sb.append('o')
                'ł' -> sb.append('l')
                'ᵹ', 'ȝ' -> sb.append('g')
                'ı' -> sb.append('i')
                '’', '‘', '`', 'ʼ' -> sb.append('\'')
                '‐', '‑', '‒', '–', '—' -> sb.append('-')
                else -> sb.append(c)
            }
        }
        return SPACES.replace(sb.toString(), " ").trim()
    }

    /** Letters and digits only: the loosest matching key. */
    fun key(s: String): String {
        val n = norm(s)
        val sb = StringBuilder(n.length)
        for (c in n) if (c.isLetterOrDigit()) sb.append(c)
        return sb.toString()
    }
}
