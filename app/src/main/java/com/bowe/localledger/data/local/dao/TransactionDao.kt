package com.bowe.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bowe.localledger.data.local.entity.TransactionEntity
import com.bowe.localledger.data.local.entity.TransactionType
import com.bowe.localledger.data.local.query.CategoryTotalRow
import com.bowe.localledger.data.local.query.MemberTotalRow
import com.bowe.localledger.data.local.query.MonthTrendRow
import com.bowe.localledger.data.local.query.MonthSummaryRow
import com.bowe.localledger.data.local.query.TransactionListRow
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT
            t.id AS transactionId,
            t.memberId AS memberId,
            t.accountId AS accountId,
            t.categoryId AS categoryId,
            t.type AS type,
            t.amount AS amount,
            t.occurredAt AS occurredAt,
            t.createdAt AS createdAt,
            t.note AS note,
            c.name AS categoryName,
            m.name AS memberName,
            a.name AS accountName
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        INNER JOIN members m ON m.id = t.memberId
        INNER JOIN accounts a ON a.id = t.accountId
        WHERE t.bookId = :bookId AND t.deletedAt IS NULL
        ORDER BY t.occurredAt DESC, t.createdAt DESC
        """
    )
    fun observeByBook(bookId: Long): Flow<List<TransactionListRow>>

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getById(transactionId: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE bookId = :bookId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(bookId: Long, remoteId: String): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense
        FROM transactions
        WHERE bookId = :bookId AND deletedAt IS NULL AND occurredAt >= :startInclusive AND occurredAt < :endExclusive
        """
    )
    fun observeMonthSummary(
        bookId: Long,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<MonthSummaryRow>

    @Query(
        """
        SELECT
            c.name AS label,
            COALESCE(SUM(t.amount), 0) AS total
        FROM transactions t
        INNER JOIN categories c ON c.id = t.categoryId
        WHERE t.bookId = :bookId
            AND t.deletedAt IS NULL
            AND t.type = :type
            AND t.occurredAt >= :startInclusive AND t.occurredAt < :endExclusive
        GROUP BY c.id, c.name
        ORDER BY total DESC
        """
    )
    fun observeCategoryTotals(
        bookId: Long,
        type: TransactionType,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<CategoryTotalRow>>

    @Query(
        """
        SELECT
            m.name AS label,
            COALESCE(SUM(
                CASE
                    WHEN t.type = 'INCOME' THEN t.amount
                    ELSE -t.amount
                END
            ), 0) AS total
        FROM transactions t
        INNER JOIN members m ON m.id = t.memberId
        WHERE t.bookId = :bookId
            AND t.deletedAt IS NULL
            AND t.occurredAt >= :startInclusive AND t.occurredAt < :endExclusive
        GROUP BY m.id, m.name
        ORDER BY total DESC
        """
    )
    fun observeMemberTotals(
        bookId: Long,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<MemberTotalRow>>

    @Query(
        """
        SELECT
            strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime') AS month,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense
        FROM transactions
        WHERE bookId = :bookId
            AND deletedAt IS NULL
            AND occurredAt >= :startInclusive
            AND occurredAt < :endExclusive
        GROUP BY strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime')
        ORDER BY month ASC
        """
    )
    fun observeMonthTrendInRange(
        bookId: Long,
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<MonthTrendRow>>

    @Query(
        """
        SELECT
            strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime') AS month,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense
        FROM transactions
        WHERE bookId = :bookId AND deletedAt IS NULL
        GROUP BY strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime')
        ORDER BY month DESC
        LIMIT :limit
        """
    )
    fun observeMonthTrend(bookId: Long, limit: Int = 6): Flow<List<MonthTrendRow>>
}
