package com.example.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.AuditLogEntity
import com.example.data.local.entities.BillWithItems
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.BillStatus
import com.example.domain.model.CancellationReason
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class BillFilterStatus(val label: String) {
    ALL("All"),
    CONFIRMED("Confirmed"),
    CANCELLED("Voided")
}

class HistoryViewModel(private val repository: ProcurementRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterStatus = MutableStateFlow(BillFilterStatus.ALL)
    val filterStatus: StateFlow<BillFilterStatus> = _filterStatus.asStateFlow()

    private val allBills: StateFlow<List<BillWithItems>> = repository.observeAllBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBills: StateFlow<List<BillWithItems>> = combine(
        allBills,
        _searchQuery,
        _filterStatus
    ) { bills, q, status ->
        bills.filter { bill ->
            val h = bill.header
            val matchesStatus = when (status) {
                BillFilterStatus.ALL -> true
                BillFilterStatus.CONFIRMED -> h.billStatus == BillStatus.CONFIRMED
                BillFilterStatus.CANCELLED -> h.billStatus == BillStatus.CANCELLED
            }
            val trimmed = q.trim()
            val matchesQuery = trimmed.isBlank() ||
                h.billNumber.contains(trimmed, ignoreCase = true) ||
                h.farmerNameSnapshot.contains(trimmed, ignoreCase = true) ||
                h.farmerVillageSnapshot.contains(trimmed, ignoreCase = true) ||
                h.farmerAadhaarSnapshot.contains(trimmed, ignoreCase = true) ||
                h.locationNameSnapshot.contains(trimmed, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilter(filter: BillFilterStatus) {
        _filterStatus.value = filter
    }

    fun observeBill(billUuid: String): Flow<BillWithItems?> = repository.observeBill(billUuid)

    fun observeBillAuditLogs(billUuid: String): Flow<List<AuditLogEntity>> =
        repository.observeAuditLogsForEntity(billUuid)

    fun cancelBill(
        billUuid: String,
        reason: CancellationReason,
        notes: String,
        onComplete: (Result<BillWithItems>) -> Unit
    ) {
        viewModelScope.launch {
            onComplete(repository.cancelBill(billUuid, reason, notes))
        }
    }

    fun regeneratePdf(billUuid: String, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onComplete(repository.regeneratePdf(billUuid))
        }
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HistoryViewModel(repository) as T
    }
}
