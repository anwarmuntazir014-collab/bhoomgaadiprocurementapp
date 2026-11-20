package com.example.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.BankAccountType
import com.example.domain.model.BillStatus
import com.example.domain.model.CancellationReason
import com.example.pdf.PdfPrintShareHelper
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun BillDetailScreen(
    billUuid: String,
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val bill by viewModel.observeBill(billUuid).collectAsStateWithLifecycle(initialValue = null)
    val auditLogs by viewModel.observeBillAuditLogs(billUuid).collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showVoidDialog by remember { mutableStateOf(false) }

    fun ensurePdfAndRun(action: (File) -> Unit) {
        val current = bill ?: return
        val existing = File(current.header.pdfFilePath)
        if (current.header.pdfFilePath.isNotBlank() && existing.exists()) {
            action(existing)
        } else {
            viewModel.regeneratePdf(current.header.billUuid) { res ->
                res.fold(
                    onSuccess = { path -> action(File(path)) },
                    onFailure = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err.message ?: "PDF generation failed") }
                    }
                )
            }
        }
    }

    BhoomgaadiScaffold(
        title = bill?.header?.billNumber ?: "Bill Detail",
        subtitle = bill?.header?.billStatus?.label,
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        val currentBill = bill
        if (currentBill == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BhoomgaadiGreen)
            }
            return@BhoomgaadiScaffold
        }

        val h = currentBill.header
        val isVoid = h.billStatus == BillStatus.CANCELLED

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
                // Header Status Card
                SectionCard(
                    containerColor = if (isVoid) DangerLightRed else BhoomgaadiLightGreen
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = h.billNumber,
                                fontFamily = JetBrainsMono,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isVoid) DangerRed else BhoomgaadiDarkGreen
                            )
                            Text(
                                text = formatDateTime(h.billDateMillis),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        StatusPill(
                            text = if (isVoid) "VOID / CANCELLED" else "CONFIRMED",
                            bgColor = if (isVoid) DangerRed else BhoomgaadiDarkGreen,
                            fgColor = CardWhite
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Net Weight", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                "${formatQty(h.totalQuantityKg)} kg (${h.totalBags} bags)",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Payable Amount", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                formatCurrency(h.totalAmountInr),
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = if (isVoid) DangerRed else BhoomgaadiDarkGreen
                            )
                        }
                    }

                    if (isVoid) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = DangerRed.copy(alpha = 0.3f))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Cancellation Reason: ${h.cancellationReason?.label ?: "Unspecified"}",
                            color = DangerRed,
                            fontWeight = FontWeight.Bold
                        )
                        if (h.cancellationNotes.isNotBlank()) {
                            Text("Notes: ${h.cancellationNotes}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // PDF Print & Share Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FieldActionButton(
                        text = "Print Receipt",
                        icon = Icons.Filled.Print,
                        color = BhoomgaadiGreen,
                        testTag = "bill_detail_print_button",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ensurePdfAndRun { file ->
                                PdfPrintShareHelper.printPdf(context, file, "Bhoomgaadi_${h.billNumber}")
                            }
                        }
                    )
                    FieldActionButton(
                        text = "Share PDF",
                        icon = Icons.Filled.Share,
                        color = SecondaryBlue,
                        testTag = "bill_detail_share_button",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ensurePdfAndRun { file ->
                                PdfPrintShareHelper.shareFile(
                                    context,
                                    file,
                                    "application/pdf",
                                    "Share Procurement Voucher ${h.billNumber}"
                                )
                            }
                        }
                    )
                }

                // Farmer & Bank Snapshot
                SectionCard(title = "Farmer & Payment Snapshot") {
                    InfoRow("Farmer Name", h.farmerNameSnapshot, boldValue = true)
                    InfoRow("Aadhaar", maskAadhaar(h.farmerAadhaarSnapshot), monoValue = true)
                    InfoRow("Village / Block", listOf(h.farmerVillageSnapshot, h.farmerBlockSnapshot).filter { it.isNotBlank() }.joinToString(", "))
                    InfoRow("Membership", "${h.farmerMembershipSnapshot.label}${if (h.farmerMemberCodeSnapshot.isNotBlank()) " (${h.farmerMemberCodeSnapshot})" else ""}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderGray.copy(alpha = 0.6f))
                    InfoRow("Bank Account", h.bankAccountTypeSnapshot.label, boldValue = true)
                    if (h.bankAccountTypeSnapshot != BankAccountType.NONE) {
                        InfoRow("Account Holder", h.accountHolderSnapshot)
                        InfoRow("Bank Name", h.bankNameSnapshot)
                        InfoRow("Account No.", h.accountNumberSnapshot, monoValue = true)
                        InfoRow("IFSC Code", h.ifscSnapshot, monoValue = true)
                    }
                }

                // Center & Logistics
                SectionCard(title = "Center & Storage") {
                    InfoRow("Location", h.locationNameSnapshot, boldValue = true)
                    InfoRow("Temporary Godown", h.godownNameSnapshot)
                    if (h.weighingSlipRef.isNotBlank()) InfoRow("Weighing Slip Ref", h.weighingSlipRef, monoValue = true)
                    if (h.remarks.isNotBlank()) InfoRow("Remarks", h.remarks)
                    InfoRow("Operator", h.operatorName)
                    InfoRow("Signatory", h.authorizedSignatory)
                    InfoRow("Drive Sync", h.driveUploadStatus.label)
                }

                // Line Items
                SectionCard(title = "Weighed Produce Items (${currentBill.items.size})") {
                    currentBill.items.sortedBy { it.lineOrder }.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${index + 1}. ${item.stockItemNameSnapshot}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${item.bagCount} bag(s) • Gross ${formatQty(item.grossWeightKg)} - Tare ${formatQty(item.tareWeightKg)} = Net ${formatQty(item.netQuantityKg)} kg",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${item.qualityGrade}${if (item.moisturePercent.isNotBlank()) " • Moisture ${item.moisturePercent}%" else ""} • ₹${formatQty(item.ratePerKg)}/kg",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BhoomgaadiDarkGreen
                                )
                            }
                            Text(
                                formatCurrency(item.lineAmountInr),
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        if (index < currentBill.items.lastIndex) {
                            HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
                        }
                    }
                }

                // Void / Cancel Button (Immutable Ledger Pattern)
                if (!isVoid) {
                    OutlinedButton(
                        onClick = { showVoidDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        border = BorderStroke(1.dp, DangerRed),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("bill_detail_void_button")
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel / Void This Bill", fontWeight = FontWeight.Bold)
                    }
                }

                // Audit Trail
                if (auditLogs.isNotEmpty()) {
                    SectionCard(title = "Immutable Audit Log") {
                        auditLogs.forEach { log ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(log.actionType.label, fontWeight = FontWeight.SemiBold, color = BhoomgaadiDarkGreen)
                                Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${formatDateTime(log.timestampMillis)} • ${log.performedBy} (${log.deviceCode})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showVoidDialog) {
        var selectedReason by remember { mutableStateOf(CancellationReason.INCORRECT_WEIGHT) }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVoidDialog = false },
            title = { Text("Cancel / Void Voucher", color = DangerRed, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Voided bills stay permanently in the SQLite audit ledger and their PDF is re-stamped with a VOID watermark.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    SimpleDropdown(
                        label = "Cancellation Reason",
                        selectedText = selectedReason.label,
                        items = CancellationReason.entries,
                        itemLabel = { it.label },
                        onSelected = { selectedReason = it }
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Audit Notes (Optional)") },
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVoidDialog = false
                        viewModel.cancelBill(billUuid, selectedReason, notes) { res ->
                            scope.launch {
                                res.fold(
                                    onSuccess = { snackbarHostState.showSnackbar("Bill voided and VOID PDF updated.") },
                                    onFailure = { snackbarHostState.showSnackbar(it.message ?: "Failed to void bill.") }
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Confirm Void")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidDialog = false }) { Text("Keep Bill") }
            }
        )
    }
}
