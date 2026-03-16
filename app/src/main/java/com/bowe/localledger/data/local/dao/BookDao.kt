package com.bowe.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bowe.localledger.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM books WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM books WHERE id = :bookId AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): BookEntity?

    @Query("SELECT * FROM books WHERE remoteId IS NOT NULL AND deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun getRemoteBooks(): List<BookEntity>

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)
}
