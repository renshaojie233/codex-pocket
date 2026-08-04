package com.codexpocket.app.ui

private val protectedCode = Regex("(?s)(```.*?```|~~~.*?~~~|`[^`\\n]*`)")
private val bracketBlock = Regex("""\\\[(.+?)\\]""", setOf(RegexOption.DOT_MATCHES_ALL))
private val parenthesizedInline = Regex("""\\\((.+?)\\\)""")
private val singleDollarInline = Regex(
    """(?<![\\$])\$(?![$\s])([^$\n]+?)(?<![\s\\])\$(?!\$)""",
)

/** Converts common ChatGPT/Codex math delimiters to Markwon's `$$` syntax. */
internal fun normalizeLatexForMarkwon(markdown: String): String {
    if ('$' !in markdown && "\\(" !in markdown && "\\[" !in markdown) return markdown
    val output = StringBuilder(markdown.length + 32)
    var cursor = 0
    protectedCode.findAll(markdown).forEach { match ->
        output.append(normalizeLatexSegment(markdown.substring(cursor, match.range.first)))
        output.append(match.value)
        cursor = match.range.last + 1
    }
    output.append(normalizeLatexSegment(markdown.substring(cursor)))
    return output.toString()
}

private fun normalizeLatexSegment(source: String): String {
    val blocks = bracketBlock.replace(source) { match ->
        "\n\$\$\n${match.groupValues[1].trim()}\n\$\$\n"
    }
    val parenthesized = parenthesizedInline.replace(blocks) { match ->
        "\$\$${match.groupValues[1]}\$\$"
    }
    return singleDollarInline.replace(parenthesized) { match ->
        "\$\$${match.groupValues[1]}\$\$"
    }
}
