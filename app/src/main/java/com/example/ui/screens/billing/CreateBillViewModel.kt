package com.example.ui.screens.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.*
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.BillDraft
import com.example.domain.model.BillDraftLineItem
import com.example.domain.model.MembershipStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class BillWizardStep {
    FIND_FARMER,
    VERIFY_FARMER,
    BILL_INFO,
    ADD_ITEM,
    ITEMS_LIST,
    MEMBERSHIP,
    SUMMARY,
    CREATED_SUCCESS
}

data class CreateBillUiState(
    val step: BillWizardStep = BillWizardStep.FIND_FARMER,
    val draft: BillDraft = BillDraft(),
    val aadhaarInput: String = "",
    val farmerNotFoundAadhaar: String? = null,
    val editingLineId: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val createdBill: BillWithItems? = null
)

class CreateBillViewModel(private val repository: ProcurementRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateBillUiState())
    val uiState: StateFlow<CreateBillUiState> = _uiState.asStateFlow()

    val activeProducts: StateFlow<List<ProductEntity>> = repository.observeActiveProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeLocations: StateFlow<List<LocationEntity>> = repository.observeActiveLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val matchingFarmers: StateFlow<List<FarmerEntity>> = _uiState
        .map { it.aadhaarInput }
        .distinctUntilChanged()
        .flatMapLatest { repository.searchFarmers(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val godownsForSelectedLocation: StateFlow<List<GodownEntity>> = _uiState
        .map { it.draft.selectedLocation?.locationId.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { locId ->
            if (locId.isBlank()) flowOf(emptyList()) else repository.observeActiveGodownsForLocation(locId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val membershipFeeInr: Double get() = repository.prefs.membershipFeeInr
    val authorizedSignatory: String get() = repository.prefs.authorizedSignatory

    fun nextBillNumberPreview(): String =
        _uiState.value.draft.editingBillNumber ?: repository.prefs.nextBillNumberPreview()

    fun startNewBill(preselectedFarmerUuid: String? = null) {
        _uiState.value = CreateBillUiState(
            draft = BillDraft(
                procurementDateMillis = System.currentTimeMillis(),
                membershipFeeInr = repository.prefs.membershipFeeInr
            )
        )
        if (!preselectedFarmerUuid.isNullOrBlank()) {
            preselectFarmerById(preselectedFarmerUuid)
        }
    }

    fun startEditExistingBill(billUuid: String) {
        viewModelScope.launch {
            val existing = repository.getBillOnce(billUuid) ?: return@launch
            val farmer = repository.getFarmerByUuid(existing.header.farmerUuid)
            val locList = repository.observeAllLocations().first()
            val gdwList = repository.observeAllGodowns().first()
            val prodList = repository.observeAllProducts().first()

            val selectedLoc = locList.find { it.locationId == existing.header.locationId }
                ?: locList.firstOrNull()
            val selectedGdw = gdwList.find { it.godownId == existing.header.godownId }

            val mappedLines = existing.items.sortedBy { it.lineOrder }.map { item ->
                val prod = prodList.find { it.productId == item.productId }
                    ?: ProductEntity(
                        productId = item.productId,
                        stockItemName = item.stockItemNameSnapshot,
                        ratePerKg = item.ratePerKg,
                        procurementYear = existing.header.procurementYear,
                        displayOrder = 99,
                        status = com.example.domain.model.RecordStatus.ACTIVE,
                        createdAt = existing.header.createdAt,
                        updatedAt = existing.header.updatedAt
                    )
                val wtPerBag = if (item.weightPerBagKg > 0.0) item.weightPerBagKg
                else if (item.bagCount > 0) item.netQuantityKg / item.bagCount else item.netQuantityKg
                BillDraftLineItem(
                    id = item.billItemUuid,
                    product = prod,
                    bagCount = item.bagCount,
                    weightPerBagKg = wtPerBag,
                    grossWeightKg = item.grossWeightKg,
                    tareWeightKg = item.tareWeightKg,
                    netQuantityKg = item.netQuantityKg,
                    ratePerKg = item.ratePerKg,
                    lineAmount = item.lineAmountInr,
                    qualityGrade = item.qualityGrade,
                    moisturePercent = item.moisturePercent
                )
            }

            _uiState.value = CreateBillUiState(
                step = BillWizardStep.ITEMS_LIST,
                draft = BillDraft(
                    editingBillUuid = existing.header.billUuid,
                    editingBillNumber = existing.header.billNumber,
                    procurementDateMillis = existing.header.billDateMillis,
                    selectedFarmer = farmer,
                    selectedLocation = selectedLoc,
                    selectedGodown = selectedGdw,
                    lines = mappedLines,
                    membershipEnrolled = existing.header.membershipEnrolled,
                    membershipFeeInr = if (existing.header.membershipAmountInr > 0.0) {
                        existing.header.membershipAmountInr
                    } else repository.prefs.membershipFeeInr,
                    remarks = existing.header.remarks,
                    weighingSlipRef = existing.header.weighingSlipRef,
                    editReason = ""
                )
            )
        }
    }

    fun preselectFarmerById(farmerUuid: String) {
        viewModelScope.launch {
            val farmer = repository.getFarmerByUuid(farmerUuid) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    step = BillWizardStep.VERIFY_FARMER,
                    aadhaarInput = farmer.aadhaarNumber,
                    farmerNotFoundAadhaar = null,
                    draft = state.draft.copy(
                        selectedFarmer = farmer,
                        membershipFeeInr = repository.prefs.membershipFeeInr
                    ),
                    errorMessage = null
                )
            }
        }
    }

    fun updateAadhaarInput(input: String) {
        _uiState.update { it.copy(aadhaarInput = input, farmerNotFoundAadhaar = null, errorMessage = null) }
    }

    fun searchFarmerByAadhaar() {
        val digits = _uiState.value.aadhaarInput.filter { it.isDigit() }
        if (digits.length != 12) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 12-digit Aadhaar number.") }
            return
        }
        viewModelScope.launch {
            val farmer = repository.getFarmerByAadhaar(digits)
            if (farmer != null) {
                _uiState.update {
                    it.copy(
                        step = BillWizardStep.VERIFY_FARMER,
                        farmerNotFoundAadhaar = null,
                        draft = it.draft.copy(selectedFarmer = farmer),
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(farmerNotFoundAadhaar = digits, errorMessage = null)
                }
            }
        }
    }

    fun selectFarmerFromList(farmer: FarmerEntity) {
        _uiState.update {
            it.copy(
                step = BillWizardStep.VERIFY_FARMER,
                aadhaarInput = farmer.aadhaarNumber,
                farmerNotFoundAadhaar = null,
                draft = it.draft.copy(selectedFarmer = farmer),
                errorMessage = null
            )
        }
    }

    fun updateProcurementDate(millis: Long) {
        _uiState.update { it.copy(draft = it.draft.copy(procurementDateMillis = millis)) }
    }

    fun selectLocation(location: LocationEntity) {
        _uiState.update {
            it.copy(
                draft = it.draft.copy(selectedLocation = location, selectedGodown = null),
                errorMessage = null
            )
        }
    }

    fun selectGodown(godown: GodownEntity) {
        _uiState.update {
            it.copy(draft = it.draft.copy(selectedGodown = godown), errorMessage = null)
        }
    }

    fun setMembershipEnrolled(enroll: Boolean) {
        _uiState.update {
            it.copy(
                draft = it.draft.copy(
                    membershipEnrolled = enroll,
                    membershipFeeInr = repository.prefs.membershipFeeInr
                )
            )
        }
    }

    fun startEditLine(lineId: String) {
        _uiState.update { it.copy(editingLineId = lineId, step = BillWizardStep.ADD_ITEM, errorMessage = null) }
    }

    fun saveLineItem(
        product: ProductEntity,
        bagCount: Int,
        weightPerBagKg: Double,
        moisturePercent: String,
        rateOverride: Double?
    ) {
        if (bagCount <= 0 || weightPerBagKg <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Enter valid No. of Bags and Weight per Bag (kg).") }
            return
        }
        val netKg = bagCount * weightPerBagKg
        val effectiveRate = rateOverride?.takeIf { it > 0.0 } ?: product.ratePerKg
        val editingId = _uiState.value.editingLineId
        val newLine = BillDraftLineItem(
            id = editingId ?: UUID.randomUUID().toString(),
            product = product,
            bagCount = bagCount,
            weightPerBagKg = weightPerBagKg,
            grossWeightKg = netKg,
            tareWeightKg = 0.0,
            netQuantityKg = netKg,
            ratePerKg = effectiveRate,
            lineAmount = netKg * effectiveRate,
            qualityGrade = "FAQ",
            moisturePercent = moisturePercent.trim()
        )
        _uiState.update { state ->
            val updatedLines = if (editingId != null) {
                state.draft.lines.map { if (it.id == editingId) newLine else it }
            } else {
                state.draft.lines + newLine
            }
            state.copy(
                step = BillWizardStep.ITEMS_LIST,
                editingLineId = null,
                draft = state.draft.copy(lines = updatedLines),
                errorMessage = null
            )
        }
    }

    fun removeLineItem(lineId: String) {
        _uiState.update {
            it.copy(draft = it.draft.copy(lines = it.draft.lines.filterNot { l -> l.id == lineId }))
        }
    }

    fun updateMeta(weighingSlipRef: String, remarks: String, editReason: String = _uiState.value.draft.editReason) {
        _uiState.update {
            it.copy(
                draft = it.draft.copy(
                    weighingSlipRef = weighingSlipRef,
                    remarks = remarks,
                    editReason = editReason
                ),
                errorMessage = null
            )
        }
    }

    fun navigateStep(target: BillWizardStep) {
        val current = _uiState.value
        if (target == BillWizardStep.ADD_ITEM && current.step == BillWizardStep.BILL_INFO) {
            if (current.draft.selectedLocation == null || current.draft.selectedGodown == null) {
                _uiState.update { it.copy(errorMessage = "Select both Procurement Location and Temporary Godown.") }
                return
            }
        }
        if (target == BillWizardStep.MEMBERSHIP) {
            if (current.draft.lines.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Add at least one procurement item first.") }
                return
            }
            // If already a Member (and not revising a membership-enrolled bill), skip Membership prompt directly to Summary
            val farmer = current.draft.selectedFarmer
            if (farmer != null && farmer.membershipStatus == MembershipStatus.MEMBER && !current.draft.membershipEnrolled) {
                _uiState.update { it.copy(step = BillWizardStep.SUMMARY, errorMessage = null) }
                return
            }
        }
        _uiState.update {
            it.copy(
                step = target,
                editingLineId = if (target != BillWizardStep.ADD_ITEM) null else it.editingLineId,
                errorMessage = null
            )
        }
    }

    fun confirmAndSaveBill(actor: UserEntity) {
        val state = _uiState.value
        if (state.isSubmitting) return
        if (state.draft.isEditMode && state.draft.editReason.trim().length < 3) {
            _uiState.update { it.copy(errorMessage = "Please enter an edit reason before saving the revised bill.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = repository.createOrUpdateBillFromDraft(state.draft, actor)
            result.fold(
                onSuccess = { saved ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            createdBill = saved,
                            step = BillWizardStep.CREATED_SUCCESS,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = err.message ?: "Failed to save procurement bill."
                        )
                    }
                }
            )
        }
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CreateBillViewModel(repository) as T
    }
}
