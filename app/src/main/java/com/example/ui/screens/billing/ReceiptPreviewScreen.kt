package com.example.ui.screens.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.BillStatus
import com.example.pdf.BillPdfGenerator
import com.example.pdf.PdfPrintShareHelper
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReceiptPreviewScreen(
    billUuid: String,
    historyViewModel: HistoryViewModel,
    onClose: () -> Unit
) {
    val bill by historyViewModel.observeBill(billUuid).collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = LocalAppStrings.current

    fun ensurePdf(action: (File) -> Unit) {
        val current = bill ?: return
        val existing = File(current.header.pdfFilePath)
        if (current.header.pdfFilePath.isNotBlank() && existing.exists()) {
            action(existing)
        } else {
            historyViewModel.regeneratePdf(current.header.billUuid) { res ->
                res.fold(
                    onSuccess = { path -> action(File(path)) },
                    onFailure = { err -> scope.launch { snackbarHostState.showSnackbar(err.message ?: "PDF error") } }
                )
            }
        }
    }

    BhoomgaadiScaffold(
        title = strings.receiptPreview,
        subtitle = bill?.header?.billNumber,
        onBack = onClose,
        snackbarHostState = snackbarHostState,
        bottomBar = {
            Surface(color = CardWhite, shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FieldActionButton(
                        text = "Print",
                        icon = Icons.Filled.Print,
                        color = SecondaryBlue,
                        testTag = "receipt_preview_print_btn",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val num = bill?.header?.billNumber ?: "Receipt"
                            ensurePdf { PdfPrintShareHelper.printPdf(context, it, num) }
                        }
                    )
                    FieldActionButton(
                        text = "Share",
                        icon = Icons.Filled.Share,
                        color = BhoomgaadiGreen,
                        testTag = "receipt_preview_share_btn",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val num = bill?.header?.billNumber ?: "Receipt"
                            ensurePdf { PdfPrintShareHelper.shareFile(context, it, "application/pdf", "Share $num.pdf") }
                        }
                    )
                    FieldActionButton(
                        text = "Close",
                        color = SlateTileBg,
                        contentColor = TextPrimary,
                        modifier = Modifier.weight(0.8f),
                        onClick = onClose
                    )
                }
            }
        }
    ) { padding ->
        val current = bill
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BhoomgaadiDarkGreen)
            }
            return@BhoomgaadiScaffold
        }
        val h = current.header
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
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                // Physical Bill Card matching Layout 2 Screen 10 & Layout 3 Screen 7
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(Modifier.width(48.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BHOOMGAADI", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = BhoomgaadiDarkGreen)
                                Text("FARMER PROCUREMENT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                text = if (h.revisionNumber > 0) "Rev ${h.revisionNumber}" else "Farmer Copy",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        if (h.billStatus == BillStatus.CANCELLED) {
                            Spacer(Modifier.height(6.dp))
                            Surface(color = DangerLightRed, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "CANCELLED / VOID",
                                    color = DangerRed,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = TextPrimary)
                        Spacer(Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bill No.: ${h.billNumber}", fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Date: ${formatShortDate(h.billDateMillis)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(8.dp))
                        ReceiptMetaLine("Farmer Name", h.farmerNameSnapshot)
                        ReceiptMetaLine("Aadhaar No.", formatAadhaarSpaced(h.farmerAadhaarSnapshot))
                        ReceiptMetaLine("Village / Block", "${h.farmerVillageSnapshot}, ${h.farmerBlockSnapshot}")
                        ReceiptMetaLine("Location", h.locationNameSnapshot)
                        ReceiptMetaLine("Temp. Godown", h.godownNameSnapshot)
                        ReceiptMetaLine("Permanent Godown", "____________________")

                        Spacer(Modifier.height(10.dp))

                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BhoomgaadiDarkGreen)
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text("#", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                            Text("Product", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.2f))
                            Text("Bags", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                            Text("Wt", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                            Text("Rate", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                            Text("Amt", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
                        }

                        current.items.sortedBy { it.lineOrder }.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, BorderGray)
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text("${idx + 1}", fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                                Text(item.stockItemNameSnapshot, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(2.2f))
                                Text("${item.bagCount}", fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                                Text(formatQty(item.netQuantityKg), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                                Text(formatQty(item.ratePerKg), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                                Text(formatCurrencyWhole(item.lineAmountInr), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.3f))
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("Total Bags : ${h.totalBags}", fontSize = 12.sp)
                            Text("Total Weight (kg) : ${formatQty(h.totalQuantityKg)}", fontSize = 12.sp)
                            Text("Procurement Amount : ${formatCurrency(procAmt)}", fontSize = 12.sp)
                            if (h.membershipAmountInr > 0.0) {
                                Text("Membership Amount : ${formatCurrency(h.membershipAmountInr)}", fontSize = 12.sp)
                            }
                            Text(
                                "Total Payable : ${formatCurrency(h.totalAmountInr)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BhoomgaadiDarkGreen
                            )
                        }

                        Spacer(Modifier.height(26.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SigBlock("Farmer Signature")
                            SigBlock("Quality Manager")
                            SigBlock("Authorized Signatory")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Offline PDF: ${h.publicPdfDisplayPath.ifBlank { "${BillPdfGenerator.PUBLIC_FOLDER_DISPLAY}/${h.billNumber}.pdf" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ReceiptMetaLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.5.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(120.dp))
        Text(": $value", fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SigBlock(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(modifier = Modifier.width(88.dp), color = TextPrimary)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = TextSecondary)
    }
}
