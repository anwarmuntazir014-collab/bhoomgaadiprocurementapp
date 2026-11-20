package com.example.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Calendar

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val productBreakdown by viewModel.productBreakdown.collectAsStateWithLifecycle()
    val locationBreakdown by viewModel.locationBreakdown.collectAsStateWithLifecycle()
    val dailyTotals by viewModel.dailyTotals.collectAsStateWithLifecycle()
    val auditLogs by viewModel.recentAuditLogs.collectAsStateWithLifecycle()

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    BhoomgaadiScaffold(
        title = "Live Procurement Summary",
        subtitle = if (selectedYear == 0) "All Seasons" else "Season $selectedYear",
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
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Year selector chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedYear == currentYear,
                        onClick = { viewModel.selectYear(currentYear) },
                        label = { Text("$currentYear") }
                    )
                    FilterChip(
                        selected = selectedYear == currentYear - 1,
                        onClick = { viewModel.selectYear(currentYear - 1) },
                        label = { Text("${currentYear - 1}") }
                    )
                    FilterChip(
                        selected = selectedYear == 0,
                        onClick = { viewModel.selectYear(0) },
                        label = { Text("All Years") }
                    )
                }

                // KPI Grid
                SectionCard(title = "Procurement Totals", containerColor = BhoomgaadiLightGreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Payout", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                formatCurrency(summary.totalAmountInr),
                                fontFamily = JetBrainsMono,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = BhoomgaadiDarkGreen
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Net Procured Qty", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                "${formatQty(summary.totalQuantityKg)} kg",
                                fontFamily = JetBrainsMono,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = ProcurementOrange
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = BhoomgaadiGreen.copy(alpha = 0.25f))
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MiniStat("Confirmed", "${summary.totalBillsConfirmed}", BhoomgaadiDarkGreen)
                        MiniStat("Voided", "${summary.totalBillsCancelled}", DangerRed)
                        MiniStat("Farmers Paid", "${summary.totalFarmersProcured}", SecondaryBlue)
                        MiniStat("Pending Sync", "${summary.pendingUploads}", ProcurementOrange)
                    }
                }

                if (productBreakdown.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Filled.BarChart,
                        title = "No Confirmed Bills Yet",
                        description = "Crop-wise weighment bars, center totals, and daily trends update automatically as you issue bills offline."
                    )
                } else {
                    // Product Breakdown Bar Chart
                    val maxProductAmt = productBreakdown.maxOfOrNull { it.totalAmountInr }?.coerceAtLeast(1.0) ?: 1.0
                    SectionCard(title = "Produce Breakdown") {
                        productBreakdown.forEach { item ->
                            val fraction = (item.totalAmountInr / maxProductAmt).toFloat().coerceIn(0.05f, 1f)
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.stockItemName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(
                                        formatCurrency(item.totalAmountInr),
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold,
                                        color = BhoomgaadiDarkGreen
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(SurfaceSubtle)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(BhoomgaadiGreen)
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${formatQty(item.totalQuantityKg)} kg across ${item.lineCount} weighment line(s)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Location Breakdown
                    if (locationBreakdown.isNotEmpty()) {
                        SectionCard(title = "Center / Location Totals") {
                            locationBreakdown.forEachIndexed { idx, loc ->
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
                                            "${loc.billCount} bill(s) • ${formatQty(loc.totalQuantityKg)} kg",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Text(
                                        formatCurrency(loc.totalAmountInr),
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold,
                                        color = SecondaryBlue
                                    )
                                }
                                if (idx < locationBreakdown.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
                            }
                        }
                    }

                    // Daily Procurement Trend
                    if (dailyTotals.isNotEmpty()) {
                        SectionCard(title = "Recent Daily Procurement") {
                            dailyTotals.forEachIndexed { idx, day ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(day.dayLabel, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                                        Text("${day.billCount} bill(s) • ${formatQty(day.totalQuantityKg)} kg", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text(
                                        formatCurrency(day.totalAmountInr),
                                        fontFamily = JetBrainsMono,
                                        fontWeight = FontWeight.Bold,
                                        color = BhoomgaadiDarkGreen
                                    )
                                }
                                if (idx < dailyTotals.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                // Recent Activity Audit Log
                if (auditLogs.isNotEmpty()) {
                    SectionCard(title = "Recent Center Activity Log") {
                        auditLogs.take(10).forEachIndexed { idx, log ->
                            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                Text(log.actionType.label, fontWeight = FontWeight.SemiBold, color = BhoomgaadiDarkGreen)
                                Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${formatDateTime(log.timestampMillis)} • ${log.performedBy}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                            if (idx < minOf(auditLogs.size, 10) - 1) HorizontalDivider(color = BorderGray.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}
