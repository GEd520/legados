package io.legado.app.ui.book.readRecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.AppConst
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import splitties.init.appCtx

private typealias RecordIdentity = Triple<String, String, String>

data class ReadRecordUiState(
    val isLoading: Boolean = true,
    val totalReadTime: Long = 0,
    val todayReadTime: Long = 0,
    val todayBookCount: Int = 0,
    val groupedRecords: Map<String, List<ReadRecordDetail>> = emptyMap(),
    val timelineRecords: Map<String, List<ReadRecordSession>> = emptyMap(),
    val latestRecords: List<ReadRecord> = emptyList(),
    val readTimeRecords: List<ReadRecord> = emptyList(),
    val selectedDate: LocalDate? = null,
    val searchKey: String? = null,
    val dailyReadCounts: Map<LocalDate, Int> = emptyMap(),
    val dailyReadTimes: Map<LocalDate, Long> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedRecords: Set<RecordIdentity> = emptySet()
)

enum class DisplayMode {
    AGGREGATE,
    TIMELINE,
    LATEST,
    READ_TIME
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReadRecordViewModel : ViewModel() {

    private val repository = ReadRecordRepository(appDb.readRecordDao)
    private val bookRepository = BookRepository()

    private val coverPathCache = ConcurrentHashMap<String, String?>()
    private val chapterTitleCache = ConcurrentHashMap<String, String?>()

    private val _displayMode = MutableStateFlow(loadDisplayMode())
    val displayMode = _displayMode.asStateFlow()
    private val _searchKey = MutableStateFlow("")
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedRecords = MutableStateFlow<Set<RecordIdentity>>(emptySet())

    init {
        viewModelScope.launch {
            if (appCtx.getPrefInt(PreferKey.readRecordRepairVersion) < ReadRecordRepository.CURRENT_REPAIR_VERSION) {
                repository.repairRecords { bookName ->
                    bookRepository.getAuthorByBookName(bookName)
                }
                appCtx.putPrefInt(
                    PreferKey.readRecordRepairVersion,
                    ReadRecordRepository.CURRENT_REPAIR_VERSION
                )
            }
        }
    }

    private fun loadDisplayMode(): DisplayMode {
        val savedOrdinal = appCtx.getPrefInt(PreferKey.readRecordDisplayMode, DisplayMode.AGGREGATE.ordinal)
        return enumValueOf<DisplayMode>(DisplayMode.values().getOrNull(savedOrdinal)?.name ?: DisplayMode.AGGREGATE.name)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loadedDataFlow = _searchKey
        .flatMapLatest { query ->
            combine(
                repository.getAllRecordDetails(query),
                repository.getLatestReadRecords(query),
                repository.getAllSessions(),
                repository.getTotalReadTime()
            ) { details, latest, sessions, totalTime ->
                LoadedData(totalTime, details, latest, sessions)
            }
        }

    val uiState: StateFlow<ReadRecordUiState> = combine(
        loadedDataFlow,
        _selectedDate,
        _searchKey,
        _isSelectionMode,
        _selectedRecords
    ) { data, selectedDate, searchKey, isSelectionMode, selectedRecords ->
        val today = LocalDate.now()
        val dateStr = selectedDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val searchedSessions = data.sessions.filter { session ->
            searchKey.isEmpty() ||
                session.bookName.contains(searchKey, ignoreCase = true) ||
                session.bookAuthor.contains(searchKey, ignoreCase = true)
        }

        val mergedDailySessions = searchedSessions
            .groupBy { it.startTime.toLocalDateString() }
            .mapValues { (_, sessions) -> mergeContinuousSessions(sessions) }

        val detailsWithDate = data.details.mapNotNull { detail ->
            parseDetailDate(detail.date)?.let { it to detail }
        }

        val dailyCounts = detailsWithDate
            .groupBy { it.first }
            .mapValues { it.value.size }

        val dailyTimes = detailsWithDate
            .groupBy { it.first }
            .mapValues { (_, details) -> details.sumOf { it.second.readTime } }

        val todayReadTime = dailyTimes[today] ?: 0L
        val todayBookCount = data.details
            .asSequence()
            .filter { it.date == today.format(DateTimeFormatter.ISO_LOCAL_DATE) }
            .map { recordIdentity(it.deviceId, it.bookName, it.bookAuthor) }
            .distinct()
            .count()

        val filteredDetails = data.details.filter { detail ->
            dateStr == null || detail.date == dateStr
        }

        val timelineMap = searchedSessions
            .asSequence()
            .filter { session ->
                val sDate = session.startTime.toLocalDateString()
                dateStr == null || sDate == dateStr
            }
            .groupBy { it.startTime.toLocalDateString() }
            .mapValues { (_, sessions) ->
                mergeContinuousSessions(sessions).reversed()
            }
            .toSortedMap(compareByDescending { it })

        val normalizedLatestRecords = repository.applyDetailReadTimes(data.latestRecords, data.details)

        val latestRecords = if (dateStr == null) {
            normalizedLatestRecords
        } else {
            val filteredDetailsByRecord = filteredDetails.groupBy {
                recordIdentity(it.deviceId, it.bookName, it.bookAuthor)
            }
            val latestRecordIndex = normalizedLatestRecords.associateBy {
                recordIdentity(it.deviceId, it.bookName, it.bookAuthor)
            }

            filteredDetailsByRecord.map { (identity, details) ->
                val baseRecord = latestRecordIndex[identity]
                val dayReadTime = details.sumOf { it.readTime }
                val dayLastRead = details.maxOf { it.lastReadTime }
                if (baseRecord != null) {
                    baseRecord.copy(
                        readTime = dayReadTime,
                        lastRead = dayLastRead
                    )
                } else {
                    ReadRecord(
                        deviceId = identity.first,
                        bookName = identity.second,
                        bookAuthor = identity.third,
                        readTime = dayReadTime,
                        lastRead = dayLastRead
                    )
                }
            }.sortedByDescending { it.lastRead }
        }

        val readTimeRecords = if (dateStr == null) {
            latestRecords.sortedByDescending { it.readTime }
        } else {
            val filteredDetailReadTimes = filteredDetails
                .groupBy { recordIdentity(it.deviceId, it.bookName, it.bookAuthor) }
                .mapValues { (_, details) -> details.sumOf { it.readTime } }

            latestRecords.mapNotNull { record ->
                val dateReadTime = filteredDetailReadTimes[
                    recordIdentity(record.deviceId, record.bookName, record.bookAuthor)
                ]
                    ?: return@mapNotNull null
                record.copy(readTime = dateReadTime)
            }.sortedByDescending { it.readTime }
        }

        ReadRecordUiState(
            isLoading = false,
            totalReadTime = data.totalReadTime,
            todayReadTime = todayReadTime,
            todayBookCount = todayBookCount,
            groupedRecords = filteredDetails.groupBy { it.date },
            timelineRecords = timelineMap,
            latestRecords = latestRecords,
            readTimeRecords = readTimeRecords,
            selectedDate = selectedDate,
            searchKey = searchKey,
            dailyReadCounts = dailyCounts,
            dailyReadTimes = dailyTimes,
            isSelectionMode = isSelectionMode,
            selectedRecords = selectedRecords
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReadRecordUiState(isLoading = true)
    )

    fun setSearchKey(query: String) {
        _searchKey.value = query
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        appCtx.putPrefInt(PreferKey.readRecordDisplayMode, mode.ordinal)
    }

    fun setSelectedDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    fun deleteDetail(detail: ReadRecordDetail) {
        viewModelScope.launch { repository.deleteDetail(detail) }
    }

    fun deleteSession(session: ReadRecordSession) {
        viewModelScope.launch { repository.deleteSession(session) }
    }

    fun deleteReadRecord(record: ReadRecord) {
        viewModelScope.launch {
            val selectedDate = _selectedDate.value?.format(DateTimeFormatter.ISO_LOCAL_DATE)
            if (selectedDate == null) {
                repository.deleteReadRecord(record)
            } else {
                repository.deleteReadRecordByDate(record, selectedDate)
            }
        }
    }

    private fun mergeContinuousSessions(sessions: List<ReadRecordSession>): List<ReadRecordSession> {
        if (sessions.isEmpty()) return emptyList()
        val sortedSessions = sessions.sortedBy { it.startTime }
        val mergedList = mutableListOf<ReadRecordSession>()
        mergedList.add(sortedSessions.first().copy())

        val gapLimit = 20 * 60 * 1000L

        for (i in 1 until sortedSessions.size) {
            val current = sortedSessions[i]
            val last = mergedList.last()
            if (current.bookName == last.bookName &&
                current.bookAuthor == last.bookAuthor &&
                (current.startTime - last.endTime) <= gapLimit
            ) {
                mergedList[mergedList.lastIndex] = last.copy(endTime = maxOf(last.endTime, current.endTime))
            } else {
                mergedList.add(current.copy())
            }
        }
        return mergedList
    }

    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndexLong: Long): String? {
        return bookRepository.getChapterTitle(bookName, bookAuthor, chapterIndexLong.toInt())
    }

    suspend fun getBookDurChapterTitle(bookName: String, bookAuthor: String): String? {
        val key = cacheKey(bookName, bookAuthor)
        chapterTitleCache[key]?.let { return it }
        val result = bookRepository.getBookDurChapterTitle(bookName, bookAuthor)
        result?.let { chapterTitleCache[key] = it }
        return result
    }

    suspend fun getBookCover(bookName: String, bookAuthor: String): String? {
        val key = cacheKey(bookName, bookAuthor)
        coverPathCache[key]?.let { return it }
        val result = bookRepository.getBookCoverByNameAndAuthor(bookName, bookAuthor)
        result?.let { coverPathCache[key] = it }
        return result
    }

    fun getConfiguredDefaultCover(): String? {
        return bookRepository.getConfiguredDefaultCover()
    }

    suspend fun getMergeCandidates(targetRecord: ReadRecord): List<ReadRecord> {
        return repository.getMergeCandidates(targetRecord)
    }

    fun mergeReadRecords(targetRecord: ReadRecord, sourceRecords: List<ReadRecord>) {
        if (sourceRecords.isEmpty()) return
        viewModelScope.launch {
            repository.mergeReadRecordInto(targetRecord, sourceRecords)
        }
    }

    fun enterSelectionMode(record: ReadRecord) {
        _isSelectionMode.value = true
        _selectedRecords.value = setOf(recordIdentity(record.deviceId, record.bookName, record.bookAuthor))
    }

    fun enterSelectionMode(detail: ReadRecordDetail) {
        _isSelectionMode.value = true
        _selectedRecords.value = setOf(recordIdentity(detail.deviceId, detail.bookName, detail.bookAuthor))
    }

    fun enterSelectionMode(session: ReadRecordSession) {
        _isSelectionMode.value = true
        _selectedRecords.value = setOf(recordIdentity(session.deviceId, session.bookName, session.bookAuthor))
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedRecords.value = emptySet()
    }

    fun toggleRecordSelection(record: ReadRecord) {
        val identity = recordIdentity(record.deviceId, record.bookName, record.bookAuthor)
        toggleIdentitySelection(identity)
    }

    fun toggleRecordSelection(detail: ReadRecordDetail) {
        val identity = recordIdentity(detail.deviceId, detail.bookName, detail.bookAuthor)
        toggleIdentitySelection(identity)
    }

    fun toggleRecordSelection(session: ReadRecordSession) {
        val identity = recordIdentity(session.deviceId, session.bookName, session.bookAuthor)
        toggleIdentitySelection(identity)
    }

    private fun toggleIdentitySelection(identity: RecordIdentity) {
        val currentSelection = _selectedRecords.value.toMutableSet()
        if (currentSelection.contains(identity)) {
            currentSelection.remove(identity)
            if (currentSelection.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            currentSelection.add(identity)
        }
        _selectedRecords.value = currentSelection
    }

    fun selectAllRecords(displayMode: DisplayMode) {
        val allIdentities = _selectedRecords.value.toMutableSet()
        when (displayMode) {
            DisplayMode.LATEST -> {
                allIdentities.addAll(uiState.value.latestRecords.map { 
                    recordIdentity(it.deviceId, it.bookName, it.bookAuthor) 
                })
            }
            DisplayMode.READ_TIME -> {
                allIdentities.addAll(uiState.value.readTimeRecords.map { 
                    recordIdentity(it.deviceId, it.bookName, it.bookAuthor) 
                })
            }
            DisplayMode.AGGREGATE -> {
                allIdentities.addAll(uiState.value.groupedRecords.values.flatten().map { 
                    recordIdentity(it.deviceId, it.bookName, it.bookAuthor) 
                })
            }
            DisplayMode.TIMELINE -> {
                allIdentities.addAll(uiState.value.timelineRecords.values.flatten().map { 
                    recordIdentity(it.deviceId, it.bookName, it.bookAuthor) 
                })
            }
        }
        _selectedRecords.value = allIdentities
    }

    fun deleteSelectedRecords() {
        viewModelScope.launch {
            val selectedList = _selectedRecords.value.map { identity ->
                ReadRecord(
                    deviceId = identity.first,
                    bookName = identity.second,
                    bookAuthor = identity.third
                )
            }
            selectedList.forEach { record ->
                repository.deleteReadRecord(record)
            }
            exitSelectionMode()
        }
    }

    fun addTestReadRecord(bookName: String, bookAuthor: String): ReadRecord {
        val deviceId = AppConst.androidId
        val now = System.currentTimeMillis()
        val record = ReadRecord(
            deviceId = deviceId,
            bookName = bookName,
            bookAuthor = bookAuthor,
            readTime = 30 * 60 * 1000L,
            lastRead = now
        )
        val session = ReadRecordSession(
            deviceId = deviceId,
            bookName = bookName,
            bookAuthor = bookAuthor,
            startTime = now - 30 * 60 * 1000L,
            endTime = now,
            words = 5000L
        )
        viewModelScope.launch {
            repository.saveReadSession(session)
        }
        return record
    }

    fun isSelected(record: ReadRecord): Boolean {
        return _selectedRecords.value.contains(recordIdentity(record.deviceId, record.bookName, record.bookAuthor))
    }

    fun isSelected(detail: ReadRecordDetail): Boolean {
        return _selectedRecords.value.contains(recordIdentity(detail.deviceId, detail.bookName, detail.bookAuthor))
    }

    fun isSelected(session: ReadRecordSession): Boolean {
        return _selectedRecords.value.contains(recordIdentity(session.deviceId, session.bookName, session.bookAuthor))
    }

    private data class LoadedData(
        val totalReadTime: Long,
        val details: List<ReadRecordDetail>,
        val latestRecords: List<ReadRecord>,
        val sessions: List<ReadRecordSession>
    )

    private fun cacheKey(bookName: String, bookAuthor: String) = "$bookName|$bookAuthor"
}

private fun Long.toLocalDateString(): String {
    return Date(this).toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}

private fun parseDetailDate(date: String): LocalDate? {
    return runCatching {
        LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull()
}

private fun recordIdentity(deviceId: String, bookName: String, bookAuthor: String): RecordIdentity {
    return Triple(deviceId, bookName, bookAuthor)
}
