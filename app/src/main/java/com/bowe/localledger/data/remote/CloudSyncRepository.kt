package com.bowe.localledger.data.remote

import androidx.room.withTransaction
import com.bowe.localledger.data.local.AppDatabase
import com.bowe.localledger.data.local.entity.AccountEntity
import com.bowe.localledger.data.local.entity.BookEntity
import com.bowe.localledger.data.local.entity.CategoryEntity
import com.bowe.localledger.data.local.entity.JournalEntryEntity
import com.bowe.localledger.data.local.entity.MemberEntity
import com.bowe.localledger.data.local.entity.PendingSyncOperationEntity
import com.bowe.localledger.data.local.entity.SyncEntityType
import com.bowe.localledger.data.local.entity.SyncOperationStatus
import com.bowe.localledger.data.local.entity.SyncOperationType
import com.bowe.localledger.data.local.entity.SyncState
import com.bowe.localledger.data.local.entity.TransactionEntity
import com.bowe.localledger.data.local.entity.TransactionType
import com.bowe.localledger.data.remote.dto.SyncChangeDto
import com.bowe.localledger.data.remote.dto.SyncOperationAckDto
import com.bowe.localledger.data.remote.dto.SyncOperationRequestDto
import com.bowe.localledger.data.remote.dto.SyncPushRequestDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CloudSyncSummary(
    val importedBooks: Int = 0,
    val pushedOperations: Int = 0,
    val pulledChanges: Int = 0,
)

