package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.domain.model.DailyProcurementItem
import com.example.domain.model.DashboardSummary
import com.example.domain.model.LocationBreakdownItem
import com.example.domain.model.ProductBreakdownItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardDao {
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN billStatus = 'CONFIRMED' THEN 1 ELSE 0 END), 0) AS totalBillsConfirmed,
            COALESCE(SUM(CASE WHEN billStatus = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS totalBillsCancelled,
            COUNT(DISTINCT CASE WHEN billStatus = 'CONFIRMED' THEN farmerUuid ELSE NULL END) AS totalFarmersProcured,
            COALESCE(SUM(CASE WHEN billStatus = 'CONFIRMED' THEN totalQuantityKg ELSE 0.0 END), 0.0) AS totalQuantityKg,
            COALESCE(SUM(CASE WHEN billStatus = 'CONFIRMED' THEN totalAmountInr ELSE 0.0 END), 0.0) AS totalAmountInr,
            COALESCE(SUM(CASE WHEN driveUploadStatus != 'UPLOADED' THEN 1 ELSE 0 END), 0) AS pendingUploads
        FROM BillHeaders
        WHERE billDateMillis >= :fromMillis
          AND (:locationName = '' OR locationNameSnapshot = :locationName)
        """
    )
    fun observeSummaryFiltered(fromMillis: Long, locationName: String): Flow<DashboardSummary>

    @Query(
        """
        SELECT
            bi.productId AS productId,
            bi.stockItemNameSnapshot AS stockItemName,
            COALESCE(SUM(bi.netQuantityKg), 0.0) AS totalQuantityKg,
            COALESCE(SUM(bi.lineAmountInr), 0.0) AS totalAmountInr,
            COUNT(bi.billItemUuid) AS lineCount
        FROM BillItems bi
        INNER JOIN BillHeaders bh ON bh.billUuid = bi.billUuid
        WHERE bh.billStatus = 'CONFIRMED'
          AND bh.billDateMillis >= :fromMillis
          AND (:locationName = '' OR bh.locationNameSnapshot = :locationName)
        GROUP BY bi.productId, bi.stockItemNameSnapshot
        ORDER BY totalQuantityKg DESC
        """
    )
    fun observeProductBreakdownFiltered(fromMillis: Long, locationName: String): Flow<List<ProductBreakdownItem>>

    @Query(
        """
        SELECT
            locationNameSnapshot AS locationName,
            COUNT(billUuid) AS billCount,
            COALESCE(SUM(totalQuantityKg), 0.0) AS totalQuantityKg,
            COALESCE(SUM(totalAmountInr), 0.0) AS totalAmountInr
        FROM BillHeaders
        WHERE billStatus = 'CONFIRMED'
          AND billDateMillis >= :fromMillis
          AND (:locationName = '' OR locationNameSnapshot = :locationName)
        GROUP BY locationNameSnapshot
        ORDER BY totalAmountInr DESC
        """
    )
    fun observeLocationBreakdownFiltered(fromMillis: Long, locationName: String): Flow<List<LocationBreakdownItem>>

    @Query(
        """
        SELECT
            billDateIso AS dayLabel,
            COUNT(billUuid) AS billCount,
            COALESCE(SUM(totalQuantityKg), 0.0) AS totalQuantityKg,
            COALESCE(SUM(totalAmountInr), 0.0) AS totalAmountInr
        FROM BillHeaders
        WHERE billStatus = 'CONFIRMED'
          AND billDateMillis >= :fromMillis
          AND (:locationName = '' OR locationNameSnapshot = :locationName)
        GROUP BY billDateIso
        ORDER BY billDateIso DESC
        LIMIT 14
        """
    )
    fun observeRecentDailyTotalsFiltered(fromMillis: Long, locationName: String): Flow<List<DailyProcurementItem>>
}
