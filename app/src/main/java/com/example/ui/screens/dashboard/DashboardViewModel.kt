package com.example.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.AuditLogEntity
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.DailyProcurementItem
import com.example.domain.model.DashboardSummary
import com.example.domain.model.LocationBreakdownItem
import com.example.domain.model.ProductBreakdownItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class DashboardViewModel(private val repository: ProcurementRepository) : ViewModel() {

    private val _selectedYear = MutableStateFlow(repository.prefs.procurementYear)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    val registeredFarmerCount: StateFlow<Int> = repository.observeFarmerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: StateFlow<DashboardSummary> = _selectedYear
        .flatMapLatest { repository.observeDashboardSummary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    @OptIn(ExperimentalCoroutinesApi::class)
    val productBreakdown: StateFlow<List<ProductBreakdownItem>> = _selectedYear
        .flatMapLatest { repository.observeProductBreakdown(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val locationBreakdown: StateFlow<List<LocationBreakdownItem>> = _selectedYear
        .flatMapLatest { repository.observeLocationBreakdown(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyTotals: StateFlow<List<DailyProcurementItem>> = _selectedYear
        .flatMapLatest { repository.observeRecentDailyTotals(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAuditLogs: StateFlow<List<AuditLogEntity>> = repository.observeAuditLogs(25)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectYear(year: Int) {
        _selectedYear.value = year
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repository) as T
    }
}
