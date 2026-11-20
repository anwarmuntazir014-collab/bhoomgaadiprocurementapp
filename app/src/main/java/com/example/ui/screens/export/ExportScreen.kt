package com.example.ui.screens.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppPreferences
import com.example.export.CsvExportManager
import com.example.export.DatabaseBackupManager
import com.example.pdf.PdfPrintShareHelper
import com.example.sync.DriveUploadWorker
import com.example.ui.components.*
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportScreen(
    prefs: AppPreferences,
    csvExportManager: CsvExportManager,
    backupManager: DatabaseBackupManager,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val summary by dashboardViewModel.summary.collectAsStateWithLifecycle()

    var isBusy by remember { mutableStateOf(false) }
    var lastStatusMessage by remember { mutableStateOf<String?>(null) }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                val res = backupManager.exportBackupToUri(uri)
                isBusy = false
                val msg = res.fold(
                    onSuccess = { "SQLite backup saved successfully." },
                    onFailure = { it.message ?: "Backup export failed." }
                )
                lastStatusMessage = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                val res = backupManager.restoreFromUri(uri)
                isBusy = false
                val msg = res.fold(
                    onSuccess = { "Database restored successfully. Records refreshed." },
                    onFailure = { it.message ?: "Restore failed." }
                )
                lastStatusMessage = msg
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    BhoomgaadiScaffold(
        title = "Export, Backup & Sync",
        subtitle = "CSV Ledgers • SQLite .db • Drive Queue",
        onBack = onBack,
        snackbarHostState = snackbarHostState
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isBusy) {
                    LinearProgressIndicator(
                        color = BhoomgaadiGreen,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (lastStatusMessage != null) {
                    Surface(
                        color = BhoomgaadiLightGreen,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = BhoomgaadiDarkGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(lastStatusMessage.orEmpty(), color = BhoomgaadiDarkGreen)
                        }
                    }
                }

                // CSV Exports
                SectionCard(title = "1. Excel / Tally CSV Exports") {
                    Text(
                        "Export offline SQLite registers as standard UTF-8 CSV files ready for Excel, Google Sheets, or bank NEFT disbursement.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    FieldActionButton(
                        text = "Export Farmers Master CSV",
                        icon = Icons.Filled.Groups,
                        enabled = !isBusy,
                        testTag = "export_farmers_csv_btn",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                isBusy = true
                                runCatching { csvExportManager.exportFarmersCsv() }.fold(
                                    onSuccess = { file ->
                                        isBusy = false
                                        lastStatusMessage = "Generated ${file.name}"
                                        PdfPrintShareHelper.shareFile(context, file, "text/csv", "Share Farmers CSV")
                                    },
                                    onFailure = { err ->
                                        isBusy = false
                                        snackbarHostState.showSnackbar(err.message ?: "Export failed")
                                    }
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    FieldActionButton(
                        text = "Export Bills Summary CSV (Bank Payout)",
                        icon = Icons.Filled.AccountBalance,
                        color = SecondaryBlue,
                        enabled = !isBusy,
                        testTag = "export_bills_csv_btn",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                isBusy = true
                                runCatching { csvExportManager.exportBillsSummaryCsv() }.fold(
                                    onSuccess = { file ->
                                        isBusy = false
                                        lastStatusMessage = "Generated ${file.name}"
                                        PdfPrintShareHelper.shareFile(context, file, "text/csv", "Share Bills Summary CSV")
                                    },
                                    onFailure = { err ->
                                        isBusy = false
                                        snackbarHostState.showSnackbar(err.message ?: "Export failed")
                                    }
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    FieldActionButton(
                        text = "Export Produce Line-Items Ledger CSV",
                        icon = Icons.Filled.TableChart,
                        color = ProcurementAmber,
                        enabled = !isBusy,
                        testTag = "export_ledger_csv_btn",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                isBusy = true
                                runCatching { csvExportManager.exportLineItemsLedgerCsv() }.fold(
                                    onSuccess = { file ->
                                        isBusy = false
                                        lastStatusMessage = "Generated ${file.name}"
                                        PdfPrintShareHelper.shareFile(context, file, "text/csv", "Share Ledger CSV")
                                    },
                                    onFailure = { err ->
                                        isBusy = false
                                        snackbarHostState.showSnackbar(err.message ?: "Export failed")
                                    }
                                )
                            }
                        }
                    )
                }

                // SQLite Database Backup & Restore
                SectionCard(title = "2. Full SQLite Database Backup & Restore") {
                    Text(
                        "Flushes WAL journal into a single portable .db snapshot containing all farmers, bills, rates, and audit logs.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FieldActionButton(
                            text = "Save .db File",
                            icon = Icons.Filled.Save,
                            enabled = !isBusy,
                            testTag = "export_save_db_btn",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                saveBackupLauncher.launch("Bhoomgaadi_${prefs.deviceCode}_$stamp.db")
                            }
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    val res = backupManager.createLocalBackupFile()
                                    isBusy = false
                                    res.fold(
                                        onSuccess = { file ->
                                            lastStatusMessage = "Backup created: ${file.name}"
                                            PdfPrintShareHelper.shareFile(
                                                context,
                                                file,
                                                "application/octet-stream",
                                                "Share SQLite Backup"
                                            )
                                        },
                                        onFailure = { snackbarHostState.showSnackbar(it.message ?: "Backup failed") }
                                    )
                                }
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .testTag("export_share_db_btn")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share .db")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { restoreBackupLauncher.launch(arrayOf("*/*")) },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("export_restore_db_btn")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restore from SQLite .db Backup", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Background Google Drive PDF Sync
                SectionCard(title = "3. Google Drive Background PDF Sync") {
                    InfoRow("Pending PDFs in Queue", "${summary.pendingUploads}", boldValue = true, monoValue = true)
                    InfoRow("Drive Folder ID", prefs.driveRootFolderId.ifBlank { "Not configured (Settings)" })
                    InfoRow("OAuth Token", if (prefs.driveAccessToken.isBlank()) "Not configured" else "Configured")
                    Spacer(Modifier.height(10.dp))
                    FieldActionButton(
                        text = "Queue Background Drive Upload",
                        icon = Icons.Filled.CloudUpload,
                        color = SecondaryBlue,
                        testTag = "export_drive_sync_btn",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (prefs.driveAccessToken.isBlank()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Configure Google Drive Folder ID & Access Token in Settings first.")
                                }
                            } else {
                                DriveUploadWorker.enqueue(context)
                                lastStatusMessage = "WorkManager Drive upload scheduled (runs automatically when online)."
                                scope.launch {
                                    snackbarHostState.showSnackbar("Drive PDF sync queued via WorkManager.")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
