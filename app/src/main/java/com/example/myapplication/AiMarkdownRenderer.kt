package com.example.myapplication

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Rich Markdown parser for Jetpack Compose with high-contrast Dark & Light Theme support:
 * - **bold text** with bold styling and distinct contrast
 * - *italic text*
 * - # Headings (H1, H2, H3)
 * - • Bullet points and emojis
 * - `code snippets`
 */
object AiMarkdownRenderer {

    fun formatMarkdown(markdown: String, defaultColor: Color, primaryColor: Color): AnnotatedString {
        val lines = markdown.lines()
        val builder = AnnotatedString.Builder()

        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trim()

            when {
                // H1 / H2 / H3 Headings
                trimmed.startsWith("###") -> {
                    val content = trimmed.removePrefix("###").trim()
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = defaultColor)) {
                        appendInlineFormatted(this, content, defaultColor, primaryColor)
                    }
                }
                trimmed.startsWith("##") -> {
                    val content = trimmed.removePrefix("##").trim()
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = primaryColor)) {
                        appendInlineFormatted(this, content, defaultColor, primaryColor)
                    }
                }
                trimmed.startsWith("#") -> {
                    val content = trimmed.removePrefix("#").trim()
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 17.sp, color = primaryColor)) {
                        appendInlineFormatted(this, content, defaultColor, primaryColor)
                    }
                }
                else -> {
                    builder.withStyle(SpanStyle(color = defaultColor)) {
                        appendInlineFormatted(this, line, defaultColor, primaryColor)
                    }
                }
            }

            if (lineIndex < lines.size - 1) {
                builder.append("\n")
            }
        }

        return builder.toAnnotatedString()
    }

    private fun appendInlineFormatted(builder: AnnotatedString.Builder, text: String, defaultColor: Color, primaryColor: Color) {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            // Check for Bold: **text**
            if (cursor + 1 < length && text[cursor] == '*' && text[cursor + 1] == '*') {
                val endBold = text.indexOf("**", cursor + 2)
                if (endBold != -1) {
                    val boldContent = text.substring(cursor + 2, endBold)
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                        append(boldContent)
                    }
                    cursor = endBold + 2
                    continue
                }
            }

            // Check for Inline Code: `text`
            if (text[cursor] == '`') {
                val endCode = text.indexOf('`', cursor + 1)
                if (endCode != -1) {
                    val codeContent = text.substring(cursor + 1, endCode)
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = primaryColor, background = primaryColor.copy(alpha = 0.15f))) {
                        append(" $codeContent ")
                    }
                    cursor = endCode + 1
                    continue
                }
            }

            // Check for Italic: *text* (single asterisk)
            if (text[cursor] == '*' && (cursor + 1 >= length || text[cursor + 1] != '*')) {
                val endItalic = text.indexOf('*', cursor + 1)
                if (endItalic != -1) {
                    val italicContent = text.substring(cursor + 1, endItalic)
                    builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor.copy(alpha = 0.9f))) {
                        append(italicContent)
                    }
                    cursor = endItalic + 1
                    continue
                }
            }

            // Normal character explicitly with defaultColor
            builder.withStyle(SpanStyle(color = defaultColor)) {
                append(text[cursor].toString())
            }
            cursor++
        }
    }
}

@Composable
fun FormattedMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE2E2EA),
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 21.sp,
    primaryColor: Color = Color(0xFF7B61FF)
) {
    val annotated = AiMarkdownRenderer.formatMarkdown(text, color, primaryColor)
    Text(
        text = annotated,
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight
    )
}
