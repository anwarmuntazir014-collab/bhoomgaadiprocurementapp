package com.example.ui.screens.farmer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.BlockEntity
import com.example.data.local.entities.ClusterEntity
import com.example.data.local.entities.FarmerEntity
import com.example.data.local.entities.VillageEntity
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.BankAccountType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.RelativeRelationship
import com.example.domain.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class FarmerSearchMode(val label: String) {
    AADHAAR("Aadhaar No."),
    FARMER_CODE("Bhoomgaadi Code"),
    FARMER_NAME("Farmer Name")
}

sealed interface AadhaarLookupState {
    data object Idle : AadhaarLookupState
    data class Found(val farmer: FarmerEntity) : AadhaarLookupState
    data class NotFound(val query: String, val mode: FarmerSearchMode = FarmerSearchMode.AADHAAR) : AadhaarLookupState
}

data class FarmerFormUiState(
    val farmerUuid: String = UUID.randomUUID().toString(),
    val isEditMode: Boolean = false,
    val step: Int = 0, // 0: Basic (Block->Cluster->Village), 1: Bank Details, 2: Document Photos, 3: Added Summary
    val aadhaarNumber: String = "",
    val farmerName: String = "",
    val fatherOrHusbandName: String = "",
    val mobileNumber: String = "",
    val selectedBlockId: String = "",
    val block: String = "",
    val selectedClusterId: String = "",
    val cluster: String = "",
    val village: String = "",
    val shgName: String = "",
    val membershipStatus: MembershipStatus = MembershipStatus.NON_MEMBER,
    val bmgdMemberCode: String = "",
    val bankAccountType: BankAccountType = BankAccountType.OWN,
    val relativeRelationship: RelativeRelationship? = null,
    val accountHolderName: String = "",
    val bankName: String = "",
    val fullAccountNumber: String = "",
    val confirmAccountNumber: String = "",
    val ifsc: String = "",
    val branch: String = "",
    val farmerPhotoPath: String = "",
    val idDocPhotoPath: String = "",
    val bankPassbookPhotoPath: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedFarmer: FarmerEntity? = null
)

