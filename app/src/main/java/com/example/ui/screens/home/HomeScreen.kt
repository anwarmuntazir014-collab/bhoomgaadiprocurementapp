package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AppPreferences
import com.example.data.local.entities.BillWithItems
import com.example.domain.model.BillStatus
import com.example.ui.components.*
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    prefs: AppPreferences,
    dashboardViewModel: DashboardViewModel,
    historyViewModel: HistoryViewModel,
    onCreateBill: () -> Unit,
    onOpenFarmers: () -> Unit,
    onAddFarmer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBillDetail: (String) -> Unit
) {
    val summary by dashboardViewModel.summary.collectAsStateWithLifecycle()
    val farmerCount by dashboardViewModel.registeredFarmerCount.collectAsStateWithLifecycle()
    val bills by historyViewModel.filteredBills.collectAsStateWithLifecycle()

    BhoomgaadiScaffold(
        title = stringResource(R.string.app_name),
        subtitle = "Device ${prefs.deviceCode} • Season ${prefs.procurementYear}",
        actions = {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_settings_icon")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_settings)
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
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Harvest Banner Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_home_harvest_banner_1795197115312),
                                contentDescription = stringResource(R.string.cd_harvest_banner),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0x661B5E20),
                                                Color(0xD9103814)
                                            )
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(
                                        text = "OFFLINE SQLITE READY",
                                        bgColor = Color(0xCCFFFFFF),
                                        fgColor = BhoomgaadiDarkGreen
                                    )
                                    Text(
                                        text = "Next: ${prefs.nextBillNumberPreview()}",
                                        fontFamily = JetBrainsMono,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFE082)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Organic Field Procurement",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Weighment, instant PDF receipts & audit-ready village records.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFE8F5E9)
                                    )
                                }
                            }
                        }
                    }
                }

                // Live KPI Strip
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeKpiTile(
                            label = "Procured Value",
                            value = formatCurrency(summary.totalAmountInr),
                            accent = BhoomgaadiDarkGreen,
                            bg = BhoomgaadiLightGreen,
                            modifier = Modifier.weight(1f)
                        )
                        HomeKpiTile(
                            label = "Net Weight",
                            value = "${formatQty(summary.totalQuantityKg)} kg",
                            accent = ProcurementOrange,
                            bg = ProcurementLightOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeKpiTile(
                            label = "Confirmed Bills",
                            value = summary.totalBillsConfirmed.toString(),
                            accent = SecondaryBlue,
                            bg = SecondaryLightBlue,
                            modifier = Modifier.weight(1f)
                        )
                        HomeKpiTile(
                            label = "Registered Farmers",
                            value = farmerCount.toString(),
                            accent = BhoomgaadiGreen,
                            bg = CardWhite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Primary CTA
                item {
                    FieldActionButton(
                        text = "New Procurement Bill",
                        icon = Icons.Filled.AddShoppingCart,
                        color = ProcurementAmber,
                        testTag = "home_create_bill_button",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCreateBill
                    )
                }

                // Quick Field Modules Grid
                item {
                    Text(
                        text = "Field Operations",
                        style = MaterialTheme.typography.titleMedium,
                        color = BhoomgaadiDarkGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ModuleActionCard(
                                title = "Farmers",
                                subtitle = "$farmerCount profiles",
                                icon = Icons.Filled.Groups,
                                iconTint = BhoomgaadiDarkGreen,
                                iconBg = BhoomgaadiLightGreen,
                                testTag = "home_tile_farmers",
                                modifier = Modifier.weight(1f),
                                onClick = onOpenFarmers
                            )
                            ModuleActionCard(
                                title = "Register Farmer",
                                subtitle = "Aadhaar + Bank",
                                icon = Icons.Filled.PersonAddAlt1,
                                iconTint = SecondaryBlue,
                                iconBg = SecondaryLightBlue,
                                testTag = "home_tile_add_farmer",
                                modifier = Modifier.weight(1f),
                                onClick = onAddFarmer
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ModuleActionCard(
                                title = "Bill History",
                                subtitle = "${bills.size} receipts",
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                iconTint = ProcurementOrange,
                                iconBg = ProcurementLightOrange,
                                testTag = "home_tile_history",
                                modifier = Modifier.weight(1f),
                                onClick = onOpenHistory
                            )
                            ModuleActionCard(
                                title = "Live Summary",
                                subtitle = "Crop & Center KPIs",
                                icon = Icons.Filled.BarChart,
                                iconTint = BhoomgaadiGreen,
                                iconBg = BhoomgaadiLightGreen,
                                testTag = "home_tile_dashboard",
                                modifier = Modifier.weight(1f),
                                onClick = onOpenDashboard
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ModuleActionCard(
                                title = "Export & Backup",
                                subtitle = "CSV + SQLite .db",
                                icon = Icons.Filled.CloudUpload,
                                iconTint = SecondaryBlue,
                                iconBg = SecondaryLightBlue,
                                testTag = "home_tile_export",
                                modifier = Modifier.weight(1f),
                                onClick = onOpenExport
                            )
                            ModuleActionCard(
                                title = "Master & Setup",
                                subtitle = "Rates, Godowns",
                                icon = Icons.Filled.Tune,
                                iconTint = TextPrimary,
                                iconBg = SurfaceSubtle,
                                testTag = "home_tile_settings",
                                modifier = Modifier.weight(1f),
                                onClick = onOpenSettings
                            )
                        }
                    }
                }

                // Recent Bills Feed
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Procurement Bills",
                            style = MaterialTheme.typography.titleMedium,
                            color = BhoomgaadiDarkGreen,
                            fontWeight = FontWeight.Bold
                        )
                        if (bills.isNotEmpty()) {
                            TextButton(
                                onClick = onOpenHistory,
                                modifier = Modifier.testTag("home_view_all_bills")
                            ) {
                                Text("View All")
                            }
                        }
                    }
                }

                if (bills.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Agriculture,
                            title = "Ready for First Procurement",
                            description = "Register a farmer with Aadhaar & bank details, then weigh produce to generate an instant printable A4 PDF voucher offline.",
                            actionLabel = "Register First Farmer",
                            onAction = onAddFarmer
                        )
                    }
                } else {
                    items(bills.take(5), key = { it.header.billUuid }) { bill ->
                        RecentBillRow(
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
private fun HomeKpiTile(
    label: String,
    value: String,
    accent: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMono,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModuleActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentBillRow(
    bill: BillWithItems,
    onClick: () -> Unit
) {
    val h = bill.header
    val isVoid = h.billStatus == BillStatus.CANCELLED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_bill_${h.billNumber}")
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, if (isVoid) DangerRed.copy(alpha = 0.4f) else BorderGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = h.billNumber,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BhoomgaadiDarkGreen
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        text = if (isVoid) "VOID" else "CONFIRMED",
                        bgColor = if (isVoid) DangerLightRed else BhoomgaadiLightGreen,
                        fgColor = if (isVoid) DangerRed else BhoomgaadiDarkGreen
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = h.farmerNameSnapshot,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${h.farmerVillageSnapshot} • ${bill.items.size} item(s) • ${formatQty(h.totalQuantityKg)} kg",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(h.totalAmountInr),
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isVoid) DangerRed else TextPrimary
                )
                Text(
                    text = formatShortDate(h.billDateMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
