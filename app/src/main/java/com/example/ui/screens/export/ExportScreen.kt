package com.example.ui.screens.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppPreferences
import com.example.data.local.entities.UserEntity
import com.example.data.repository.ProcurementRepository
import com.example.domain.model.UserRole
import com.example.export.CsvExportManager
import com.example.export.DatabaseBackupManager
import com.example.export.FarmerImportSummary
import com.example.pdf.PdfPrintShareHelper
import com.example.sync.DriveService
import com.example.sync.DriveUploadWorker
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportScreen(
    currentUser: UserEntity,
    prefs: AppPreferences,
    repository: ProcurementRepository,
    csvExportManager: CsvExportManager,
    backupManager: DatabaseBackupManager,
    dashboardViewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = LocalAppStrings.current
    val summary by dashboardViewModel.summary.collectAsStateWithLifecycle()

    var isBusy by remember { mutableStateOf(false) }
    var lastImportSummary by remember { mutableStateOf<FarmerImportSummary?>(null) }
    var showRestoreWarning by remember { mutableStateOf(false) }

    // Direct .xlsx / .csv Farmer Master Data Importer (Admin only)
    val importExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                val res = repository.importFarmersFromExcel(currentUser, uri)
                isBusy = false
                res.fold(
                    onSuccess = { importRes ->
                        lastImportSummary = importRes
                        snackbarHostState.showSnackbar(
                            "Imported: ${importRes.addedCount} added, ${importRes.updatedCount} updated."
                        )
                    },
                    onFailure = { err ->
                        snackbarHostState.showSnackbar(err.message ?: "Excel import failed.")
                    }
                )
            }
        }
    }

    // Save empty Farmer Master Excel Template (.xlsx) directly to device storage via SAF
    val saveTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                val res = csvExportManager.writeFarmerImportTemplateToUri(uri)
                isBusy = false
                res.fold(
                    onSuccess = { snackbarHostState.showSnackbar("Empty Excel template saved! Fill rows & import here.") },
                    onFailure = { snackbarHostState.showSnackbar(it.message ?: "Could not save template.") }
                )
            }
        }
    }

    // Export SQLite .db Backup via SAF
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                val res = backupManager.exportBackupToUri(uri)
                isBusy = false
                res.fold(
                    onSuccess = { snackbarHostState.showSnackbar("SQLite backup saved successfully.") },
                    onFailure = { snackbarHostState.showSnackbar(it.message ?: "Backup failed.") }
                )
            }
        }
    }

    // Restore SQLite .db Backup via SAF
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                val res = backupManager.restoreFromUri(uri)
                isBusy = false
                res.fold(
                    onSuccess = {
                        snackbarHostState.showSnackbar("Database restored! Restart the app if needed to refresh all screens.")
                    },
                    onFailure = { snackbarHostState.showSnackbar(it.message ?: "Restore failed.") }
                )
            }
        }
    }

    BhoomgaadiScaffold(
        title = strings.exportData,
        subtitle = "Excel Import, Reports & Offline Backup",
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
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = BhoomgaadiDarkGreen
                    )
                }

                // 1. Direct .xlsx / .csv Farmer Master Import (Admin Only)
                if (currentUser.role == UserRole.ADMIN) {
                    SectionCard(
                        title = "Master Data Import (Admin)",
                        containerColor = BhoomgaadiLightGreen
                    ) {
                        Text(
                            text = "Load your existing Farmers Excel (.xlsx) or CSV sheet (Member & Non-Member). Automatically upserts by Aadhaar and builds Block → Cluster → Village dropdown lists.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FieldActionButton(
                                text = "Save Template (.xlsx)",
                                icon = Icons.Filled.Download,
                                color = SecondaryBlue,
                                enabled = !isBusy,
                                testTag = "export_download_template_btn",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    saveTemplateLauncher.launch("Bhoomgaadi_Farmer_Master_Template.xlsx")
                                }
                            )
                            FieldActionButton(
                                text = "Share Template",
                                icon = Icons.Filled.Share,
                                color = CardWhite,
                                contentColor = BhoomgaadiDarkGreen,
                                enabled = !isBusy,
                                testTag = "export_share_template_btn",
                                modifier = Modifier.weight(0.9f),
                                onClick = {
                                    scope.launch {
                                        isBusy = true
                                        runCatching { csvExportManager.exportFarmerImportTemplateXlsx() }
                                            .onSuccess { file ->
                                                PdfPrintShareHelper.shareFile(
                                                    context,
                                                    file,
                                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                    "Share Farmer Master Excel Template"
                                                )
                                            }
                                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Template error") }
                                        isBusy = false
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        FieldActionButton(
                            text = strings.importExcelMaster,
                            icon = Icons.Filled.FileUpload,
                            color = BhoomgaadiDarkGreen,
                            enabled = !isBusy,
                            testTag = "export_import_excel_btn",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                importExcelLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            }
                        )

                        lastImportSummary?.let { s ->
                            Spacer(Modifier.height(10.dp))
                            KeyValueTable(
                                rows = listOf(
                                    "Total Rows Read" to s.totalRowsRead.toString(),
                                    "New Farmers Added" to s.addedCount.toString(),
                                    "Existing Updated" to s.updatedCount.toString(),
                                    "Skipped (Invalid Aadhaar)" to s.skippedCount.toString(),
                                    "New Blocks / Clusters / Villages" to "${s.blocksAdded} / ${s.clustersAdded} / ${s.villagesAdded}"
                                )
                            )
                        }
                    }
                } else {
                    Surface(
                        color = SlateTileBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Excel Farmer Master Import is restricted to Admin accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // 2. Layout 3 Export Cards
                ExportActionTile(
                    icon = Icons.Filled.Receipt,
                    iconBg = SecondaryLightBlue,
                    iconTint = SecondaryBlue,
                    title = "Export Bills (CSV)",
                    subtitle = "Export all finalized & cancelled procurement bills",
                    testTag = "export_bills_csv_btn",
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            runCatching { csvExportManager.exportBillsSummaryCsv() }
                                .onSuccess { file ->
                                    PdfPrintShareHelper.shareFile(context, file, "text/csv", "Share Bills CSV")
                                }
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Export failed") }
                            isBusy = false
                        }
                    }
                )

                ExportActionTile(
                    icon = Icons.Filled.People,
                    iconBg = SecondaryLightBlue,
                    iconTint = SecondaryBlue,
                    title = "Export Farmers (CSV)",
                    subtitle = "Export full farmer master data with Block/Cluster/Village & Bank details",
                    testTag = "export_farmers_csv_btn",
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            runCatching { csvExportManager.exportFarmersCsv() }
                                .onSuccess { file ->
                                    PdfPrintShareHelper.shareFile(context, file, "text/csv", "Share Farmers CSV")
                                }
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Export failed") }
                            isBusy = false
                        }
                    }
                )

                ExportActionTile(
                    icon = Icons.Filled.Inventory2,
                    iconBg = SecondaryLightBlue,
                    iconTint = SecondaryBlue,
                    title = "Export Items (CSV)",
                    subtitle = "Export item-wise procurement ledger (bags, weight, moisture, rates)",
                    testTag = "export_items_csv_btn",
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            runCatching { csvExportManager.exportLineItemsLedgerCsv() }
                                .onSuccess { file ->
                                    PdfPrintShareHelper.shareFile(context, file, "text/csv", "Share Items CSV")
                                }
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Export failed") }
                            isBusy = false
                        }
                    }
                )

                ExportActionTile(
                    icon = Icons.Filled.CardMembership,
                    iconBg = DashboardLightPurple,
                    iconTint = DashboardPurple,
                    title = "Export Membership (CSV)",
                    subtitle = "Export Member farmers & new ₹${prefs.membershipFeeInr.toInt()} enrollments",
                    testTag = "export_membership_csv_btn",
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            runCatching { csvExportManager.exportMembershipCsv() }
                                .onSuccess { file ->
                                    PdfPrintShareHelper.shareFile(context, file, "text/csv", "Share Membership CSV")
                                }
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Export failed") }
                            isBusy = false
                        }
                    }
                )

                ExportActionTile(
                    icon = Icons.Filled.TableChart,
                    iconBg = BhoomgaadiLightGreen,
                    iconTint = BhoomgaadiDarkGreen,
                    title = "Export to Excel (Single File .xlsx)",
                    subtitle = "All 4 tabs (Bills, Farmers, Items, Membership) in one Excel workbook",
                    testTag = "export_all_excel_btn",
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            runCatching { csvExportManager.exportAllInOneExcelXlsx() }
                                .onSuccess { file ->
                                    PdfPrintShareHelper.shareFile(
                                        context,
                                        file,
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "Share Bhoomgaadi Excel Workbook"
                                    )
                                }
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Excel export failed") }
                            isBusy = false
                        }
                    }
                )

                ExportActionTile(
                    icon = Icons.Filled.CloudUpload,
                    iconBg = ProcurementLightOrange,
                    iconTint = ProcurementOrange,
                    title = "Upload Pending PDFs to Drive",
                    subtitle = "${summary.pendingUploads} locally saved bill PDFs awaiting Google Drive sync",
                    testTag = "export_drive_upload_btn",
                    enabled = !isBusy,
                    onClick = {
                        if (!prefs.isDriveConfigured) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Configure Google Drive Web App URL & Secret Key in Settings first.")
                            }
                        } else {
                            DriveUploadWorker.enqueue(context)
                            scope.launch {
                                snackbarHostState.showSnackbar("Google Drive PDF sync queued in background.")
                            }
                        }
                    }
                )

                ExportActionTile(
                    icon = Icons.Filled.Backup,
                    iconBg = BhoomgaadiLightGreen,
                    iconTint = BhoomgaadiDarkGreen,
                    title = "1-Tap Backup Excel & DB to Drive",
                    subtitle = "Upload complete 4-tab Excel (.xlsx) & SQLite backup (.db) directly to Drive",
                    testTag = "export_drive_full_backup_btn",
                    enabled = !isBusy,
                    onClick = {
                        if (!prefs.isDriveConfigured) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Configure Google Drive Web App URL & Secret Key in Settings first.")
                            }
                        } else {
                            scope.launch {
                                isBusy = true
                                val uploadRes = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val drive = DriveService()
                                        val folderPath = listOf("Backups", prefs.deviceCode, currentUser.username)
                                        val xlsxFile = csvExportManager.exportAllInOneExcelXlsx()
                                        drive.uploadFile(
                                            webhookUrl = prefs.driveWebhookUrl,
                                            secret = prefs.driveSyncSecret,
                                            rootFolderId = prefs.driveRootFolderId,
                                            file = xlsxFile,
                                            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            subFolders = folderPath
                                        ).getOrThrow()

                                        val dbFile = backupManager.createLocalBackupFile().getOrThrow()
                                        drive.uploadFile(
                                            webhookUrl = prefs.driveWebhookUrl,
                                            secret = prefs.driveSyncSecret,
                                            rootFolderId = prefs.driveRootFolderId,
                                            file = dbFile,
                                            mimeType = "application/octet-stream",
                                            subFolders = folderPath
                                        ).getOrThrow()
                                    }
                                }
                                isBusy = false
                                uploadRes.fold(
                                    onSuccess = {
                                        snackbarHostState.showSnackbar("Excel workbook & SQLite database backed up to Google Drive!")
                                    },
                                    onFailure = { err ->
                                        snackbarHostState.showSnackbar(err.message ?: "Drive backup failed.")
                                    }
                                )
                            }
                        }
                    }
                )

                // Offline SQLite Full Database Backup & Restore
                SectionCard(title = "Offline SQLite Database Backup & Restore") {
                    Text(
                        text = "Save a complete copy of your offline SQLite database (.db) to device storage or restore from a previous backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        FieldActionButton(
                            text = "Save .db Backup",
                            icon = Icons.Filled.SaveAlt,
                            color = BhoomgaadiDarkGreen,
                            enabled = !isBusy,
                            testTag = "export_db_backup_btn",
                            modifier = Modifier.weight(1f),
                            onClick = { createBackupLauncher.launch("Bhoomgaadi_Backup.db") }
                        )
                        if (currentUser.role == UserRole.ADMIN) {
                            FieldActionButton(
                                text = "Restore .db",
                                icon = Icons.Filled.Restore,
                                color = DangerLightRed,
                                contentColor = DangerRed,
                                enabled = !isBusy,
                                testTag = "export_db_restore_btn",
                                modifier = Modifier.weight(1f),
                                onClick = { showRestoreWarning = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRestoreWarning) {
        AlertDialog(
            onDismissRequest = { showRestoreWarning = false },
            title = { Text("Restore SQLite Backup?", color = DangerRed, fontWeight = FontWeight.Bold) },
            text = {
                Text("Restoring a backup replaces all current local farmers, bills, and user accounts with the selected .db file. Continue?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreWarning = false
                        restoreBackupLauncher.launch(arrayOf("*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Select Backup File", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreWarning = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ExportActionTile(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.8f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(enabled = enabled, onClick = onClick)
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
