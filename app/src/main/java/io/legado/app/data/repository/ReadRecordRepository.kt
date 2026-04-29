package io.legado.app.data.repository

import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.constant.AppConst
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class ReadRecordRepository(
    private val dao: ReadRecordDao
) {
    private fun getCurrentDeviceId(): String = AppConst.androidId

    fun getTotalReadTime(): Flow<Long> {
        return dao.getTotalReadTime().map { it ?: 0L }
    }

    fun getLatestReadRecords(query: String = ""): Flow<List<ReadRecord>> {
        return if (query.isBlank()) {
            dao.getAllReadRecordsSortedByLastRead()
        } else {
            dao.searchReadRecordsByLastRead(query)
        }
    }

    fun getAllRecordDetails(query: String = ""): Flow<List<ReadRecordDetail>> {
        return if (query.isBlank()) {
            dao.getAllDetails()
        } else {
            dao.searchDetails(query)
        }
    }

    fun getAllSessions(): Flow<List<ReadRecordSession>> {
        return dao.getAllSessions()
    }

    fun getBookSessions(bookName: String, bookAuthor: String): Flow<List<ReadRecordSession>> {
        return dao.getSessionsByBookFlow(getCurrentDeviceId(), bookName, bookAuthor)
    }

    fun getBookTimelineDays(bookName: String, bookAuthor: String): Flow<List<ReadRecordTimelineDay>> {
        return getBookSessions(bookName, bookAuthor).map { sessions ->
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sessions.groupBy { dateFormat.format(Date(it.startTime)) }
                .toSortedMap(compareByDescending { it })
                .map { (date, daySessions) ->
                    ReadRecordTimelineDay(
                        date = date,
                        sessions = daySessions.sortedByDescending { it.startTime }
                    )
                }
        }
    }

    fun getBookReadTime(bookName: String, bookAuthor: String): Flow<Long> {
        return dao.getReadTimeFlow(getCurrentDeviceId(), bookName, bookAuthor).map { it ?: 0L }
    }

    suspend fun getMergeCandidates(targetRecord: ReadRecord): List<ReadRecord> {
        return dao.getReadRecordsByNameExcludingTarget(
            targetRecord.bookName,
            targetRecord.deviceId,
            targetRecord.bookAuthor
        )
    }

    suspend fun saveReadSession(newSession: ReadRecordSession) {
        val segmentDuration = newSession.endTime - newSession.startTime
        dao.insertSession(newSession)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(Date(newSession.startTime))
        updateReadRecordDetail(newSession, segmentDuration, newSession.words, dateString)
        updateReadRecord(newSession, segmentDuration)
    }

    private suspend fun updateReadRecord(session: ReadRecordSession, durationDelta: Long) {
        if (durationDelta <= 0) return
        val existingRecord = dao.getReadRecord(session.deviceId, session.bookName, session.bookAuthor)
        if (existingRecord != null) {
            dao.update(
                existingRecord.copy(
                    readTime = existingRecord.readTime + durationDelta,
                    lastRead = session.endTime
                )
            )
        } else {
            dao.insert(
                ReadRecord(
                    deviceId = session.deviceId,
                    bookName = session.bookName,
                    bookAuthor = session.bookAuthor,
                    readTime = durationDelta,
                    lastRead = session.endTime
                )
            )
        }
    }

    private suspend fun updateReadRecordDetail(
        session: ReadRecordSession,
        durationDelta: Long,
        wordsDelta: Long,
        dateString: String
    ) {
        if (durationDelta <= 0 && wordsDelta <= 0) return
        val existingDetail = dao.getDetail(
            session.deviceId,
            session.bookName,
            session.bookAuthor,
            dateString
        )
        if (existingDetail != null) {
            existingDetail.readTime += durationDelta
            existingDetail.readWords += wordsDelta
            existingDetail.firstReadTime = min(existingDetail.firstReadTime, session.startTime)
            existingDetail.lastReadTime = max(existingDetail.lastReadTime, session.endTime)
            dao.insertDetail(existingDetail)
        } else {
            dao.insertDetail(
                ReadRecordDetail(
                    deviceId = session.deviceId,
                    bookName = session.bookName,
                    bookAuthor = session.bookAuthor,
                    date = dateString,
                    readTime = durationDelta,
                    readWords = wordsDelta,
                    firstReadTime = session.startTime,
                    lastReadTime = session.endTime
                )
            )
        }
    }

    suspend fun deleteDetail(detail: ReadRecordDetail) {
        dao.deleteDetail(detail)
        dao.deleteSessionsByBookAndDate(
            detail.deviceId,
            detail.bookName,
            detail.bookAuthor,
            detail.date
        )
        updateReadRecordTotal(detail.deviceId, detail.bookName, detail.bookAuthor)
    }

    suspend fun deleteSession(session: ReadRecordSession) {
        dao.deleteSession(session)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(Date(session.startTime))
        val remainingSessions =
            dao.getSessionsByBookAndDate(
                session.deviceId,
                session.bookName,
                session.bookAuthor,
                dateString
            )

        if (remainingSessions.isEmpty()) {
            val detail = dao.getDetail(
                session.deviceId,
                session.bookName,
                session.bookAuthor,
                dateString
            )
            detail?.let { dao.deleteDetail(it) }
        } else {
            val totalTime = remainingSessions.sumOf { it.endTime - it.startTime }
            val totalWords = remainingSessions.sumOf { it.words }
            val firstRead = remainingSessions.minOf { it.startTime }
            val lastRead = remainingSessions.maxOf { it.endTime }

            val existingDetail = dao.getDetail(
                session.deviceId,
                session.bookName,
                session.bookAuthor,
                dateString
            )
            existingDetail?.copy(
                readTime = totalTime,
                readWords = totalWords,
                firstReadTime = firstRead,
                lastReadTime = lastRead
            )?.let { dao.insertDetail(it) }
        }

        updateReadRecordTotal(session.deviceId, session.bookName, session.bookAuthor)
    }

    private suspend fun updateReadRecordTotal(deviceId: String, bookName: String, bookAuthor: String) {
        val allRemainingSessions = dao.getSessionsByBook(deviceId, bookName, bookAuthor)

        if (allRemainingSessions.isEmpty()) {
            dao.getReadRecord(deviceId, bookName, bookAuthor)?.let { dao.deleteReadRecord(it) }
        } else {
            val totalTime = allRemainingSessions.sumOf { it.endTime - it.startTime }
            val lastRead = allRemainingSessions.maxOf { it.endTime }

            dao.getReadRecord(deviceId, bookName, bookAuthor)?.copy(
                readTime = totalTime,
                lastRead = lastRead
            )?.let { dao.update(it) }
        }
    }

    suspend fun deleteReadRecord(record: ReadRecord) {
        dao.deleteReadRecord(record)
        dao.deleteDetailsByBook(record.deviceId, record.bookName, record.bookAuthor)
        dao.deleteSessionsByBook(record.deviceId, record.bookName, record.bookAuthor)
    }

    suspend fun mergeReadRecordInto(targetRecord: ReadRecord, sourceRecords: List<ReadRecord>) {
        sourceRecords.forEach { sourceRecord ->
            mergeSingleReadRecordInto(targetRecord, sourceRecord)
        }
    }

    private suspend fun mergeSingleReadRecordInto(targetRecord: ReadRecord, sourceRecord: ReadRecord) {
        if (targetRecord == sourceRecord) return
        if (targetRecord.bookName != sourceRecord.bookName) return

        val source = dao.getReadRecord(
            sourceRecord.deviceId,
            sourceRecord.bookName,
            sourceRecord.bookAuthor
        ) ?: return

        val target = dao.getReadRecord(
            targetRecord.deviceId,
            targetRecord.bookName,
            targetRecord.bookAuthor
        ) ?: targetRecord.copy(readTime = 0L, lastRead = 0L)

        val useSourceProgress = source.lastRead >= target.lastRead

        dao.insert(
            target.copy(
                readTime = target.readTime + source.readTime,
                lastRead = max(target.lastRead, source.lastRead),
                durChapterTitle = if (useSourceProgress) source.durChapterTitle else target.durChapterTitle,
                durChapterIndex = if (useSourceProgress) source.durChapterIndex else target.durChapterIndex
            )
        )

        val sourceDetails = dao.getDetailsByBook(
            sourceRecord.deviceId,
            sourceRecord.bookName,
            sourceRecord.bookAuthor
        )
        sourceDetails.forEach { detail ->
            val existingTargetDetail = dao.getDetail(
                targetRecord.deviceId,
                targetRecord.bookName,
                targetRecord.bookAuthor,
                detail.date
            )
            if (existingTargetDetail == null) {
                dao.insertDetail(
                    detail.copy(
                        deviceId = targetRecord.deviceId,
                        bookAuthor = targetRecord.bookAuthor
                    )
                )
            } else {
                dao.insertDetail(
                    existingTargetDetail.copy(
                        readTime = existingTargetDetail.readTime + detail.readTime,
                        readWords = existingTargetDetail.readWords + detail.readWords,
                        firstReadTime = min(existingTargetDetail.firstReadTime, detail.firstReadTime),
                        lastReadTime = max(existingTargetDetail.lastReadTime, detail.lastReadTime)
                    )
                )
            }
        }
        dao.deleteDetailsByBook(sourceRecord.deviceId, sourceRecord.bookName, sourceRecord.bookAuthor)

        val sourceSessions = dao.getSessionsByBook(
            sourceRecord.deviceId,
            sourceRecord.bookName,
            sourceRecord.bookAuthor
        )
        sourceSessions.forEach { session ->
            dao.updateSession(
                session.copy(
                    deviceId = targetRecord.deviceId,
                    bookAuthor = targetRecord.bookAuthor
                )
            )
        }

        dao.deleteReadRecord(source)
        updateReadRecordTotal(targetRecord.deviceId, targetRecord.bookName, targetRecord.bookAuthor)
    }

    suspend fun fixEmptyAuthors(getAuthorByBookName: suspend (String) -> String?) {
        val recordsWithEmptyAuthor = dao.getRecordsWithEmptyAuthor()
        recordsWithEmptyAuthor.forEach { record ->
            val author = getAuthorByBookName(record.bookName)
            if (!author.isNullOrBlank()) {
                val existingRecord = dao.getReadRecord(record.deviceId, record.bookName, author)
                if (existingRecord != null) {
                    mergeSingleReadRecordInto(existingRecord, record)
                } else {
                    migrateRecordAuthor(record, author)
                }
            }
        }
    }

    private suspend fun migrateRecordAuthor(record: ReadRecord, author: String) {
        val source = dao.getReadRecord(record.deviceId, record.bookName, record.bookAuthor) ?: return

        dao.insert(
            source.copy(
                bookAuthor = author
            )
        )
        dao.deleteReadRecord(source)

        val sourceDetails = dao.getDetailsByBook(record.deviceId, record.bookName, record.bookAuthor)
        sourceDetails.forEach { detail ->
            val existingTargetDetail = dao.getDetail(
                record.deviceId,
                record.bookName,
                author,
                detail.date
            )
            if (existingTargetDetail == null) {
                dao.insertDetail(detail.copy(bookAuthor = author))
            } else {
                dao.insertDetail(
                    existingTargetDetail.copy(
                        readTime = existingTargetDetail.readTime + detail.readTime,
                        readWords = existingTargetDetail.readWords + detail.readWords,
                        firstReadTime = min(existingTargetDetail.firstReadTime, detail.firstReadTime),
                        lastReadTime = max(existingTargetDetail.lastReadTime, detail.lastReadTime)
                    )
                )
            }
        }
        dao.deleteDetailsByBook(record.deviceId, record.bookName, record.bookAuthor)

        val sourceSessions = dao.getSessionsByBook(record.deviceId, record.bookName, record.bookAuthor)
        sourceSessions.forEach { session ->
            dao.updateSession(session.copy(bookAuthor = author))
        }
    }
}
