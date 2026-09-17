package com.lexis.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexis.reader.core.Block
import com.lexis.reader.core.Citation
import com.lexis.reader.core.Exporter
import com.lexis.reader.data.QuotationsMode
import com.lexis.reader.ui.theme.LocalArticleColors

/** Text styles for article rendering, scaled by the user's text-size preference. */
class ArticleTypography(scale: Float, serif: Boolean) {
    private val family = if (serif) FontFamily.Serif else FontFamily.Default
    val body = TextStyle(fontFamily = family, fontSize = (16.5f * scale).sp, lineHeight = (24f * scale).sp)
    val citation = TextStyle(fontFamily = family, fontSize = (15f * scale).sp, lineHeight = (21.5f * scale).sp)
    val small = TextStyle(fontFamily = family, fontSize = (13.5f * scale).sp, lineHeight = (19f * scale).sp)
    val headword = TextStyle(fontFamily = family, fontSize = (28f * scale).sp, lineHeight = (34f * scale).sp, fontWeight = FontWeight.Bold)
    val section = TextStyle(fontFamily = family, fontSize = (18f * scale).sp, lineHeight = (25f * scale).sp, fontWeight = FontWeight.SemiBold)
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = (12.5f * scale).sp, lineHeight = (16f * scale).sp)
    val hanging = TextIndent(firstLine = 0.sp, restLine = (18f * scale).sp)
}

@Composable
fun rememberArticleTypography(scale: Float, serif: Boolean): ArticleTypography =
    remember(scale, serif) { ArticleTypography(scale, serif) }

private fun indentDp(indent: Int) = (indent.coerceIn(0, 6) * 14).dp

/**
 * Renders one block. [quotesExpanded] is the user's explicit choice for this block (null = follow
 * the default [quotesMode]); [etymExpanded] likewise for long etymologies.
 */
@Composable
fun BlockView(
    block: Block,
    typo: ArticleTypography,
    styler: RunStyler,
    quotesMode: QuotationsMode,
    quotesExpanded: Boolean?,
    etymExpanded: Boolean,
    highlighted: Boolean,
    onToggle: () -> Unit,
) {
    val hl = if (highlighted) Modifier.background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)) else Modifier
    Box(Modifier.fillMaxWidth().then(hl).padding(horizontal = 16.dp)) {
        when (block) {
            is Block.Headword -> HeadwordBlock(block, typo)
            is Block.HomographHeader -> HomographBlock(block, typo, styler)
            is Block.Pronunciation -> PronunciationBlock(block, typo, styler)
            is Block.Etymology -> EtymologyBlock(block, typo, styler, etymExpanded, onToggle)
            is Block.Sense -> SenseBlock(block, typo, styler)
            is Block.Paragraph -> ParagraphBlock(block, typo, styler)
            is Block.Quotations -> QuotationsBlock(block, typo, styler, quotesMode, quotesExpanded, onToggle)
            Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 10.dp))
        }
    }
}

@Composable
private fun HeadwordBlock(b: Block.Headword, typo: ArticleTypography) {
    Text(
        text = b.text.joinToString("") { it.text },
        style = typo.headword,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun HomographBlock(b: Block.HomographHeader, typo: ArticleTypography, styler: RunStyler) {
    Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(end = 10.dp, top = 2.dp),
            ) {
                Text(
                    b.label,
                    style = typo.label.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Text(styler.build(b.text), style = typo.section)
        }
    }
}

@Composable
private fun PronunciationBlock(b: Block.Pronunciation, typo: ArticleTypography, styler: RunStyler) {
    Text(
        styler.build(b.text),
        style = typo.body.copy(color = LocalArticleColors.current.pronunciation),
        modifier = Modifier.padding(start = indentDp(b.indent), top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun EtymologyBlock(b: Block.Etymology, typo: ArticleTypography, styler: RunStyler, expanded: Boolean, onToggle: () -> Unit) {
    val colors = LocalArticleColors.current
    Text(
        styler.build(b.text),
        style = typo.small.copy(color = colors.etymology),
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = indentDp(b.indent), top = 3.dp, bottom = 3.dp),
    )
}

@Composable
private fun SenseBlock(b: Block.Sense, typo: ArticleTypography, styler: RunStyler) {
    val colors = LocalArticleColors.current
    val text = remember(b, styler) {
        AnnotatedString.Builder().apply {
            if (b.markers.isNotEmpty()) {
                withStyle(SpanStyle(color = colors.marker)) { append(b.markers) }
                append(' ')
            }
            withStyle(SpanStyle(color = colors.senseLabel, fontWeight = FontWeight.Bold)) { append(b.label) }
            append("  ")
            styler.append(this, b.text)
        }.toAnnotatedString()
    }
    val top = when (b.kind) {
        com.lexis.reader.core.SenseKind.BRANCH -> 14.dp
        com.lexis.reader.core.SenseKind.SENSE -> 10.dp
        else -> 6.dp
    }
    Text(
        text,
        style = typo.body.copy(textIndent = typo.hanging),
        modifier = Modifier.padding(start = indentDp(b.indent), top = top, bottom = 2.dp),
    )
}

@Composable
private fun ParagraphBlock(b: Block.Paragraph, typo: ArticleTypography, styler: RunStyler) {
    Text(
        styler.build(b.text),
        style = typo.body,
        modifier = Modifier.padding(start = indentDp(b.indent), top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun QuotationsBlock(
    b: Block.Quotations,
    typo: ArticleTypography,
    styler: RunStyler,
    mode: QuotationsMode,
    expandedChoice: Boolean?,
    onToggle: () -> Unit,
) {
    val expanded = expandedChoice ?: (mode == QuotationsMode.EXPANDED)
    val shown = when {
        expanded -> b.items.size
        mode == QuotationsMode.FIRST -> 1
        else -> 0
    }
    val hidden = b.items.size - shown
    val colors = LocalArticleColors.current
    Row(
        Modifier
            .padding(start = indentDp(b.indent) + 4.dp, top = 4.dp, bottom = 4.dp)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Column(Modifier.padding(start = 10.dp).fillMaxWidth()) {
            for (i in 0 until shown) {
                CitationView(b.items[i], typo, styler, colors.citation)
                if (i < shown - 1) Spacer(Modifier.height(5.dp))
            }
            val summaryText = when {
                hidden > 0 && shown == 0 -> "▸ " + Exporter.summary(b)
                hidden > 0 -> "▸ $hidden more" + (b.lastDate?.let { " · to $it" } ?: "")
                b.items.size > 1 -> "▾ Show fewer"
                else -> null
            }
            if (summaryText != null) {
                Text(
                    summaryText,
                    style = typo.label.copy(color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .padding(top = if (shown > 0) 6.dp else 1.dp, bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CitationView(c: Citation, typo: ArticleTypography, styler: RunStyler, citationColor: androidx.compose.ui.graphics.Color) {
    val text = remember(c, styler) {
        AnnotatedString.Builder().apply {
            withStyle(SpanStyle(color = citationColor, fontSize = typo.small.fontSize)) {
                styler.append(this, c.before)
            }
            if (c.before.isNotEmpty() && c.quote.isNotEmpty()) append("  ")
            styler.append(this, c.quote)
            if (c.after.isNotEmpty()) {
                append(' ')
                withStyle(SpanStyle(color = citationColor, fontSize = typo.small.fontSize)) { styler.append(this, c.after) }
            }
        }.toAnnotatedString()
    }
    Text(text, style = typo.citation.copy(textIndent = typo.hanging))
}
