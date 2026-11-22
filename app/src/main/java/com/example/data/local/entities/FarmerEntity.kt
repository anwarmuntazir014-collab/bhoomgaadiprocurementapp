package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.*

@Entity(
    tableName = "Farmers",
    indices = [
        Index(value = ["aadhaarNumber"], unique = true),
        Index(value = ["farmerName"]),
        Index(value = ["village"])
    ]
)
data class FarmerEntity(
    @PrimaryKey val farmerUuid: String,
    val aadhaarNumber: String,
    val farmerName: String,
    val fatherOrHusbandName: String,
    val mobileNumber: String = "",
    val village: String,
    val block: String,
    val cluster: String,
    val shgName: String,
    val membershipStatus: MembershipStatus,
    val bmgdMemberCode: String,
    val bankAccountType: BankAccountType,
    val relativeRelationship: RelativeRelationship?,
    val accountHolderName: String,
    val bankName: String,
    val fullAccountNumber: String,
    val ifsc: String,
    val branch: String,
    val farmerPhotoPath: String = "",
    val idDocPhotoPath: String,
    val bankPassbookPhotoPath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceId: String,
    val syncStatus: SyncStatus
)
