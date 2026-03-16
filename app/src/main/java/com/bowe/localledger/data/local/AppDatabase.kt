package com.bowe.localledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bowe.localledger.data.local.dao.AccountDao
import com.bowe.localledger.data.local.dao.BookDao
import com.bowe.localledger.data.local.dao.CategoryDao
import com.bowe.localledger.data.local.dao.JournalEntryDao
import com.bowe.localledger.data.local.dao.MemberDao
import com.bowe.localledger.data.local.dao.PendingSyncOperationDao
import com.bowe.localledger.data.local.dao.TransactionDao
import com.bowe.localledger.data.local.entity.AccountEntity
import com.bowe.localledger.data.local.entity.BookEntity
import com.bowe.localledger.data.local.entity.CategoryEntity
import com.bowe.localledger.data.local.entity.JournalEntryEntity
import com.bowe.localledger.data.local.entity.MemberEntity
import com.bowe.localledger.data.local.entity.PendingSyncOperationEntity
import com.bowe.localledger.data.local.entity.TransactionEntity

@Database(
    entities = [
        BookEntity::class,
        JournalEntryEntity::class,
        MemberEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        PendingSyncOperationEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun memberDao(): MemberDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun pendingSyncOperationDao(): PendingSyncOperationDao
}
