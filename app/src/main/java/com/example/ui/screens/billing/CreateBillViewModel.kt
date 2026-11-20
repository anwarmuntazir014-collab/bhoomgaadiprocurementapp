package com.example.ui.screens.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.*
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.BillDraft
import com.example.domain.model.BillDraftLineItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class CreateBillUiState(
    val step: Int = 0, // 0: Farmer & Center, 1: Add Produce Items, 2: Review & Confirm
    val draft: BillDraft = BillDraft(),
    val farmerQuery: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val createdBillUuid: String? = null
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
        .map { it.farmerQuery }
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

    fun nextBillNumberPreview(): String = repository.prefs.nextBillNumberPreview()

    fun preselectFarmerById(farmerUuid: String?) {
        if (farmerUuid.isNullOrBlank()) return
        viewModelScope.launch {
            val farmer = repository.getFarmerByUuid(farmerUuid) ?: return@launch
            _uiState.update { state ->
                state.copy(draft = state.draft.copy(selectedFarmer = farmer), errorMessage = null)
            }
        }
    }

    fun updateFarmerQuery(query: String) {
        _uiState.update { it.copy(farmerQuery = query) }
    }

    fun selectFarmer(farmer: FarmerEntity) {
        _uiState.update {
            it.copy(draft = it.draft.copy(selectedFarmer = farmer), errorMessage = null)
        }
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

    fun addLineItem(
        product: ProductEntity,
        bagCount: Int,
        grossWeightKg: Double,
        tareWeightKg: Double,
        rateOverride: Double?,
        qualityGrade: String,
        moisturePercent: String
    ) {
        val netKg = (grossWeightKg - tareWeightKg).coerceAtLeast(0.0)
        if (netKg <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Net weight must be greater than 0 kg.") }
            return
        }
        val effectiveRate = rateOverride?.takeIf { it > 0.0 } ?: product.ratePerKg
        val lineItem = BillDraftLineItem(
            id = UUID.randomUUID().toString(),
            product = product,
            bagCount = bagCount.coerceAtLeast(1),
            grossWeightKg = grossWeightKg,
            tareWeightKg = tareWeightKg.coerceAtLeast(0.0),
            netQuantityKg = netKg,
            ratePerKg = effectiveRate,
            lineAmount = netKg * effectiveRate,
            qualityGrade = qualityGrade.ifBlank { "Grade A" },
            moisturePercent = moisturePercent.trim()
        )
        _uiState.update {
            it.copy(
                draft = it.draft.copy(lines = it.draft.lines + lineItem),
                errorMessage = null
            )
        }
    }

    fun removeLineItem(lineId: String) {
        _uiState.update {
            it.copy(draft = it.draft.copy(lines = it.draft.lines.filterNot { line -> line.id == lineId }))
        }
    }

    fun updateMeta(weighingSlipRef: String, remarks: String) {
        _uiState.update {
            it.copy(draft = it.draft.copy(weighingSlipRef = weighingSlipRef, remarks = remarks))
        }
    }

    fun goToStep(step: Int) {
        val current = _uiState.value
        if (step > current.step) {
            if (current.step == 0) {
                when {
                    current.draft.selectedFarmer == null -> {
                        _uiState.update { it.copy(errorMessage = "Select a farmer to proceed.") }
                        return
                    }
                    current.draft.selectedLocation == null -> {
                        _uiState.update { it.copy(errorMessage = "Select a procurement center/location.") }
                        return
                    }
                    current.draft.selectedGodown == null -> {
                        _uiState.update { it.copy(errorMessage = "Select a temporary godown for storage.") }
                        return
                    }
                }
            }
            if (current.step == 1 && current.draft.lines.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Add at least one weighed produce item.") }
                return
            }
        }
        _uiState.update { it.copy(step = step.coerceIn(0, 2), errorMessage = null) }
    }

    fun confirmAndCreateBill() {
        val state = _uiState.value
        if (state.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val result = repository.createBillFromDraft(state.draft)
            result.fold(
                onSuccess = { created ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            createdBillUuid = created.header.billUuid
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = err.message ?: "Failed to create bill."
                        )
                    }
                }
            )
        }
    }

    fun resetWizard() {
        _uiState.value = CreateBillUiState()
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CreateBillViewModel(repository) as T
    }
}
