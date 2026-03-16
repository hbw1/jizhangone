package com.bowe.localledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class TransactionType {
    INCOME,
    EXPENSE,
}

enum class SyncState {
    LOCAL_ONLY,
    DIRTY,
    SYNCED,
    DELETED,
}

enum class SyncEntityType {
    BOOK,
    MEMBER,
    ACCOUNT,
    CATEGORY,
    JOURNAL_ENTRY,
    TRANSACTION,
}

enum class SyncOperationType {
    UPSERT,
    DELETE,
}

enum class SyncOperationStatus {
    PENDING,
    IN_FLIGHT,
    FAILED,
    COMPLETED,
}

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val name: String,
    val createdAt: Instant = Instant.now(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val deletedAt: Instant? = null,
    val version: Long = 1,
)

@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val bookId: Long,
    val entryDate: Instant,
    val rawText: String,
    val createdAt: Instant = Instant.now(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val deletedAt: Instant? = null,
    val version: Long = 1,
)

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val bookId: Long,
    val name: String,
    val active: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val deletedAt: Instant? = null,
    val version: Long = 1,
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val bookId: Long,
    val name: String,
    val initialBalance: Double = 0.0,
    val active: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val deletedAt: Instant? = null,
    val version: Long = 1,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val bookId: Long,
    val type: TransactionType,
    val name: String,
    val sortOrder: Int = 0,
    val active: Boolean = true,
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val deletedAt: Instant? = null,
    val version: Long = 1,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("bookId"), Index("memberId"), Index("accountId"), Index("categoryId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,
    val bookId: Long,
    val memberId: Long,
    val accountId: Long,
    val categoryId: Long,
    val type: TransactionType,
    val amount: Double,
    val occurredAt: Instant,
    val note: String = "",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val deletedAt: Instant? = null,
    val version: Long = 1,
)

@Entity(
    tableName = "pending_sync_operations",
    indices = [Index("bookId"), Index("entityType", "entityLocalId"), Index("status")],
)
data class PendingSyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val entityType: SyncEntityType,
    val entityLocalId: Long,
    val operationType: SyncOperationType,
    val payloadJson: String = "{}",
    val clientMutationId: String,
    val status: SyncOperationStatus = SyncOperationStatus.PENDING,
    val retryCount: Int = 0,
    val createdAt: Instant = Instant.now(),
)
