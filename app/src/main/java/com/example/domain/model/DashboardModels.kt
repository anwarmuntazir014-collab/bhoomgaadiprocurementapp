package com.example.domain.model

data class DashboardSummary(
    val totalBillsConfirmed: Int = 0,
    val totalBillsCancelled: Int = 0,
    val totalFarmersProcured: Int = 0,
    val totalQuantityKg: Double = 0.0,
    val totalAmountInr: Double = 0.0,
    val pendingUploads: Int = 0
)

data class ProductBreakdownItem(
    val productId: String,
    val stockItemName: String,
    val totalQuantityKg: Double,
    val totalAmountInr: Double,
    val lineCount: Int
)

data class LocationBreakdownItem(
    val locationName: String,
    val billCount: Int,
    val totalQuantityKg: Double,
    val totalAmountInr: Double
)

data class DailyProcurementItem(
    val dayLabel: String,
    val billCount: Int,
    val totalQuantityKg: Double,
    val totalAmountInr: Double
)
