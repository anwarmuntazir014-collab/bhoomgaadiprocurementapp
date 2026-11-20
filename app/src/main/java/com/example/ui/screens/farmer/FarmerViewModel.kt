package com.example.ui.screens.farmer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.FarmerEntity
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.BankAccountType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.RelativeRelationship
import com.example.domain.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class FarmerFormUiState(
    val farmerUuid: String = UUID.randomUUID().toString(),
    val isEditMode: Boolean = false,
    val aadhaarNumber: String = "",
    val farmerName: String = "",
    val fatherOrHusbandName: String = "",
    val mobileNumber: String = "",
    val village: String = "",
    val block: String = "",
    val cluster: String = "",
    val shgName: String = "",
    val membershipStatus: MembershipStatus = MembershipStatus.MEMBER,
    val bmgdMemberCode: String = "",
    val bankAccountType: BankAccountType = BankAccountType.OWN,
    val relativeRelationship: RelativeRelationship? = null,
    val accountHolderName: String = "",
    val bankName: String = "",
    val fullAccountNumber: String = "",
    val ifsc: String = "",
    val branch: String = "",
    val idDocPhotoPath: String = "",
    val bankPassbookPhotoPath: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedFarmer: FarmerEntity? = null
)

class FarmerViewModel(private val repository: ProcurementRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val farmers: StateFlow<List<FarmerEntity>> = _searchQuery
        .flatMapLatest { query -> repository.searchFarmers(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _formState = MutableStateFlow(FarmerFormUiState())
    val formState: StateFlow<FarmerFormUiState> = _formState.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startNewFarmer(prefillAadhaar: String = "") {
        _formState.value = FarmerFormUiState(
            farmerUuid = UUID.randomUUID().toString(),
            isEditMode = false,
            aadhaarNumber = prefillAadhaar.filter { it.isDigit() }.take(12)
        )
    }

    fun loadFarmerForEdit(farmerUuid: String) {
        viewModelScope.launch {
            val f = repository.getFarmerByUuid(farmerUuid) ?: return@launch
            _formState.value = FarmerFormUiState(
                farmerUuid = f.farmerUuid,
                isEditMode = true,
                aadhaarNumber = f.aadhaarNumber,
                farmerName = f.farmerName,
                fatherOrHusbandName = f.fatherOrHusbandName,
                mobileNumber = f.mobileNumber,
                village = f.village,
                block = f.block,
                cluster = f.cluster,
                shgName = f.shgName,
                membershipStatus = f.membershipStatus,
                bmgdMemberCode = f.bmgdMemberCode,
                bankAccountType = f.bankAccountType,
                relativeRelationship = f.relativeRelationship,
                accountHolderName = f.accountHolderName,
                bankName = f.bankName,
                fullAccountNumber = f.fullAccountNumber,
                ifsc = f.ifsc,
                branch = f.branch,
                idDocPhotoPath = f.idDocPhotoPath,
                bankPassbookPhotoPath = f.bankPassbookPhotoPath
            )
        }
    }

    fun updateForm(transform: (FarmerFormUiState) -> FarmerFormUiState) {
        _formState.update { transform(it).copy(errorMessage = null) }
    }

    fun clearSavedFarmer() {
        _formState.update { it.copy(savedFarmer = null) }
    }

    fun saveFarmer() {
        val s = _formState.value
        val digitsAadhaar = s.aadhaarNumber.filter { it.isDigit() }
        val validationError = when {
            digitsAadhaar.length != 12 -> "Enter a valid 12-digit Aadhaar number."
            s.farmerName.isBlank() -> "Farmer full name is required."
            s.village.isBlank() -> "Village name is required."
            s.bankAccountType == BankAccountType.RELATIVE && s.relativeRelationship == null ->
                "Select relationship for relative's bank account."
            s.bankAccountType != BankAccountType.NONE && s.fullAccountNumber.isBlank() ->
                "Enter bank account number or select 'No Bank Account'."
            s.bankAccountType != BankAccountType.NONE && s.ifsc.trim().length < 11 ->
                "Enter a valid 11-character IFSC code."
            else -> null
        }

        if (validationError != null) {
            _formState.update { it.copy(errorMessage = validationError) }
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true, errorMessage = null) }
            val now = System.currentTimeMillis()
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
                idDocPhotoPath = s.idDocPhotoPath,
                bankPassbookPhotoPath = s.bankPassbookPhotoPath,
                createdAt = now,
                updatedAt = now,
                deviceId = repository.prefs.deviceCode,
                syncStatus = SyncStatus.PENDING
            )
            val result = repository.saveFarmer(entity, isUpdate = s.isEditMode)
            result.fold(
                onSuccess = { saved ->
                    _formState.update { it.copy(isSaving = false, savedFarmer = saved) }
                },
                onFailure = { err ->
                    _formState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.message ?: "Unable to save farmer record."
                        )
                    }
                }
            )
        }
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FarmerViewModel(repository) as T
    }
}
