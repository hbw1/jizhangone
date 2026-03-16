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
    @Query("SELECT * FROM categories WHERE bookId = :bookId ORDER BY sortOrder ASC, name ASC")
    fun observeByBook(bookId: Long): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query(
        """
        SELECT * FROM categories
        WHERE bookId = :bookId AND active = 1 AND type = :type
        ORDER BY sortOrder ASC, name ASC
        """
    )
    fun observeActiveByBookAndType(bookId: Long, type: TransactionType): Flow<List<CategoryEntity>>
}
