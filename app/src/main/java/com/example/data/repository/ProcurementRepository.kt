package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppPreferences
import com.example.data.local.BhoomgaadiDatabase
import com.example.data.local.PinSecurity
import com.example.data.local.entities.*
import com.example.domain.model.*
import com.example.export.ExcelFarmerImporter
import com.example.export.FarmerImportSummary
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
    private val excelImporter = ExcelFarmerImporter(context, dbProvider)

    // ==================== AUTH & USERS ====================
    fun observeAllUsers(): Flow<List<UserEntity>> = db.userDao().observeAllUsers()
    fun observeActiveUsers(): Flow<List<UserEntity>> = db.userDao().observeActiveUsers()

    suspend fun hasAnyAdmin(): Boolean = withContext(Dispatchers.IO) {
        db.userDao().countActiveByRole(UserRole.ADMIN) > 0
    }

    suspend fun createInitialAdmin(username: String, displayName: String, pin: String): Result<UserEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (db.userDao().countActiveByRole(UserRole.ADMIN) > 0) {
                    throw IllegalStateException("An Admin account already exists.")
                }
                createUserInternal(
                    username = username,
                    displayName = displayName,
                    role = UserRole.ADMIN,
                    pin = pin,
                    actorName = displayName.ifBlank { username }
                )
            }
        }

    suspend fun authenticateUser(username: String, pin: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUser = username.trim()
            if (cleanUser.isBlank() || pin.length != 4) {
                throw IllegalArgumentException("Enter username and 4-digit PIN.")
            }
            val user = db.userDao().getUserByUsername(cleanUser)
                ?: throw IllegalArgumentException("Invalid username or PIN.")
            if (!user.isActive) {
                throw IllegalStateException("This user account has been disabled by Admin.")
            }
            if (!PinSecurity.verifyPin(pin, user.pinSalt, user.pinHash)) {
                throw IllegalArgumentException("Invalid username or PIN.")
            }
            prefs.operatorName = user.displayName.ifBlank { user.username }
            user
        }
    }

    suspend fun adminCreateUser(
        actor: UserEntity,
        username: String,
        displayName: String,
        role: UserRole,
        pin: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        runCatching {
            require(actor.role == UserRole.ADMIN) { "Only Admin can create user accounts." }
            createUserInternal(username, displayName, role, pin, actor.displayName)
        }
    }

    suspend fun adminResetUserPin(actor: UserEntity, targetUserUuid: String, newPin: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(actor.role == UserRole.ADMIN) { "Only Admin can reset user PINs." }
                require(newPin.length == 4 && newPin.all { it.isDigit() }) { "PIN must be exactly 4 digits." }
                val user = db.userDao().getUserByUuid(targetUserUuid)
                    ?: throw IllegalStateException("User not found.")
                val salt = PinSecurity.generateSaltHex()
                val hash = PinSecurity.hashPin(newPin, salt)
                db.userDao().updateUser(
                    user.copy(pinSalt = salt, pinHash = hash, updatedAt = System.currentTimeMillis())
                )
                logAudit(
                    actionType = AuditActionType.USER_MANAGED,
                    entityType = "User",
                    entityId = user.userUuid,
                    summary = "PIN reset for ${user.username} (${user.role.label})",
                    actorOverride = actor.displayName
                )
            }
        }

    suspend fun adminToggleUserActive(actor: UserEntity, targetUserUuid: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(actor.role == UserRole.ADMIN) { "Only Admin can manage users." }
                val user = db.userDao().getUserByUuid(targetUserUuid)
                    ?: throw IllegalStateException("User not found.")
                if (user.userUuid == actor.userUuid) {
                    throw IllegalStateException("You cannot disable your own active Admin account.")
                }
                val nextActive = !user.isActive
                db.userDao().updateUser(user.copy(isActive = nextActive, updatedAt = System.currentTimeMillis()))
                logAudit(
                    actionType = AuditActionType.USER_MANAGED,
                    entityType = "User",
                    entityId = user.userUuid,
                    summary = "${user.username} marked ${if (nextActive) "Active" else "Disabled"}",
                    actorOverride = actor.displayName
                )
            }
        }

    private suspend fun createUserInternal(
        username: String,
        displayName: String,
        role: UserRole,
        pin: String,
        actorName: String
    ): UserEntity {
        val cleanUser = username.trim()
        require(cleanUser.length >= 3) { "Username must be at least 3 characters." }
        require(pin.length == 4 && pin.all { it.isDigit() }) { "PIN must be exactly 4 digits." }
        if (db.userDao().getUserByUsername(cleanUser) != null) {
            throw IllegalStateException("Username '$cleanUser' already exists.")
        }
        val salt = PinSecurity.generateSaltHex()
        val hash = PinSecurity.hashPin(pin, salt)
        val now = System.currentTimeMillis()
        val entity = UserEntity(
            userUuid = UUID.randomUUID().toString(),
            username = cleanUser,
            displayName = displayName.trim().ifBlank { cleanUser },
            role = role,
            pinSalt = salt,
            pinHash = hash,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        db.userDao().insertUser(entity)
        logAudit(
            actionType = AuditActionType.USER_MANAGED,
            entityType = "User",
            entityId = entity.userUuid,
            summary = "Account created: ${entity.username} (${role.label})",
            actorOverride = actorName
        )
        return entity
    }

    // ==================== GEOGRAPHY CASCADE (Block -> Cluster -> Village) ====================
    fun observeAllBlocks(): Flow<List<BlockEntity>> = db.geoDao().observeAllBlocks()
    fun observeClustersForBlock(blockId: String): Flow<List<ClusterEntity>> = db.geoDao().observeClustersForBlock(blockId)
    fun observeVillagesForCluster(clusterId: String): Flow<List<VillageEntity>> = db.geoDao().observeVillagesForCluster(clusterId)
    fun observeAllClusters(): Flow<List<ClusterEntity>> = db.geoDao().observeAllClusters()
    fun observeAllVillages(): Flow<List<VillageEntity>> = db.geoDao().observeAllVillages()

    suspend fun addBlock(blockName: String): BlockEntity = withContext(Dispatchers.IO) {
        val clean = blockName.trim()
        val existing = db.geoDao().findBlockByName(clean)
        if (existing != null) return@withContext existing
        val block = BlockEntity("blk_${UUID.randomUUID().toString().take(8)}", clean, System.currentTimeMillis())
        db.geoDao().insertBlock(block)
        block
    }

    suspend fun addCluster(blockId: String, clusterName: String): ClusterEntity = withContext(Dispatchers.IO) {
        val clean = clusterName.trim()
        val existing = db.geoDao().findClusterByName(blockId, clean)
        if (existing != null) return@withContext existing
        val cluster = ClusterEntity("cls_${UUID.randomUUID().toString().take(8)}", blockId, clean, System.currentTimeMillis())
        db.geoDao().insertCluster(cluster)
        cluster
    }

    suspend fun addVillage(clusterId: String, villageName: String): VillageEntity = withContext(Dispatchers.IO) {
        val clean = villageName.trim()
        val existing = db.geoDao().findVillageByName(clusterId, clean)
        if (existing != null) return@withContext existing
        val village = VillageEntity("vil_${UUID.randomUUID().toString().take(8)}", clusterId, clean, System.currentTimeMillis())
        db.geoDao().insertVillage(village)
        village
    }

    // ==================== EXCEL MASTER DATA IMPORT ====================
    suspend fun importFarmersFromExcel(actor: UserEntity, uri: Uri): Result<FarmerImportSummary> =
        withContext(Dispatchers.IO) {
            if (actor.role != UserRole.ADMIN) {
                return@withContext Result.failure(SecurityException("Only Admin can import Master Data from Excel."))
            }
            val result = excelImporter.importFromUri(uri, prefs.deviceCode)
            result.onSuccess { summary ->
                logAudit(
                    actionType = AuditActionType.FARMERS_IMPORTED,
                    entityType = "FarmerMaster",
                    entityId = "excel_import",
                    summary = "Excel import: ${summary.addedCount} added, ${summary.updatedCount} updated, ${summary.blocksAdded} blocks, ${summary.clustersAdded} clusters, ${summary.villagesAdded} villages",
                    actorOverride = actor.displayName
                )
            }
            result
        }

    // ==================== FARMERS ====================
    fun observeAllFarmers(): Flow<List<FarmerEntity>> = db.farmerDao().observeAllFarmers()
    fun searchFarmers(query: String): Flow<List<FarmerEntity>> =
        if (query.isBlank()) db.farmerDao().observeAllFarmers() else db.farmerDao().searchFarmers(query.trim())

    fun observeFarmerCount(): Flow<Int> = db.farmerDao().observeFarmerCount()
    suspend fun getFarmerByUuid(uuid: String): FarmerEntity? = db.farmerDao().getFarmerByUuid(uuid)
    suspend fun getFarmerByAadhaar(aadhaar: String): FarmerEntity? =
        db.farmerDao().getFarmerByAadhaar(aadhaar.filter { it.isDigit() })
    suspend fun getFarmerByMemberCode(code: String): FarmerEntity? =
        db.farmerDao().getFarmerByMemberCode(code.trim())

    suspend fun saveFarmer(farmer: FarmerEntity, isUpdate: Boolean, actorName: String = prefs.operatorName): Result<FarmerEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanedAadhaar = farmer.aadhaarNumber.filter { it.isDigit() }
                if (cleanedAadhaar.length != 12) {
                    throw IllegalArgumentException("Aadhaar number must be exactly 12 digits.")
                }
                val existingByAadhaar = db.farmerDao().getFarmerByAadhaar(cleanedAadhaar)
                if (existingByAadhaar != null && existingByAadhaar.farmerUuid != farmer.farmerUuid) {
                    throw IllegalStateException("A farmer with Aadhaar $cleanedAadhaar is already registered (${existingByAadhaar.farmerName}).")
                }

                // Ensure selected Block -> Cluster -> Village exists in Geo tables
                if (farmer.block.isNotBlank()) {
                    val blk = addBlock(farmer.block)
                    val clsName = farmer.cluster.ifBlank { "General Cluster" }
                    val cls = addCluster(blk.blockId, clsName)
                    if (farmer.village.isNotBlank()) {
                        addVillage(cls.clusterId, farmer.village)
                    }
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
                        summary = "${normalized.farmerName} (${normalized.village}, ${normalized.block}) updated",
                        actorOverride = actorName
                    )
                } else {
                    db.farmerDao().insertFarmer(normalized.copy(createdAt = now))
                    logAudit(
                        actionType = AuditActionType.FARMER_ADDED,
                        entityType = "Farmer",
                        entityId = normalized.farmerUuid,
                        summary = "${normalized.farmerName} registered from ${normalized.village}, ${normalized.block}",
                        actorOverride = actorName
                    )
                }
                normalized
            }
        }

    // ==================== MASTER DATA ====================
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

    // ==================== BILLS (CREATE, AUDITED EDIT, CANCEL) ====================
    fun observeAllBills(): Flow<List<BillWithItems>> = db.billDao().observeAllBillsWithItems()
    fun observeBill(billUuid: String): Flow<BillWithItems?> = db.billDao().observeBillWithItems(billUuid)
    suspend fun getBillOnce(billUuid: String): BillWithItems? = db.billDao().getBillWithItemsOnce(billUuid)
    fun observeBillsForFarmer(farmerUuid: String): Flow<List<BillWithItems>> = db.billDao().observeBillsForFarmer(farmerUuid)
    fun observeAuditLogs(limit: Int = 50): Flow<List<AuditLogEntity>> = db.auditLogDao().observeRecentLogs(limit)
    fun observeAuditLogsForEntity(entityId: String): Flow<List<AuditLogEntity>> = db.auditLogDao().observeLogsForEntity(entityId)

    /**
     * Policy confirmed by user:
     * - Cancelled/Void bills cannot be edited.
     * - Admin can edit any finalized bill anytime.
     * - Procurement Officer can edit a finalized bill only on the same calendar day it was created.
     */
    fun canUserEditBill(bill: BillHeaderEntity, user: UserEntity?): Boolean {
        if (user == null || bill.billStatus == BillStatus.CANCELLED) return false
        if (user.role == UserRole.ADMIN) return true
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return bill.billDateIso == todayIso
    }

    suspend fun createOrUpdateBillFromDraft(draft: BillDraft, actor: UserEntity): Result<BillWithItems> =
        if (draft.isEditMode) updateBillFromDraft(draft, actor) else createBillFromDraft(draft, actor)

    suspend fun createBillFromDraft(draft: BillDraft, actor: UserEntity): Result<BillWithItems> =
        withContext(Dispatchers.IO) {
            runCatching {
                val farmer = draft.selectedFarmer ?: throw IllegalArgumentException("Please select a farmer.")
                val location = draft.selectedLocation ?: throw IllegalArgumentException("Please select a procurement location.")
                val godown = draft.selectedGodown ?: throw IllegalArgumentException("Please select a temporary godown.")
                if (draft.lines.isEmpty()) throw IllegalArgumentException("Please add at least one produce item.")

                val now = System.currentTimeMillis()
                val billMillis = draft.procurementDateMillis
                val year = prefs.procurementYear
                val device = prefs.deviceCode
                val maxDbSeq = db.billDao().getMaxSequenceForYear(year, device)
                val prefSeq = prefs.getLastBillSequence(year)
                val nextSeq = maxOf(maxDbSeq, prefSeq) + 1
                val billNumber = prefs.formatBillNumber(device, year, nextSeq)
                val billUuid = UUID.randomUUID().toString()
                val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(billMillis))

                val effectiveMembershipStatus = if (draft.membershipEnrolled) MembershipStatus.MEMBER else farmer.membershipStatus
                val header = BillHeaderEntity(
                    billUuid = billUuid,
                    billNumber = billNumber,
                    sequenceNumber = nextSeq,
                    procurementYear = year,
                    deviceCode = device,
                    billDateMillis = billMillis,
                    billDateIso = dateIso,
                    farmerUuid = farmer.farmerUuid,
                    farmerNameSnapshot = farmer.farmerName,
                    farmerAadhaarSnapshot = farmer.aadhaarNumber,
                    farmerVillageSnapshot = farmer.village,
                    farmerBlockSnapshot = farmer.block,
                    farmerMembershipSnapshot = effectiveMembershipStatus,
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
                    procurementAmountInr = draft.procurementAmountInr,
                    membershipEnrolled = draft.membershipEnrolled,
                    membershipAmountInr = draft.appliedMembershipAmount,
                    totalAmountInr = draft.grandTotalAmount,
                    weighingSlipRef = "",
                    remarks = draft.remarks.trim(),
                    operatorName = actor.displayName,
                    authorizedSignatory = prefs.authorizedSignatory,
                    billStatus = BillStatus.CONFIRMED,
                    revisionNumber = 0,
                    lastEditedAtMillis = null,
                    lastEditedBy = "",
                    lastEditReason = "",
                    cancellationReason = null,
                    cancellationNotes = "",
                    cancelledAtMillis = null,
                    pdfFilePath = "",
                    publicPdfDisplayPath = "",
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
                        weightPerBagKg = line.weightPerBagKg,
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

                // If Non-Member opted into Membership on this bill, update Farmer Master automatically
                if (draft.membershipEnrolled && farmer.membershipStatus == MembershipStatus.NON_MEMBER) {
                    db.farmerDao().updateFarmer(
                        farmer.copy(
                            membershipStatus = MembershipStatus.MEMBER,
                            updatedAt = now
                        )
                    )
                }

                val createdBill = BillWithItems(header, items)
                val pdfResult = BillPdfGenerator.generateAndSaveBillPdf(context, createdBill)
                db.billDao().updatePdfPaths(
                    billUuid = billUuid,
                    pdfPath = pdfResult.internalFile.absolutePath,
                    publicPath = pdfResult.publicDisplayPath,
                    updatedAt = System.currentTimeMillis()
                )

                logAudit(
                    actionType = AuditActionType.BILL_CREATED,
                    entityType = "Bill",
                    entityId = billUuid,
                    summary = "Bill $billNumber for ${farmer.farmerName} — ${String.format(Locale.US, "%.2f", draft.totalQuantityKg)} kg (Rs. ${String.format(Locale.US, "%.2f", draft.grandTotalAmount)})",
                    actorOverride = actor.displayName
                )

                if (prefs.autoQueueDriveUpload) {
                    DriveUploadWorker.enqueue(context)
                }

                createdBill.copy(
                    header = header.copy(
                        pdfFilePath = pdfResult.internalFile.absolutePath,
                        publicPdfDisplayPath = pdfResult.publicDisplayPath
                    )
                )
            }
        }

    suspend fun updateBillFromDraft(draft: BillDraft, actor: UserEntity): Result<BillWithItems> =
        withContext(Dispatchers.IO) {
            runCatching {
                val billUuid = draft.editingBillUuid ?: throw IllegalArgumentException("Missing bill reference for edit.")
                val existing = db.billDao().getBillWithItemsOnce(billUuid)
                    ?: throw IllegalStateException("Original bill not found.")
                if (!canUserEditBill(existing.header, actor)) {
                    throw SecurityException("Officers can edit bills only on the same day. Contact Admin to edit older bills.")
                }
                val reason = draft.editReason.trim()
                if (reason.length < 3) {
                    throw IllegalArgumentException("Please enter a short reason for editing this bill.")
                }
                if (draft.lines.isEmpty()) throw IllegalArgumentException("Bill must contain at least one item.")

                val location = draft.selectedLocation ?: throw IllegalArgumentException("Please select a location.")
                val godown = draft.selectedGodown ?: throw IllegalArgumentException("Please select a temporary godown.")
                val now = System.currentTimeMillis()
                val nextRevision = existing.header.revisionNumber + 1
                val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(draft.procurementDateMillis))

                val updatedHeader = existing.header.copy(
                    billDateMillis = draft.procurementDateMillis,
                    billDateIso = dateIso,
                    locationId = location.locationId,
                    locationNameSnapshot = location.locationName,
                    godownId = godown.godownId,
                    godownNameSnapshot = godown.godownName,
                    totalBags = draft.totalBags,
                    totalQuantityKg = draft.totalQuantityKg,
                    procurementAmountInr = draft.procurementAmountInr,
                    membershipEnrolled = draft.membershipEnrolled,
                    membershipAmountInr = draft.appliedMembershipAmount,
                    totalAmountInr = draft.grandTotalAmount,
                    weighingSlipRef = "",
                    remarks = draft.remarks.trim(),
                    revisionNumber = nextRevision,
                    lastEditedAtMillis = now,
                    lastEditedBy = actor.displayName,
                    lastEditReason = reason,
                    driveUploadStatus = DriveUploadStatus.PENDING,
                    updatedAt = now
                )

                val newItems = draft.lines.mapIndexed { index, line ->
                    BillItemEntity(
                        billItemUuid = UUID.randomUUID().toString(),
                        billUuid = billUuid,
                        lineOrder = index + 1,
                        productId = line.product.productId,
                        stockItemNameSnapshot = line.product.stockItemName,
                        bagCount = line.bagCount,
                        weightPerBagKg = line.weightPerBagKg,
                        grossWeightKg = line.grossWeightKg,
                        tareWeightKg = line.tareWeightKg,
                        netQuantityKg = line.netQuantityKg,
                        ratePerKg = line.ratePerKg,
                        lineAmountInr = line.lineAmount,
                        qualityGrade = line.qualityGrade,
                        moisturePercent = line.moisturePercent
                    )
                }

                db.billDao().updateBillAtomic(updatedHeader, newItems)

                val updatedBill = BillWithItems(updatedHeader, newItems)
                val pdfResult = BillPdfGenerator.generateAndSaveBillPdf(context, updatedBill)
                db.billDao().updatePdfPaths(
                    billUuid = billUuid,
                    pdfPath = pdfResult.internalFile.absolutePath,
                    publicPath = pdfResult.publicDisplayPath,
                    updatedAt = System.currentTimeMillis()
                )

                logAudit(
                    actionType = AuditActionType.BILL_EDITED,
                    entityType = "Bill",
                    entityId = billUuid,
                    summary = "Rev $nextRevision of ${updatedHeader.billNumber}: Rs. ${String.format(Locale.US, "%.2f", existing.header.totalAmountInr)} -> Rs. ${String.format(Locale.US, "%.2f", updatedHeader.totalAmountInr)} | Reason: $reason",
                    actorOverride = actor.displayName
                )

                updatedBill.copy(
                    header = updatedHeader.copy(
                        pdfFilePath = pdfResult.internalFile.absolutePath,
                        publicPdfDisplayPath = pdfResult.publicDisplayPath
                    )
                )
            }
        }

    suspend fun cancelBill(
        billUuid: String,
        reason: CancellationReason,
        notes: String,
        actor: UserEntity
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
            val pdfResult = BillPdfGenerator.generateAndSaveBillPdf(context, updated)
            db.billDao().updatePdfPaths(
                billUuid = billUuid,
                pdfPath = pdfResult.internalFile.absolutePath,
                publicPath = pdfResult.publicDisplayPath,
                updatedAt = System.currentTimeMillis()
            )

            logAudit(
                actionType = AuditActionType.BILL_CANCELLED,
                entityType = "Bill",
                entityId = billUuid,
                summary = "Bill ${updated.header.billNumber} voided by ${actor.displayName}: ${reason.label}",
                actorOverride = actor.displayName
            )
            updated.copy(
                header = updated.header.copy(
                    pdfFilePath = pdfResult.internalFile.absolutePath,
                    publicPdfDisplayPath = pdfResult.publicDisplayPath
                )
            )
        }
    }

    suspend fun regeneratePdf(billUuid: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bill = db.billDao().getBillWithItemsOnce(billUuid)
                ?: throw IllegalStateException("Bill not found.")
            val pdfResult = BillPdfGenerator.generateAndSaveBillPdf(context, bill)
            db.billDao().updatePdfPaths(
                billUuid = billUuid,
                pdfPath = pdfResult.internalFile.absolutePath,
                publicPath = pdfResult.publicDisplayPath,
                updatedAt = System.currentTimeMillis()
            )
            pdfResult.internalFile.absolutePath
        }
    }

    // ==================== DASHBOARD ====================
    fun observeDashboardSummary(fromMillis: Long, locationName: String): Flow<DashboardSummary> =
        db.dashboardDao().observeSummaryFiltered(fromMillis, locationName)

    fun observeProductBreakdown(fromMillis: Long, locationName: String): Flow<List<ProductBreakdownItem>> =
        db.dashboardDao().observeProductBreakdownFiltered(fromMillis, locationName)

    fun observeLocationBreakdown(fromMillis: Long, locationName: String): Flow<List<LocationBreakdownItem>> =
        db.dashboardDao().observeLocationBreakdownFiltered(fromMillis, locationName)

    fun observeRecentDailyTotals(fromMillis: Long, locationName: String): Flow<List<DailyProcurementItem>> =
        db.dashboardDao().observeRecentDailyTotalsFiltered(fromMillis, locationName)

    private suspend fun logAudit(
        actionType: AuditActionType,
        entityType: String,
        entityId: String,
        summary: String,
        actorOverride: String = prefs.operatorName
    ) {
        db.auditLogDao().insertLog(
            AuditLogEntity(
                auditId = UUID.randomUUID().toString(),
                actionType = actionType,
                entityType = entityType,
                entityId = entityId,
                summary = summary,
                performedBy = actorOverride,
                deviceCode = prefs.deviceCode,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }
}
