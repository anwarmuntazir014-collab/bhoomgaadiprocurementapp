package com.example.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.BillWithItems
import com.example.domain.model.BillStatus
import com.example.domain.model.DriveUploadStatus
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun ProcurementHistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onCreateBill: () -> Unit,
    onOpenBillDetail: (String) -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filter by viewModel.filterStatus.collectAsStateWithLifecycle()
    val bills by viewModel.filteredBills.collectAsStateWithLifecycle()

    val confirmedAmount = bills.filter { it.header.billStatus == BillStatus.CONFIRMED }
        .sumOf { it.header.totalAmountInr }
    val confirmedKg = bills.filter { it.header.billStatus == BillStatus.CONFIRMED }
        .sumOf { it.header.totalQuantityKg }

    BhoomgaadiScaffold(
        title = "Procurement Bills",
        subtitle = "${bills.size} matching vouchers",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateBill,
                containerColor = ProcurementAmber,
                contentColor = CardWhite,
                modifier = Modifier.testTag("history_new_bill_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Bill")
            }
        }
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
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = { Text("Search bill no, farmer, village, center...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BillFilterStatus.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { viewModel.updateFilter(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("history_filter_${option.name.lowercase()}")
                        )
                    }
                }

                if (bills.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = BhoomgaadiLightGreen,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Confirmed Qty: ${formatQty(confirmedKg)} kg",
                                fontFamily = JetBrainsMono,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BhoomgaadiDarkGreen
                            )
                            Text(
                                formatCurrency(confirmedAmount),
                                fontFamily = JetBrainsMono,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BhoomgaadiDarkGreen
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (bills.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "No Procurement Bills Found",
                        description = "Issued vouchers appear here immediately with printable A4 PDFs and audit records.",
                        actionLabel = "Create Procurement Bill",
                        onAction = onCreateBill
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        items(bills, key = { it.header.billUuid }) { bill ->
                            HistoryBillCard(
                                bill = bill,
                                onClick = { onOpenBillDetail(bill.header.billUuid) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryBillCard(
    bill: BillWithItems,
    onClick: () -> Unit
) {
    val h = bill.header
    val isVoid = h.billStatus == BillStatus.CANCELLED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_bill_card_${h.billNumber}")
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, if (isVoid) DangerRed.copy(alpha = 0.5f) else BorderGray)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = h.billNumber,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = BhoomgaadiDarkGreen
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (h.driveUploadStatus == DriveUploadStatus.UPLOADED) {
                        StatusPill("SYNCED", SecondaryLightBlue, SecondaryBlue)
                    }
                    StatusPill(
                        text = if (isVoid) "VOID" else "CONFIRMED",
                        bgColor = if (isVoid) DangerLightRed else BhoomgaadiLightGreen,
                        fgColor = if (isVoid) DangerRed else BhoomgaadiDarkGreen
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = h.farmerNameSnapshot,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${h.farmerVillageSnapshot} • ${h.locationNameSnapshot} (${h.godownNameSnapshot})",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderGray.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${formatDateTime(h.billDateMillis)} • ${formatQty(h.totalQuantityKg)} kg",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = formatCurrency(h.totalAmountInr),
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isVoid) DangerRed else TextPrimary
                )
            }
        }
    }
}
