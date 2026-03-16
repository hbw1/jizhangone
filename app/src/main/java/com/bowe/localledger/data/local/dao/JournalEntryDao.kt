package com.bowe.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bowe.localledger.data.local.entity.JournalEntryEntity

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries WHERE id = :entryId LIMIT 1")
    suspend fun getById(entryId: Long): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE bookId = :bookId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(bookId: Long, remoteId: String): JournalEntryEntity?

    @Insert
    suspend fun insert(entry: JournalEntryEntity): Long

    @Update
    suspend fun update(entry: JournalEntryEntity)
}
