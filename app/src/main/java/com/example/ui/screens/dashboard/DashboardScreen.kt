package com.example.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ProductBreakdownItem
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlin.math.max

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val selectedLocation by viewModel.selectedLocation.collectAsStateWithLifecycle()
    val allLocations by viewModel.allLocations.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val registeredFarmers by viewModel.registeredFarmerCount.collectAsStateWithLifecycle()
    val products by viewModel.productBreakdown.collectAsStateWithLifecycle()
    val locations by viewModel.locationBreakdown.collectAsStateWithLifecycle()
    val auditLogs by viewModel.recentAuditLogs.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    val locationOptions = remember(allLocations) {
        listOf("") + allLocations.map { it.locationName }
    }

    BhoomgaadiScaffold(
        title = strings.dashboard,
        subtitle = "${selectedPeriod.label} • ${selectedLocation.ifBlank { "All Locations" }}",
        onBack = onBack
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top 2 Filter Dropdowns matching Layout 3 Screen 8
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SimpleDropdown(
                        label = "Period",
                        selectedText = selectedPeriod.label,
                        items = DashboardPeriod.entries,
                        itemLabel = { it.label },
                        onSelected = viewModel::selectPeriod,
                        testTag = "dashboard_period_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                    SimpleDropdown(
                        label = "Location",
                        selectedText = selectedLocation.ifBlank { "All Locations" },
                        items = locationOptions,
                        itemLabel = { it.ifBlank { "All Locations" } },
                        onSelected = viewModel::selectLocation,
                        testTag = "dashboard_location_dropdown",
                        modifier = Modifier.weight(1f)
                    )
                }

                // 2x2 Pastel KPI Grid matching Layout 3 Screen 8
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PastelKpiCard(
                        title = "Total Bills",
                        value = summary.totalBillsConfirmed.toString(),
                        subtitle = if (summary.totalBillsCancelled > 0) "${summary.totalBillsCancelled} cancelled" else "Finalized",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        bgColor = BhoomgaadiLightGreen,
                        accentColor = BhoomgaadiDarkGreen,
                        modifier = Modifier.weight(1f)
                    )
                    PastelKpiCard(
                        title = "Total Farmers",
                        value = summary.totalFarmersProcured.toString(),
                        subtitle = "$registeredFarmers in master",
                        icon = Icons.Filled.People,
                        bgColor = SecondaryLightBlue,
                        accentColor = SecondaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PastelKpiCard(
                        title = "Total Weight (kg)",
                        value = formatQty(summary.totalQuantityKg),
                        subtitle = "Net weight",
                        icon = Icons.Filled.Scale,
                        bgColor = ProcurementLightOrange,
                        accentColor = ProcurementOrange,
                        modifier = Modifier.weight(1f)
                    )
                    PastelKpiCard(
                        title = "Total Amount (₹)",
                        value = formatCurrencyWhole(summary.totalAmountInr),
                        subtitle = "${summary.pendingUploads} PDFs pending sync",
                        icon = Icons.Filled.CurrencyRupee,
                        bgColor = DangerLightRed,
                        accentColor = DangerRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Vertical Colored Bar Chart: Procurement by Product (kg) matching Layout 3 Screen 8
                SectionCard(title = "Procurement by Product (kg)") {
                    if (products.isEmpty()) {
                        Text("No finalized procurement records in this period.", color = TextSecondary)
                    } else {
                        ProductVerticalBarChart(products = products.take(6))
                    }
                }

                // Location Breakdown
                if (locations.isNotEmpty()) {
                    SectionCard(title = "Location Breakdown") {
                        locations.forEachIndexed { idx, loc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(loc.locationName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${loc.billCount} bills • ${formatQty(loc.totalQuantityKg)} kg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    formatCurrencyWhole(loc.totalAmountInr),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = JetBrainsMono,
                                    color = BhoomgaadiDarkGreen
                                )
                            }
                            if (idx < locations.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
                        }
                    }
                }

                // Recent Activity / Audit Log
                if (auditLogs.isNotEmpty()) {
                    SectionCard(title = "Recent Activity Log") {
                        auditLogs.take(10).forEachIndexed { idx, log ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(log.actionType.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BhoomgaadiDarkGreen)
                                Text(log.summary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${log.performedBy} • ${formatDateTime(log.timestampMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            if (idx < minOf(auditLogs.size, 10) - 1) HorizontalDivider(color = BorderGray.copy(alpha = 0.45f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastelKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    bgColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = JetBrainsMono,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProductVerticalBarChart(products: List<ProductBreakdownItem>) {
    val barColors = listOf(
        BhoomgaadiGreen,
        SecondaryBlue,
        ProcurementAmber,
        DashboardPurple,
        BhoomgaadiDarkGreen,
        DangerRed
    )
    val maxQty = max(products.maxOfOrNull { it.totalQuantityKg } ?: 1.0, 1.0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        products.forEachIndexed { idx, item ->
            val ratio = (item.totalQuantityKg / maxQty).toFloat().coerceIn(0.08f, 1f)
            val color = barColors[idx % barColors.size]
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = if (item.totalQuantityKg >= 10) "${item.totalQuantityKg.toInt()}" else formatQty(item.totalQuantityKg),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMono,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height((115f * ratio).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(color)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.stockItemName,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 12.sp
                )
            }
        }
    }
}
