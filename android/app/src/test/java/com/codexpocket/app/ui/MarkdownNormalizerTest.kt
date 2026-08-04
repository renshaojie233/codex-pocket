package com.codexpocket.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownNormalizerTest {
    @Test
    fun `normalizes common inline delimiters`() {
        assertEquals(
            "结果是 \$\$x^2 + 1\$\$，以及 \$\$y_1\$\$。",
            normalizeLatexForMarkwon("结果是 \\(x^2 + 1\\)，以及 \$y_1\$。"),
        )
    }

    @Test
    fun `normalizes bracketed display formula`() {
        assertEquals(
            "公式：\n\n\$\$\n\\frac{a}{b}\n\$\$\n",
            normalizeLatexForMarkwon("公式：\n\\[\\frac{a}{b}\\]"),
        )
    }

    @Test
    fun `does not rewrite formulas inside code`() {
        assertEquals(
            "`\$x\$` and ```\n\\(y\\)\n```",
            normalizeLatexForMarkwon("`\$x\$` and ```\n\\(y\\)\n```"),
        )
    }
}
