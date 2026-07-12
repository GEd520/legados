package io.legado.app.ui.book.readRecord

import io.legado.app.data.entities.readRecord.ReadRecordSession
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadRecordTimelineTest {

    @Test
    fun `appending an older continuous session keeps the visible item id`() {
        val newest = session(id = 30, startTime = 3_000, endTime = 4_000)
        val older = session(id = 10, startTime = 1_000, endTime = 2_500)

        val initial = mergeContinuousReadRecordSessions(listOf(newest)).single()
        val expanded = mergeContinuousReadRecordSessions(listOf(newest, older)).single()

        assertEquals(initial.session.id, expanded.session.id)
        assertEquals(1_000L, expanded.session.startTime)
        assertEquals(4_000L, expanded.session.endTime)
        assertEquals(listOf(10L, 30L), expanded.sourceSessionIds)
    }

    @Test
    fun `equal timestamps use the greatest id as a stable tie breaker`() {
        val first = session(id = 10, startTime = 1_000, endTime = 2_000)
        val second = session(id = 20, startTime = 1_000, endTime = 2_500)

        val merged = mergeContinuousReadRecordSessions(listOf(first, second)).single()

        assertEquals(20L, merged.session.id)
        assertEquals(2_500L, merged.session.endTime)
    }

    @Test
    fun `descending database order merges without changing the result`() {
        val newest = session(id = 30, startTime = 3_000, endTime = 4_000)
        val middle = session(id = 20, startTime = 2_000, endTime = 2_500)
        val oldest = session(id = 10, startTime = 1_000, endTime = 1_500)

        val merged = mergeContinuousReadRecordSessions(listOf(newest, middle, oldest)).single()

        assertEquals(30L, merged.session.id)
        assertEquals(1_000L, merged.session.startTime)
        assertEquals(4_000L, merged.session.endTime)
    }

    @Test
    fun `sessions from different devices are never merged`() {
        val first = session(id = 10, startTime = 1_000, endTime = 2_000, deviceId = "A")
        val second = session(id = 20, startTime = 2_100, endTime = 3_000, deviceId = "B")

        val merged = mergeContinuousReadRecordSessions(listOf(first, second))

        assertEquals(2, merged.size)
        assertEquals(listOf("A", "B"), merged.map { it.session.deviceId })
    }

    private fun session(
        id: Long,
        startTime: Long,
        endTime: Long,
        deviceId: String = "device"
    ) = ReadRecordSession(
        id = id,
        deviceId = deviceId,
        bookName = "Book",
        bookAuthor = "Author",
        startTime = startTime,
        endTime = endTime
    )
}
