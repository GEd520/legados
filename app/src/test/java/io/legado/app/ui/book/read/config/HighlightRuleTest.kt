package io.legado.app.ui.book.read.config

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
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

    @Test
    fun releaseObfuscatedBackupRemainsReadable() {
        val json = """
            {
              "a":[{"a":"rule-id","b":"rule-name","c":"keyword","d":false,"f":"default","h":true}],
              "b":["default"],
              "c":"default",
              "d":true,
              "e":false,
              "f":true
            }
        """.trimIndent()

        val backup = GSON.fromJson(json, HighlightRuleStore.BackupData::class.java)

        assertEquals("rule-id", backup.rules.single().id)
        assertEquals("keyword", backup.rules.single().pattern)
        assertFalse(backup.rules.single().isRegex ?: true)
        assertEquals(false, backup.bookTitleEnabled)
    }

    @Test
    fun backupSerializationUsesStableFieldNames() {
        val backup = HighlightRuleStore.BackupData(
            rules = listOf(HighlightRule(id = "rule-id", pattern = "keyword"))
        )

        val json = GSON.toJson(backup)

        assertTrue(json.contains("\"rules\""))
        assertTrue(json.contains("\"id\""))
        assertFalse(json.contains("\"a\""))
    }
}
