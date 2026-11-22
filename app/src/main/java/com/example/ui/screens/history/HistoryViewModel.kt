package com.example.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.AuditLogEntity
import com.example.data.local.entities.BillHeaderEntity
import com.example.data.local.entities.BillWithItems
import com.example.data.local.entities.UserEntity
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.BillStatus
import com.example.domain.model.CancellationReason
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

enum class HistoryQuickRange(val label: String) {
    ALL("All Records"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days")
}

enum class BillFilterStatus(val label: String) {
    ALL("All"),
    CONFIRMED("Finalized"),
    CANCELLED("Cancelled")
}

data class HistorySearchState(
    val billNumberQuery: String = "",
    val aadhaarQuery: String = "",
    val farmerNameQuery: String = "",
    val quickRange: HistoryQuickRange = HistoryQuickRange.ALL,
    val statusFilter: BillFilterStatus = BillFilterStatus.ALL
)

class HistoryViewModel(private val repository: ProcurementRepository) : ViewModel() {

    private val _searchState = MutableStateFlow(HistorySearchState())
    val searchState: StateFlow<HistorySearchState> = _searchState.asStateFlow()

    private val allBills: StateFlow<List<BillWithItems>> = repository.observeAllBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBills: StateFlow<List<BillWithItems>> = combine(
        allBills,
        _searchState
    ) { bills, filter ->
        val cutoffMillis = computeCutoffMillis(filter.quickRange)
        bills.filter { bill ->
            val h = bill.header
            val matchesStatus = when (filter.statusFilter) {
                BillFilterStatus.ALL -> true
                BillFilterStatus.CONFIRMED -> h.billStatus == BillStatus.CONFIRMED
                BillFilterStatus.CANCELLED -> h.billStatus == BillStatus.CANCELLED
            }
            val matchesDate = h.billDateMillis >= cutoffMillis
            val matchesBillNo = filter.billNumberQuery.isBlank() ||
                h.billNumber.contains(filter.billNumberQuery.trim(), ignoreCase = true)
            val aadhaarDigits = filter.aadhaarQuery.filter { it.isDigit() }
            val matchesAadhaar = aadhaarDigits.isBlank() ||
                h.farmerAadhaarSnapshot.contains(aadhaarDigits)
            val matchesName = filter.farmerNameQuery.isBlank() ||
                h.farmerNameSnapshot.contains(filter.farmerNameQuery.trim(), ignoreCase = true) ||
                h.farmerVillageSnapshot.contains(filter.farmerNameQuery.trim(), ignoreCase = true)
            matchesStatus && matchesDate && matchesBillNo && matchesAadhaar && matchesName
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearch(transform: (HistorySearchState) -> HistorySearchState) {
        _searchState.update(transform)
    }

    fun clearFilters() {
        _searchState.value = HistorySearchState()
    }

    fun canEditBill(header: BillHeaderEntity, user: UserEntity?): Boolean =
        repository.canUserEditBill(header, user)

    fun observeBill(billUuid: String): Flow<BillWithItems?> = repository.observeBill(billUuid)

    fun observeBillAuditLogs(billUuid: String): Flow<List<AuditLogEntity>> =
        repository.observeAuditLogsForEntity(billUuid)

    fun cancelBill(
        billUuid: String,
        reason: CancellationReason,
        notes: String,
        actor: UserEntity,
        onComplete: (Result<BillWithItems>) -> Unit
    ) {
        viewModelScope.launch {
            onComplete(repository.cancelBill(billUuid, reason, notes, actor))
        }
    }

    fun regeneratePdf(billUuid: String, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onComplete(repository.regeneratePdf(billUuid))
        }
    }

    private fun computeCutoffMillis(range: HistoryQuickRange): Long {
        if (range == HistoryQuickRange.ALL) return 0L
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (range) {
            HistoryQuickRange.TODAY -> {}
            HistoryQuickRange.LAST_7_DAYS -> cal.add(Calendar.DAY_OF_YEAR, -6)
            HistoryQuickRange.LAST_30_DAYS -> cal.add(Calendar.DAY_OF_YEAR, -29)
            HistoryQuickRange.ALL -> return 0L
        }
        return cal.timeInMillis
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(repository) as T
    }
}