class FarmerViewModel(private val repository: ProcurementRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _aadhaarLookup = MutableStateFlow<AadhaarLookupState>(AadhaarLookupState.Idle)
    val aadhaarLookup: StateFlow<AadhaarLookupState> = _aadhaarLookup.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val farmers: StateFlow<List<FarmerEntity>> = _searchQuery
        .flatMapLatest { query -> repository.searchFarmers(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _formState = MutableStateFlow(FarmerFormUiState())
    val formState: StateFlow<FarmerFormUiState> = _formState.asStateFlow()

    // Cascading Block -> Cluster -> Village flows
    val allBlocks: StateFlow<List<BlockEntity>> = repository.observeAllBlocks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val clustersForSelectedBlock: StateFlow<List<ClusterEntity>> = _formState
        .map { it.selectedBlockId }
        .distinctUntilChanged()
        .flatMapLatest { blockId ->
            if (blockId.isBlank()) flowOf(emptyList()) else repository.observeClustersForBlock(blockId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val villagesForSelectedCluster: StateFlow<List<VillageEntity>> = _formState
        .map { it.selectedClusterId }
        .distinctUntilChanged()
        .flatMapLatest { clusterId ->
            if (clusterId.isBlank()) flowOf(emptyList()) else repository.observeVillagesForCluster(clusterId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun lookupFarmerByAadhaar(rawAadhaar: String) {
        val digits = rawAadhaar.filter { it.isDigit() }
        if (digits.length != 12) return
        viewModelScope.launch {
            val found = repository.getFarmerByAadhaar(digits)
            _aadhaarLookup.value = if (found != null) {
                AadhaarLookupState.Found(found)
            } else {
                AadhaarLookupState.NotFound(digits, FarmerSearchMode.AADHAAR)
            }
        }
    }

    fun lookupFarmerByCode(rawCode: String) {
        val cleanCode = rawCode.trim()
        if (cleanCode.isBlank()) return
        viewModelScope.launch {
            val found = repository.getFarmerByMemberCode(cleanCode)
            _aadhaarLookup.value = if (found != null) {
                AadhaarLookupState.Found(found)
            } else {
                AadhaarLookupState.NotFound(cleanCode, FarmerSearchMode.FARMER_CODE)
            }
        }
    }

    fun selectFoundFarmer(farmer: FarmerEntity) {
        _aadhaarLookup.value = AadhaarLookupState.Found(farmer)
    }

    fun resetAadhaarLookup() {
        _aadhaarLookup.value = AadhaarLookupState.Idle
    }

    fun startNewFarmer(prefillAadhaar: String = "") {
        _formState.value = FarmerFormUiState(
            farmerUuid = UUID.randomUUID().toString(),
            isEditMode = false,
            step = 0,
            aadhaarNumber = prefillAadhaar.filter { it.isDigit() }.take(12)
        )
    }

    fun loadFarmerForEdit(farmerUuid: String) {
        viewModelScope.launch {
            val f = repository.getFarmerByUuid(farmerUuid) ?: return@launch
            val matchingBlock = allBlocks.value.find { it.blockName.equals(f.block, ignoreCase = true) }
            _formState.value = FarmerFormUiState(
                farmerUuid = f.farmerUuid,
                isEditMode = true,
                step = 0,
                aadhaarNumber = f.aadhaarNumber,
                farmerName = f.farmerName,
                fatherOrHusbandName = f.fatherOrHusbandName,
                mobileNumber = f.mobileNumber,
                selectedBlockId = matchingBlock?.blockId.orEmpty(),
                block = f.block,
                selectedClusterId = "",
                cluster = f.cluster,
                village = f.village,
                shgName = f.shgName,
                membershipStatus = f.membershipStatus,
                bmgdMemberCode = f.bmgdMemberCode,
                bankAccountType = f.bankAccountType,
                relativeRelationship = f.relativeRelationship,
                accountHolderName = f.accountHolderName,
                bankName = f.bankName,
                fullAccountNumber = f.fullAccountNumber,
                confirmAccountNumber = f.fullAccountNumber,
                ifsc = f.ifsc,
                branch = f.branch,
                farmerPhotoPath = f.farmerPhotoPath,
                idDocPhotoPath = f.idDocPhotoPath,
                bankPassbookPhotoPath = f.bankPassbookPhotoPath
            )
            // Resolve matching clusterId once blockId is known
            if (matchingBlock != null && f.cluster.isNotBlank()) {
                val clsList = repository.observeClustersForBlock(matchingBlock.blockId).first()
                val matchingCluster = clsList.find { it.clusterName.equals(f.cluster, ignoreCase = true) }
                if (matchingCluster != null) {
                    _formState.update { it.copy(selectedClusterId = matchingCluster.clusterId) }
                }
            }
        }
    }

    fun onSelectBlock(block: BlockEntity) {
        _formState.update {
            it.copy(
                selectedBlockId = block.blockId,
                block = block.blockName,
                selectedClusterId = "",
                cluster = "",
                village = "",
                errorMessage = null
            )
        }
    }

    fun onSelectCluster(cluster: ClusterEntity) {
        _formState.update {
            it.copy(
                selectedClusterId = cluster.clusterId,
                cluster = cluster.clusterName,
                village = "",
                errorMessage = null
            )
        }
    }

    fun onSelectVillage(village: VillageEntity) {
        _formState.update {
            it.copy(
                village = village.villageName,
                errorMessage = null
            )
        }
    }

    fun addCustomVillageForCurrentCluster(villageName: String) {
        val clusterId = _formState.value.selectedClusterId
        if (clusterId.isBlank() || villageName.isBlank()) return
        viewModelScope.launch {
            val v = repository.addVillage(clusterId, villageName)
            _formState.update { it.copy(village = v.villageName, errorMessage = null) }
        }
    }

    fun updateForm(transform: (FarmerFormUiState) -> FarmerFormUiState) {
        _formState.update { transform(it).copy(errorMessage = null) }
    }

    fun goToStep(nextStep: Int) {
        val s = _formState.value
        if (nextStep > s.step) {
            if (s.step == 0) {
                val err = validateBasicStep(s)
                if (err != null) {
                    _formState.update { it.copy(errorMessage = err) }
                    return
                }
            }
            if (s.step == 1) {
                val err = validateBankStep(s)
                if (err != null) {
                    _formState.update { it.copy(errorMessage = err) }
                    return
                }
            }
        }
        _formState.update { it.copy(step = nextStep.coerceIn(0, 3), errorMessage = null) }
    }

    private fun validateBasicStep(s: FarmerFormUiState): String? {
        val digitsAadhaar = s.aadhaarNumber.filter { it.isDigit() }
        return when {
            digitsAadhaar.length != 12 -> "Enter a valid 12-digit Aadhaar number."
            s.farmerName.isBlank() -> "Farmer full name is required."
            s.block.isBlank() -> "Please select a Block first."
            s.cluster.isBlank() -> "Please select a Cluster for ${s.block}."
            s.village.isBlank() -> "Please select a Village for ${s.cluster}."
            else -> null
        }
    }

    private fun validateBankStep(s: FarmerFormUiState): String? {
        if (s.bankAccountType == BankAccountType.NONE) return null
        return when {
            s.bankAccountType == BankAccountType.RELATIVE && s.relativeRelationship == null ->
                "Select relationship for relative's bank account."
            s.fullAccountNumber.isBlank() -> "Enter bank account number or select 'No Bank Account'."
            s.fullAccountNumber != s.confirmAccountNumber -> "Account Number and Confirm Account Number do not match."
            s.ifsc.trim().length != 11 -> "Enter a valid 11-character IFSC code."
            else -> null
        }
    }

    fun saveFarmer(actorName: String) {
        val s = _formState.value
        val err = validateBasicStep(s) ?: validateBankStep(s)
        if (err != null) {
            _formState.update { it.copy(errorMessage = err) }
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true, errorMessage = null) }
            val now = System.currentTimeMillis()
            val digitsAadhaar = s.aadhaarNumber.filter { it.isDigit() }
            val entity = FarmerEntity(
                farmerUuid = s.farmerUuid,
                aadhaarNumber = digitsAadhaar,
                farmerName = s.farmerName.trim(),
                fatherOrHusbandName = s.fatherOrHusbandName.trim(),
                mobileNumber = s.mobileNumber.filter { it.isDigit() }.take(10),
                village = s.village.trim(),
                block = s.block.trim(),
                cluster = s.cluster.trim(),
                shgName = s.shgName.trim(),
                membershipStatus = s.membershipStatus,
                bmgdMemberCode = if (s.membershipStatus == MembershipStatus.MEMBER) s.bmgdMemberCode.trim() else "",
                bankAccountType = s.bankAccountType,
                relativeRelationship = if (s.bankAccountType == BankAccountType.RELATIVE) s.relativeRelationship else null,
                accountHolderName = if (s.bankAccountType == BankAccountType.OWN && s.accountHolderName.isBlank()) {
                    s.farmerName.trim()
                } else if (s.bankAccountType == BankAccountType.NONE) "" else s.accountHolderName.trim(),
                bankName = if (s.bankAccountType == BankAccountType.NONE) "" else s.bankName.trim(),
                fullAccountNumber = if (s.bankAccountType == BankAccountType.NONE) "" else s.fullAccountNumber.trim(),
                ifsc = if (s.bankAccountType == BankAccountType.NONE) "" else s.ifsc.trim().uppercase(),
                branch = if (s.bankAccountType == BankAccountType.NONE) "" else s.branch.trim(),
                farmerPhotoPath = s.farmerPhotoPath,
                idDocPhotoPath = s.idDocPhotoPath,
                bankPassbookPhotoPath = s.bankPassbookPhotoPath,
                createdAt = now,
                updatedAt = now,
                deviceId = repository.prefs.deviceCode,
                syncStatus = SyncStatus.PENDING
            )
            val result = repository.saveFarmer(entity, isUpdate = s.isEditMode, actorName = actorName)
            result.fold(
                onSuccess = { saved ->
                    _aadhaarLookup.value = AadhaarLookupState.Found(saved)
                    _formState.update { it.copy(isSaving = false, savedFarmer = saved, step = 3) }
                },
                onFailure = { ex ->
                    _formState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = ex.message ?: "Unable to save farmer record."
                        )
                    }
                }
            )
        }
    }

    fun clearSavedFarmer() {
        _formState.update { it.copy(savedFarmer = null) }
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FarmerViewModel(repository) as T
    }
}
