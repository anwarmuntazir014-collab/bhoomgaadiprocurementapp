package com.example.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.AuditLogEntity
import com.example.data.local.entities.LocationEntity
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.DailyProcurementItem
import com.example.domain.model.DashboardSummary
import com.example.domain.model.LocationBreakdownItem
import com.example.domain.model.ProductBreakdownItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.util.Calendar

enum class DashboardPeriod(val label: String) {
    THIS_MONTH("This Month"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    ALL_TIME("All Season")
}

class DashboardViewModel(private val repository: ProcurementRepository) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(DashboardPeriod.ALL_TIME)
    val selectedPeriod: StateFlow<DashboardPeriod> = _selectedPeriod.asStateFlow()

    private val _selectedLocation = MutableStateFlow("")
    val selectedLocation: StateFlow<String> = _selectedLocation.asStateFlow()

    val allLocations: StateFlow<List<LocationEntity>> = repository.observeAllLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val registeredFarmerCount: StateFlow<Int> = repository.observeFarmerCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val filterPair = combine(_selectedPeriod, _selectedLocation) { period, loc ->
        computeFromMillis(period) to loc
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: StateFlow<DashboardSummary> = filterPair
        .flatMapLatest { (from, loc) -> repository.observeDashboardSummary(from, loc) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    @OptIn(ExperimentalCoroutinesApi::class)
    val productBreakdown: StateFlow<List<ProductBreakdownItem>> = filterPair
        .flatMapLatest { (from, loc) -> repository.observeProductBreakdown(from, loc) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val locationBreakdown: StateFlow<List<LocationBreakdownItem>> = filterPair
        .flatMapLatest { (from, loc) -> repository.observeLocationBreakdown(from, loc) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyTotals: StateFlow<List<DailyProcurementItem>> = filterPair
        .flatMapLatest { (from, loc) -> repository.observeRecentDailyTotals(from, loc) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAuditLogs: StateFlow<List<AuditLogEntity>> = repository.observeAuditLogs(25)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPeriod(period: DashboardPeriod) {
        _selectedPeriod.value = period
    }

    fun selectLocation(locationName: String) {
        _selectedLocation.value = locationName
    }

    private fun computeFromMillis(period: DashboardPeriod): Long {
        if (period == DashboardPeriod.ALL_TIME) return 0L
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            DashboardPeriod.TODAY -> {}
            DashboardPeriod.LAST_7_DAYS -> cal.add(Calendar.DAY_OF_YEAR, -6)
            DashboardPeriod.THIS_MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
            DashboardPeriod.ALL_TIME -> return 0L
        }
        return cal.timeInMillis
    }

    class Factory(private val repository: ProcurementRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repository) as T
    }
}
