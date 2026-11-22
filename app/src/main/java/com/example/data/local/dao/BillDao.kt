package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entities.BillHeaderEntity
import com.example.data.local.entities.BillItemEntity
import com.example.data.local.entities.BillWithItems
import com.example.domain.model.BillStatus
import com.example.domain.model.CancellationReason
import com.example.domain.model.DriveUploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Transaction
    @Query("SELECT * FROM BillHeaders ORDER BY billDateMillis DESC")
    fun observeAllBillsWithItems(): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM BillHeaders WHERE billUuid = :billUuid LIMIT 1")
    fun observeBillWithItems(billUuid: String): Flow<BillWithItems?>

    @Transaction
    @Query("SELECT * FROM BillHeaders WHERE billUuid = :billUuid LIMIT 1")
    suspend fun getBillWithItemsOnce(billUuid: String): BillWithItems?

    @Transaction
    @Query("SELECT * FROM BillHeaders ORDER BY billDateMillis DESC")
    suspend fun getAllBillsWithItemsOnce(): List<BillWithItems>

    @Transaction
    @Query("SELECT * FROM BillHeaders WHERE farmerUuid = :farmerUuid ORDER BY billDateMillis DESC")
    fun observeBillsForFarmer(farmerUuid: String): Flow<List<BillWithItems>>

    @Query("SELECT COALESCE(MAX(sequenceNumber), 0) FROM BillHeaders WHERE procurementYear = :year AND deviceCode = :deviceCode")
    suspend fun getMaxSequenceForYear(year: Int, deviceCode: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHeader(header: BillHeaderEntity)

    @Update
    suspend fun updateHeader(header: BillHeaderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<BillItemEntity>)

    @Query("DELETE FROM BillItems WHERE billUuid = :billUuid")
    suspend fun deleteItemsForBill(billUuid: String)

    @Transaction
    suspend fun insertBillAtomic(header: BillHeaderEntity, items: List<BillItemEntity>) {
        insertHeader(header)
        insertItems(items)
    }

    @Transaction
    suspend fun updateBillAtomic(header: BillHeaderEntity, items: List<BillItemEntity>) {
        updateHeader(header)
        deleteItemsForBill(header.billUuid)
        insertItems(items)
    }

    @Query(
        """
        UPDATE BillHeaders
        SET billStatus = :status,
            cancellationReason = :reason,
            cancellationNotes = :notes,
            cancelledAtMillis = :cancelledAt,
            updatedAt = :cancelledAt
        WHERE billUuid = :billUuid
        """
    )
    suspend fun cancelBill(
        billUuid: String,
        status: BillStatus = BillStatus.CANCELLED,
        reason: CancellationReason,
        notes: String,
        cancelledAt: Long
    )

    @Query(
        """
        UPDATE BillHeaders
        SET pdfFilePath = :pdfPath,
            publicPdfDisplayPath = :publicPath,
            updatedAt = :updatedAt
        WHERE billUuid = :billUuid
        """
    )
    suspend fun updatePdfPaths(
        billUuid: String,
        pdfPath: String,
        publicPath: String,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE BillHeaders
        SET driveUploadStatus = :status,
            driveFileId = :driveFileId,
            updatedAt = :updatedAt
        WHERE billUuid = :billUuid
        """
    )
    suspend fun updateDriveUploadStatus(
        billUuid: String,
        status: DriveUploadStatus,
        driveFileId: String,
        updatedAt: Long
    )

    @Query("SELECT * FROM BillHeaders WHERE driveUploadStatus != 'UPLOADED' AND pdfFilePath != '' ORDER BY createdAt ASC")
    suspend fun getPendingUploadBills(): List<BillHeaderEntity>
}
