package io.legado.app.data.entities.readRecord

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "readRecordSession")
data class ReadRecordSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val deviceId: String = "",
    val bookName: String = "",
    @ColumnInfo(defaultValue = "")
    val bookAuthor: String = "",

    val startTime: Long = 0,
    val endTime: Long = 0,

    val words: Long = 0
)
