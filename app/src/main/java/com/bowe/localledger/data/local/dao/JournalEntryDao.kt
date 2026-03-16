package com.bowe.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.bowe.localledger.data.local.entity.JournalEntryEntity

@Dao
interface JournalEntryDao {
    @Insert
    suspend fun insert(entry: JournalEntryEntity): Long
}
