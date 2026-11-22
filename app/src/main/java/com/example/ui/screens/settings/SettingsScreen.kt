package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.UserEntity
import com.example.domain.model.AppLanguage
import com.example.domain.model.RecordStatus
import com.example.domain.model.UserRole
import com.example.pdf.BillPdfGenerator
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUser: UserEntity,
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val isAdmin = currentUser.role == UserRole.ADMIN
    val language by authViewModel.language.collectAsStateWithLifecycle()
    val allUsers by authViewModel.allUsers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val godowns by viewModel.godowns.collectAsStateWithLifecycle()
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val clusters by viewModel.clusters.collectAsStateWithLifecycle()
    val villages by viewModel.villages.collectAsStateWithLifecycle()

    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs = if (isAdmin) listOf("General", "Users & PINs", "Master Data") else listOf("General")
    var selectedTab by remember { mutableIntStateOf(0) }

    BhoomgaadiScaffold(
        title = strings.settings,
        subtitle = "${currentUser.displayName} (${currentUser.role.label})",
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (tabs.size > 1) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CardWhite,
                    contentColor = BhoomgaadiDarkGreen
                ) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("settings_tab_$idx")
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> GeneralSettingsTab(
                        isAdmin = isAdmin,
                        language = language,
                        viewModel = viewModel,
                        onSetLanguage = authViewModel::setLanguage,
                        onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    1 -> UsersAndPinsTab(
                        currentUser = currentUser,
                        users = allUsers,
                        authViewModel = authViewModel,
                        onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                    2 -> MasterDataTab(
                        products = products,
                        locations = locations,
                        godowns = godowns,
                        blocks = blocks,
                        clusters = clusters,
                        villages = villages,
                        viewModel = viewModel,
                        onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsTab(
    isAdmin: Boolean,
    language: AppLanguage,
    viewModel: SettingsViewModel,
    onSetLanguage: (AppLanguage) -> Unit,
    onMessage: (String) -> Unit
) {
    val prefs = viewModel.prefs
    var deviceCode by remember { mutableStateOf(prefs.deviceCode) }
    var seasonYear by remember { mutableStateOf(prefs.procurementYear.toString()) }
    var signatory by remember { mutableStateOf(prefs.authorizedSignatory) }
    var membershipFee by remember { mutableStateOf(prefs.membershipFeeInr.toInt().toString()) }
    var driveFolderId by remember { mutableStateOf(prefs.driveRootFolderId) }
    var driveToken by remember { mutableStateOf(prefs.driveAccessToken) }
    var autoDrive by remember { mutableStateOf(prefs.autoQueueDriveUpload) }

    // Language Selection Card
    SectionCard(title = "App Language / भाषा") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = language == AppLanguage.ENGLISH,
                onClick = { onSetLanguage(AppLanguage.ENGLISH) },
                label = { Text("English (EN)") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_lang_en")
            )
            FilterChip(
                selected = language == AppLanguage.HINDI,
                onClick = { onSetLanguage(AppLanguage.HINDI) },
                label = { Text("हिन्दी (Hindi)") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_lang_hi")
            )
        }
    }

    // Printer & Offline PDF Storage Card matching Layout 3 Screen 10
    SectionCard(title = "Offline PDF Storage") {
        InfoRow("PDF File Location", BillPdfGenerator.PUBLIC_FOLDER_DISPLAY, boldValue = true)
        InfoRow("PDF File Name Format", "<BillNo>.pdf (e.g. ${prefs.nextBillNumberPreview()}.pdf)", monoValue = true)
        Text(
            text = "All finalized and revised bills are automatically saved in the device Documents folder so they can be viewed offline in the phone's Files app.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    // Center & Billing Settings
    SectionCard(title = "Center, Billing & Drive Settings") {
        OutlinedTextField(
            value = deviceCode,
            onValueChange = { if (isAdmin) deviceCode = it.uppercase().take(12) },
            readOnly = !isAdmin,
            label = { Text("Device / Center Code Prefix") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = seasonYear,
                onValueChange = { if (isAdmin) seasonYear = it.filter { c -> c.isDigit() }.take(4) },
                readOnly = !isAdmin,
                label = { Text("Procurement Year") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = membershipFee,
                onValueChange = { if (isAdmin) membershipFee = it.filter { c -> c.isDigit() }.take(6) },
                readOnly = !isAdmin,
                label = { Text("Membership Fee (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = signatory,
            onValueChange = { if (isAdmin) signatory = it },
            readOnly = !isAdmin,
            label = { Text("Authorized Signatory Name") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = BorderGray)
        Spacer(Modifier.height(10.dp))
        Text("Google Drive Sync Settings", fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = driveFolderId,
            onValueChange = { if (isAdmin) driveFolderId = it.trim() },
            readOnly = !isAdmin,
            label = { Text("Google Drive Root Folder ID") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = driveToken,
            onValueChange = { if (isAdmin) driveToken = it.trim() },
            readOnly = !isAdmin,
            label = { Text("Drive OAuth Access Token (Optional)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto Upload PDFs", fontWeight = FontWeight.SemiBold)
                Text("Upload when internet is available", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
                checked = autoDrive,
                enabled = isAdmin,
                onCheckedChange = { autoDrive = it }
            )
        }

        if (isAdmin) {
            Spacer(Modifier.height(12.dp))
            FieldActionButton(
                text = "Save Settings",
                icon = Icons.Filled.Save,
                color = BhoomgaadiDarkGreen,
                testTag = "settings_save_general_btn",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.saveCenterSettings(
                        deviceCode = deviceCode.ifBlank { "DNT-KTK" },
                        procurementYear = seasonYear.toIntOrNull() ?: 2026,
                        authorizedSignatory = signatory.ifBlank { "Authorized Signatory" },
                        membershipFeeInr = membershipFee.toDoubleOrNull() ?: 1100.0,
                        driveFolderId = driveFolderId,
                        driveAccessToken = driveToken,
                        autoQueueDrive = autoDrive
                    )
                    onMessage("Settings saved successfully.")
                }
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Only Admin can modify Center, Membership Fee, and Drive settings.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }

    // About App Card
    SectionCard(title = "About App") {
        InfoRow("Application", "Bhoomgaadi Farmer Procurement")
        InfoRow("Version", "2.0.0 (Offline SQLite)")
        InfoRow("Bill Edit Policy", "Officer: Same Day • Admin: Anytime (Audited)")
    }
}

@Composable
private fun UsersAndPinsTab(
    currentUser: UserEntity,
    users: List<UserEntity>,
    authViewModel: AuthViewModel,
    onMessage: (String) -> Unit
) {
    var newUsername by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf(UserRole.OFFICER) }
    var newPin by remember { mutableStateOf("") }

    var resetTargetUser by remember { mutableStateOf<UserEntity?>(null) }
    var resetPinValue by remember { mutableStateOf("") }

    SectionCard(title = "Create New User Account (Offline PIN)") {
        OutlinedTextField(
            value = newUsername,
            onValueChange = { newUsername = it.trim() },
            label = { Text("Username (e.g. officer1)") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_new_user_username")
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newDisplayName,
            onValueChange = { newDisplayName = it },
            label = { Text("Full Name (e.g. Suresh Verma)") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_new_user_name")
        )
        Spacer(Modifier.height(8.dp))
        SimpleDropdown(
            label = "Role",
            selectedText = newRole.label,
            items = UserRole.entries,
            itemLabel = { it.label },
            onSelected = { newRole = it }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newPin,
            onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("4-Digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_new_user_pin")
        )
        Spacer(Modifier.height(10.dp))
        FieldActionButton(
            text = "Create User",
            icon = Icons.Filled.PersonAdd,
            color = BhoomgaadiDarkGreen,
            enabled = newUsername.length >= 3 && newPin.length == 4,
            testTag = "settings_create_user_btn",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                authViewModel.adminAddUser(newUsername, newDisplayName, newRole, newPin) { res ->
                    res.fold(
                        onSuccess = { created ->
                            newUsername = ""
                            newDisplayName = ""
                            newPin = ""
                            onMessage("Account '${created.username}' (${created.role.label}) created.")
                        },
                        onFailure = { onMessage(it.message ?: "Could not create user.") }
                    )
                }
            }
        )
    }

    SectionCard(title = "Existing Users (${users.size})") {
        users.forEachIndexed { idx, u ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(u.displayName, fontWeight = FontWeight.Bold)
                        StatusPill(
                            text = if (u.role == UserRole.ADMIN) "ADMIN" else "OFFICER",
                            bgColor = if (u.role == UserRole.ADMIN) BhoomgaadiLightGreen else SecondaryLightBlue,
                            fgColor = if (u.role == UserRole.ADMIN) BhoomgaadiDarkGreen else SecondaryBlue
                        )
                    }
                    Text(
                        "@${u.username} • ${if (u.isActive) "Active" else "Disabled"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (u.isActive) TextSecondary else DangerRed
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            resetTargetUser = u
                            resetPinValue = ""
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Reset PIN", fontSize = 12.sp)
                    }
                    if (u.userUuid != currentUser.userUuid) {
                        IconButton(
                            onClick = {
                                authViewModel.adminToggleUser(u.userUuid) { res ->
                                    res.fold(
                                        onSuccess = { onMessage("Updated status for ${u.username}.") },
                                        onFailure = { onMessage(it.message ?: "Action failed.") }
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (u.isActive) Icons.Filled.PersonOff else Icons.Filled.HowToReg,
                                contentDescription = "Toggle Active",
                                tint = if (u.isActive) DangerRed else BhoomgaadiGreen
                            )
                        }
                    }
                }
            }
            if (idx < users.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
        }
    }

    resetTargetUser?.let { target ->
        AlertDialog(
            onDismissRequest = { resetTargetUser = null },
            title = { Text("Reset PIN for ${target.displayName}") },
            text = {
                OutlinedTextField(
                    value = resetPinValue,
                    onValueChange = { resetPinValue = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("New 4-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = resetPinValue.length == 4,
                    onClick = {
                        authViewModel.adminResetPin(target.userUuid, resetPinValue) { res ->
                            resetTargetUser = null
                            res.fold(
                                onSuccess = { onMessage("PIN reset for ${target.username}.") },
                                onFailure = { onMessage(it.message ?: "PIN reset failed.") }
                            )
                        }
                    }
                ) { Text("Update PIN") }
            },
            dismissButton = {
                TextButton(onClick = { resetTargetUser = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MasterDataTab(
    products: List<com.example.data.local.entities.ProductEntity>,
    locations: List<com.example.data.local.entities.LocationEntity>,
    godowns: List<com.example.data.local.entities.GodownEntity>,
    blocks: List<com.example.data.local.entities.BlockEntity>,
    clusters: List<com.example.data.local.entities.ClusterEntity>,
    villages: List<com.example.data.local.entities.VillageEntity>,
    viewModel: SettingsViewModel,
    onMessage: (String) -> Unit
) {
    // 1. Produce Rates Master
    var prodName by remember { mutableStateOf("") }
    var prodRate by remember { mutableStateOf("") }
    SectionCard(title = "Produce & Rate Master (${products.size})") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = prodName,
                onValueChange = { prodName = it },
                label = { Text("Product Name") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.4f)
            )
            OutlinedTextField(
                value = prodRate,
                onValueChange = { prodRate = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("₹/kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(0.8f)
            )
        }
        Spacer(Modifier.height(8.dp))
        FieldActionButton(
            text = "Add Product",
            icon = Icons.Filled.Add,
            color = BhoomgaadiDarkGreen,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val r = prodRate.toDoubleOrNull() ?: 0.0
                if (prodName.isNotBlank() && r > 0.0) {
                    viewModel.addOrUpdateProduct(name = prodName, ratePerKg = r)
                    prodName = ""
                    prodRate = ""
                    onMessage("Product saved.")
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        products.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.stockItemName, fontWeight = FontWeight.SemiBold)
                    Text("₹${formatQty(p.ratePerKg)}/kg • ${p.status.label}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = p.status == RecordStatus.ACTIVE,
                    onCheckedChange = { viewModel.toggleProductStatus(p) }
                )
            }
        }
    }

    // 2. Locations & Temporary Godowns
    var locName by remember { mutableStateOf("") }
    var selectedLocId by remember(locations) { mutableStateOf(locations.firstOrNull()?.locationId.orEmpty()) }
    var gdwName by remember { mutableStateOf("") }
    SectionCard(title = "Locations (${locations.size}) & Temp. Godowns (${godowns.size})") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = locName,
                onValueChange = { locName = it },
                label = { Text("New Procurement Location") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (locName.isNotBlank()) {
                        viewModel.addLocation(locName)
                        locName = ""
                        onMessage("Location added.")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue)
            ) { Text("Add") }
        }
        Spacer(Modifier.height(10.dp))
        SimpleDropdown(
            label = "Location for Godown",
            selectedText = locations.find { it.locationId == selectedLocId }?.locationName ?: "Select Location",
            items = locations,
            itemLabel = { it.locationName },
            onSelected = { selectedLocId = it.locationId }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = gdwName,
                onValueChange = { gdwName = it },
                label = { Text("New Temporary Godown") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (selectedLocId.isNotBlank() && gdwName.isNotBlank()) {
                        viewModel.addGodown(selectedLocId, gdwName)
                        gdwName = ""
                        onMessage("Temporary Godown added.")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BhoomgaadiDarkGreen)
            ) { Text("Add") }
        }
    }

    // 3. Cascading Geography Master: Block -> Cluster -> Village
    var newBlockName by remember { mutableStateOf("") }
    var selectedBlockId by remember(blocks) { mutableStateOf(blocks.firstOrNull()?.blockId.orEmpty()) }
    var newClusterName by remember { mutableStateOf("") }
    val blockClusters = remember(clusters, selectedBlockId) { clusters.filter { it.blockId == selectedBlockId } }
    var selectedClusterId by remember(blockClusters) { mutableStateOf(blockClusters.firstOrNull()?.clusterId.orEmpty()) }
    var newVillageName by remember { mutableStateOf("") }

    SectionCard(title = "Geography Master: Block → Cluster → Village") {
        Text(
            "${blocks.size} Blocks • ${clusters.size} Clusters • ${villages.size} Villages (also auto-populated during Excel import)",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))

        // Add Block
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newBlockName,
                onValueChange = { newBlockName = it },
                label = { Text("1. New Block") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (newBlockName.isNotBlank()) {
                        viewModel.addBlock(newBlockName)
                        newBlockName = ""
                        onMessage("Block added.")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BhoomgaadiDarkGreen)
            ) { Text("Add") }
        }

        Spacer(Modifier.height(10.dp))
        SimpleDropdown(
            label = "Select Block",
            selectedText = blocks.find { it.blockId == selectedBlockId }?.blockName ?: "Select Block",
            items = blocks,
            itemLabel = { it.blockName },
            onSelected = { selectedBlockId = it.blockId }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newClusterName,
                onValueChange = { newClusterName = it },
                label = { Text("2. New Cluster in Block") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (selectedBlockId.isNotBlank() && newClusterName.isNotBlank()) {
                        viewModel.addCluster(selectedBlockId, newClusterName)
                        newClusterName = ""
                        onMessage("Cluster added.")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SecondaryBlue)
            ) { Text("Add") }
        }

        Spacer(Modifier.height(10.dp))
        SimpleDropdown(
            label = "Select Cluster",
            selectedText = blockClusters.find { it.clusterId == selectedClusterId }?.clusterName ?: "Select Cluster",
            items = blockClusters,
            itemLabel = { it.clusterName },
            onSelected = { selectedClusterId = it.clusterId },
            enabled = blockClusters.isNotEmpty()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newVillageName,
                onValueChange = { newVillageName = it },
                label = { Text("3. New Village in Cluster") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (selectedClusterId.isNotBlank() && newVillageName.isNotBlank()) {
                        viewModel.addVillage(selectedClusterId, newVillageName)
                        newVillageName = ""
                        onMessage("Village added.")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ProcurementAmber)
            ) { Text("Add") }
        }
    }
}
