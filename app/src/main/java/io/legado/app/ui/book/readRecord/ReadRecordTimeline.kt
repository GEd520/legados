package io.legado.app.ui.book.readRecord

import io.legado.app.data.entities.readRecord.ReadRecordSession

private const val CONTINUOUS_SESSION_GAP = 20 * 60 * 1000L

data class TimelineSessionItem(
    val session: ReadRecordSession,
    val sourceSessionIds: List<Long>
)

internal fun mergeContinuousReadRecordSessions(
    sessions: List<ReadRecordSession>
): List<TimelineSessionItem> {
    if (sessions.isEmpty()) return emptyList()
    var isAscending = true
    var isDescending = true
    for (i in 1 until sessions.size) {
        val previousStart = sessions[i - 1].startTime
        val currentStart = sessions[i].startTime
        if (previousStart > currentStart) isAscending = false
        if (previousStart < currentStart) isDescending = false
        if (!isAscending && !isDescending) break
    }
    val orderedSessions = when {
        isAscending -> sessions
        isDescending -> sessions.asReversed()
        else -> sessions.sortedBy { it.startTime }
    }
    val mergedList = mutableListOf(
        MutableTimelineSessionItem(
            session = orderedSessions.first().copy(),
            sourceSessionIds = mutableListOf(orderedSessions.first().id)
        )
    )

    for (i in 1 until orderedSessions.size) {
        val current = orderedSessions[i]
        val last = mergedList.last()
        val lastSession = last.session
        if (current.deviceId == lastSession.deviceId &&
            current.bookName == lastSession.bookName &&
            current.bookAuthor == lastSession.bookAuthor &&
            current.startTime - lastSession.endTime <= CONTINUOUS_SESSION_GAP
        ) {
            val representativeId = when {
                current.startTime > lastSession.startTime -> current.id
                current.startTime == lastSession.startTime -> maxOf(lastSession.id, current.id)
                else -> lastSession.id
            }
            last.session = lastSession.copy(
                id = representativeId,
                endTime = maxOf(lastSession.endTime, current.endTime),
                words = lastSession.words + current.words,
                durChapterTitle = current.durChapterTitle.ifBlank {
                    lastSession.durChapterTitle
                }
            )
            last.sourceSessionIds.add(current.id)
        } else {
            mergedList.add(
                MutableTimelineSessionItem(
                    session = current.copy(),
                    sourceSessionIds = mutableListOf(current.id)
                )
            )
        }
    }
    return mergedList.map { item ->
        TimelineSessionItem(item.session, item.sourceSessionIds.toList())
    }
}

private data class MutableTimelineSessionItem(
    var session: ReadRecordSession,
    val sourceSessionIds: MutableList<Long>
)
