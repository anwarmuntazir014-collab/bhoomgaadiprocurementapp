package com.example.ui.screens.farmer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entities.FarmerEntity
import com.example.domain.model.BankAccountType
import com.example.domain.model.MembershipStatus
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import java.io.File

@Composable
fun FarmerSearchScreen(
    viewModel: FarmerViewModel,
    onBack: () -> Unit,
    onAddFarmer: (prefillAadhaar: String) -> Unit,
    onEditFarmer: (farmerUuid: String) -> Unit,
    onCreateBillForFarmer: (farmerUuid: String) -> Unit
) {
    val lookupState by viewModel.aadhaarLookup.collectAsStateWithLifecycle()
    val directoryQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val farmers by viewModel.farmers.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    var aadhaarInput by remember { mutableStateOf("") }
    var showBrowseList by remember { mutableStateOf(false) }
    var showBankDialog by remember { mutableStateOf(false) }
    var showDocsDialog by remember { mutableStateOf(false) }

    val screenTitle = when (lookupState) {
        is AadhaarLookupState.Idle -> strings.searchFarmer
        is AadhaarLookupState.NotFound -> strings.farmerNotFound
        is AadhaarLookupState.Found -> "Farmer Details"
    }

    BhoomgaadiScaffold(
        title = screenTitle,
        onBack = {
            if (lookupState !is AadhaarLookupState.Idle) {
                viewModel.resetAadhaarLookup()
            } else {
                onBack()
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val current = lookupState) {
                is AadhaarLookupState.Idle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 560.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Search Farmer by Aadhaar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = aadhaarInput,
                            onValueChange = { aadhaarInput = it.filter { c -> c.isDigit() }.take(12) },
                            placeholder = { Text("1234 5678 9012") },
                            label = { Text(strings.enterAadhaar) },
                            leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                            trailingIcon = {
                                if (aadhaarInput.isNotEmpty()) {
                                    IconButton(onClick = { aadhaarInput = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            supportingText = { Text("${aadhaarInput.length}/12 digits") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CardWhite,
                                unfocusedContainerColor = CardWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_aadhaar_input")
                        )

                        FieldActionButton(
                            text = strings.search,
                            icon = Icons.Filled.Search,
                            color = BhoomgaadiDarkGreen,
                            enabled = aadhaarInput.length == 12,
                            testTag = "search_aadhaar_button",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.lookupFarmerByAadhaar(aadhaarInput) }
                        )

                        Surface(
                            color = SecondaryLightBlue.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Enter 12-digit Aadhaar number to find an existing farmer from the Master Data. Registration is required only for new farmers.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        // Optional Browse / Search by Name or Village toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Master Register (${farmers.size} farmers)",
                                style = MaterialTheme.typography.titleMedium,
                                color = BhoomgaadiDarkGreen
                            )
                            TextButton(onClick = { showBrowseList = !showBrowseList }) {
                                Text(if (showBrowseList) "Hide List" else "Browse by Name/Village")
                            }
                        }

                        if (showBrowseList) {
                            OutlinedTextField(
                                value = directoryQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = { Text("Filter by name, village, block, SHG...") },
                                leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = CardWhite,
                                    unfocusedContainerColor = CardWhite
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(farmers.take(40), key = { it.farmerUuid }) { f ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.selectFoundFarmer(f) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                                        border = BorderStroke(1.dp, BorderGray)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(f.farmerName, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "${f.village}, ${f.block} • ${formatAadhaarSpaced(f.aadhaarNumber)}",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            StatusPill(
                                                text = f.membershipStatus.label,
                                                bgColor = if (f.membershipStatus == MembershipStatus.MEMBER) BhoomgaadiLightGreen else SurfaceSubtle,
                                                fgColor = if (f.membershipStatus == MembershipStatus.MEMBER) BhoomgaadiDarkGreen else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }

                        FieldActionButton(
                            text = strings.backToHome,
                            icon = Icons.Filled.Home,
                            color = SlateTileBg,
                            contentColor = TextPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onBack
                        )
                    }
                }

                is AadhaarLookupState.NotFound -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 520.dp)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(DangerRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PersonOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = strings.farmerNotFound,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No farmer record found with Aadhaar number:",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = SurfaceSubtle,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderGray)
                        ) {
                            Text(
                                text = formatAadhaarSpaced(current.aadhaar),
                                fontFamily = JetBrainsMono,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Would you like to register this new farmer in the database?",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(22.dp))
                        FieldActionButton(
                            text = "Add New Farmer",
                            icon = Icons.Filled.PersonAdd,
                            color = SecondaryBlue,
                            testTag = "not_found_add_farmer_button",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.resetAadhaarLookup()
                                onAddFarmer(current.aadhaar)
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        FieldActionButton(
                            text = "Search Again",
                            icon = Icons.Filled.Search,
                            color = SlateTileBg,
                            contentColor = TextPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.resetAadhaarLookup() }
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = {
                            viewModel.resetAadhaarLookup()
                            onBack()
                        }) {
                            Text(strings.backToHome)
                        }
                    }
                }

                is AadhaarLookupState.Found -> {
                    val f = current.farmer
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 560.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Green Verified Banner (Layout 1 Screen 4)
                        Surface(
                            color = BhoomgaadiLightGreen,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BhoomgaadiGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BhoomgaadiGreen)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(strings.farmerFound, fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen)
                                    Text(
                                        "Aadhaar: ${formatAadhaarSpaced(f.aadhaarNumber)}",
                                        fontFamily = JetBrainsMono,
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        KeyValueTable(
                            rows = listOf(
                                "Farmer Name" to f.farmerName,
                                "Father / Husband" to f.fatherOrHusbandName,
                                "Block" to f.block,
                                "Cluster" to f.cluster,
                                "Village" to f.village,
                                "SHG Name" to f.shgName,
                                "Membership No." to f.bmgdMemberCode,
                                "Membership Status" to f.membershipStatus.label
                            )
                        )

                        OutlinedButton(
                            onClick = { showBankDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SecondaryLightBlue.copy(alpha = 0.5f),
                                contentColor = SecondaryBlue
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.viewBankDetails, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { showDocsDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SlateTileBg,
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.viewDocuments, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(Modifier.height(4.dp))

                        // 2x2 Action Grid matching Layout 1 Screen 4
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FieldActionButton(
                                text = strings.createNewBill,
                                icon = Icons.Filled.NoteAdd,
                                color = ProcurementAmber,
                                testTag = "found_create_bill_btn",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.resetAadhaarLookup()
                                    onCreateBillForFarmer(f.farmerUuid)
                                }
                            )
                            FieldActionButton(
                                text = strings.updateDetails,
                                icon = Icons.Filled.Edit,
                                color = SecondaryBlue,
                                testTag = "found_update_btn",
                                modifier = Modifier.weight(1f),
                                onClick = { onEditFarmer(f.farmerUuid) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FieldActionButton(
                                text = "Search Another",
                                color = BhoomgaadiGreen,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.resetAadhaarLookup() }
                            )
                            FieldActionButton(
                                text = strings.backToHome,
                                color = SlateTileBg,
                                contentColor = TextPrimary,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.resetAadhaarLookup()
                                    onBack()
                                }
                            )
                        }
                    }

                    if (showBankDialog) {
                        FarmerBankDetailsDialog(farmer = f, onDismiss = { showBankDialog = false })
                    }
                    if (showDocsDialog) {
                        FarmerDocumentsDialog(farmer = f, onDismiss = { showDocsDialog = false })
                    }
                }
            }
        }
    }
}

@Composable
fun FarmerBankDetailsDialog(farmer: FarmerEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bank Details", fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen) },
        text = {
            if (farmer.bankAccountType == BankAccountType.NONE) {
                Text("No bank account linked for this farmer yet.")
            } else {
                KeyValueTable(
                    rows = listOf(
                        "Account Type" to farmer.bankAccountType.label,
                        "Account Holder" to farmer.accountHolderName,
                        "Bank Name" to farmer.bankName,
                        "Account No." to farmer.fullAccountNumber,
                        "IFSC Code" to farmer.ifsc,
                        "Branch" to farmer.branch
                    )
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun FarmerDocumentsDialog(farmer: FarmerEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uploaded Documents", fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DocPreviewBlock("Aadhaar / ID Proof", farmer.idDocPhotoPath)
                DocPreviewBlock("Bank Passbook", farmer.bankPassbookPhotoPath)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun DocPreviewBlock(title: String, path: String) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        if (path.isNotBlank() && File(path).exists()) {
            AsyncImage(
                model = File(path),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Surface(
                color = SurfaceSubtle,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "No photo captured yet",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
