package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.RecordStatus

@Entity(tableName = "Products")
data class ProductEntity(
    @PrimaryKey val productId: String,
    val stockItemName: String,
    val ratePerKg: Double,
    val procurementYear: Int,
    val displayOrder: Int,
    val status: RecordStatus,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "ProcurementLocations",
    indices = [Index(value = ["locationName"], unique = true)]
)
data class LocationEntity(
    @PrimaryKey val locationId: String,
    val locationName: String,
    val status: RecordStatus,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "TemporaryGodowns",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["locationId"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["locationId"])]
)
data class GodownEntity(
    @PrimaryKey val godownId: String,
    val locationId: String,
    val godownName: String,
    val status: RecordStatus,
    val createdAt: Long,
    val updatedAt: Long
)
