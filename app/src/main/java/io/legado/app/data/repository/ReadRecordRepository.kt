package io.legado.app.data.repository

import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.constant.AppConst
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

class ReadRecordRepository(
    private val dao: ReadRecordDao,
    private val currentDeviceIdProvider: () -> String = { AppConst.androidId }
) {
    companion object {
        const val CURRENT_REPAIR_VERSION = 4
        private const val IMPORT_BATCH_SIZE = 500
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    private data class RecordIdentity(
        val deviceId: String,
        val bookName: String,
        val bookAuthor: String
    )

    private data class DetailIdentity(
        val deviceId: String,
        val bookName: String,
        val bookAuthor: String,
        val date: String
    )

    private data class SessionIdentity(
        val deviceId: String,
        val bookName: String,
        val bookAuthor: String,
        val startTime: Long,
        val endTime: Long,
        val words: Long
    )

    private data class ReadRecordAggregate(
        var detailReadTime: Long = 0L,
        var sessionReadTime: Long = 0L,
        var lastRead: Long = 0L
    ) {
        val readTime: Long
            get() = max(detailReadTime, sessionReadTime)
    }

    private fun getCurrentDeviceId(): String = currentDeviceIdProvider()

    private fun normalizeBookName(bookName: String): String = bookName.trim()

    private fun normalizeBookAuthor(bookAuthor: String): String = bookAuthor.trim()

    private fun normalizeRecord(record: ReadRecord): ReadRecord {
        return record.copy(
            bookName = normalizeBookName(record.bookName),
            bookAuthor = normalizeBookAuthor(record.bookAuthor)
        )
    }

    private fun normalizeDetail(detail: ReadRecordDetail): ReadRecordDetail {
        return detail.copy(
            bookName = normalizeBookName(detail.bookName),
            bookAuthor = normalizeBookAuthor(detail.bookAuthor)
        )
    }

    private fun normalizeSession(session: ReadRecordSession): ReadRecordSession {
        return session.copy(
            bookName = normalizeBookName(session.bookName),
            bookAuthor = normalizeBookAuthor(session.bookAuthor)
        )
    }

    private fun hasValidIdentity(deviceId: String, bookName: String): Boolean {
        return deviceId.isNotBlank() && bookName.isNotBlank()
    }

    private fun isValidRecord(record: ReadRecord): Boolean {
        return hasValidIdentity(record.deviceId, record.bookName)
    }

    private fun isValidDetail(detail: ReadRecordDetail): Boolean {
        return hasValidIdentity(detail.deviceId, detail.bookName) &&
            runCatching {
                LocalDate.parse(detail.date, DateTimeFormatter.ISO_LOCAL_DATE)
            }.isSuccess
    }

    private fun isValidSession(session: ReadRecordSession): Boolean {
        return hasValidIdentity(session.deviceId, session.bookName)
    }

    fun getTotalReadTime(): Flow<Long> {
        return combine(
            dao.getAllReadRecordsSortedByLastRead(),
            dao.getAllDetails()
        ) { records, details ->
            applyDetailReadTimes(records, details).sumOf { it.readTime }
        }
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
            val merged = mergeCloseSessions(sessions)
            merged.groupBy { dateFormat.format(Date(it.startTime)) }
                .toSortedMap(compareByDescending { it })
                .map { (date, daySessions) ->
                    ReadRecordTimelineDay(
                        date = date,
                        sessions = daySessions.sortedByDescending { it.startTime }
                    )
                }
        }
    }

    private fun mergeCloseSessions(sessions: List<ReadRecordSession>): List<ReadRecordSession> {
        if (sessions.isEmpty()) return emptyList()
        val sorted = sessions.sortedBy { it.startTime }
        val merged = mutableListOf<ReadRecordSession>()
        merged.add(sorted.first().copy())
        val gapLimit = 5 * 60 * 1000L
        for (i in 1 until sorted.size) {
            val current = sorted[i]
            val last = merged.last()
            if ((current.startTime - last.endTime) <= gapLimit) {
                merged[merged.lastIndex] = last.copy(
                    endTime = max(current.endTime, last.endTime),
                    words = last.words + current.words
                )
            } else {
                merged.add(current.copy())
            }
        }
        return merged
    }

    fun getBookReadTime(bookName: String, bookAuthor: String): Flow<Long> {
        return combine(
            dao.getReadTimeFlow(getCurrentDeviceId(), bookName, bookAuthor),
            getAllRecordDetails()
        ) { recordReadTime, details ->
            val detailReadTime = details.asSequence()
                .filter {
                    it.deviceId == getCurrentDeviceId() &&
                        it.bookName == bookName &&
                        it.bookAuthor == bookAuthor
                }
                .sumOf { it.readTime }
            max(recordReadTime ?: 0L, detailReadTime)
        }
    }

    fun applyDetailReadTimes(
        records: List<ReadRecord>,
        details: List<ReadRecordDetail>
    ): List<ReadRecord> {
        val detailReadTimes = details
            .groupBy { RecordIdentity(it.deviceId, it.bookName, it.bookAuthor) }
            .mapValues { (_, groupedDetails) -> groupedDetails.sumOf { it.readTime } }
        return records.map { record ->
            val detailReadTime = detailReadTimes[
                RecordIdentity(record.deviceId, record.bookName, record.bookAuthor)
            ]
            if (detailReadTime != null) {
                record.copy(readTime = max(record.readTime, detailReadTime))
            } else {
                record
            }
        }
    }

    suspend fun getMergeCandidates(targetRecord: ReadRecord): List<ReadRecord> {
        return dao.getReadRecordsByNameExcludingTarget(
            targetRecord.bookName,
            targetRecord.deviceId,
            targetRecord.bookAuthor
        )
    }

    suspend fun saveReadSession(newSession: ReadRecordSession) {
        val session = normalizeSession(newSession)
        if (!isValidSession(session)) return
        splitSessionByDay(session).forEach { sessionSegment ->
            val segmentDuration = sessionSegment.endTime - sessionSegment.startTime
            if (segmentDuration <= 0L && sessionSegment.words <= 0L) return@forEach
            dao.insertSession(sessionSegment)
            val dateString = dateFormat.format(Date(sessionSegment.startTime))
            updateReadRecordDetail(sessionSegment, segmentDuration, sessionSegment.words, dateString)
            updateReadRecord(sessionSegment, segmentDuration)
        }
    }

    private fun splitSessionByDay(session: ReadRecordSession): List<ReadRecordSession> {
        val totalDuration = session.endTime - session.startTime
        if (totalDuration <= 0L) {
            return if (session.words > 0L) listOf(session) else emptyList()
        }
        val zoneId = ZoneId.systemDefault()
        val segments = mutableListOf<ReadRecordSession>()
        var segmentStart = session.startTime
        var remainingWords = session.words.coerceAtLeast(0L)

        while (segmentStart < session.endTime) {
            val nextDayStart = Instant.ofEpochMilli(segmentStart)
                .atZone(zoneId)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
            val segmentEnd = min(session.endTime, nextDayStart)
            val segmentDuration = segmentEnd - segmentStart
            if (segmentDuration <= 0L) {
                break
            }
            val isLastSegment = segmentEnd >= session.endTime
            val segmentWords = when {
                remainingWords <= 0L -> 0L
                isLastSegment -> remainingWords
                else -> ((session.words * segmentDuration) / totalDuration).coerceAtMost(remainingWords)
            }
            segments += session.copy(
                id = 0,
                startTime = segmentStart,
                endTime = segmentEnd,
                words = segmentWords
            )
            remainingWords -= segmentWords
            segmentStart = segmentEnd
        }
        return segments
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

    private suspend fun updateReadRecordTotal(
        deviceId: String,
        bookName: String,
        bookAuthor: String,
        minimumReadTime: Long = 0L,
        minimumLastRead: Long = 0L
    ) {
        val allRemainingSessions = dao.getSessionsByBook(deviceId, bookName, bookAuthor)
        val allRemainingDetails = dao.getDetailsByBook(deviceId, bookName, bookAuthor)

        if (allRemainingSessions.isEmpty() && allRemainingDetails.isEmpty()) {
            dao.getReadRecord(deviceId, bookName, bookAuthor)?.let { existing ->
                if (minimumReadTime > 0L || minimumLastRead > 0L) {
                    dao.update(
                        existing.copy(
                            readTime = max(existing.readTime, minimumReadTime),
                            lastRead = max(existing.lastRead, minimumLastRead)
                        )
                    )
                } else {
                    dao.deleteReadRecord(existing)
                }
            }
        } else {
            val sessionTotalTime = allRemainingSessions.sumOf { it.endTime - it.startTime }
            val detailTotalTime = allRemainingDetails.sumOf { it.readTime }
            val totalTime = max(max(sessionTotalTime, detailTotalTime), minimumReadTime)
            val sessionLastRead = allRemainingSessions.maxOfOrNull { it.endTime } ?: 0L
            val detailLastRead = allRemainingDetails.maxOfOrNull { it.lastReadTime } ?: 0L
            val lastRead = max(max(sessionLastRead, detailLastRead), minimumLastRead)

            val existingRecord = dao.getReadRecord(deviceId, bookName, bookAuthor)
            if (existingRecord != null) {
                dao.update(
                    existingRecord.copy(
                        readTime = totalTime,
                        lastRead = lastRead
                    )
                )
            } else {
                dao.insert(
                    ReadRecord(
                        deviceId = deviceId,
                        bookName = bookName,
                        bookAuthor = bookAuthor,
                        readTime = totalTime,
                        lastRead = lastRead
                    )
                )
            }
        }
    }

    suspend fun deleteReadRecord(record: ReadRecord) {
        dao.deleteReadRecord(record)
        dao.deleteDetailsByBook(record.deviceId, record.bookName, record.bookAuthor)
        dao.deleteSessionsByBook(record.deviceId, record.bookName, record.bookAuthor)
    }

    suspend fun deleteReadRecordByDate(record: ReadRecord, date: String) {
        dao.getDetail(record.deviceId, record.bookName, record.bookAuthor, date)?.let {
            dao.deleteDetail(it)
        }
        dao.deleteSessionsByBookAndDate(record.deviceId, record.bookName, record.bookAuthor, date)
        updateReadRecordTotal(record.deviceId, record.bookName, record.bookAuthor)
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

        val mergedReadTime = target.readTime + source.readTime
        val mergedLastRead = max(target.lastRead, source.lastRead)

        dao.insert(
            target.copy(
                readTime = mergedReadTime,
                lastRead = mergedLastRead,
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
        updateReadRecordTotal(
            targetRecord.deviceId,
            targetRecord.bookName,
            targetRecord.bookAuthor,
            minimumReadTime = mergedReadTime,
            minimumLastRead = mergedLastRead
        )
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

    suspend fun repairRecords(getAuthorByBookName: suspend (String) -> String?) {
        cleanupBlankBookNameData()
        fixEmptyAuthors(getAuthorByBookName)
        normalizeDuplicateDeviceRecords()
        rebuildAggregateRecordsFromHistory()
    }

    suspend fun cleanupBlankBookNameData() {
        dao.deleteRecordsWithBlankBookName()
        dao.deleteDetailsWithBlankBookName()
        dao.deleteSessionsWithBlankBookName()
    }

    suspend fun rebuildAggregateRecordsFromHistory() {
        val rebuiltRecords = buildAggregateRecords(
            records = dao.all.filter(::isValidRecord),
            details = dao.getAllDetailsList().filter(::isValidDetail),
            sessions = dao.getAllSessionsList().filter(::isValidSession)
        )
        insertRecordsInBatches(rebuiltRecords)
    }

    suspend fun normalizeDuplicateDeviceRecords() {
        val currentDeviceId = getCurrentDeviceId()
        val groupedRecords = dao.all.groupBy { it.bookName to it.bookAuthor }
        groupedRecords.values.forEach { records ->
            if (records.size <= 1) return@forEach
            val targetRecord = records.firstOrNull { it.deviceId == currentDeviceId }
                ?: records.maxByOrNull { it.lastRead }
                ?: return@forEach

            if (targetRecord.deviceId != currentDeviceId) {
                val migratedTarget = targetRecord.copy(deviceId = currentDeviceId)
                importSingleRecord(migratedTarget)
                importSingleDetailRecords(
                    dao.getDetailsByBook(
                        targetRecord.deviceId,
                        targetRecord.bookName,
                        targetRecord.bookAuthor
                    ).map { it.copy(deviceId = currentDeviceId) }
                )
                importSingleSessionRecords(
                    dao.getSessionsByBook(
                        targetRecord.deviceId,
                        targetRecord.bookName,
                        targetRecord.bookAuthor
                    ).map { it.copy(id = 0, deviceId = currentDeviceId) }
                )
                dao.deleteDetailsByBook(targetRecord.deviceId, targetRecord.bookName, targetRecord.bookAuthor)
                dao.deleteSessionsByBook(targetRecord.deviceId, targetRecord.bookName, targetRecord.bookAuthor)
                dao.deleteReadRecord(targetRecord)
            }

            val normalizedTarget = dao.getReadRecord(
                currentDeviceId,
                targetRecord.bookName,
                targetRecord.bookAuthor
            ) ?: return@forEach

            records.filter { it.deviceId != currentDeviceId }.forEach { sourceRecord ->
                mergeSingleReadRecordInto(normalizedTarget, sourceRecord)
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

    suspend fun importRecords(
        records: List<ReadRecord>,
        details: List<ReadRecordDetail> = emptyList(),
        sessions: List<ReadRecordSession> = emptyList(),
        databaseCleared: Boolean = false
    ) {
        if (databaseCleared) {
            importRecordsIntoClearedTables(records, details, sessions)
            return
        }
        records.forEach { record ->
            importSingleRecord(record)
        }
        importSingleDetailRecords(details)
        importSingleSessionRecords(sessions)
        rebuildImportedBookTotals(records, details, sessions)
    }

    private suspend fun importSingleDetailRecords(details: List<ReadRecordDetail>) {
        details.forEach { detail ->
            importSingleDetail(detail)
        }
    }

    private suspend fun importSingleSessionRecords(sessions: List<ReadRecordSession>) {
        sessions.forEach { session ->
            importSingleSession(session)
        }
    }

    private suspend fun importSingleRecord(record: ReadRecord) {
        val normalized = normalizeRecord(record).copy(deviceId = getCurrentDeviceId())
        if (!isValidRecord(normalized)) return
        val existing = dao.getReadRecord(
            normalized.deviceId,
            normalized.bookName,
            normalized.bookAuthor
        )
        if (existing == null || existing.readTime < normalized.readTime) {
            dao.insert(normalized)
        }
    }

    private suspend fun importSingleDetail(detail: ReadRecordDetail) {
        val normalized = normalizeDetail(detail).copy(deviceId = getCurrentDeviceId())
        if (!isValidDetail(normalized)) return
        val existing = dao.getDetail(
            normalized.deviceId,
            normalized.bookName,
            normalized.bookAuthor,
            normalized.date
        )
        if (existing == null || existing.readTime < normalized.readTime) {
            dao.insertDetail(normalized)
        }
    }

    private suspend fun importSingleSession(session: ReadRecordSession) {
        val normalized = normalizeSession(session).copy(id = 0, deviceId = getCurrentDeviceId())
        if (!isValidSession(normalized)) return
        val existing = dao.getSessionExact(
            normalized.deviceId,
            normalized.bookName,
            normalized.bookAuthor,
            normalized.startTime,
            normalized.endTime,
            normalized.words
        )
        if (existing == null) {
            dao.insertSession(normalized)
        }
    }

    private suspend fun importRecordsIntoClearedTables(
        records: List<ReadRecord>,
        details: List<ReadRecordDetail>,
        sessions: List<ReadRecordSession>
    ) {
        val normalizedRecords = prepareImportedRecords(records)
        val normalizedDetails = prepareImportedDetails(details)
        val normalizedSessions = prepareImportedSessions(sessions)

        insertDetailsInBatches(normalizedDetails)
        insertSessionsInBatches(normalizedSessions)
        insertRecordsInBatches(
            buildAggregateRecords(normalizedRecords, normalizedDetails, normalizedSessions)
        )
    }

    private fun prepareImportedRecords(records: List<ReadRecord>): List<ReadRecord> {
        val prepared = linkedMapOf<RecordIdentity, ReadRecord>()
        records.asSequence()
            .map { normalizeRecord(it).copy(deviceId = getCurrentDeviceId()) }
            .filter(::isValidRecord)
            .forEach { record ->
                val identity = RecordIdentity(record.deviceId, record.bookName, record.bookAuthor)
                val existing = prepared[identity]
                if (existing == null || existing.readTime < record.readTime) {
                    prepared[identity] = record
                }
            }
        return prepared.values.toList()
    }

    private fun prepareImportedDetails(details: List<ReadRecordDetail>): List<ReadRecordDetail> {
        val prepared = linkedMapOf<DetailIdentity, ReadRecordDetail>()
        details.asSequence()
            .map { normalizeDetail(it).copy(deviceId = getCurrentDeviceId()) }
            .filter(::isValidDetail)
            .forEach { detail ->
                val identity = DetailIdentity(
                    detail.deviceId,
                    detail.bookName,
                    detail.bookAuthor,
                    detail.date
                )
                val existing = prepared[identity]
                if (existing == null || existing.readTime < detail.readTime) {
                    prepared[identity] = detail
                }
            }
        return prepared.values.toList()
    }

    private fun prepareImportedSessions(sessions: List<ReadRecordSession>): List<ReadRecordSession> {
        val prepared = linkedMapOf<SessionIdentity, ReadRecordSession>()
        sessions.asSequence()
            .map { normalizeSession(it).copy(id = 0, deviceId = getCurrentDeviceId()) }
            .filter(::isValidSession)
            .forEach { session ->
                val identity = SessionIdentity(
                    session.deviceId,
                    session.bookName,
                    session.bookAuthor,
                    session.startTime,
                    session.endTime,
                    session.words
                )
                prepared.putIfAbsent(identity, session)
            }
        return prepared.values.toList()
    }

    private suspend fun rebuildImportedBookTotals(
        records: List<ReadRecord>,
        details: List<ReadRecordDetail>,
        sessions: List<ReadRecordSession>
    ) {
        val importedBooks = linkedSetOf<Pair<String, String>>()
        records.forEach { importedBooks.add(it.bookName to it.bookAuthor) }
        details.forEach { importedBooks.add(it.bookName to it.bookAuthor) }
        sessions.forEach { importedBooks.add(it.bookName to it.bookAuthor) }
        importedBooks.forEach { (bookName, bookAuthor) ->
            val current = dao.getReadRecord(getCurrentDeviceId(), bookName, bookAuthor)
            updateReadRecordTotal(
                getCurrentDeviceId(),
                bookName,
                bookAuthor,
                minimumReadTime = current?.readTime ?: 0L,
                minimumLastRead = current?.lastRead ?: 0L
            )
        }
    }

    private fun buildAggregateRecords(
        records: List<ReadRecord>,
        details: List<ReadRecordDetail>,
        sessions: List<ReadRecordSession>
    ): List<ReadRecord> {
        val historyAggregates = linkedMapOf<RecordIdentity, ReadRecordAggregate>()
        details.forEach { detail ->
            val identity = RecordIdentity(detail.deviceId, detail.bookName, detail.bookAuthor)
            val aggregate = historyAggregates.getOrPut(identity) { ReadRecordAggregate() }
            aggregate.detailReadTime += detail.readTime.coerceAtLeast(0L)
            aggregate.lastRead = max(aggregate.lastRead, detail.lastReadTime)
        }
        sessions.forEach { session ->
            val identity = RecordIdentity(session.deviceId, session.bookName, session.bookAuthor)
            val aggregate = historyAggregates.getOrPut(identity) { ReadRecordAggregate() }
            aggregate.sessionReadTime += (session.endTime - session.startTime).coerceAtLeast(0L)
            aggregate.lastRead = max(aggregate.lastRead, session.endTime)
        }

        val recordsByIdentity = records.associateBy {
            RecordIdentity(it.deviceId, it.bookName, it.bookAuthor)
        }
        val identities = linkedSetOf<RecordIdentity>()
        identities.addAll(recordsByIdentity.keys)
        identities.addAll(historyAggregates.keys)

        return identities.map { identity ->
            val record = recordsByIdentity[identity]
            val aggregate = historyAggregates[identity]
            val readTime = max(record?.readTime ?: 0L, aggregate?.readTime ?: 0L)
            val lastRead = max(record?.lastRead ?: 0L, aggregate?.lastRead ?: 0L)
            (record ?: ReadRecord(
                deviceId = identity.deviceId,
                bookName = identity.bookName,
                bookAuthor = identity.bookAuthor
            )).copy(
                readTime = readTime,
                lastRead = lastRead
            )
        }
    }

    private suspend fun insertRecordsInBatches(records: List<ReadRecord>) {
        records.chunked(IMPORT_BATCH_SIZE).forEach { batch ->
            dao.insert(*batch.toTypedArray())
        }
    }

    private suspend fun insertDetailsInBatches(details: List<ReadRecordDetail>) {
        details.chunked(IMPORT_BATCH_SIZE).forEach { batch ->
            dao.insertDetails(batch)
        }
    }

    private suspend fun insertSessionsInBatches(sessions: List<ReadRecordSession>) {
        sessions.chunked(IMPORT_BATCH_SIZE).forEach { batch ->
            dao.insertSessions(batch)
        }
    }
}
