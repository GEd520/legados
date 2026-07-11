package io.legado.app.ui.book.read.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightRuleTest {

    @Test
    fun legacyRuleUsesRegex() {
        val rule = HighlightRule(pattern = "a.+b", isRegex = null)

        assertTrue(rule.compilePattern().containsMatchIn("a12b"))
    }

    @Test
    fun plainTextRuleEscapesRegexCharacters() {
        val rule = HighlightRule(pattern = "a.+b", isRegex = false)

        assertTrue(rule.compilePattern().containsMatchIn("text a.+b text"))
        assertFalse(rule.compilePattern().containsMatchIn("a12b"))
    }

    @Test
    fun explicitRegexRuleUsesRegex() {
        val rule = HighlightRule(pattern = "a.+b", isRegex = true)

        assertTrue(rule.compilePattern().containsMatchIn("a12b"))
    }
}
