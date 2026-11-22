package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.UserRole

@Entity(
    tableName = "Users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val userUuid: String,
    val username: String,
    val displayName: String,
    val role: UserRole,
    val pinSalt: String,
    val pinHash: String,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "Blocks",
    indices = [Index(value = ["blockName"], unique = true)]
)
data class BlockEntity(
    @PrimaryKey val blockId: String,
    val blockName: String,
    val createdAt: Long
)

@Entity(
    tableName = "Clusters",
    foreignKeys = [
        ForeignKey(
            entity = BlockEntity::class,
            parentColumns = ["blockId"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["blockId"]),
        Index(value = ["blockId", "clusterName"], unique = true)
    ]
)
data class ClusterEntity(
    @PrimaryKey val clusterId: String,
    val blockId: String,
    val clusterName: String,
    val createdAt: Long
)

@Entity(
    tableName = "Villages",
    foreignKeys = [
        ForeignKey(
            entity = ClusterEntity::class,
            parentColumns = ["clusterId"],
            childColumns = ["clusterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["clusterId"]),
        Index(value = ["clusterId", "villageName"], unique = true)
    ]
)
data class VillageEntity(
    @PrimaryKey val villageId: String,
    val clusterId: String,
    val villageName: String,
    val createdAt: Long
)
