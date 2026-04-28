package io.legado.app.data.entities.readRecord

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "readRecordDetail",
    primaryKeys = ["deviceId", "bookName", "bookAuthor", "date"]
)
data class ReadRecordDetail(
    val deviceId: String = "",
    val bookName: String = "",
    @ColumnInfo(defaultValue = "")
    val bookAuthor: String = "",
    val date: String = "",

    @ColumnInfo(defaultValue = "0")
    var readTime: Long = 0L,

    @ColumnInfo(defaultValue = "0")
    var readWords: Long = 0L,

    @ColumnInfo(defaultValue = "0")
    var firstReadTime: Long = 0L,

    @ColumnInfo(defaultValue = "0")
    var lastReadTime: Long = 0L
)
