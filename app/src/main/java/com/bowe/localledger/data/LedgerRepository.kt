package com.bowe.localledger.data

import androidx.room.withTransaction
import com.bowe.localledger.data.local.AppDatabase
import com.bowe.localledger.data.local.entity.AccountEntity
import com.bowe.localledger.data.local.entity.BookEntity
import com.bowe.localledger.data.local.entity.CategoryEntity
import com.bowe.localledger.data.local.entity.JournalEntryEntity
import com.bowe.localledger.data.local.entity.MemberEntity
import com.bowe.localledger.data.local.entity.PendingSyncOperationEntity
import com.bowe.localledger.data.local.entity.SyncEntityType
import com.bowe.localledger.data.local.entity.SyncOperationType
import com.bowe.localledger.data.local.entity.SyncState
import com.bowe.localledger.data.local.entity.TransactionEntity
import com.bowe.localledger.data.local.entity.TransactionType
import com.bowe.localledger.data.nlp.ParsedTransactionCandidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class LedgerRepository(
    private val database: AppDatabase,
) {
    suspend fun hasAnyBooks(): Boolean = database.bookDao().count() > 0

    fun observeBooks(): Flow<List<BookEntity>> = database.bookDao().observeAll()

    fun observeMembers(bookId: Long): Flow<List<MemberEntity>> =
        database.memberDao().observeActiveByBook(bookId)

    fun observeAccounts(bookId: Long): Flow<List<AccountEntity>> =
        database.accountDao().observeByBook(bookId).map { accounts -> accounts.filter { it.active } }

    fun observeCategories(bookId: Long, type: TransactionType): Flow<List<CategoryEntity>> =
        database.categoryDao().observeActiveByBookAndType(bookId, type)

    fun observeTransactions(bookId: Long): Flow<List<TransactionListItem>> =
        database.transactionDao().observeByBook(bookId).map { rows ->
            rows.map { row ->
                TransactionListItem(
                    id = row.transactionId,
                    memberId = row.memberId,
                    accountId = row.accountId,
                    categoryId = row.categoryId,
                    categoryName = row.categoryName,
                    memberName = row.memberName,
                    accountName = row.accountName,
                    amount = row.amount,
                    type = row.type,
                    occurredAt = Instant.ofEpochMilli(row.occurredAt),
                    note = row.note,
                    createdAt = Instant.ofEpochMilli(row.createdAt),
                )
            }
        }

    fun observeDashboard(bookId: Long): Flow<DashboardData> {
        val monthRange = currentMonthRange().toEpochRange()
        return combine(
            database.bookDao().observeAll(),
            database.transactionDao().observeMonthSummary(bookId, monthRange.startInclusive, monthRange.endExclusive),
            database.accountDao().observeBalancesByBook(bookId),
        ) { books, summary, accounts ->
            val bookName = books.firstOrNull { it.id == bookId }?.name ?: "账本"
            DashboardData(
                bookName = bookName,
                income = summary.income,
                expense = summary.expense,
                balance = summary.income - summary.expense,
                accountBalances = accounts.map {
                    ReportValue(it.accountName, it.balance)
                },
            )
        }
    }

    fun observeReports(bookId: Long, range: ReportDateRange): Flow<ReportsData> {
        val epochRange = range.toEpochRange()
        return combine(
            database.transactionDao().observeMonthSummary(
                bookId = bookId,
                startInclusive = epochRange.startInclusive,
                endExclusive = epochRange.endExclusive,
            ),
            database.transactionDao().observeCategoryTotals(
                bookId = bookId,
                type = TransactionType.EXPENSE,
                startInclusive = epochRange.startInclusive,
                endExclusive = epochRange.endExclusive,
            ),
            database.transactionDao().observeMemberTotals(
                bookId = bookId,
                startInclusive = epochRange.startInclusive,
                endExclusive = epochRange.endExclusive,
            ),
            database.transactionDao().observeMonthTrendInRange(
                bookId = bookId,
                startInclusive = epochRange.startInclusive,
                endExclusive = epochRange.endExclusive,
            ),
            database.accountDao().observeBalancesByBook(bookId),
        ) { summary, categories, members, trend, accounts ->
            ReportsData(
                income = summary.income,
                expense = summary.expense,
                categoryExpense = categories.map { ReportValue(it.label, it.total) },
                memberNet = members.map { ReportValue(it.label, it.total) },
                monthTrend = trend.map {
                    TrendValue(
                        label = it.month,
                        income = it.income,
                        expense = it.expense,
                    )
                },
                accountBalances = accounts.map { ReportValue(it.accountName, it.balance) },
            )
        }
    }

    suspend fun ensureSeedData() {
        if (database.bookDao().count() > 0) return

        val now = Instant.now()
        val bookId = database.bookDao().insert(BookEntity(name = "家庭账本", createdAt = now))

        val momId = database.memberDao().insert(MemberEntity(bookId = bookId, name = "妈妈", createdAt = now))
        val dadId = database.memberDao().insert(MemberEntity(bookId = bookId, name = "爸爸", createdAt = now))
        val meId = database.memberDao().insert(MemberEntity(bookId = bookId, name = "我", createdAt = now))

        val wechatId = database.accountDao().insert(
            AccountEntity(bookId = bookId, name = "微信", initialBalance = 2200.0, createdAt = now),
        )
        val alipayId = database.accountDao().insert(
            AccountEntity(bookId = bookId, name = "支付宝", initialBalance = 1200.0, createdAt = now),
        )
        val cardId = database.accountDao().insert(
            AccountEntity(bookId = bookId, name = "招商银行卡", initialBalance = 6000.0, createdAt = now),
        )

        val foodId = database.categoryDao().insert(
            CategoryEntity(bookId = bookId, type = TransactionType.EXPENSE, name = "餐饮", sortOrder = 1),
        )
        val transportId = database.categoryDao().insert(
            CategoryEntity(bookId = bookId, type = TransactionType.EXPENSE, name = "交通", sortOrder = 2),
        )
        val homeId = database.categoryDao().insert(
            CategoryEntity(bookId = bookId, type = TransactionType.EXPENSE, name = "居家", sortOrder = 3),
        )
        val salaryId = database.categoryDao().insert(
            CategoryEntity(bookId = bookId, type = TransactionType.INCOME, name = "工资", sortOrder = 1),
        )
        val bonusId = database.categoryDao().insert(
            CategoryEntity(bookId = bookId, type = TransactionType.INCOME, name = "奖金", sortOrder = 2),
        )

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val sampleTransactions = listOf(
            TransactionEntity(
                bookId = bookId,
                memberId = momId,
                accountId = wechatId,
                categoryId = foodId,
                type = TransactionType.EXPENSE,
                amount = 86.0,
                occurredAt = today.atTime(9, 0).atZone(zoneId).toInstant(),
                note = "买菜",
                createdAt = now,
                updatedAt = now,
            ),
            TransactionEntity(
                bookId = bookId,
                memberId = dadId,
                accountId = cardId,
                categoryId = salaryId,
                type = TransactionType.INCOME,
                amount = 12000.0,
                occurredAt = today.minusDays(2).atTime(10, 0).atZone(zoneId).toInstant(),
                note = "工资到账",
                createdAt = now,
                updatedAt = now,
            ),
            TransactionEntity(
                bookId = bookId,
                memberId = meId,
                accountId = alipayId,
                categoryId = transportId,
                type = TransactionType.EXPENSE,
                amount = 24.5,
                occurredAt = today.minusDays(1).atTime(18, 0).atZone(zoneId).toInstant(),
                note = "打车",
                createdAt = now,
                updatedAt = now,
            ),
            TransactionEntity(
                bookId = bookId,
                memberId = momId,
                accountId = wechatId,
                categoryId = homeId,
                type = TransactionType.EXPENSE,
                amount = 280.0,
                occurredAt = today.minusDays(3).atTime(15, 30).atZone(zoneId).toInstant(),
                note = "家庭用品",
                createdAt = now,
                updatedAt = now,
            ),
            TransactionEntity(
                bookId = bookId,
                memberId = dadId,
                accountId = cardId,
                categoryId = bonusId,
                type = TransactionType.INCOME,
                amount = 3200.0,
                occurredAt = today.minusDays(5).atTime(12, 0).atZone(zoneId).toInstant(),
                note = "绩效奖金",
                createdAt = now,
                updatedAt = now,
            ),
        )
        sampleTransactions.forEach { database.transactionDao().insert(it) }
    }

    suspend fun addTransaction(input: AddTransactionInput) {
        database.withTransaction {
            val transactionId = database.transactionDao().insert(
                TransactionEntity(
                    bookId = input.bookId,
                    memberId = input.memberId,
                    accountId = input.accountId,
                    categoryId = input.categoryId,
                    type = input.type,
                    amount = input.amount,
                    occurredAt = input.occurredAt,
                    note = input.note,
                ),
            )
            queueSyncOperation(
                bookId = input.bookId,
                entityType = SyncEntityType.TRANSACTION,
                entityLocalId = transactionId,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun updateTransaction(input: UpdateTransactionInput) {
        database.withTransaction {
            val existing = database.transactionDao().getById(input.id) ?: return@withTransaction
            database.transactionDao().update(
                TransactionEntity(
                    id = input.id,
                    remoteId = existing.remoteId,
                    bookId = input.bookId,
                    memberId = input.memberId,
                    accountId = input.accountId,
                    categoryId = input.categoryId,
                    type = input.type,
                    amount = input.amount,
                    occurredAt = input.occurredAt,
                    note = input.note,
                    createdAt = input.createdAt,
                    updatedAt = Instant.now(),
                    syncState = existing.syncState.markDirty(),
                    deletedAt = existing.deletedAt,
                    version = existing.version + 1,
                ),
            )
            queueSyncOperation(
                bookId = input.bookId,
                entityType = SyncEntityType.TRANSACTION,
                entityLocalId = input.id,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun deleteTransaction(transactionId: Long) {
        database.withTransaction {
            val existing = database.transactionDao().getById(transactionId) ?: return@withTransaction
            if (existing.remoteId == null && existing.syncState == SyncState.LOCAL_ONLY) {
                database.transactionDao().deleteById(transactionId)
                database.pendingSyncOperationDao().deleteByEntity(SyncEntityType.TRANSACTION, transactionId)
            } else {
                database.transactionDao().update(
                    existing.copy(
                        updatedAt = Instant.now(),
                        syncState = SyncState.DELETED,
                        deletedAt = Instant.now(),
                        version = existing.version + 1,
                    ),
                )
                queueSyncOperation(
                    bookId = existing.bookId,
                    entityType = SyncEntityType.TRANSACTION,
                    entityLocalId = transactionId,
                    operationType = SyncOperationType.DELETE,
                )
            }
        }
    }

    suspend fun saveNaturalLanguageEntry(
        bookId: Long,
        rawText: String,
        candidates: List<ParsedTransactionCandidate>,
        memberNameToId: Map<String, Long>,
        accountId: Long,
        expenseCategoryNameToId: Map<String, Long>,
        incomeCategoryNameToId: Map<String, Long>,
    ) = database.withTransaction {
        val now = Instant.now()
        val entryDate = candidates.firstOrNull()?.occurredOn?.atStartOfDay(ZoneId.systemDefault())?.toInstant() ?: now
        val journalId = database.journalEntryDao().insert(
            JournalEntryEntity(
                bookId = bookId,
                entryDate = entryDate,
                rawText = rawText,
                createdAt = now,
            ),
        )
        queueSyncOperation(
            bookId = bookId,
            entityType = SyncEntityType.JOURNAL_ENTRY,
            entityLocalId = journalId,
            operationType = SyncOperationType.UPSERT,
        )
        candidates.forEach { candidate ->
            val amount = candidate.amount ?: return@forEach
            val memberId = memberNameToId[candidate.memberName] ?: memberNameToId.values.firstOrNull() ?: return@forEach
            val categoryId = when (candidate.type) {
                TransactionType.EXPENSE -> expenseCategoryNameToId[candidate.categoryName]
                TransactionType.INCOME -> incomeCategoryNameToId[candidate.categoryName]
            } ?: return@forEach
            val occurredAt = candidate.occurredOn?.atStartOfDay(ZoneId.systemDefault())?.toInstant() ?: now

            val transactionId = database.transactionDao().insert(
                TransactionEntity(
                    bookId = bookId,
                    memberId = memberId,
                    accountId = accountId,
                    categoryId = categoryId,
                    type = candidate.type,
                    amount = amount,
                    occurredAt = occurredAt,
                    note = candidate.note,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            queueSyncOperation(
                bookId = bookId,
                entityType = SyncEntityType.TRANSACTION,
                entityLocalId = transactionId,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun addMember(bookId: Long, name: String) {
        database.withTransaction {
            val memberId = database.memberDao().insert(
            MemberEntity(
                bookId = bookId,
                name = name.trim(),
            ),
        )
            queueSyncOperation(
                bookId = bookId,
                entityType = SyncEntityType.MEMBER,
                entityLocalId = memberId,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun renameBook(book: BookEntity, name: String) {
        database.withTransaction {
            database.bookDao().update(
                book.copy(
                    name = name.trim(),
                    syncState = book.syncState.markDirty(),
                    version = book.version + 1,
                ),
            )
            queueSyncOperation(
                bookId = book.id,
                entityType = SyncEntityType.BOOK,
                entityLocalId = book.id,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun renameMember(member: MemberEntity, name: String) {
        database.withTransaction {
            database.memberDao().update(
                member.copy(
                    name = name.trim(),
                    syncState = member.syncState.markDirty(),
                    version = member.version + 1,
                ),
            )
            queueSyncOperation(
                bookId = member.bookId,
                entityType = SyncEntityType.MEMBER,
                entityLocalId = member.id,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun deactivateMember(member: MemberEntity) {
        database.withTransaction {
            database.memberDao().update(
                member.copy(
                    active = false,
                    syncState = member.syncState.markDirty(),
                    version = member.version + 1,
                ),
            )
            queueSyncOperation(
                bookId = member.bookId,
                entityType = SyncEntityType.MEMBER,
                entityLocalId = member.id,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun addAccount(bookId: Long, name: String, initialBalance: Double) {
        database.withTransaction {
            val accountId = database.accountDao().insert(
            AccountEntity(
                bookId = bookId,
                name = name.trim(),
                initialBalance = initialBalance,
            ),
        )
            queueSyncOperation(
                bookId = bookId,
                entityType = SyncEntityType.ACCOUNT,
                entityLocalId = accountId,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun renameAccount(account: AccountEntity, name: String) {
        database.withTransaction {
            database.accountDao().update(
                account.copy(
                    name = name.trim(),
                    syncState = account.syncState.markDirty(),
                    version = account.version + 1,
                ),
            )
            queueSyncOperation(
                bookId = account.bookId,
                entityType = SyncEntityType.ACCOUNT,
                entityLocalId = account.id,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun deactivateAccount(account: AccountEntity) {
        database.withTransaction {
            database.accountDao().update(
                account.copy(
                    active = false,
                    syncState = account.syncState.markDirty(),
                    version = account.version + 1,
                ),
            )
            queueSyncOperation(
                bookId = account.bookId,
                entityType = SyncEntityType.ACCOUNT,
                entityLocalId = account.id,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun addCategory(bookId: Long, type: TransactionType, name: String, sortOrder: Int) {
        database.withTransaction {
            val categoryId = database.categoryDao().insert(
            CategoryEntity(
                bookId = bookId,
                type = type,
                name = name.trim(),
                sortOrder = sortOrder,
            ),
        )
            queueSyncOperation(
                bookId = bookId,
                entityType = SyncEntityType.CATEGORY,
                entityLocalId = categoryId,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun renameCategory(category: CategoryEntity, name: String) {
        database.withTransaction {
            database.categoryDao().update(
                category.copy(
                    name = name.trim(),
                    syncState = category.syncState.markDirty(),
                    version = category.version + 1,
                ),
            )
            queueSyncOperation(
                bookId = category.bookId,
                entityType = SyncEntityType.CATEGORY,
                entityLocalId = category.id,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun deactivateCategory(category: CategoryEntity) {
        database.withTransaction {
            database.categoryDao().update(
                category.copy(
                    active = false,
                    syncState = category.syncState.markDirty(),
                    version = category.version + 1,
                ),
            )
            queueSyncOperation(
                bookId = category.bookId,
                entityType = SyncEntityType.CATEGORY,
                entityLocalId = category.id,
                operationType = SyncOperationType.UPSERT,
            )
        }
    }

    suspend fun addBook(name: String): Long {
        return database.withTransaction {
            val now = Instant.now()
            val bookId = database.bookDao().insert(
                BookEntity(
                    name = name.trim(),
                    createdAt = now,
                ),
            )
            queueSyncOperation(
                bookId = bookId,
                entityType = SyncEntityType.BOOK,
                entityLocalId = bookId,
                operationType = SyncOperationType.UPSERT,
            )
            val memberId = database.memberDao().insert(MemberEntity(bookId = bookId, name = "我", createdAt = now))
            queueSyncOperation(bookId, SyncEntityType.MEMBER, memberId, SyncOperationType.UPSERT)
            val accountId = database.accountDao().insert(
                AccountEntity(
                    bookId = bookId,
                    name = "现金",
                    initialBalance = 0.0,
                    createdAt = now,
                ),
            )
            queueSyncOperation(bookId, SyncEntityType.ACCOUNT, accountId, SyncOperationType.UPSERT)
            val expenseCategoryId = database.categoryDao().insert(
                CategoryEntity(
                    bookId = bookId,
                    type = TransactionType.EXPENSE,
                    name = "餐饮",
                    sortOrder = 1,
                ),
            )
            queueSyncOperation(bookId, SyncEntityType.CATEGORY, expenseCategoryId, SyncOperationType.UPSERT)
            val incomeCategoryId = database.categoryDao().insert(
                CategoryEntity(
                    bookId = bookId,
                    type = TransactionType.INCOME,
                    name = "工资",
                    sortOrder = 1,
                ),
            )
            queueSyncOperation(bookId, SyncEntityType.CATEGORY, incomeCategoryId, SyncOperationType.UPSERT)
            bookId
        }
    }

    suspend fun importBackup(snapshot: BackupSnapshot): Long = database.withTransaction {
        val now = Instant.now()
        val importedBookId = database.bookDao().insert(
            BookEntity(
                name = "${snapshot.bookName}（导入）",
                createdAt = now,
            ),
        )

        val memberIds = linkedMapOf<String, Long>()
        val memberNames = snapshot.members.ifEmpty { listOf("我") }.distinct()
        memberNames.forEach { memberName ->
            val id = database.memberDao().insert(
                MemberEntity(
                    bookId = importedBookId,
                    name = memberName,
                    createdAt = now,
                ),
            )
            memberIds[memberName] = id
        }

        val accountIds = linkedMapOf<String, Long>()
        val backupAccounts = snapshot.accounts.ifEmpty { listOf(BackupAccount(name = "现金", initialBalance = 0.0)) }
        backupAccounts.forEach { account ->
            val id = database.accountDao().insert(
                AccountEntity(
                    bookId = importedBookId,
                    name = account.name,
                    initialBalance = account.initialBalance,
                    createdAt = now,
                ),
            )
            accountIds[account.name] = id
        }

        val categoryIds = linkedMapOf<Pair<String, TransactionType>, Long>()
        val backupCategories = snapshot.categories.ifEmpty {
            listOf(
                BackupCategory(name = "餐饮", type = TransactionType.EXPENSE),
                BackupCategory(name = "工资", type = TransactionType.INCOME),
            )
        }
        backupCategories.distinctBy { it.name to it.type }.forEachIndexed { index, category ->
            val id = database.categoryDao().insert(
                CategoryEntity(
                    bookId = importedBookId,
                    type = category.type,
                    name = category.name,
                    sortOrder = index + 1,
                ),
            )
            categoryIds[category.name to category.type] = id
        }

        snapshot.transactions.forEach { transaction ->
            val memberId = memberIds[transaction.memberName] ?: memberIds.getValue(memberNames.first())
            val accountId = accountIds[transaction.accountName] ?: accountIds.getValue(backupAccounts.first().name)
            val categoryKey = transaction.categoryName to transaction.type
            val categoryId = categoryIds[categoryKey] ?: database.categoryDao().insert(
                CategoryEntity(
                    bookId = importedBookId,
                    type = transaction.type,
                    name = transaction.categoryName,
                    sortOrder = categoryIds.size + 1,
                ),
            ).also { insertedId ->
                categoryIds[categoryKey] = insertedId
            }

            database.transactionDao().insert(
                TransactionEntity(
                    bookId = importedBookId,
                    memberId = memberId,
                    accountId = accountId,
                    categoryId = categoryId,
                    type = transaction.type,
                    amount = transaction.amount,
                    occurredAt = Instant.ofEpochMilli(transaction.occurredAtEpochMs),
                    note = transaction.note,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        importedBookId
    }

    private suspend fun queueSyncOperation(
        bookId: Long,
        entityType: SyncEntityType,
        entityLocalId: Long,
        operationType: SyncOperationType,
    ) {
        val book = database.bookDao().getById(bookId) ?: return
        if (book.remoteId == null) return
        database.pendingSyncOperationDao().deleteByEntity(entityType, entityLocalId)
        database.pendingSyncOperationDao().insert(
            PendingSyncOperationEntity(
                bookId = bookId,
                entityType = entityType,
                entityLocalId = entityLocalId,
                operationType = operationType,
                clientMutationId = UUID.randomUUID().toString(),
            ),
        )
    }

    private fun SyncState.markDirty(): SyncState = when (this) {
        SyncState.SYNCED -> SyncState.DIRTY
        SyncState.DELETED -> SyncState.DELETED
        else -> this
    }

    private fun currentMonthRange(): ReportDateRange {
        val today = LocalDate.now(ZoneId.systemDefault())
        return ReportDateRange(
            startDate = today.withDayOfMonth(1),
            endDateInclusive = today,
        )
    }
}

data class AddTransactionInput(
    val bookId: Long,
    val memberId: Long,
    val accountId: Long,
    val categoryId: Long,
    val type: TransactionType,
    val amount: Double,
    val occurredAt: Instant,
    val note: String,
)

data class UpdateTransactionInput(
    val id: Long,
    val bookId: Long,
    val memberId: Long,
    val accountId: Long,
    val categoryId: Long,
    val type: TransactionType,
    val amount: Double,
    val occurredAt: Instant,
    val note: String,
    val createdAt: Instant,
)

data class DashboardData(
    val bookName: String,
    val income: Double,
    val expense: Double,
    val balance: Double,
    val accountBalances: List<ReportValue>,
)

data class ReportsData(
    val income: Double,
    val expense: Double,
    val categoryExpense: List<ReportValue>,
    val memberNet: List<ReportValue>,
    val monthTrend: List<TrendValue>,
    val accountBalances: List<ReportValue>,
)

data class ReportValue(
    val label: String,
    val value: Double,
)

data class TrendValue(
    val label: String,
    val income: Double,
    val expense: Double,
)

data class TransactionListItem(
    val id: Long,
    val memberId: Long,
    val accountId: Long,
    val categoryId: Long,
    val categoryName: String,
    val memberName: String,
    val accountName: String,
    val amount: Double,
    val type: TransactionType,
    val occurredAt: Instant,
    val note: String,
    val createdAt: Instant = Instant.now(),
)
