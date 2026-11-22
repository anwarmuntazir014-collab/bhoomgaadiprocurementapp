package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.*
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.RecordStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(private val repository: ProcurementRepository) : ViewModel() {

    val products: StateFlow<List<ProductEntity>> = repository.observeAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val locations: StateFlow<List<LocationEntity>> = repository.observeAllLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val godowns: StateFlow<List<GodownEntity>> = repository.observeAllGodowns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blocks: StateFlow<List<BlockEntity>> = repository.observeAllBlocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clusters: StateFlow<List<ClusterEntity>> = repository.observeAllClusters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val villages: StateFlow<List<VillageEntity>> = repository.observeAllVillages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prefs get() = repository.prefs

    fun saveCenterSettings(
        deviceCode: String,
        procurementYear: Int,
        authorizedSignatory: String,
        membershipFeeInr: Double,
        driveFolderId: String,
        driveAccessToken: String,
        autoQueueDrive: Boolean
    ) {
        prefs.deviceCode = deviceCode
        prefs.procurementYear = procurementYear
        prefs.authorizedSignatory = authorizedSignatory
        prefs.membershipFeeInr = membershipFeeInr
        prefs.driveRootFolderId = driveFolderId
        prefs.driveAccessToken = driveAccessToken
        prefs.autoQueueDriveUpload = autoQueueDrive
    }

    fun addOrUpdateProduct(
        existingId: String? = null,
        name: String,
        ratePerKg: Double,
        status: RecordStatus = RecordStatus.ACTIVE
    ) {
        if (name.isBlank() || ratePerKg <= 0.0) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextOrder = (products.value.maxOfOrNull { it.displayOrder } ?: 0) + 1
            repository.upsertProduct(
                ProductEntity(
                    productId = existingId ?: "prod_${UUID.randomUUID().toString().take(8)}",
                    stockItemName = name.trim(),
                    ratePerKg = ratePerKg,
                    procurementYear = prefs.procurementYear,
                    displayOrder = existingId?.let { id ->
                        products.value.find { it.productId == id }?.displayOrder
                    } ?: nextOrder,
                    status = status,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun toggleProductStatus(product: ProductEntity) {
        viewModelScope.launch {
            val next = if (product.status == RecordStatus.ACTIVE) RecordStatus.INACTIVE else RecordStatus.ACTIVE
            repository.upsertProduct(product.copy(status = next, updatedAt = System.currentTimeMillis()))
        }
    }

    fun addLocation(locationName: String) {
        if (locationName.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.upsertLocation(
                LocationEntity(
                    locationId = "loc_${UUID.randomUUID().toString().take(8)}",
                    locationName = locationName.trim(),
                    status = RecordStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun addGodown(locationId: String, godownName: String) {
        if (locationId.isBlank() || godownName.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.upsertGodown(
                GodownEntity(
                    godownId = "gdw_${UUID.randomUUID().toString().take(8)}",
                    locationId = locationId,
                    godownName = godownName.trim(),
                    status = RecordStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun addBlock(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addBlock(name) }
    }

    fun addCluster(blockId: String, name: String) {
        if (blockId.isBlank() || name.isBlank()) return
        viewModelScope.launch { repository.addCluster(blockId, name) }
    }

    fun addVillage(clusterId: String, name: String) {
        if (clusterId.isBlank() || name.isBlank()) return
        viewModelScope.launch { repository.addVillage(clusterId, name) }
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
    }
}
