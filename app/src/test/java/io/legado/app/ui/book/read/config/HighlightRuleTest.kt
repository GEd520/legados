package io.legado.app.ui.book.read.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun backupNamesDoNotCollideForSameFileName() {
        val first = HighlightRuleStore.backupBgFileName("/first/background.png")
        val second = HighlightRuleStore.backupBgFileName("/second/background.png")

        assertNotEquals(first, second)
        assertTrue(first.endsWith(".png"))
    }
}
