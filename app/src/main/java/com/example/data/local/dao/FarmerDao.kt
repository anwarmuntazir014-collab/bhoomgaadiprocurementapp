package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.FarmerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmerDao {
    @Query("SELECT * FROM Farmers ORDER BY updatedAt DESC")
    fun observeAllFarmers(): Flow<List<FarmerEntity>>

    @Query(
        """
        SELECT * FROM Farmers
        WHERE farmerName LIKE '%' || :query || '%'
           OR aadhaarNumber LIKE '%' || :query || '%'
           OR village LIKE '%' || :query || '%'
           OR bmgdMemberCode LIKE '%' || :query || '%'
           OR shgName LIKE '%' || :query || '%'
        ORDER BY farmerName ASC
        """
    )
    fun searchFarmers(query: String): Flow<List<FarmerEntity>>

    @Query("SELECT * FROM Farmers WHERE farmerUuid = :uuid LIMIT 1")
    suspend fun getFarmerByUuid(uuid: String): FarmerEntity?

    @Query("SELECT * FROM Farmers WHERE aadhaarNumber = :aadhaar LIMIT 1")
    suspend fun getFarmerByAadhaar(aadhaar: String): FarmerEntity?

    @Query("SELECT COUNT(*) FROM Farmers")
    fun observeFarmerCount(): Flow<Int>

    @Query("SELECT * FROM Farmers ORDER BY farmerName ASC")
    suspend fun getAllFarmersOnce(): List<FarmerEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFarmer(farmer: FarmerEntity)

    @Update
    suspend fun updateFarmer(farmer: FarmerEntity)
}
