package com.bowe.localledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bowe.localledger.data.local.entity.PendingSyncOperationEntity
import com.bowe.localledger.data.local.entity.SyncEntityType
import com.bowe.localledger.data.local.entity.SyncOperationStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncOperationDao {
    @Query(
        """
        SELECT * FROM pending_sync_operations
        WHERE bookId = :bookId AND status IN (:statuses)
        ORDER BY createdAt ASC
        """
    )
    suspend fun getByBookAndStatuses(
        bookId: Long,
        statuses: List<SyncOperationStatus>,
    ): List<PendingSyncOperationEntity>

    @Query(
        """
        SELECT * FROM pending_sync_operations
        WHERE status IN (:statuses)
        ORDER BY createdAt ASC
        """
    )
    fun observeByStatuses(statuses: List<SyncOperationStatus>): Flow<List<PendingSyncOperationEntity>>

    @Insert
    suspend fun insert(operation: PendingSyncOperationEntity): Long

    @Update
    suspend fun update(operation: PendingSyncOperationEntity)

    @Query("DELETE FROM pending_sync_operations WHERE id = :operationId")
    suspend fun deleteById(operationId: Long)

    @Query(
        """
        DELETE FROM pending_sync_operations
        WHERE entityType = :entityType
          AND entityLocalId = :entityLocalId
        """
    )
    suspend fun deleteByEntity(entityType: SyncEntityType, entityLocalId: Long)
}
