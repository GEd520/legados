package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg readRecord: ReadRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetail(detail: ReadRecordDetail)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadRecordSession)

    @Update
    suspend fun update(vararg record: ReadRecord)

    @Update
    suspend fun updateSession(session: ReadRecordSession)

    @Delete
    suspend fun delete(vararg record: ReadRecord)

    @Delete
    suspend fun deleteDetail(detail: ReadRecordDetail)

    @Delete
    suspend fun deleteSession(session: ReadRecordSession)

    @Query("DELETE FROM readRecord")
    suspend fun clear()

    @Query("DELETE FROM readRecord WHERE bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun deleteByNameAndAuthor(bookName: String, bookAuthor: String)

    @Query("SELECT * FROM readRecord WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun getReadRecord(deviceId: String, bookName: String, bookAuthor: String): ReadRecord?

    @Query("SELECT * FROM readRecord WHERE deviceId = :deviceId AND bookName = :bookName")
    suspend fun getReadRecordsByName(deviceId: String, bookName: String): List<ReadRecord>

    @Query("SELECT * FROM readRecord WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor != :excludeAuthor")
    suspend fun getReadRecordsByNameExcludingAuthor(deviceId: String, bookName: String, excludeAuthor: String): List<ReadRecord>

    @Query("SELECT SUM(readTime) FROM readRecord")
    fun getTotalReadTime(): Flow<Long?>

    @Query("SELECT readTime FROM readRecord WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    fun getReadTimeFlow(deviceId: String, bookName: String, bookAuthor: String): Flow<Long?>

    @Query("SELECT readTime FROM readRecord WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun getReadTime(deviceId: String, bookName: String, bookAuthor: String): Long?

    @Query("SELECT * FROM readRecord ORDER BY lastRead DESC")
    fun getAllReadRecordsSortedByLastRead(): Flow<List<ReadRecord>>

    @get:Query("SELECT * FROM readRecord")
    val all: List<ReadRecord>

    @Query("SELECT * FROM readRecord WHERE bookName LIKE '%' || :query || '%' OR bookAuthor LIKE '%' || :query || '%' ORDER BY lastRead DESC")
    fun searchReadRecordsByLastRead(query: String): Flow<List<ReadRecord>>

    @Query("SELECT * FROM readRecordDetail WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor AND date = :date")
    suspend fun getDetail(deviceId: String, bookName: String, bookAuthor: String, date: String): ReadRecordDetail?

    @Query("SELECT * FROM readRecordDetail WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun getDetailsByBook(deviceId: String, bookName: String, bookAuthor: String): List<ReadRecordDetail>

    @Query("SELECT * FROM readRecordDetail ORDER BY date DESC")
    fun getAllDetails(): Flow<List<ReadRecordDetail>>

    @Query("SELECT * FROM readRecordDetail")
    suspend fun getAllDetailsList(): List<ReadRecordDetail>

    @Query("SELECT COUNT(*) FROM readRecordDetail")
    fun getDetailsCount(): Int

    @Query("SELECT * FROM readRecordDetail WHERE bookName LIKE '%' || :query || '%' OR bookAuthor LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchDetails(query: String): Flow<List<ReadRecordDetail>>

    @Query("DELETE FROM readRecordDetail WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun deleteDetailsByBook(deviceId: String, bookName: String, bookAuthor: String)

    @Query("SELECT * FROM readRecordSession WHERE deviceId = :deviceId ORDER BY startTime DESC")
    fun getAllSessions(deviceId: String): Flow<List<ReadRecordSession>>

    @Query("SELECT * FROM readRecordSession")
    suspend fun getAllSessionsList(): List<ReadRecordSession>

    @Query("SELECT COUNT(*) FROM readRecordSession")
    fun getSessionsCount(): Int

    @Query("SELECT * FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor ORDER BY startTime DESC")
    fun getSessionsByBookFlow(deviceId: String, bookName: String, bookAuthor: String): Flow<List<ReadRecordSession>>

    @Query("SELECT * FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun getSessionsByBook(deviceId: String, bookName: String, bookAuthor: String): List<ReadRecordSession>

    @Query("SELECT * FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor AND date(startTime / 1000, 'unixepoch') = :date")
    suspend fun getSessionsByBookAndDate(deviceId: String, bookName: String, bookAuthor: String, date: String): List<ReadRecordSession>

    @Query("DELETE FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun deleteSessionsByBook(deviceId: String, bookName: String, bookAuthor: String)

    @Query("DELETE FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor AND date(startTime / 1000, 'unixepoch') = :date")
    suspend fun deleteSessionsByBookAndDate(deviceId: String, bookName: String, bookAuthor: String, date: String)

    @Delete
    suspend fun deleteReadRecord(record: ReadRecord)
}
