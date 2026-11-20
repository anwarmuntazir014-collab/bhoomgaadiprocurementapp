package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity)

    @Query("SELECT * FROM AuditLogs ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 50): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM AuditLogs WHERE entityId = :entityId ORDER BY timestampMillis DESC")
    fun observeLogsForEntity(entityId: String): Flow<List<AuditLogEntity>>
}
