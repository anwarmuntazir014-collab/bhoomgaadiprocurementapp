package com.example.ui.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppPreferences
import com.example.data.local.entities.UserEntity
import com.example.domain.model.BillStatus
import com.example.domain.model.CancellationReason
import com.example.domain.model.DriveUploadStatus
import com.example.domain.model.UserRole
import com.example.pdf.BillPdfGenerator
import com.example.pdf.PdfPrintShareHelper
import com.example.sync.DriveUploadWorker
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun BillDetailScreen(
    billUuid: String,
    currentUser: UserEntity,
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onEditBill: (billUuid: String) -> Unit,
    onOpenReceiptPreview: (billUuid: String) -> Unit
) {
    val bill by viewModel.observeBill(billUuid).collectAsStateWithLifecycle(initialValue = null)
    val auditLogs by viewModel.observeBillAuditLogs(billUuid).collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = LocalAppStrings.current

    var showCancelDialog by remember { mutableStateOf(false) }
    var selectedCancelReason by remember { mutableStateOf(CancellationReason.INCORRECT_WEIGHT) }
    var cancelNotes by remember { mutableStateOf("") }

    fun ensurePdf(action: (File) -> Unit) {
        val current = bill ?: return
        val existing = File(current.header.pdfFilePath)
        if (current.header.pdfFilePath.isNotBlank() && existing.exists()) {
            action(existing)
        } else {
            viewModel.regeneratePdf(current.header.billUuid) { res ->
                res.fold(
                    onSuccess = { path -> action(File(path)) },
                    onFailure = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err.message ?: "Unable to generate PDF") }
                    }
                )
            }
        }
    }

    BhoomgaadiScaffold(
        title = "Bill Details",
        subtitle = bill?.header?.billNumber,
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        val current = bill
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BhoomgaadiDarkGreen)
            }
            return@BhoomgaadiScaffold
        }

        val h = current.header
        val isCancelled = h.billStatus == BillStatus.CANCELLED
        val canEdit = viewModel.canEditBill(h, currentUser)
        val procAmt = if (h.procurementAmountInr > 0.0) h.procurementAmountInr else (h.totalAmountInr - h.membershipAmountInr)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Status Card (Layout 3 Screen 4)
                Surface(
                    color = if (isCancelled) DangerLightRed else BhoomgaadiLightGreen,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isCancelled) DangerRed.copy(alpha = 0.5f) else BhoomgaadiGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bill No. ${h.billNumber}",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCancelled) DangerRed else BhoomgaadiDarkGreen
                            )
                            Text(
                                text = "Date: ${formatDateTime(h.billDateMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            if (h.revisionNumber > 0) {
                                Text(
                                    text = "Revision #${h.revisionNumber} by ${h.lastEditedBy} • Reason: ${h.lastEditReason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryBlue
                                )
                            }
                        }
                        StatusPill(
                            text = h.billStatus.label,
                            bgColor = if (isCancelled) DangerRed else BhoomgaadiDarkGreen,
                            fgColor = Color.White
                        )
                    }
                }

                // Farmer Details
                SectionCard(title = "Farmer Details") {
                    KeyValueTable(
                        rows = listOf(
                            "Name" to h.farmerNameSnapshot,
                            "Aadhaar" to formatAadhaarSpaced(h.farmerAadhaarSnapshot),
                            "Village / Block" to "${h.farmerVillageSnapshot}, ${h.farmerBlockSnapshot}",
                            "Membership" to if (h.membershipEnrolled) "Member (Enrolled ₹${h.membershipAmountInr.toInt()})" else h.farmerMembershipSnapshot.label,
                            "Bank / Account" to if (h.accountNumberSnapshot.isNotBlank()) "${h.bankNameSnapshot} • ${h.accountNumberSnapshot}" else "No Bank Account"
                        )
                    )
                }

                // Procurement Details
                SectionCard(title = "Procurement Details") {
                    KeyValueTable(
                        rows = listOf(
                            "Location" to h.locationNameSnapshot,
                            "Temporary Godown" to h.godownNameSnapshot,
                            "Authorized Signatory" to h.authorizedSignatory,
                            "Operator" to h.operatorName
                        )
                    )
                }

                // Produce Items & Totals
                SectionCard(title = "Items (${current.items.size})") {
                    current.items.sortedBy { it.lineOrder }.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${idx + 1}. ${item.stockItemNameSnapshot}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${item.bagCount} bags • ${formatQty(item.netQuantityKg)} kg @ ₹${formatQty(item.ratePerKg)}" +
                                        if (item.moisturePercent.isNotBlank()) " • M:${item.moisturePercent}%" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Text(formatCurrency(item.lineAmountInr), fontWeight = FontWeight.Bold, fontFamily = JetBrainsMono)
                        }
                        if (idx < current.items.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = BhoomgaadiDarkGreen)
                    Spacer(Modifier.height(6.dp))
                    InfoRow("Total Bags", h.totalBags.toString(), boldValue = true)
                    InfoRow("Total Weight (kg)", formatQty(h.totalQuantityKg), boldValue = true, monoValue = true)
                    InfoRow("Procurement Amount (₹)", formatCurrency(procAmt), boldValue = true, monoValue = true)
                    if (h.membershipAmountInr > 0.0) {
                        InfoRow("Membership Amount (₹)", formatCurrency(h.membershipAmountInr), boldValue = true, monoValue = true)
                    }

                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = BhoomgaadiLightGreen,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Payable (₹)", fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen)
                            Text(
                                formatCurrency(h.totalAmountInr),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = JetBrainsMono,
                                color = BhoomgaadiDarkGreen
                            )
                        }
                    }
                }

                // Bill Actions matching Layout 3 Screen 5
                SectionCard(title = "Bill Actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldActionButton(
                            text = "View PDF / Receipt Preview",
                            icon = Icons.Filled.PictureAsPdf,
                            color = SecondaryBlue,
                            testTag = "bill_detail_view_pdf_btn",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenReceiptPreview(h.billUuid) }
                        )
                        FieldActionButton(
                            text = "Print / Reprint",
                            icon = Icons.Filled.Print,
                            color = BhoomgaadiDarkGreen,
                            testTag = "bill_detail_print_btn",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { ensurePdf { PdfPrintShareHelper.printPdf(context, it, h.billNumber) } }
                        )
                        FieldActionButton(
                            text = "Share PDF",
                            icon = Icons.Filled.Share,
                            color = SecondaryLightBlue,
                            contentColor = SecondaryBlue,
                            testTag = "bill_detail_share_btn",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { ensurePdf { PdfPrintShareHelper.shareFile(context, it, "application/pdf", "Share ${h.billNumber}.pdf") } }
                        )
                        FieldActionButton(
                            text = "Save to Drive",
                            icon = Icons.Filled.CloudUpload,
                            color = SecondaryLightBlue,
                            contentColor = SecondaryBlue,
                            testTag = "bill_detail_drive_btn",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val prefs = AppPreferences(context)
                                if (!prefs.isDriveConfigured) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Google Drive Sync is not configured yet. Ask Admin to set it up in Settings.")
                                    }
                                } else {
                                    ensurePdf {
                                        DriveUploadWorker.enqueue(context)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Queued ${h.billNumber}.pdf for Google Drive upload.")
                                        }
                                    }
                                }
                            }
                        )

                        // Role-aware Audited Bill Edit button
                        if (!isCancelled) {
                            FieldActionButton(
                                text = strings.editBill,
                                icon = Icons.Filled.Edit,
                                color = ProcurementAmber,
                                enabled = canEdit,
                                testTag = "bill_detail_edit_btn",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onEditBill(h.billUuid) }
                            )
                            if (!canEdit && currentUser.role == UserRole.OFFICER) {
                                Text(
                                    text = "Procurement Officers can only edit bills created today. Ask an Admin to revise older bills.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Document Status Card (Layout 3 Screen 5)
                SectionCard(title = "Document Status") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BhoomgaadiGreen)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("PDF Generated Offline", fontWeight = FontWeight.Bold)
                            Text(
                                h.publicPdfDisplayPath.ifBlank { "${BillPdfGenerator.PUBLIC_FOLDER_DISPLAY}/${h.billNumber}.pdf" },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val uploaded = h.driveUploadStatus == DriveUploadStatus.UPLOADED
                        Icon(
                            imageVector = if (uploaded) Icons.Filled.CloudDone else Icons.Filled.CloudQueue,
                            contentDescription = null,
                            tint = if (uploaded) BhoomgaadiGreen else ProcurementAmber
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Google Drive Sync: ${h.driveUploadStatus.label}", fontWeight = FontWeight.Bold)
                            Text(
                                if (uploaded) "Drive File ID: ${h.driveFileId}" else "Stored safely offline on device; will sync when Drive is configured.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Cancel / Void Bill button (Layout 3 Screen 5 & 6)
                if (!isCancelled) {
                    FieldActionButton(
                        text = strings.cancelVoidBill,
                        icon = Icons.Filled.Block,
                        color = DangerLightRed,
                        contentColor = DangerRed,
                        testTag = "bill_detail_cancel_btn",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showCancelDialog = true }
                    )
                } else {
                    SectionCard(title = "Cancellation Details", containerColor = DangerLightRed) {
                        InfoRow("Reason", h.cancellationReason?.label ?: "Cancelled", boldValue = true)
                        if (h.cancellationNotes.isNotBlank()) {
                            InfoRow("Notes", h.cancellationNotes)
                        }
                        h.cancelledAtMillis?.let { InfoRow("Cancelled At", formatDateTime(it)) }
                    }
                }

                // Audit History Trail
                if (auditLogs.isNotEmpty()) {
                    SectionCard(title = "Audit History (${auditLogs.size})") {
                        auditLogs.forEachIndexed { idx, log ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(log.actionType.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BhoomgaadiDarkGreen)
                                Text(log.summary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${log.performedBy} • ${formatDateTime(log.timestampMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            if (idx < auditLogs.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    // Cancel Bill Confirmation Dialog matching Layout 3 Screen 6
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel / Void Bill", color = DangerRed, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = DangerLightRed,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "This will permanently mark the bill as Cancelled/Void and update the PDF. This action cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    SimpleDropdown(
                        label = "Reason for Cancellation *",
                        selectedText = selectedCancelReason.label,
                        items = CancellationReason.entries,
                        itemLabel = { it.label },
                        onSelected = { selectedCancelReason = it }
                    )
                    OutlinedTextField(
                        value = cancelNotes,
                        onValueChange = { cancelNotes = it },
                        label = { Text("Additional Remarks (Optional)") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelBill(billUuid, selectedCancelReason, cancelNotes, currentUser) { res ->
                            showCancelDialog = false
                            scope.launch {
                                res.fold(
                                    onSuccess = { snackbarHostState.showSnackbar("Bill cancelled and PDF updated.") },
                                    onFailure = { snackbarHostState.showSnackbar(it.message ?: "Cancellation failed.") }
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Cancel Bill", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Back") }
            }
        )
    }
}
