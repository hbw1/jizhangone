package com.bowe.localledger

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bowe.localledger.data.LedgerRepository
import com.bowe.localledger.data.local.AppDatabase

class LocalLedgerApp : Application() {
    lateinit var repository: LedgerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "local-ledger.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
        repository = LedgerRepository(database)
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
    }
}
