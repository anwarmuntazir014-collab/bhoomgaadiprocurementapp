package com.example.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.RecordStatus
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val godowns by viewModel.godowns.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Center config fields
    var deviceCode by remember { mutableStateOf(viewModel.prefs.deviceCode) }
    var yearText by remember { mutableStateOf(viewModel.prefs.procurementYear.toString()) }
    var operatorName by remember { mutableStateOf(viewModel.prefs.operatorName) }
    var signatory by remember { mutableStateOf(viewModel.prefs.authorizedSignatory) }
    var driveFolderId by remember { mutableStateOf(viewModel.prefs.driveRootFolderId) }
    var driveToken by remember { mutableStateOf(viewModel.prefs.driveAccessToken) }
    var autoQueueDrive by remember { mutableStateOf(viewModel.prefs.autoQueueDriveUpload) }

    // New master item states
    var newProductName by remember { mutableStateOf("") }
    var newProductRate by remember { mutableStateOf("") }
    var newLocationName by remember { mutableStateOf("") }
    var newGodownName by remember { mutableStateOf("") }
    var selectedLocationForGodown by remember(locations) { mutableStateOf(locations.firstOrNull()) }

    BhoomgaadiScaffold(
        title = "Settings & Master Catalog",
        subtitle = "Device ${viewModel.prefs.deviceCode} • Offline Config",
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
            ) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CardWhite,
                    contentColor = BhoomgaadiDarkGreen
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Center") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Produce Rates") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Locations") })
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            SectionCard(title = "Procurement Device & Voucher Config") {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = deviceCode,
                                        onValueChange = { deviceCode = it.uppercase().take(8) },
                                        label = { Text("Device Code") },
                                        supportingText = { Text("Prefix e.g. KT01") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("settings_device_code_input")
                                    )
                                    OutlinedTextField(
                                        value = yearText,
                                        onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                                        label = { Text("Season Year") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = operatorName,
                                    onValueChange = { operatorName = it },
                                    label = { Text("Field Operator Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = signatory,
                                    onValueChange = { signatory = it },
                                    label = { Text("Authorized Signatory (PDF Footer)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            SectionCard(title = "Google Drive PDF Sync (Optional)") {
                                OutlinedTextField(
                                    value = driveFolderId,
                                    onValueChange = { driveFolderId = it },
                                    label = { Text("Drive Root Folder ID") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = driveToken,
                                    onValueChange = { driveToken = it },
                                    label = { Text("OAuth 2.0 Bearer Access Token") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Auto-queue PDF upload on bill issue", modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = autoQueueDrive,
                                        onCheckedChange = { autoQueueDrive = it }
                                    )
                                }
                            }

                            FieldActionButton(
                                text = "Save Configuration",
                                icon = Icons.Filled.Save,
                                testTag = "settings_save_button",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val yr = yearText.toIntOrNull() ?: viewModel.prefs.procurementYear
                                    viewModel.saveCenterSettings(
                                        deviceCode = deviceCode,
                                        procurementYear = yr,
                                        operatorName = operatorName,
                                        authorizedSignatory = signatory,
                                        driveFolderId = driveFolderId,
                                        driveAccessToken = driveToken,
                                        autoQueueDrive = autoQueueDrive
                                    )
                                    scope.launch { snackbarHostState.showSnackbar("Center settings saved.") }
                                }
                            )
                        }

                        1 -> {
                            SectionCard(title = "Add Organic Produce Item") {
                                OutlinedTextField(
                                    value = newProductName,
                                    onValueChange = { newProductName = it },
                                    label = { Text("Stock Item Name") },
                                    placeholder = { Text("e.g. Organic Forest Honey") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = newProductRate,
                                    onValueChange = { newProductRate = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Rate per kg (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                FieldActionButton(
                                    text = "Add Produce Rate",
                                    icon = Icons.Filled.Add,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val rate = newProductRate.toDoubleOrNull()
                                        if (newProductName.isNotBlank() && rate != null && rate > 0.0) {
                                            viewModel.addOrUpdateProduct(name = newProductName, ratePerKg = rate)
                                            newProductName = ""
                                            newProductRate = ""
                                            scope.launch { snackbarHostState.showSnackbar("Product added to catalog.") }
                                        }
                                    }
                                )
                            }

                            SectionCard(title = "Active & Inactive Produce (${products.size})") {
                                products.forEachIndexed { idx, prod ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(prod.stockItemName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "₹${formatQty(prod.ratePerKg)} / kg • ${prod.status.label}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (prod.status == RecordStatus.ACTIVE) BhoomgaadiDarkGreen else TextSecondary
                                            )
                                        }
                                        Switch(
                                            checked = prod.status == RecordStatus.ACTIVE,
                                            onCheckedChange = { viewModel.toggleProductStatus(prod) }
                                        )
                                    }
                                    if (idx < products.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
                                }
                            }
                        }

                        else -> {
                            SectionCard(title = "Add Procurement Center / Location") {
                                OutlinedTextField(
                                    value = newLocationName,
                                    onValueChange = { newLocationName = it },
                                    label = { Text("Location Name") },
                                    placeholder = { Text("e.g. Barsur Collection Point") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                FieldActionButton(
                                    text = "Add Location",
                                    icon = Icons.Filled.Add,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        if (newLocationName.isNotBlank()) {
                                            viewModel.addLocation(newLocationName)
                                            newLocationName = ""
                                            scope.launch { snackbarHostState.showSnackbar("Location added.") }
                                        }
                                    }
                                )
                            }

                            SectionCard(title = "Add Temporary Godown") {
                                SimpleDropdown(
                                    label = "Parent Procurement Location",
                                    selectedText = selectedLocationForGodown?.locationName ?: "Select Location",
                                    items = locations,
                                    itemLabel = { it.locationName },
                                    onSelected = { selectedLocationForGodown = it }
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = newGodownName,
                                    onValueChange = { newGodownName = it },
                                    label = { Text("Godown Name") },
                                    placeholder = { Text("e.g. Godown B / Transit Shed") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                FieldActionButton(
                                    text = "Add Godown",
                                    icon = Icons.Filled.Add,
                                    color = SecondaryBlue,
                                    enabled = selectedLocationForGodown != null,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val parent = selectedLocationForGodown ?: return@FieldActionButton
                                        if (newGodownName.isNotBlank()) {
                                            viewModel.addGodown(parent.locationId, newGodownName)
                                            newGodownName = ""
                                            scope.launch { snackbarHostState.showSnackbar("Godown added to ${parent.locationName}.") }
                                        }
                                    }
                                )
                            }

                            SectionCard(title = "Configured Centers & Godowns") {
                                locations.forEachIndexed { idx, loc ->
                                    val linked = godowns.filter { it.locationId == loc.locationId }
                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                        Text(loc.locationName, fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen)
                                        if (linked.isEmpty()) {
                                            Text("No godowns linked yet", style = MaterialTheme.typography.bodyMedium)
                                        } else {
                                            linked.forEach { g ->
                                                Text("• ${g.godownName}", style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                    if (idx < locations.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
