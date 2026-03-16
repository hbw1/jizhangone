package com.bowe.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bowe.localledger.data.local.entity.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeByBook(bookId: Long): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE bookId = :bookId AND active = 1 AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeActiveByBook(bookId: Long): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :memberId LIMIT 1")
    suspend fun getById(memberId: Long): MemberEntity?

    @Query("SELECT * FROM members WHERE bookId = :bookId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(bookId: Long, remoteId: String): MemberEntity?

    @Insert
    suspend fun insert(member: MemberEntity): Long

    @Update
    suspend fun update(member: MemberEntity)
}
