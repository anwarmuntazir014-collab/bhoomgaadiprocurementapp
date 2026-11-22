package com.example.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.BillWithItems
import com.example.domain.model.BillStatus
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProcurementHistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onCreateBill: () -> Unit,
    onOpenBillDetail: (billUuid: String) -> Unit
) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val bills by viewModel.filteredBills.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    var showSearchPanel by remember { mutableStateOf(false) }

    BhoomgaadiScaffold(
        title = strings.procurementHistory,
        subtitle = "${searchState.quickRange.label} (${bills.size})",
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { showSearchPanel = !showSearchPanel },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_toggle_search_btn")
            ) {
                Icon(
                    imageVector = if (showSearchPanel) Icons.Filled.FilterListOff else Icons.Filled.Search,
                    contentDescription = "Search & Filter"
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Date Filter Chips + Status Filter Chips (Layout 3 Screen 2)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(HistoryQuickRange.entries) { range ->
                                FilterChip(
                                    selected = searchState.quickRange == range,
                                    onClick = { viewModel.updateSearch { it.copy(quickRange = range) } },
                                    label = { Text(range.label) },
                                    modifier = Modifier.testTag("history_range_${range.name.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BillFilterStatus.entries.forEach { st ->
                                FilterChip(
                                    selected = searchState.statusFilter == st,
                                    onClick = { viewModel.updateSearch { it.copy(statusFilter = st) } },
                                    label = { Text(st.label) }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { showSearchPanel = !showSearchPanel },
                                modifier = Modifier.testTag("history_search_expand_btn")
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showSearchPanel) "Hide" else "Search")
                            }
                        }
                    }
                }

                // Expandable Multi-Field Search Panel matching Layout 3 Screen 2
                if (showSearchPanel) {
                    item {
                        SectionCard(title = "Search & Filter") {
                            OutlinedTextField(
                                value = searchState.billNumberQuery,
                                onValueChange = { v -> viewModel.updateSearch { it.copy(billNumberQuery = v) } },
                                label = { Text("Search by Bill Number") },
                                leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_search_bill_no")
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = searchState.aadhaarQuery,
                                onValueChange = { v ->
                                    viewModel.updateSearch { it.copy(aadhaarQuery = v.filter { c -> c.isDigit() }.take(12)) }
                                },
                                label = { Text("Search by Farmer Aadhaar") },
                                leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_search_aadhaar")
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = searchState.farmerNameQuery,
                                onValueChange = { v -> viewModel.updateSearch { it.copy(farmerNameQuery = v) } },
                                label = { Text("Search by Farmer Name / Village") },
                                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_search_name")
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                FieldActionButton(
                                    text = strings.search,
                                    icon = Icons.Filled.Search,
                                    color = BhoomgaadiDarkGreen,
                                    modifier = Modifier.weight(1f),
                                    onClick = { showSearchPanel = false }
                                )
                                FieldActionButton(
                                    text = strings.clear,
                                    icon = Icons.Filled.Refresh,
                                    color = SlateTileBg,
                                    contentColor = TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.clearFilters() }
                                )
                            }
                        }
                    }
                }

                // Tabular Bill History List matching Layout 3 Screen 3
                if (bills.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "No Procurement Bills Found",
                            description = "Create a new bill or adjust your search and date filters.",
                            actionLabel = strings.createNewBill,
                            onAction = onCreateBill
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                .background(BhoomgaadiDarkGreen)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bill No.", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.7f))
                            Text("Farmer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                            Text("Date", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f))
                            Text("Wt(kg)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                            Text("Amt(₹)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                            Spacer(Modifier.width(18.dp))
                        }
                    }

                    items(bills, key = { it.header.billUuid }) { bill ->
                        HistoryTableRow(
                            bill = bill,
                            onClick = { onOpenBillDetail(bill.header.billUuid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTableRow(
    bill: BillWithItems,
    onClick: () -> Unit
) {
    val h = bill.header
    val isCancelled = h.billStatus == BillStatus.CANCELLED
    val shortDay = remember(h.billDateMillis) {
        SimpleDateFormat("dd MMM\nyyyy", Locale.US).format(Date(h.billDateMillis))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, BorderGray.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .background(if (isCancelled) DangerLightRed.copy(alpha = 0.45f) else CardWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("history_bill_row_${h.billNumber}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.7f)) {
            Text(
                text = h.billNumber,
                fontFamily = JetBrainsMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCancelled) DangerRed else BhoomgaadiDarkGreen,
                textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None
            )
            if (isCancelled) {
                Text("CANCELLED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DangerRed)
            } else if (h.revisionNumber > 0) {
                Text("Rev ${h.revisionNumber}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SecondaryBlue)
            }
        }

        Column(modifier = Modifier.weight(1.5f).padding(horizontal = 4.dp)) {
            Text(
                text = h.farmerNameSnapshot,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = h.farmerVillageSnapshot,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = shortDay,
            fontSize = 10.sp,
            color = TextSecondary,
            lineHeight = 13.sp,
            modifier = Modifier.weight(1.0f)
        )

        Text(
            text = formatQty(h.totalQuantityKg),
            fontSize = 11.sp,
            fontFamily = JetBrainsMono,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.9f)
        )

        Text(
            text = formatCurrencyWhole(h.totalAmountInr),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMono,
            color = if (isCancelled) DangerRed else TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open Bill",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
