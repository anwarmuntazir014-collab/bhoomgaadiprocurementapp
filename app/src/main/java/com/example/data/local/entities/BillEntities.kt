package com.example.data.local.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.domain.model.*

@Entity(
    tableName = "BillHeaders",
    foreignKeys = [
        ForeignKey(
            entity = FarmerEntity::class,
            parentColumns = ["farmerUuid"],
            childColumns = ["farmerUuid"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["billNumber"], unique = true),
        Index(value = ["farmerUuid"]),
        Index(value = ["billDateMillis"]),
        Index(value = ["billStatus"])
    ]
)
data class BillHeaderEntity(
    @PrimaryKey val billUuid: String,
    val billNumber: String,
    val sequenceNumber: Int,
    val procurementYear: Int,
    val deviceCode: String,
    val billDateMillis: Long,
    val billDateIso: String,
    val farmerUuid: String,
    val farmerNameSnapshot: String,
    val farmerAadhaarSnapshot: String,
    val farmerVillageSnapshot: String,
    val farmerBlockSnapshot: String,
    val farmerMembershipSnapshot: MembershipStatus,
    val farmerMemberCodeSnapshot: String,
    val bankAccountTypeSnapshot: BankAccountType,
    val accountHolderSnapshot: String,
    val bankNameSnapshot: String,
    val accountNumberSnapshot: String,
    val ifscSnapshot: String,
    val locationId: String,
    val locationNameSnapshot: String,
    val godownId: String,
    val godownNameSnapshot: String,
    val totalBags: Int,
    val totalQuantityKg: Double,
    val procurementAmountInr: Double = 0.0,
    val membershipEnrolled: Boolean = false,
    val membershipAmountInr: Double = 0.0,
    val totalAmountInr: Double,
    val weighingSlipRef: String,
    val remarks: String,
    val operatorName: String,
    val authorizedSignatory: String,
    val billStatus: BillStatus,
    val revisionNumber: Int = 0,
    val lastEditedAtMillis: Long? = null,
    val lastEditedBy: String = "",
    val lastEditReason: String = "",
    val cancellationReason: CancellationReason?,
    val cancellationNotes: String,
    val cancelledAtMillis: Long?,
    val pdfFilePath: String,
    val publicPdfDisplayPath: String = "",
    val driveUploadStatus: DriveUploadStatus,
    val driveFileId: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "BillItems",
    foreignKeys = [
        ForeignKey(
            entity = BillHeaderEntity::class,
            parentColumns = ["billUuid"],
            childColumns = ["billUuid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["billUuid"]),
        Index(value = ["productId"])
    ]
)
data class BillItemEntity(
    @PrimaryKey val billItemUuid: String,
    val billUuid: String,
    val lineOrder: Int,
    val productId: String,
    val stockItemNameSnapshot: String,
    val bagCount: Int,
    val weightPerBagKg: Double = 0.0,
    val grossWeightKg: Double,
    val tareWeightKg: Double,
    val netQuantityKg: Double,
    val ratePerKg: Double,
    val lineAmountInr: Double,
    val qualityGrade: String,
    val moisturePercent: String
)

@Entity(
    tableName = "AuditLogs",
    indices = [Index(value = ["timestampMillis"]), Index(value = ["entityId"])]
)
data class AuditLogEntity(
    @PrimaryKey val auditId: String,
    val actionType: AuditActionType,
    val entityType: String,
    val entityId: String,
    val summary: String,
    val performedBy: String,
    val deviceCode: String,
    val timestampMillis: Long
)

data class BillWithItems(
    @Embedded val header: BillHeaderEntity,
    @Relation(
        parentColumn = "billUuid",
        entityColumn = "billUuid"
    )
    val items: List<BillItemEntity>
)
