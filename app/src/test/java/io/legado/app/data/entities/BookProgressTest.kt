package io.legado.app.data.entities

import org.junit.Assert.assertTrue
import org.junit.Test

class BookProgressTest {

    @Test
    fun compareWithUsesReadingPositionBeforeSaveTime() {
        val localBook = Book(
            name = "book",
            author = "author",
            durChapterIndex = 5,
            durChapterPos = 100,
            durChapterTime = 2_000L
        )
        val remoteProgress = BookProgress(
            name = "book",
            author = "author",
            durChapterIndex = 6,
            durChapterPos = 0,
            durChapterTime = 1_000L,
            durChapterTitle = null
        )

        assertTrue(remoteProgress.isNewerThan(localBook))
    }

    @Test
    fun compareWithUsesChapterPositionWhenChapterIsSame() {
        val localBook = Book(
            name = "book",
            author = "author",
            durChapterIndex = 5,
            durChapterPos = 100,
            durChapterTime = 2_000L
        )
        val remoteProgress = BookProgress(
            name = "book",
            author = "author",
            durChapterIndex = 5,
            durChapterPos = 80,
            durChapterTime = 3_000L,
            durChapterTitle = null
        )

        assertTrue(remoteProgress.isOlderThan(localBook))
    }
}