class CloudSyncRepository(
    private val database: AppDatabase,
    private val tokenStore: TokenStore,
    private val cursorStore: SyncCursorStore,
    private val remoteDataSource: RemoteLedgerDataSource,
) {
    suspend fun clearLocalRemoteData() {
        val remoteBooks = database.bookDao().getRemoteBooks()
        val remoteBookIds = remoteBooks.map { it.id }
        database.withTransaction {
            if (remoteBookIds.isNotEmpty()) {
                database.pendingSyncOperationDao().deleteByBookIds(remoteBookIds)
            }
            database.bookDao().deleteRemoteBooks()
        }
        cursorStore.clearAll()
    }

    suspend fun bootstrapBooks(cloudBooks: List<CloudBook>): List<Long> = database.withTransaction {
        cloudBooks.map { cloudBook ->
            val existing = database.bookDao().getByRemoteId(cloudBook.id)
            if (existing == null) {
                database.bookDao().insert(
                    BookEntity(
                        remoteId = cloudBook.id,
                        name = cloudBook.name,
                        syncState = SyncState.SYNCED,
                    ),
                )
            } else {
                database.bookDao().update(
                    existing.copy(
                        name = cloudBook.name,
                        syncState = SyncState.SYNCED,
                        deletedAt = null,
                    ),
                )
                existing.id
            }
        }
    }

    suspend fun syncAll(): Result<CloudSyncSummary> = runCatching {
        runSyncWithValidToken()
    }

    private suspend fun runSyncWithValidToken(): CloudSyncSummary {
        val accessToken = tokenStore.accessToken.first()
            ?: error("云端 token 不存在，请重新登录")

        return try {
            syncAllWithAccessToken(accessToken)
        } catch (_: Exception) {
            val refreshToken = tokenStore.refreshToken.first()
                ?: error("云端登录已失效，请重新登录")
            val refreshed = remoteDataSource.refresh(refreshToken)
            tokenStore.saveSession(refreshed.accessToken, refreshed.refreshToken)
            syncAllWithAccessToken(refreshed.accessToken)
        }
    }

    private suspend fun syncAllWithAccessToken(accessToken: String): CloudSyncSummary {
        val remoteBooks = database.bookDao().getRemoteBooks()
        var pushed = 0
        var pulled = 0

        remoteBooks.forEach { localBook ->
            pushed += pushPendingOperationsForBook(accessToken, localBook)
            pulled += pullRemoteChangesForBook(accessToken, localBook)
        }
        return CloudSyncSummary(
            importedBooks = remoteBooks.size,
            pushedOperations = pushed,
            pulledChanges = pulled,
        )
    }

    private suspend fun pushPendingOperationsForBook(accessToken: String, book: BookEntity): Int {
        val remoteBookId = book.remoteId ?: return 0
        val pendingOperations = database.pendingSyncOperationDao().getByBookAndStatuses(
            bookId = book.id,
            statuses = listOf(SyncOperationStatus.PENDING, SyncOperationStatus.FAILED),
        ).sortedBy { it.entityType.pushPriority() }

        if (pendingOperations.isEmpty()) return 0

        val requestOperations = linkedMapOf<Long, SyncOperationRequestDto>()
        for (operation in pendingOperations) {
            val dto = buildPushOperation(operation) ?: continue
            requestOperations[operation.id] = dto
        }
        if (requestOperations.isEmpty()) return 0

        val response = remoteDataSource.pushChanges(
            accessToken = accessToken,
            payload = SyncPushRequestDto(
                bookId = remoteBookId,
                operations = requestOperations.values.toList(),
            ),
        )

        database.withTransaction {
            response.applied.forEach { ack ->
                val operation = pendingOperations.firstOrNull { it.clientMutationId == ack.clientMutationId } ?: return@forEach
                applyPushAck(operation, ack)
                database.pendingSyncOperationDao().deleteById(operation.id)
            }
        }

        return response.applied.size
    }

    private suspend fun pullRemoteChangesForBook(accessToken: String, book: BookEntity): Int {
        val remoteBookId = book.remoteId ?: return 0
        val cursor = cursorStore.getCursor(remoteBookId)
        val response = remoteDataSource.pullChanges(
            accessToken = accessToken,
            bookId = remoteBookId,
            cursor = cursor,
        )

        if (response.changes.isEmpty()) {
            cursorStore.saveCursor(remoteBookId, response.nextCursor)
            return 0
        }

        database.withTransaction {
            response.changes.forEach { change ->
                applyPulledChange(localBookId = book.id, change = change)
            }
        }
        cursorStore.saveCursor(remoteBookId, response.nextCursor)
        return response.changes.size
    }

    private suspend fun buildPushOperation(
        operation: PendingSyncOperationEntity,
    ): SyncOperationRequestDto? {
        val payload = when (operation.entityType) {
            SyncEntityType.BOOK -> buildBookPayload(operation.entityLocalId) ?: return null
            SyncEntityType.MEMBER -> buildMemberPayload(operation.entityLocalId) ?: return null
            SyncEntityType.ACCOUNT -> buildAccountPayload(operation.entityLocalId) ?: return null
            SyncEntityType.CATEGORY -> buildCategoryPayload(operation.entityLocalId) ?: return null
            SyncEntityType.JOURNAL_ENTRY -> buildJournalPayload(operation.entityLocalId) ?: return null
            SyncEntityType.TRANSACTION -> buildTransactionPayload(operation.entityLocalId) ?: return null
        }

        return SyncOperationRequestDto(
            clientMutationId = operation.clientMutationId,
            entityType = operation.entityType.apiName(),
            operationType = operation.operationType.apiName(),
            payload = if (operation.operationType == SyncOperationType.DELETE) {
                buildDeletePayload(operation.entityType, operation.entityLocalId) ?: return null
            } else {
                payload
            },
        )
    }

    private suspend fun buildDeletePayload(entityType: SyncEntityType, localId: Long): JsonObject? {
        val remoteId = when (entityType) {
            SyncEntityType.BOOK -> database.bookDao().getById(localId)?.remoteId
            SyncEntityType.MEMBER -> database.memberDao().getById(localId)?.remoteId
            SyncEntityType.ACCOUNT -> database.accountDao().getById(localId)?.remoteId
            SyncEntityType.CATEGORY -> database.categoryDao().getById(localId)?.remoteId
            SyncEntityType.JOURNAL_ENTRY -> database.journalEntryDao().getById(localId)?.remoteId
            SyncEntityType.TRANSACTION -> database.transactionDao().getById(localId)?.remoteId
        } ?: return null
        return buildJsonObject {
            put("id", JsonPrimitive(remoteId))
        }
    }

    private suspend fun buildBookPayload(localId: Long): JsonObject? {
        val book = database.bookDao().getById(localId) ?: return null
        val remoteId = book.remoteId ?: return null
        return buildJsonObject {
            put("id", JsonPrimitive(remoteId))
            put("name", JsonPrimitive(book.name))
        }
    }

    private suspend fun buildMemberPayload(localId: Long): JsonObject? {
        val member = database.memberDao().getById(localId) ?: return null
        return buildJsonObject {
            member.remoteId?.let { put("id", JsonPrimitive(it)) }
            put("name", JsonPrimitive(member.name))
            put("active", JsonPrimitive(member.active))
        }
    }

    private suspend fun buildAccountPayload(localId: Long): JsonObject? {
        val account = database.accountDao().getById(localId) ?: return null
        return buildJsonObject {
            account.remoteId?.let { put("id", JsonPrimitive(it)) }
            put("name", JsonPrimitive(account.name))
            put("initial_balance", JsonPrimitive(account.initialBalance))
            put("active", JsonPrimitive(account.active))
        }
    }

    private suspend fun buildCategoryPayload(localId: Long): JsonObject? {
        val category = database.categoryDao().getById(localId) ?: return null
        return buildJsonObject {
            category.remoteId?.let { put("id", JsonPrimitive(it)) }
            put("type", JsonPrimitive(category.type.name.lowercase()))
            put("name", JsonPrimitive(category.name))
            put("sort_order", JsonPrimitive(category.sortOrder))
            put("active", JsonPrimitive(category.active))
        }
    }

    private suspend fun buildJournalPayload(localId: Long): JsonObject? {
        val journal = database.journalEntryDao().getById(localId) ?: return null
        return buildJsonObject {
            journal.remoteId?.let { put("id", JsonPrimitive(it)) }
            put("entry_date", JsonPrimitive(journal.entryDate.toString()))
            put("raw_text", JsonPrimitive(journal.rawText))
            put("source_type", JsonPrimitive("manual"))
        }
    }

    private suspend fun buildTransactionPayload(localId: Long): JsonObject? {
        val transaction = database.transactionDao().getById(localId) ?: return null
        val memberRemoteId = database.memberDao().getById(transaction.memberId)?.remoteId ?: return null
        val accountRemoteId = database.accountDao().getById(transaction.accountId)?.remoteId ?: return null
        val categoryRemoteId = database.categoryDao().getById(transaction.categoryId)?.remoteId ?: return null
        return buildJsonObject {
            transaction.remoteId?.let { put("id", JsonPrimitive(it)) }
            put("member_id", JsonPrimitive(memberRemoteId))
            put("account_id", JsonPrimitive(accountRemoteId))
            put("category_id", JsonPrimitive(categoryRemoteId))
            put("type", JsonPrimitive(transaction.type.name.lowercase()))
            put("amount", JsonPrimitive(transaction.amount))
            put("occurred_at", JsonPrimitive(transaction.occurredAt.toString()))
            put("note", JsonPrimitive(transaction.note))
            put("source_type", JsonPrimitive("manual"))
        }
    }

    private suspend fun applyPushAck(
        operation: PendingSyncOperationEntity,
        ack: SyncOperationAckDto,
    ) {
        when (operation.entityType) {
            SyncEntityType.BOOK -> {
                val entity = database.bookDao().getById(operation.entityLocalId) ?: return
                database.bookDao().update(
                    entity.copy(
                        remoteId = ack.entityId,
                        syncState = if (operation.operationType == SyncOperationType.DELETE) SyncState.DELETED else SyncState.SYNCED,
                    ),
                )
            }
            SyncEntityType.MEMBER -> {
                val entity = database.memberDao().getById(operation.entityLocalId) ?: return
                database.memberDao().update(
                    entity.copy(
                        remoteId = ack.entityId,
                        syncState = if (operation.operationType == SyncOperationType.DELETE) SyncState.DELETED else SyncState.SYNCED,
                    ),
                )
            }
            SyncEntityType.ACCOUNT -> {
                val entity = database.accountDao().getById(operation.entityLocalId) ?: return
                database.accountDao().update(
                    entity.copy(
                        remoteId = ack.entityId,
                        syncState = if (operation.operationType == SyncOperationType.DELETE) SyncState.DELETED else SyncState.SYNCED,
                    ),
                )
            }
            SyncEntityType.CATEGORY -> {
                val entity = database.categoryDao().getById(operation.entityLocalId) ?: return
                database.categoryDao().update(
                    entity.copy(
                        remoteId = ack.entityId,
                        syncState = if (operation.operationType == SyncOperationType.DELETE) SyncState.DELETED else SyncState.SYNCED,
                    ),
                )
            }
            SyncEntityType.JOURNAL_ENTRY -> {
                val entity = database.journalEntryDao().getById(operation.entityLocalId) ?: return
                database.journalEntryDao().update(
                    entity.copy(
                        remoteId = ack.entityId,
                        syncState = if (operation.operationType == SyncOperationType.DELETE) SyncState.DELETED else SyncState.SYNCED,
                    ),
                )
            }
            SyncEntityType.TRANSACTION -> {
                val entity = database.transactionDao().getById(operation.entityLocalId) ?: return
                database.transactionDao().update(
                    entity.copy(
                        remoteId = ack.entityId,
                        syncState = if (operation.operationType == SyncOperationType.DELETE) SyncState.DELETED else SyncState.SYNCED,
                    ),
                )
            }
        }
    }

    private suspend fun applyPulledChange(localBookId: Long, change: SyncChangeDto) {
        when (change.entityType) {
            "book" -> upsertRemoteBook(change)
            "member" -> upsertRemoteMember(localBookId, change)
            "account" -> upsertRemoteAccount(localBookId, change)
            "category" -> upsertRemoteCategory(localBookId, change)
            "journal_entry" -> upsertRemoteJournal(localBookId, change)
            "transaction" -> upsertRemoteTransaction(localBookId, change)
        }
    }

    private suspend fun upsertRemoteBook(change: SyncChangeDto) {
        val remoteId = change.entityId
        val existing = database.bookDao().getByRemoteId(remoteId)
        val deletedAt = change.payload.stringValue("deleted_at")?.toInstantOrNull()
        if (existing == null) return
        database.bookDao().update(
            existing.copy(
                name = change.payload["name"]?.jsonPrimitive?.content ?: existing.name,
                deletedAt = deletedAt,
                syncState = if (deletedAt != null) SyncState.DELETED else SyncState.SYNCED,
            ),
        )
    }

    private suspend fun upsertRemoteMember(localBookId: Long, change: SyncChangeDto) {
        val remoteId = change.entityId
        val existing = database.memberDao().getByRemoteId(localBookId, remoteId)
        val deletedAt = change.payload.stringValue("deleted_at")?.toInstantOrNull()
        if (change.operationType == "delete") {
            existing?.let {
                database.memberDao().update(
                    it.copy(deletedAt = deletedAt ?: Instant.now(), syncState = SyncState.DELETED),
                )
            }
            return
        }
        val name = change.payload["name"]?.jsonPrimitive?.content ?: return
        val active = change.payload["active"]?.jsonPrimitive?.booleanOrNull ?: true
        if (existing == null) {
            database.memberDao().insert(
                MemberEntity(
                    remoteId = remoteId,
                    bookId = localBookId,
                    name = name,
                    active = active,
                    syncState = SyncState.SYNCED,
                ),
            )
        } else {
            database.memberDao().update(
                existing.copy(
                    name = name,
                    active = active,
                    deletedAt = null,
                    syncState = SyncState.SYNCED,
                ),
            )
        }
    }

    private suspend fun upsertRemoteAccount(localBookId: Long, change: SyncChangeDto) {
        val remoteId = change.entityId
        val existing = database.accountDao().getByRemoteId(localBookId, remoteId)
        val deletedAt = change.payload.stringValue("deleted_at")?.toInstantOrNull()
        if (change.operationType == "delete") {
            existing?.let {
                database.accountDao().update(
                    it.copy(deletedAt = deletedAt ?: Instant.now(), syncState = SyncState.DELETED),
                )
            }
            return
        }
        val name = change.payload["name"]?.jsonPrimitive?.content ?: return
        val initialBalance = change.payload["initial_balance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val active = change.payload["active"]?.jsonPrimitive?.booleanOrNull ?: true
        if (existing == null) {
            database.accountDao().insert(
                AccountEntity(
                    remoteId = remoteId,
                    bookId = localBookId,
                    name = name,
                    initialBalance = initialBalance,
                    active = active,
                    syncState = SyncState.SYNCED,
                ),
            )
        } else {
            database.accountDao().update(
                existing.copy(
                    name = name,
                    initialBalance = initialBalance,
                    active = active,
                    deletedAt = null,
                    syncState = SyncState.SYNCED,
                ),
            )
        }
    }

    private suspend fun upsertRemoteCategory(localBookId: Long, change: SyncChangeDto) {
        val remoteId = change.entityId
        val existing = database.categoryDao().getByRemoteId(localBookId, remoteId)
        val deletedAt = change.payload.stringValue("deleted_at")?.toInstantOrNull()
        if (change.operationType == "delete") {
            existing?.let {
                database.categoryDao().update(
                    it.copy(deletedAt = deletedAt ?: Instant.now(), syncState = SyncState.DELETED),
                )
            }
            return
        }
        val name = change.payload["name"]?.jsonPrimitive?.content ?: return
        val type = change.payload["type"]?.jsonPrimitive?.content?.toTransactionType() ?: return
        val sortOrder = change.payload["sort_order"]?.jsonPrimitive?.intOrNull ?: 0
        val active = change.payload["active"]?.jsonPrimitive?.booleanOrNull ?: true
        if (existing == null) {
            database.categoryDao().insert(
                CategoryEntity(
                    remoteId = remoteId,
                    bookId = localBookId,
                    type = type,
                    name = name,
                    sortOrder = sortOrder,
                    active = active,
                    syncState = SyncState.SYNCED,
                ),
            )
        } else {
            database.categoryDao().update(
                existing.copy(
                    type = type,
                    name = name,
                    sortOrder = sortOrder,
                    active = active,
                    deletedAt = null,
                    syncState = SyncState.SYNCED,
                ),
            )
        }
    }

    private suspend fun upsertRemoteJournal(localBookId: Long, change: SyncChangeDto) {
        val remoteId = change.entityId
        val existing = database.journalEntryDao().getByRemoteId(localBookId, remoteId)
        val deletedAt = change.payload.stringValue("deleted_at")?.toInstantOrNull()
        if (change.operationType == "delete") {
            existing?.let {
                database.journalEntryDao().update(
                    it.copy(deletedAt = deletedAt ?: Instant.now(), syncState = SyncState.DELETED),
                )
            }
            return
        }
        val entryDate = change.payload.stringValue("entry_date")?.toInstantOrNull() ?: Instant.now()
        val rawText = change.payload["raw_text"]?.jsonPrimitive?.content ?: return
        if (existing == null) {
            database.journalEntryDao().insert(
                JournalEntryEntity(
                    remoteId = remoteId,
                    bookId = localBookId,
                    entryDate = entryDate,
                    rawText = rawText,
                    syncState = SyncState.SYNCED,
                ),
            )
        } else {
            database.journalEntryDao().update(
                existing.copy(
                    entryDate = entryDate,
                    rawText = rawText,
                    deletedAt = null,
                    syncState = SyncState.SYNCED,
                ),
            )
        }
    }

    private suspend fun upsertRemoteTransaction(localBookId: Long, change: SyncChangeDto) {
        val remoteId = change.entityId
        val existing = database.transactionDao().getByRemoteId(localBookId, remoteId)
        val deletedAt = change.payload.stringValue("deleted_at")?.toInstantOrNull()
        if (change.operationType == "delete") {
            existing?.let {
                database.transactionDao().update(
                    it.copy(deletedAt = deletedAt ?: Instant.now(), syncState = SyncState.DELETED),
                )
            }
            return
        }

        val memberRemoteId = change.payload["member_id"]?.jsonPrimitive?.content ?: return
        val accountRemoteId = change.payload["account_id"]?.jsonPrimitive?.content ?: return
        val categoryRemoteId = change.payload["category_id"]?.jsonPrimitive?.content ?: return
        val memberId = database.memberDao().getByRemoteId(localBookId, memberRemoteId)?.id ?: return
        val accountId = database.accountDao().getByRemoteId(localBookId, accountRemoteId)?.id ?: return
        val categoryId = database.categoryDao().getByRemoteId(localBookId, categoryRemoteId)?.id ?: return
        val type = change.payload["type"]?.jsonPrimitive?.content?.toTransactionType() ?: return
        val amount = change.payload["amount"]?.jsonPrimitive?.doubleOrNull ?: return
        val occurredAt = change.payload.stringValue("occurred_at")?.toInstantOrNull() ?: Instant.now()
        val note = change.payload["note"]?.jsonPrimitive?.content ?: ""
        if (existing == null) {
            database.transactionDao().insert(
                TransactionEntity(
                    remoteId = remoteId,
                    bookId = localBookId,
                    memberId = memberId,
                    accountId = accountId,
                    categoryId = categoryId,
                    type = type,
                    amount = amount,
                    occurredAt = occurredAt,
                    note = note,
                    syncState = SyncState.SYNCED,
                ),
            )
        } else {
            database.transactionDao().update(
                existing.copy(
                    memberId = memberId,
                    accountId = accountId,
                    categoryId = categoryId,
                    type = type,
                    amount = amount,
                    occurredAt = occurredAt,
                    note = note,
                    deletedAt = null,
                    syncState = SyncState.SYNCED,
                ),
            )
        }
    }

    private fun SyncEntityType.pushPriority(): Int = when (this) {
        SyncEntityType.BOOK -> 0
        SyncEntityType.MEMBER -> 1
        SyncEntityType.ACCOUNT -> 2
        SyncEntityType.CATEGORY -> 3
        SyncEntityType.JOURNAL_ENTRY -> 4
        SyncEntityType.TRANSACTION -> 5
    }

    private fun SyncEntityType.apiName(): String = when (this) {
        SyncEntityType.BOOK -> "book"
        SyncEntityType.MEMBER -> "member"
        SyncEntityType.ACCOUNT -> "account"
        SyncEntityType.CATEGORY -> "category"
        SyncEntityType.JOURNAL_ENTRY -> "journal_entry"
        SyncEntityType.TRANSACTION -> "transaction"
    }

    private fun SyncOperationType.apiName(): String = when (this) {
        SyncOperationType.UPSERT -> "upsert"
        SyncOperationType.DELETE -> "delete"
    }

    private fun String.toTransactionType(): TransactionType = when (lowercase()) {
        "income" -> TransactionType.INCOME
        else -> TransactionType.EXPENSE
    }

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    private val kotlinx.serialization.json.JsonPrimitive.booleanOrNull: Boolean?
        get() = content.lowercase().let { value ->
            when (value) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }

    private val kotlinx.serialization.json.JsonPrimitive.doubleOrNull: Double?
        get() = content.toDoubleOrNull()

    private val kotlinx.serialization.json.JsonPrimitive.intOrNull: Int?
        get() = content.toIntOrNull()

    private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.content
}
