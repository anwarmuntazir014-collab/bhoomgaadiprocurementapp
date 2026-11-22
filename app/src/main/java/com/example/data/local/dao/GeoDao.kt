package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.BlockEntity
import com.example.data.local.entities.ClusterEntity
import com.example.data.local.entities.VillageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoDao {
    // Blocks
    @Query("SELECT * FROM Blocks ORDER BY blockName ASC")
    fun observeAllBlocks(): Flow<List<BlockEntity>>

    @Query("SELECT * FROM Blocks WHERE LOWER(blockName) = LOWER(:name) LIMIT 1")
    suspend fun findBlockByName(name: String): BlockEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlock(block: BlockEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlocks(blocks: List<BlockEntity>)

    // Clusters
    @Query("SELECT * FROM Clusters ORDER BY clusterName ASC")
    fun observeAllClusters(): Flow<List<ClusterEntity>>

    @Query("SELECT * FROM Clusters WHERE blockId = :blockId ORDER BY clusterName ASC")
    fun observeClustersForBlock(blockId: String): Flow<List<ClusterEntity>>

    @Query("SELECT * FROM Clusters WHERE blockId = :blockId AND LOWER(clusterName) = LOWER(:name) LIMIT 1")
    suspend fun findClusterByName(blockId: String, name: String): ClusterEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCluster(cluster: ClusterEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClusters(clusters: List<ClusterEntity>)

    // Villages
    @Query("SELECT * FROM Villages ORDER BY villageName ASC")
    fun observeAllVillages(): Flow<List<VillageEntity>>

    @Query("SELECT * FROM Villages WHERE clusterId = :clusterId ORDER BY villageName ASC")
    fun observeVillagesForCluster(clusterId: String): Flow<List<VillageEntity>>

    @Query("SELECT * FROM Villages WHERE clusterId = :clusterId AND LOWER(villageName) = LOWER(:name) LIMIT 1")
    suspend fun findVillageByName(clusterId: String, name: String): VillageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVillage(village: VillageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVillages(villages: List<VillageEntity>)
}
