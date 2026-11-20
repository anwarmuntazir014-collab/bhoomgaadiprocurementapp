package com.example.data.repository

import android.content.Context
import com.example.data.local.AppPreferences
import com.example.data.local.BhoomgaadiDatabase
import com.example.data.local.entities.*
import com.example.domain.model.*
import com.example.pdf.BillPdfGenerator
import com.example.sync.DriveUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ProcurementRepository(
    private val context: Context,
    private val dbProvider: () -> BhoomgaadiDatabase,
    val prefs: AppPreferences
) {
    private val db: BhoomgaadiDatabase get() = dbProvider()

    // Farmers
    fun observeAllFarmers(): Flow<List<FarmerEntity>> = db.farmerDao().observeAllFarmers()
    fun searchFarmers(query: String): Flow<List<FarmerEntity>> =
        if (query.isBlank()) db.farmerDao().observeAllFarmers() else db.farmerDao().searchFarmers(query.trim())

    fun observeFarmerCount(): Flow<Int> = db.farmerDao().observeFarmerCount()
    suspend fun getFarmerByUuid(uuid: String): FarmerEntity? = db.farmerDao().getFarmerByUuid(uuid)
    suspend fun getFarmerByAadhaar(aadhaar: String): FarmerEntity? = db.farmerDao().getFarmerByAadhaar(aadhaar)

    suspend fun saveFarmer(farmer: FarmerEntity, isUpdate: Boolean): Result<FarmerEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanedAadhaar = farmer.aadhaarNumber.filter { it.isDigit() }
            if (cleanedAadhaar.length != 12) {
                throw IllegalArgumentException("Aadhaar number must be exactly 12 digits.")
            }
            val existingByAadhaar = db.farmerDao().getFarmerByAadhaar(cleanedAadhaar)
            if (existingByAadhaar != null && existingByAadhaar.farmerUuid != farmer.farmerUuid) {
                throw IllegalStateException("A farmer with Aadhaar $cleanedAadhaar is already registered (${existingByAadhaar.farmerName}).")
            }

            val now = System.currentTimeMillis()
            val normalized = farmer.copy(
                aadhaarNumber = cleanedAadhaar,
                ifsc = farmer.ifsc.trim().uppercase(),
                updatedAt = now,
                deviceId = prefs.deviceCode
            )

            if (isUpdate) {
                val previous = db.farmerDao().getFarmerByUuid(farmer.farmerUuid)
                db.farmerDao().updateFarmer(normalized)
                val bankChanged = previous != null && (
                    previous.fullAccountNumber != normalized.fullAccountNumber ||
                        previous.ifsc != normalized.ifsc ||
                        previous.bankAccountType != normalized.bankAccountType
                    )
                logAudit(
                    actionType = if (bankChanged) AuditActionType.BANK_DETAILS_UPDATED else AuditActionType.FARMER_UPDATED,
                    entityType = "Farmer",
                    entityId = normalized.farmerUuid,
                    summary = "${normalized.farmerName} (${normalized.village}) updated"
                )
            } else {
                db.farmerDao().insertFarmer(normalized.copy(createdAt = now))
                logAudit(
                    actionType = AuditActionType.FARMER_ADDED,
                    entityType = "Farmer",
                    entityId = normalized.farmerUuid,
                    summary = "${normalized.farmerName} registered from ${normalized.village}"
                )
            }
            normalized
        }
    }

    // Master Data
    fun observeAllProducts(): Flow<List<ProductEntity>> = db.masterDao().observeAllProducts()
    fun observeActiveProducts(): Flow<List<ProductEntity>> = db.masterDao().observeProductsByStatus(RecordStatus.ACTIVE)
    fun observeAllLocations(): Flow<List<LocationEntity>> = db.masterDao().observeAllLocations()
    fun observeActiveLocations(): Flow<List<LocationEntity>> = db.masterDao().observeActiveLocations(RecordStatus.ACTIVE)
    fun observeAllGodowns(): Flow<List<GodownEntity>> = db.masterDao().observeAllGodowns()
    fun observeActiveGodownsForLocation(locationId: String): Flow<List<GodownEntity>> =
        db.masterDao().observeActiveGodownsForLocation(locationId, RecordStatus.ACTIVE)

    suspend fun upsertProduct(product: ProductEntity) = withContext(Dispatchers.IO) {
        db.masterDao().upsertProduct(product)
    }

    suspend fun upsertLocation(location: LocationEntity) = withContext(Dispatchers.IO) {
        db.masterDao().upsertLocation(location)
    }

    suspend fun upsertGodown(godown: GodownEntity) = withContext(Dispatchers.IO) {
        db.masterDao().upsertGodown(godown)
    }

    // Bills
    fun observeAllBills(): Flow<List<BillWithItems>> = db.billDao().observeAllBillsWithItems()
    fun observeBill(billUuid: String): Flow<BillWithItems?> = db.billDao().observeBillWithItems(billUuid)
    fun observeBillsForFarmer(farmerUuid: String): Flow<List<BillWithItems>> = db.billDao().observeBillsForFarmer(farmerUuid)
    fun observeAuditLogs(limit: Int = 50): Flow<List<AuditLogEntity>> = db.auditLogDao().observeRecentLogs(limit)
    fun observeAuditLogsForEntity(entityId: String): Flow<List<AuditLogEntity>> = db.auditLogDao().observeLogsForEntity(entityId)

    suspend fun createBillFromDraft(draft: BillDraft): Result<BillWithItems> = withContext(Dispatchers.IO) {
        runCatching {
            val farmer = draft.selectedFarmer ?: throw IllegalArgumentException("Please select a farmer.")
            val location = draft.selectedLocation ?: throw IllegalArgumentException("Please select a procurement location.")
            val godown = draft.selectedGodown ?: throw IllegalArgumentException("Please select a temporary godown.")
            if (draft.lines.isEmpty()) throw IllegalArgumentException("Please add at least one produce item.")

            val now = System.currentTimeMillis()
            val year = prefs.procurementYear
            val device = prefs.deviceCode
            val maxDbSeq = db.billDao().getMaxSequenceForYear(year, device)
            val prefSeq = prefs.getLastBillSequence(year)
            val nextSeq = maxOf(maxDbSeq, prefSeq) + 1
            val billNumber = prefs.formatBillNumber(device, year, nextSeq)
            val billUuid = UUID.randomUUID().toString()
            val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

            val header = BillHeaderEntity(
                billUuid = billUuid,
                billNumber = billNumber,
                sequenceNumber = nextSeq,
                procurementYear = year,
                deviceCode = device,
                billDateMillis = now,
                billDateIso = dateIso,
                farmerUuid = farmer.farmerUuid,
                farmerNameSnapshot = farmer.farmerName,
                farmerAadhaarSnapshot = farmer.aadhaarNumber,
                farmerVillageSnapshot = farmer.village,
                farmerBlockSnapshot = farmer.block,
                farmerMembershipSnapshot = farmer.membershipStatus,
                farmerMemberCodeSnapshot = farmer.bmgdMemberCode,
                bankAccountTypeSnapshot = farmer.bankAccountType,
                accountHolderSnapshot = farmer.accountHolderName,
                bankNameSnapshot = farmer.bankName,
                accountNumberSnapshot = farmer.fullAccountNumber,
                ifscSnapshot = farmer.ifsc,
                locationId = location.locationId,
                locationNameSnapshot = location.locationName,
                godownId = godown.godownId,
                godownNameSnapshot = godown.godownName,
                totalBags = draft.totalBags,
                totalQuantityKg = draft.totalQuantityKg,
                totalAmountInr = draft.grandTotalAmount,
                weighingSlipRef = draft.weighingSlipRef.trim(),
                remarks = draft.remarks.trim(),
                operatorName = prefs.operatorName,
                authorizedSignatory = prefs.authorizedSignatory,
                billStatus = BillStatus.CONFIRMED,
                cancellationReason = null,
                cancellationNotes = "",
                cancelledAtMillis = null,
                pdfFilePath = "",
                driveUploadStatus = DriveUploadStatus.PENDING,
                driveFileId = "",
                createdAt = now,
                updatedAt = now
            )

            val items = draft.lines.mapIndexed { index, line ->
                BillItemEntity(
                    billItemUuid = UUID.randomUUID().toString(),
                    billUuid = billUuid,
                    lineOrder = index + 1,
                    productId = line.product.productId,
                    stockItemNameSnapshot = line.product.stockItemName,
                    bagCount = line.bagCount,
                    grossWeightKg = line.grossWeightKg,
                    tareWeightKg = line.tareWeightKg,
                    netQuantityKg = line.netQuantityKg,
                    ratePerKg = line.ratePerKg,
                    lineAmountInr = line.lineAmount,
                    qualityGrade = line.qualityGrade,
                    moisturePercent = line.moisturePercent
                )
            }

            db.billDao().insertBillAtomic(header, items)
            prefs.setLastBillSequence(year, nextSeq)

            // Generate official PDF immediately offline
            val createdBill = BillWithItems(header, items)
            val pdfFile = BillPdfGenerator.generateBillPdf(context, createdBill)
            db.billDao().updatePdfPath(billUuid, pdfFile.absolutePath, System.currentTimeMillis())

            logAudit(
                actionType = AuditActionType.BILL_CREATED,
                entityType = "Bill",
                entityId = billUuid,
                summary = "Bill $billNumber for ${farmer.farmerName} — ${String.format(Locale.US, "%.2f", draft.totalQuantityKg)} kg (Rs. ${String.format(Locale.US, "%.2f", draft.grandTotalAmount)})"
            )

            if (prefs.autoQueueDriveUpload) {
                DriveUploadWorker.enqueue(context)
            }

            createdBill.copy(header = header.copy(pdfFilePath = pdfFile.absolutePath))
        }
    }

    suspend fun cancelBill(
        billUuid: String,
        reason: CancellationReason,
        notes: String
    ): Result<BillWithItems> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            db.billDao().cancelBill(
                billUuid = billUuid,
                reason = reason,
                notes = notes.trim(),
                cancelledAt = now
            )
            val updated = db.billDao().getBillWithItemsOnce(billUuid)
                ?: throw IllegalStateException("Bill not found.")
            // Regenerate PDF with VOID watermark
            val pdfFile = BillPdfGenerator.generateBillPdf(context, updated)
            db.billDao().updatePdfPath(billUuid, pdfFile.absolutePath, System.currentTimeMillis())

            logAudit(
                actionType = AuditActionType.BILL_CANCELLED,
                entityType = "Bill",
                entityId = billUuid,
                summary = "Bill ${updated.header.billNumber} voided: ${reason.label}"
            )
            updated.copy(header = updated.header.copy(pdfFilePath = pdfFile.absolutePath))
        }
    }

    suspend fun regeneratePdf(billUuid: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bill = db.billDao().getBillWithItemsOnce(billUuid)
                ?: throw IllegalStateException("Bill not found.")
            val file = BillPdfGenerator.generateBillPdf(context, bill)
            db.billDao().updatePdfPath(billUuid, file.absolutePath, System.currentTimeMillis())
            file.absolutePath
        }
    }

    // Dashboard
    fun observeDashboardSummary(year: Int): Flow<DashboardSummary> = db.dashboardDao().observeSummary(year)
    fun observeProductBreakdown(year: Int): Flow<List<ProductBreakdownItem>> = db.dashboardDao().observeProductBreakdown(year)
    fun observeLocationBreakdown(year: Int): Flow<List<LocationBreakdownItem>> = db.dashboardDao().observeLocationBreakdown(year)
    fun observeRecentDailyTotals(year: Int): Flow<List<DailyProcurementItem>> = db.dashboardDao().observeRecentDailyTotals(year)

    private suspend fun logAudit(
        actionType: AuditActionType,
        entityType: String,
        entityId: String,
        summary: String
    ) {
        db.auditLogDao().insertLog(
            AuditLogEntity(
                auditId = UUID.randomUUID().toString(),
                actionType = actionType,
                entityType = entityType,
                entityId = entityId,
                summary = summary,
                performedBy = prefs.operatorName,
                deviceCode = prefs.deviceCode,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }
}
