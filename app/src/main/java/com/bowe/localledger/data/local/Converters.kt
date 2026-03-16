package com.bowe.localledger.data.local

import androidx.room.TypeConverter
import com.bowe.localledger.data.local.entity.SyncEntityType
import com.bowe.localledger.data.local.entity.SyncOperationStatus
import com.bowe.localledger.data.local.entity.SyncOperationType
import com.bowe.localledger.data.local.entity.SyncState
import com.bowe.localledger.data.local.entity.TransactionType
import java.time.Instant

class Converters {
    @TypeConverter
    fun instantToEpoch(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun syncEntityTypeToString(value: SyncEntityType): String = value.name

    @TypeConverter
    fun stringToSyncEntityType(value: String): SyncEntityType = SyncEntityType.valueOf(value)

    @TypeConverter
    fun syncOperationTypeToString(value: SyncOperationType): String = value.name

    @TypeConverter
    fun stringToSyncOperationType(value: String): SyncOperationType = SyncOperationType.valueOf(value)

    @TypeConverter
    fun syncOperationStatusToString(value: SyncOperationStatus): String = value.name

    @TypeConverter
    fun stringToSyncOperationStatus(value: String): SyncOperationStatus = SyncOperationStatus.valueOf(value)
}
