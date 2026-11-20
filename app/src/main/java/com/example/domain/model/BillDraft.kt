package com.example.domain.model

import com.example.data.local.entities.FarmerEntity
import com.example.data.local.entities.GodownEntity
import com.example.data.local.entities.LocationEntity
import com.example.data.local.entities.ProductEntity

data class BillDraftLineItem(
    val id: String,
    val product: ProductEntity,
    val bagCount: Int,
    val grossWeightKg: Double,
    val tareWeightKg: Double,
    val netQuantityKg: Double,
    val ratePerKg: Double,
    val lineAmount: Double,
    val qualityGrade: String = "Grade A",
    val moisturePercent: String = ""
)

data class BillDraft(
    val selectedFarmer: FarmerEntity? = null,
    val selectedLocation: LocationEntity? = null,
    val selectedGodown: GodownEntity? = null,
    val lines: List<BillDraftLineItem> = emptyList(),
    val remarks: String = "",
    val weighingSlipRef: String = ""
) {
    val totalBags: Int get() = lines.sumOf { it.bagCount }
    val totalQuantityKg: Double get() = lines.sumOf { it.netQuantityKg }
    val grandTotalAmount: Double get() = lines.sumOf { it.lineAmount }
}
