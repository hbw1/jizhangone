package com.bowe.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bowe.localledger.data.local.entity.AccountEntity
import com.bowe.localledger.data.local.query.AccountBalanceRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeByBook(bookId: Long): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    suspend fun getById(accountId: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE bookId = :bookId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(bookId: Long, remoteId: String): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query(
        """
        SELECT
            a.id AS accountId,
            a.name AS accountName,
            a.initialBalance + COALESCE(SUM(
                CASE
                    WHEN t.type = 'INCOME' THEN t.amount
                    ELSE -t.amount
                END
            ), 0) AS balance
        FROM accounts a
        LEFT JOIN transactions t ON t.accountId = a.id AND t.deletedAt IS NULL
        WHERE a.bookId = :bookId AND a.active = 1 AND a.deletedAt IS NULL
        GROUP BY a.id, a.name, a.initialBalance
        ORDER BY balance DESC
        """
    )
    fun observeBalancesByBook(bookId: Long): Flow<List<AccountBalanceRow>>
}
