package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.GodownEntity
import com.example.data.local.entities.LocationEntity
import com.example.data.local.entities.ProductEntity
import com.example.domain.model.RecordStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MasterDao {
    // Products
    @Query("SELECT * FROM Products ORDER BY displayOrder ASC, stockItemName ASC")
    fun observeAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM Products WHERE status = :status ORDER BY displayOrder ASC, stockItemName ASC")
    fun observeProductsByStatus(status: RecordStatus = RecordStatus.ACTIVE): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    // Locations
    @Query("SELECT * FROM ProcurementLocations ORDER BY locationName ASC")
    fun observeAllLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM ProcurementLocations WHERE status = :status ORDER BY locationName ASC")
    fun observeActiveLocations(status: RecordStatus = RecordStatus.ACTIVE): Flow<List<LocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocation(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocations(locations: List<LocationEntity>)

    // Godowns
    @Query("SELECT * FROM TemporaryGodowns ORDER BY godownName ASC")
    fun observeAllGodowns(): Flow<List<GodownEntity>>

    @Query("SELECT * FROM TemporaryGodowns WHERE locationId = :locationId AND status = :status ORDER BY godownName ASC")
    fun observeActiveGodownsForLocation(
        locationId: String,
        status: RecordStatus = RecordStatus.ACTIVE
    ): Flow<List<GodownEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGodown(godown: GodownEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGodowns(godowns: List<GodownEntity>)
}
