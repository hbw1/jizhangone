package com.bowe.localledger

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bowe.localledger.data.LedgerRepository
import com.bowe.localledger.data.local.AppDatabase
import com.bowe.localledger.data.remote.CloudAuthRepository
import com.bowe.localledger.data.remote.CloudSyncRepository
import com.bowe.localledger.data.remote.NetworkSettingsStore
import com.bowe.localledger.data.remote.RemoteLedgerDataSource
import com.bowe.localledger.data.remote.SyncCursorStore
import com.bowe.localledger.data.remote.TokenStore

class LocalLedgerApp : Application() {
    lateinit var repository: LedgerRepository
        private set
    lateinit var cloudAuthRepository: CloudAuthRepository
        private set
    lateinit var cloudSyncRepository: CloudSyncRepository
        private set
    lateinit var networkSettingsStore: NetworkSettingsStore
        private set
    lateinit var remoteDataSource: RemoteLedgerDataSource
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "local-ledger.db",
        )
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .build()
        repository = LedgerRepository(database)
        val tokenStore = TokenStore(applicationContext)
        val syncCursorStore = SyncCursorStore(applicationContext)
        networkSettingsStore = NetworkSettingsStore(applicationContext)
        remoteDataSource = RemoteLedgerDataSource(networkSettingsStore)
        cloudAuthRepository = CloudAuthRepository(
            tokenStore = tokenStore,
            remoteDataSource = remoteDataSource,
        )
        cloudSyncRepository = CloudSyncRepository(
            database = database,
            tokenStore = tokenStore,
            cursorStore = syncCursorStore,
            remoteDataSource = remoteDataSource,
        )
    }

    private companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `journal_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `entryDate` INTEGER NOT NULL,
                        `rawText` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_bookId` ON `journal_entries` (`bookId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `remoteId` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `remoteId` TEXT")
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE `members` ADD COLUMN `remoteId` TEXT")
                db.execSQL("ALTER TABLE `members` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE `members` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `members` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `remoteId` TEXT")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE `categories` ADD COLUMN `remoteId` TEXT")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `remoteId` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_sync_operations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityLocalId` INTEGER NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `clientMutationId` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `retryCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_bookId` ON `pending_sync_operations` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_entityType_entityLocalId` ON `pending_sync_operations` (`entityType`, `entityLocalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_status` ON `pending_sync_operations` (`status`)")
            }
        }
    }
}
