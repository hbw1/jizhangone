package com.bowe.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bowe.localledger.data.local.entity.CategoryEntity
import com.bowe.localledger.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    fun observeByBook(bookId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    suspend fun getById(categoryId: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE bookId = :bookId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(bookId: Long, remoteId: String): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query(
        """
        SELECT * FROM categories
        WHERE bookId = :bookId AND active = 1 AND type = :type AND deletedAt IS NULL
        ORDER BY sortOrder ASC, name ASC
        """
    )
    fun observeActiveByBookAndType(bookId: Long, type: TransactionType): Flow<List<CategoryEntity>>
}
