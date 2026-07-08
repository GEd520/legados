package io.legado.app.help.book

import io.legado.app.constant.AppPattern

object SpecialContentProtector {

    private val imgRegex = Regex(
        """<img\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val newPageRegex = Regex("""(?m)^\s*\[newpage]\s*$""")

    fun protect(content: String): ProtectedContent {
        val placeholders = linkedMapOf<String, String>()
        var protected = content

        fun reserve(value: String): String {
            val key = "\uE000LEGADO_SPECIAL_${placeholders.size}\uE001"
            placeholders[key] = value
            return key
        }

        protected = AppPattern.useHtmlRegex.replace(protected) { reserve(it.value) }
        protected = imgRegex.replace(protected) { reserve(it.value) }
        protected = newPageRegex.replace(protected) { reserve(it.value) }
        return ProtectedContent(protected, placeholders)
    }

    data class ProtectedContent(
        val content: String,
        private val placeholders: Map<String, String>
    ) {
        fun restore(value: String): String {
            var restored = value
            placeholders.forEach { (placeholder, original) ->
                restored = restored.replace(placeholder, original)
            }
            return restored
        }
    }
}
