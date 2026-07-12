package io.legado.app.data.repository

import io.legado.app.data.dao.BookReadTime
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReadRecordRepositoryTest {

    @Test
    fun deleteMergedTimelineSessionsRemovesTheWholeGroup() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        val start = LocalDateTime.of(2026, 7, 12, 10, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val first = ReadRecordSession(1L, CURRENT_DEVICE_ID, "Book", "Author", start, start + 1_000L, 10L)
        val second = ReadRecordSession(2L, CURRENT_DEVICE_ID, "Book", "Author", start + 2_000L, start + 3_000L, 20L)
        val remaining = ReadRecordSession(3L, CURRENT_DEVICE_ID, "Book", "Author", start + 30_000L, start + 31_000L, 30L)
        dao.insert(ReadRecord(CURRENT_DEVICE_ID, "Book", "Author", 3_000L, start + 31_000L))
        dao.insertDetail(
            ReadRecordDetail(
                CURRENT_DEVICE_ID,
                "Book",
                "Author",
                "2026-07-12",
                3_000L,
                60L,
                start,
                start + 31_000L
            )
        )
        dao.insertSessions(listOf(first, second, remaining))

        repository.deleteSessions(first, listOf(first.id, second.id))

        assertEquals(1, dao.getSessionsCount())
        val detail = dao.getDetail(CURRENT_DEVICE_ID, "Book", "Author", "2026-07-12")
        assertEquals(1_000L, detail?.readTime)
        assertEquals(30L, detail?.readWords)
    }

    @Test
    fun importAggregateOnlyBackupKeepsReadTime() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }

        repository.importRecords(
            records = listOf(
                ReadRecord(
                    deviceId = "remote",
                    bookName = "Test Book",
                    bookAuthor = "Author",
                    readTime = 3_600_000L,
                    lastRead = 200L
                )
            )
        )

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Test Book", "Author")
        assertNotNull(record)
        assertEquals(3_600_000L, record?.readTime)
        assertEquals(200L, record?.lastRead)
    }

    @Test
    fun importDetailAndSessionRebuildsAggregateWhenMissing() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }

        repository.importRecords(
            records = emptyList(),
            details = listOf(
                ReadRecordDetail(
                    deviceId = "remote",
                    bookName = "Detail Book",
                    bookAuthor = "Author",
                    date = "2026-05-02",
                    readTime = 90_000L,
                    lastReadTime = 150L
                )
            ),
            sessions = listOf(
                ReadRecordSession(
                    deviceId = "remote",
                    bookName = "Detail Book",
                    bookAuthor = "Author",
                    startTime = 100L,
                    endTime = 150L,
                    words = 0L
                )
            )
        )

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Detail Book", "Author")
        assertNotNull(record)
        assertEquals(90_000L, record?.readTime)
        assertEquals(150L, record?.lastRead)
    }

    @Test
    fun importFreshRestoreDeduplicatesAndKeepsTimelineAsAlternativeHistory() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }

        repository.importRecords(
            records = listOf(
                ReadRecord("remote-a", " Bulk Book ", " Author ", 100L, 200L),
                ReadRecord("remote-b", "Bulk Book", "Author", 200L, 250L)
            ),
            details = listOf(
                ReadRecordDetail("remote-a", " Bulk Book ", " Author ", "2026-05-02", 120L, 0L, 100L, 300L),
                ReadRecordDetail("remote-b", "Bulk Book", "Author", "2026-05-02", 250L, 0L, 100L, 500L)
            ),
            sessions = listOf(
                ReadRecordSession(7L, "remote-a", " Bulk Book ", " Author ", 100L, 400L, 10L),
                ReadRecordSession(8L, "remote-b", "Bulk Book", "Author", 100L, 400L, 10L)
            ),
            databaseCleared = true
        )

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Bulk Book", "Author")
        assertNotNull(record)
        assertEquals(300L, record?.readTime)
        assertEquals(500L, record?.lastRead)
        assertEquals(1, dao.getDetailsCount())
        assertEquals(1, dao.getSessionsCount())
    }

    @Test
    fun mergeReadRecordIntoAccumulatesReadTime() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        val target = ReadRecord(CURRENT_DEVICE_ID, "Merge Book", "Author", 360_000_000L, 1_000L)
        val source = ReadRecord("remote", "Merge Book", "Author", 60_000L, 1_100L)
        dao.insert(target)
        dao.insert(source)

        repository.mergeReadRecordInto(target, listOf(source))

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Merge Book", "Author")
        assertNotNull(record)
        assertEquals(360_060_000L, record?.readTime)
        assertEquals(1_100L, record?.lastRead)
    }

    @Test
    fun repairRecordsMergesEmptyAuthorAndDuplicateDeviceRecords() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        dao.insert(
            ReadRecord(deviceId = "remote", bookName = "Repair Book", bookAuthor = "", readTime = 60_000L, lastRead = 100L),
            ReadRecord(deviceId = CURRENT_DEVICE_ID, bookName = "Repair Book", bookAuthor = "Author", readTime = 120_000L, lastRead = 200L)
        )

        repository.repairRecords { "Author" }

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Repair Book", "Author")
        assertNotNull(record)
        assertEquals(180_000L, record?.readTime)
        assertEquals(200L, record?.lastRead)
        assertEquals(1, dao.all.size)
    }

    @Test
    fun saveReadSessionSplitsCrossDaySessionIntoDailyRecords() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        val zoneId = ZoneId.systemDefault()
        val start = LocalDateTime.of(2026, 5, 2, 23, 50)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val end = LocalDateTime.of(2026, 5, 3, 0, 10)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        repository.saveReadSession(
            ReadRecordSession(
                deviceId = CURRENT_DEVICE_ID,
                bookName = "Night Book",
                bookAuthor = "Author",
                startTime = start,
                endTime = end,
                words = 0L
            )
        )

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Night Book", "Author")
        assertNotNull(record)
        assertEquals(1_200_000L, record?.readTime)
        assertEquals(end, record?.lastRead)

        val details = dao.getDetailsByBook(CURRENT_DEVICE_ID, "Night Book", "Author")
            .sortedBy { it.date }
        assertEquals(2, details.size)
        assertEquals("2026-05-02", details[0].date)
        assertEquals(600_000L, details[0].readTime)
        assertEquals("2026-05-03", details[1].date)
        assertEquals(600_000L, details[1].readTime)

        val sessions = dao.getSessionsByBook(CURRENT_DEVICE_ID, "Night Book", "Author")
            .sortedBy { it.startTime }
        assertEquals(2, sessions.size)
        assertEquals(start, sessions[0].startTime)
        assertEquals(600_000L, sessions[0].endTime - sessions[0].startTime)
        assertEquals(600_000L, sessions[1].endTime - sessions[1].startTime)
        assertEquals(end, sessions[1].endTime)
    }

    @Test
    fun saveReadSessionIgnoresBlankBookName() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }

        repository.saveReadSession(
            ReadRecordSession(
                deviceId = CURRENT_DEVICE_ID,
                bookName = "   ",
                bookAuthor = "",
                startTime = 100L,
                endTime = 200L,
                words = 0L
            )
        )

        assertEquals(emptyList<ReadRecord>(), dao.all)
        assertEquals(0, dao.getDetailsCount())
        assertEquals(0, dao.getSessionsCount())
    }

    @Test
    fun deleteReadRecordByDateRemovesOnlySelectedDayHistory() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        val zoneId = ZoneId.systemDefault()
        val dayOneStart = LocalDateTime.of(2026, 5, 2, 10, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val dayOneEnd = LocalDateTime.of(2026, 5, 2, 10, 1)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val dayTwoStart = LocalDateTime.of(2026, 5, 3, 10, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val dayTwoEnd = LocalDateTime.of(2026, 5, 3, 10, 2)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val record = ReadRecord(CURRENT_DEVICE_ID, "Delete Book", "Author", 180_000L, dayTwoEnd)
        dao.insert(record)
        dao.insertDetail(
            ReadRecordDetail(CURRENT_DEVICE_ID, "Delete Book", "Author", "2026-05-02", 60_000L, 0L, dayOneStart, dayOneEnd)
        )
        dao.insertDetail(
            ReadRecordDetail(CURRENT_DEVICE_ID, "Delete Book", "Author", "2026-05-03", 120_000L, 0L, dayTwoStart, dayTwoEnd)
        )
        dao.insertSession(ReadRecordSession(deviceId = CURRENT_DEVICE_ID, bookName = "Delete Book", bookAuthor = "Author", startTime = dayOneStart, endTime = dayOneEnd, words = 0L))
        dao.insertSession(ReadRecordSession(deviceId = CURRENT_DEVICE_ID, bookName = "Delete Book", bookAuthor = "Author", startTime = dayTwoStart, endTime = dayTwoEnd, words = 0L))

        repository.deleteReadRecordByDate(record, "2026-05-02")

        val remainingRecord = dao.getReadRecord(CURRENT_DEVICE_ID, "Delete Book", "Author")
        assertNotNull(remainingRecord)
        assertEquals(120_000L, remainingRecord?.readTime)
        assertEquals(dayTwoEnd, remainingRecord?.lastRead)
        assertEquals(listOf("2026-05-03"), dao.getDetailsByBook(CURRENT_DEVICE_ID, "Delete Book", "Author").map { it.date })
        assertEquals(1, dao.getSessionsByBook(CURRENT_DEVICE_ID, "Delete Book", "Author").size)
    }

    @Test
    fun getTotalReadTimePrefersDetailForBooksWithHistory() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        dao.insert(
            ReadRecord(CURRENT_DEVICE_ID, "Dirty Book", "Author", 3_600_000L, 200L),
            ReadRecord(CURRENT_DEVICE_ID, "Legacy Book", "Author", 300_000L, 150L)
        )
        dao.insertDetail(
            ReadRecordDetail(CURRENT_DEVICE_ID, "Dirty Book", "Author", "2026-05-03", 120_000L, 0L, 100L, 200L)
        )

        val totalReadTime = repository.getTotalReadTime().first()

        assertEquals(3_900_000L, totalReadTime)
    }

    @Test
    fun repairRecordsPreservesExistingAggregateWhenHistoryIsPartial() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        dao.insert(ReadRecord(CURRENT_DEVICE_ID, "Rebuild Book", "Author", 3_600_000L, 500L))
        dao.insertDetail(
            ReadRecordDetail(CURRENT_DEVICE_ID, "Rebuild Book", "Author", "2026-05-03", 120_000L, 0L, 100L, 200L)
        )
        dao.insertSession(
            ReadRecordSession(deviceId = CURRENT_DEVICE_ID, bookName = "Rebuild Book", bookAuthor = "Author", startTime = 100L, endTime = 200L, words = 0L)
        )

        repository.repairRecords { "Author" }

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Rebuild Book", "Author")
        assertNotNull(record)
        assertEquals(3_600_000L, record?.readTime)
        assertEquals(500L, record?.lastRead)
    }

    @Test
    fun repairRecordsRemovesBlankBookNameHistory() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        dao.insert(ReadRecord(CURRENT_DEVICE_ID, "", "", 86_400_000L, 300L))
        dao.insertDetail(ReadRecordDetail(CURRENT_DEVICE_ID, "", "", "2026-05-03", 86_400_000L, 0L, 100L, 300L))
        dao.insertSession(ReadRecordSession(deviceId = CURRENT_DEVICE_ID, bookName = "", bookAuthor = "", startTime = 100L, endTime = 300L, words = 0L))

        repository.repairRecords { null }

        assertEquals(emptyList<ReadRecord>(), dao.all)
        assertEquals(0, dao.getDetailsCount())
        assertEquals(0, dao.getSessionsCount())
    }

    @Test
    fun applyDetailReadTimesKeepsLargerAggregateAndFillsMissingDetailTime() {
        val repository = ReadRecordRepository(FakeReadRecordDao()) { CURRENT_DEVICE_ID }
        val records = listOf(
            ReadRecord(CURRENT_DEVICE_ID, "Book A", "Author", 3_600_000L, 500L),
            ReadRecord(CURRENT_DEVICE_ID, "Book B", "Author", 0L, 200L)
        )
        val details = listOf(
            ReadRecordDetail(CURRENT_DEVICE_ID, "Book A", "Author", "2026-05-03", 120_000L, 0L, 100L, 200L),
            ReadRecordDetail(CURRENT_DEVICE_ID, "Book B", "Author", "2026-05-03", 180_000L, 0L, 100L, 200L)
        )

        val normalized = repository.applyDetailReadTimes(records, details)

        assertEquals(3_600_000L, normalized.first { it.bookName == "Book A" }.readTime)
        assertEquals(180_000L, normalized.first { it.bookName == "Book B" }.readTime)
    }

    private class FakeReadRecordDao : ReadRecordDao {
        private val records = mutableListOf<ReadRecord>()
        private val details = mutableListOf<ReadRecordDetail>()
        private val sessions = mutableListOf<ReadRecordSession>()
        private var nextSessionId = 1L

        override suspend fun insert(vararg readRecord: ReadRecord) {
            readRecord.forEach { record ->
                records.removeAll {
                    it.deviceId == record.deviceId &&
                        it.bookName == record.bookName &&
                        it.bookAuthor == record.bookAuthor
                }
                records.add(record.copy())
            }
        }

        override suspend fun insertDetail(detail: ReadRecordDetail) {
            details.removeAll {
                it.deviceId == detail.deviceId &&
                    it.bookName == detail.bookName &&
                    it.bookAuthor == detail.bookAuthor &&
                    it.date == detail.date
            }
            details.add(detail.copy())
        }

        override suspend fun insertDetails(details: List<ReadRecordDetail>) {
            details.forEach { insertDetail(it) }
        }

        override suspend fun insertSession(session: ReadRecordSession) {
            val stored = if (session.id == 0L) {
                session.copy(id = nextSessionId++)
            } else {
                session
            }
            sessions.removeAll { it.id == stored.id }
            sessions.add(stored)
        }

        override suspend fun insertSessions(sessions: List<ReadRecordSession>) {
            sessions.forEach { insertSession(it) }
        }

        override suspend fun update(vararg record: ReadRecord) {
            insert(*record)
        }

        override suspend fun updateSession(session: ReadRecordSession) {
            sessions.removeAll { it.id == session.id }
            sessions.add(session.copy())
        }

        override suspend fun delete(vararg record: ReadRecord) {
            record.forEach { target ->
                records.removeAll {
                    it.deviceId == target.deviceId &&
                        it.bookName == target.bookName &&
                        it.bookAuthor == target.bookAuthor
                }
            }
        }

        override suspend fun deleteDetail(detail: ReadRecordDetail) {
            details.removeAll {
                it.deviceId == detail.deviceId &&
                    it.bookName == detail.bookName &&
                    it.bookAuthor == detail.bookAuthor &&
                    it.date == detail.date
            }
        }

        override suspend fun deleteSession(session: ReadRecordSession) {
            sessions.removeAll { it.id == session.id }
        }

        override suspend fun deleteSessionsByIds(sessionIds: List<Long>) {
            sessions.removeAll { it.id in sessionIds }
        }

        override suspend fun clear() {
            records.clear()
        }

        override suspend fun clearDetails() {
            details.clear()
        }

        override suspend fun clearSessions() {
            sessions.clear()
        }

        override suspend fun deleteByNameAndAuthor(bookName: String, bookAuthor: String) {
            records.removeAll { it.bookName == bookName && it.bookAuthor == bookAuthor }
        }

        override suspend fun getReadRecord(deviceId: String, bookName: String, bookAuthor: String): ReadRecord? {
            return records.firstOrNull {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }?.copy()
        }

        override suspend fun getReadRecordsByName(bookName: String): List<ReadRecord> {
            return records.filter { it.bookName == bookName }.map { it.copy() }
        }

        override suspend fun getReadRecordsByNameExcludingTarget(
            bookName: String,
            excludeDeviceId: String,
            excludeAuthor: String
        ): List<ReadRecord> {
            return records.filter {
                it.bookName == bookName && !(it.deviceId == excludeDeviceId && it.bookAuthor == excludeAuthor)
            }.map { it.copy() }
        }

        override fun getTotalReadTime(): Flow<Long?> {
            return flowOf(records.sumOf { it.readTime })
        }

        override fun getNormalizedTotalReadTime(): Flow<Long> {
            val detailTotals = details
                .groupBy { Triple(it.deviceId, it.bookName, it.bookAuthor) }
                .mapValues { (_, items) -> items.sumOf { it.readTime } }
            return flowOf(
                records.sumOf { record ->
                    maxOf(
                        record.readTime,
                        detailTotals[Triple(record.deviceId, record.bookName, record.bookAuthor)] ?: 0L
                    )
                }
            )
        }

        override fun getReadTimeFlow(deviceId: String, bookName: String, bookAuthor: String): Flow<Long?> {
            return flowOf(
                records.firstOrNull {
                    it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
                }?.readTime
            )
        }

        override suspend fun getReadTime(deviceId: String, bookName: String, bookAuthor: String): Long? {
            return getReadRecord(deviceId, bookName, bookAuthor)?.readTime
        }

        override fun getAllReadRecordsSortedByLastRead(): Flow<List<ReadRecord>> {
            return flowOf(records.sortedByDescending { it.lastRead }.map { it.copy() })
        }

        override val all: List<ReadRecord>
            get() = records.map { it.copy() }

        override suspend fun getAllReadRecords(limit: Int, offset: Int): List<ReadRecord> {
            return records
                .sortedWith(compareBy<ReadRecord> { it.deviceId }.thenBy { it.bookName }.thenBy { it.bookAuthor })
                .drop(offset)
                .take(limit)
                .map { it.copy() }
        }

        override fun getReadRecordCount(): Int = records.size

        override fun searchReadRecordsByLastRead(query: String): Flow<List<ReadRecord>> {
            return flowOf(
                records.filter { it.bookName.contains(query) || it.bookAuthor.contains(query) }
                    .sortedByDescending { it.lastRead }
                    .map { it.copy() }
            )
        }

        override suspend fun getDetail(
            deviceId: String,
            bookName: String,
            bookAuthor: String,
            date: String
        ): ReadRecordDetail? {
            return details.firstOrNull {
                it.deviceId == deviceId &&
                    it.bookName == bookName &&
                    it.bookAuthor == bookAuthor &&
                    it.date == date
            }?.copy()
        }

        override suspend fun getDetailsByBook(deviceId: String, bookName: String, bookAuthor: String): List<ReadRecordDetail> {
            return details.filter {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }.map { it.copy() }
        }

        override fun getAllDetails(): Flow<List<ReadRecordDetail>> {
            return flowOf(details.sortedByDescending { it.date }.map { it.copy() })
        }

        override suspend fun getAllDetailsList(): List<ReadRecordDetail> {
            return details.map { it.copy() }
        }

        override suspend fun getAllDetailsList(limit: Int, offset: Int): List<ReadRecordDetail> {
            return details
                .sortedWith(
                    compareBy<ReadRecordDetail> { it.deviceId }
                        .thenBy { it.bookName }
                        .thenBy { it.bookAuthor }
                        .thenBy { it.date }
                )
                .drop(offset)
                .take(limit)
                .map { it.copy() }
        }

        override fun getDetailsCount(): Int {
            return details.size
        }

        override fun searchDetails(query: String): Flow<List<ReadRecordDetail>> {
            return flowOf(
                details.filter { it.bookName.contains(query) || it.bookAuthor.contains(query) }
                    .sortedByDescending { it.date }
                    .map { it.copy() }
            )
        }

        override suspend fun deleteDetailsByBook(deviceId: String, bookName: String, bookAuthor: String) {
            details.removeAll {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }
        }

        override fun getAllSessions(): Flow<List<ReadRecordSession>> {
            return flowOf(sessions.sortedByDescending { it.startTime }.map { it.copy() })
        }

        override fun getTimelineSessions(
            query: String,
            date: String?
        ): Flow<List<ReadRecordSession>> {
            return flowOf(filteredTimelineSessions(query, date).map { it.copy() })
        }

        override suspend fun getAllSessionsList(): List<ReadRecordSession> {
            return sessions.map { it.copy() }
        }

        override suspend fun getAllSessionsList(limit: Int, offset: Int): List<ReadRecordSession> {
            return sessions.sortedBy { it.id }.drop(offset).take(limit).map { it.copy() }
        }

        override fun getSessionsCount(): Int {
            return sessions.size
        }

        private fun filteredTimelineSessions(
            query: String,
            date: String?
        ): List<ReadRecordSession> {
            return sessions.asSequence()
                .filter {
                    query.isEmpty() ||
                        it.bookName.contains(query) ||
                        it.bookAuthor.contains(query)
                }
                .filter {
                    date == null || java.time.Instant.ofEpochMilli(it.startTime)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString() == date
                }
                .sortedByDescending { it.startTime }
                .toList()
        }

        override fun getSessionsByBookFlow(deviceId: String, bookName: String, bookAuthor: String): Flow<List<ReadRecordSession>> {
            return flowOf(
                sessions.filter {
                    it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
                }.map { it.copy() }
            )
        }

        override suspend fun getSessionsByBook(deviceId: String, bookName: String, bookAuthor: String): List<ReadRecordSession> {
            return sessions.filter {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }.map { it.copy() }
        }

        override suspend fun getSessionsByBookAndDate(
            deviceId: String,
            bookName: String,
            bookAuthor: String,
            date: String
        ): List<ReadRecordSession> {
            return getSessionsByBook(deviceId, bookName, bookAuthor).filter {
                java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.startTime)) == date
            }
        }

        override suspend fun getSessionExact(
            deviceId: String,
            bookName: String,
            bookAuthor: String,
            startTime: Long,
            endTime: Long,
            words: Long
        ): ReadRecordSession? {
            return sessions.firstOrNull {
                it.deviceId == deviceId &&
                    it.bookName == bookName &&
                    it.bookAuthor == bookAuthor &&
                    it.startTime == startTime &&
                    it.endTime == endTime &&
                    it.words == words
            }?.copy()
        }

        override suspend fun deleteSessionsByBook(deviceId: String, bookName: String, bookAuthor: String) {
            sessions.removeAll {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }
        }

        override suspend fun deleteSessionsByBookAndDate(
            deviceId: String,
            bookName: String,
            bookAuthor: String,
            date: String
        ) {
            sessions.removeAll {
                it.deviceId == deviceId &&
                    it.bookName == bookName &&
                    it.bookAuthor == bookAuthor &&
                    java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.startTime)) == date
            }
        }

        override suspend fun getRecordsWithEmptyAuthor(): List<ReadRecord> {
            return records.filter { it.bookAuthor.isEmpty() }.map { it.copy() }
        }

        override suspend fun deleteRecordsWithBlankBookName() {
            records.removeAll { it.bookName.isBlank() }
        }

        override suspend fun deleteDetailsWithBlankBookName() {
            details.removeAll { it.bookName.isBlank() }
        }

        override suspend fun deleteSessionsWithBlankBookName() {
            sessions.removeAll { it.bookName.isBlank() }
        }

        override suspend fun updateAuthorByBookName(deviceId: String, bookName: String, author: String) {
            records.indices.forEach { index ->
                val record = records[index]
                if (record.deviceId == deviceId && record.bookName == bookName && record.bookAuthor.isEmpty()) {
                    records[index] = record.copy(bookAuthor = author)
                }
            }
        }

        override suspend fun getBookReadTimes(): List<BookReadTime> {
            return details.groupBy { it.bookName to it.bookAuthor }
                .map { (identity, grouped) ->
                    BookReadTime(identity.first, identity.second, grouped.sumOf { it.readTime })
                }
        }

        override suspend fun deleteReadRecord(record: ReadRecord) {
            delete(record)
        }
    }

    private companion object {
        const val CURRENT_DEVICE_ID = "current-device"
    }
}
