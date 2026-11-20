package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.*

class Converters {
    @TypeConverter
    fun fromMembershipStatus(value: MembershipStatus): String = value.name

    @TypeConverter
    fun toMembershipStatus(value: String): MembershipStatus =
        runCatching { MembershipStatus.valueOf(value) }.getOrDefault(MembershipStatus.NON_MEMBER)

    @TypeConverter
    fun fromBankAccountType(value: BankAccountType): String = value.name

    @TypeConverter
    fun toBankAccountType(value: String): BankAccountType =
        runCatching { BankAccountType.valueOf(value) }.getOrDefault(BankAccountType.NONE)

    @TypeConverter
    fun fromRelativeRelationship(value: RelativeRelationship?): String? = value?.name

    @TypeConverter
    fun toRelativeRelationship(value: String?): RelativeRelationship? =
        value?.let { runCatching { RelativeRelationship.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromRecordStatus(value: RecordStatus): String = value.name

    @TypeConverter
    fun toRecordStatus(value: String): RecordStatus =
        runCatching { RecordStatus.valueOf(value) }.getOrDefault(RecordStatus.ACTIVE)

    @TypeConverter
    fun fromBillStatus(value: BillStatus): String = value.name

    @TypeConverter
    fun toBillStatus(value: String): BillStatus =
        runCatching { BillStatus.valueOf(value) }.getOrDefault(BillStatus.CONFIRMED)

    @TypeConverter
    fun fromCancellationReason(value: CancellationReason?): String? = value?.name

    @TypeConverter
    fun toCancellationReason(value: String?): CancellationReason? =
        value?.let { runCatching { CancellationReason.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus =
        runCatching { SyncStatus.valueOf(value) }.getOrDefault(SyncStatus.PENDING)

    @TypeConverter
    fun fromDriveUploadStatus(value: DriveUploadStatus): String = value.name

    @TypeConverter
    fun toDriveUploadStatus(value: String): DriveUploadStatus =
        runCatching { DriveUploadStatus.valueOf(value) }.getOrDefault(DriveUploadStatus.PENDING)

    @TypeConverter
    fun fromAuditActionType(value: AuditActionType): String = value.name

    @TypeConverter
    fun toAuditActionType(value: String): AuditActionType =
        runCatching { AuditActionType.valueOf(value) }.getOrDefault(AuditActionType.BILL_CREATED)
}
