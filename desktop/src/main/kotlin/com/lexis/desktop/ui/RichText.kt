package com.lexis.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.lexis.reader.core.Rich
import com.lexis.reader.core.Run
import com.lexis.reader.core.Tone
import com.lexis.desktop.ui.theme.ArticleColors
import com.lexis.desktop.ui.theme.LocalArticleColors

/** Converts runs to an [AnnotatedString]; cross-references and abbreviations become tappable links. */
class RunStyler(
    private val colors: ArticleColors,
    private val onLink: (String) -> Unit,
    /** Called with the abbreviation text when the user taps one; null disables abbreviation taps. */
    private val onAbbreviation: ((String) -> Unit)?,
    /** Faint background behind tappable abbreviations so they are discoverable. */
    private val abbreviationTint: Color,
) {
    fun spanFor(r: Run): SpanStyle {
        var style = SpanStyle()
        if (r.bold) style = style.copy(fontWeight = FontWeight.Bold)
        if (r.italic) style = style.copy(fontStyle = FontStyle.Italic)
        if (r.sup) style = style.copy(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em)
        if (r.sub) style = style.copy(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)
        when (r.tone) {
            Tone.QUOTE -> style = style.copy(color = colors.quote)
            Tone.SENSE -> style = style.copy(color = colors.senseLabel, fontWeight = FontWeight.Bold)
            Tone.ETYM -> style = style.copy(color = colors.etymology)
            Tone.PRON -> style = style.copy(color = colors.pronunciation)
            null -> {}
        }
        return style
    }

    fun append(builder: AnnotatedString.Builder, runs: Rich) {
        for (r in runs) {
            val span = spanFor(r)
            val target = r.link
            val abbr = r.text.trim()
            when {
                target != null -> {
                    val link = LinkAnnotation.Clickable(
                        tag = target,
                        styles = TextLinkStyles(style = span.copy(color = colors.link)),
                        linkInteractionListener = { onLink(target) },
                    )
                    builder.withLink(link) { append(r.text) }
                }
                r.abbr && onAbbreviation != null && abbr.isNotEmpty() && abbr.any { it.isLetterOrDigit() } -> {
                    val handler = onAbbreviation
                    val link = LinkAnnotation.Clickable(
                        tag = "abbr:$abbr",
                        styles = TextLinkStyles(style = span.copy(background = abbreviationTint)),
                        linkInteractionListener = { handler(abbr) },
                    )
                    builder.withLink(link) { append(r.text) }
                }
                else -> builder.withStyle(span) { append(r.text) }
            }
        }
    }

    fun build(runs: Rich): AnnotatedString = AnnotatedString.Builder().also { append(it, runs) }.toAnnotatedString()
}

@Composable
fun rememberRunStyler(onLink: (String) -> Unit, onAbbreviation: ((String) -> Unit)? = null): RunStyler {
    val colors = LocalArticleColors.current
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    return remember(colors, onLink, onAbbreviation, tint) { RunStyler(colors, onLink, onAbbreviation, tint) }
}
