package com.example.ui.screens.billing

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.ProductEntity
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun CreateBillWizardScreen(
    viewModel: CreateBillViewModel,
    preselectedFarmerUuid: String?,
    onBack: () -> Unit,
    onQuickAddFarmer: () -> Unit,
    onBillCreated: (billUuid: String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val farmers by viewModel.matchingFarmers.collectAsStateWithLifecycle()
    val products by viewModel.activeProducts.collectAsStateWithLifecycle()
    val locations by viewModel.activeLocations.collectAsStateWithLifecycle()
    val godowns by viewModel.godownsForSelectedLocation.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(preselectedFarmerUuid) {
        if (!preselectedFarmerUuid.isNullOrBlank()) {
            viewModel.preselectFarmerById(preselectedFarmerUuid)
        }
    }

    LaunchedEffect(state.createdBillUuid) {
        val id = state.createdBillUuid
        if (id != null) {
            viewModel.resetWizard()
            onBillCreated(id)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    val stepTitles = listOf("1. Farmer & Center", "2. Weighment Items", "3. Review & Issue")

    BhoomgaadiScaffold(
        title = "Create Procurement Bill",
        subtitle = "${stepTitles[state.step]} • ${viewModel.nextBillNumberPreview()}",
        onBack = {
            if (state.step > 0) viewModel.goToStep(state.step - 1) else onBack()
        },
        snackbarHostState = snackbarHostState,
        bottomBar = {
            Surface(color = CardWhite, shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.step > 0) {
                        OutlinedButton(
                            onClick = { viewModel.goToStep(state.step - 1) },
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("wizard_prev_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Back")
                        }
                    }
                    when (state.step) {
                        0 -> FieldActionButton(
                            text = "Continue to Weighment",
                            icon = Icons.Filled.ArrowForward,
                            testTag = "wizard_next_step_1",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.goToStep(1) }
                        )
                        1 -> FieldActionButton(
                            text = "Review (${state.draft.lines.size} items • ${formatCurrency(state.draft.grandTotalAmount)})",
                            icon = Icons.Filled.FactCheck,
                            color = ProcurementAmber,
                            testTag = "wizard_next_step_2",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.goToStep(2) }
                        )
                        else -> FieldActionButton(
                            text = if (state.isSubmitting) "Generating PDF..." else "Confirm & Issue Voucher",
                            icon = Icons.Filled.PictureAsPdf,
                            color = BhoomgaadiDarkGreen,
                            enabled = !state.isSubmitting,
                            testTag = "wizard_confirm_bill_button",
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::confirmAndCreateBill
                        )
                    }
                }
            }
        }
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
                // Step Progress Strip
                StepProgressBar(currentStep = state.step, labels = listOf("Farmer", "Weighment", "Confirm"))

                when (state.step) {
                    0 -> StepFarmerAndCenter(
                        state = state,
                        farmers = farmers,
                        locations = locations,
                        godowns = godowns,
                        onQueryChange = viewModel::updateFarmerQuery,
                        onSelectFarmer = viewModel::selectFarmer,
                        onSelectLocation = viewModel::selectLocation,
                        onSelectGodown = viewModel::selectGodown,
                        onQuickAddFarmer = onQuickAddFarmer
                    )
                    1 -> StepWeighmentItems(
                        state = state,
                        products = products,
                        onAddLine = viewModel::addLineItem,
                        onRemoveLine = viewModel::removeLineItem
                    )
                    else -> StepReviewAndConfirm(
                        state = state,
                        nextBillNumber = viewModel.nextBillNumberPreview(),
                        onUpdateMeta = viewModel::updateMeta
                    )
                }
            }
        }
    }
}

@Composable
private fun StepProgressBar(currentStep: Int, labels: List<String>) {
    Surface(color = CardWhite, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { idx, label ->
                val active = idx <= currentStep
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (active) BhoomgaadiGreen else BorderGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = CardWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) BhoomgaadiDarkGreen else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepFarmerAndCenter(
    state: CreateBillUiState,
    farmers: List<com.example.data.local.entities.FarmerEntity>,
    locations: List<com.example.data.local.entities.LocationEntity>,
    godowns: List<com.example.data.local.entities.GodownEntity>,
    onQueryChange: (String) -> Unit,
    onSelectFarmer: (com.example.data.local.entities.FarmerEntity) -> Unit,
    onSelectLocation: (com.example.data.local.entities.LocationEntity) -> Unit,
    onSelectGodown: (com.example.data.local.entities.GodownEntity) -> Unit,
    onQuickAddFarmer: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Center & Godown selection
        item {
            SectionCard(title = "Procurement Center & Godown") {
                SimpleDropdown(
                    label = "Procurement Location *",
                    selectedText = state.draft.selectedLocation?.locationName ?: "Select Location",
                    items = locations,
                    itemLabel = { it.locationName },
                    onSelected = onSelectLocation,
                    testTag = "bill_location_dropdown"
                )
                Spacer(Modifier.height(10.dp))
                SimpleDropdown(
                    label = "Temporary Godown *",
                    selectedText = state.draft.selectedGodown?.godownName
                        ?: if (state.draft.selectedLocation == null) "Select Location First" else "Select Godown",
                    items = godowns,
                    itemLabel = { it.godownName },
                    onSelected = onSelectGodown,
                    enabled = state.draft.selectedLocation != null && godowns.isNotEmpty(),
                    testTag = "bill_godown_dropdown"
                )
            }
        }

        // Selected Farmer Banner
        if (state.draft.selectedFarmer != null) {
            val f = state.draft.selectedFarmer
            item {
                SectionCard(
                    title = "Selected Farmer",
                    containerColor = BhoomgaadiLightGreen
                ) {
                    Text(f.farmerName, style = MaterialTheme.typography.titleLarge, color = BhoomgaadiDarkGreen)
                    Spacer(Modifier.height(4.dp))
                    InfoRow("Aadhaar", maskAadhaar(f.aadhaarNumber), monoValue = true)
                    InfoRow("Village", "${f.village}${if (f.block.isNotBlank()) ", ${f.block}" else ""}")
                    InfoRow("Bank Account", "${f.bankAccountType.label} (${f.ifsc.ifBlank { "No IFSC" }})")
                }
            }
        }

        // Farmer Picker
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Farmer",
                    style = MaterialTheme.typography.titleMedium,
                    color = BhoomgaadiDarkGreen,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onQuickAddFarmer, modifier = Modifier.testTag("bill_quick_add_farmer")) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Farmer")
                }
            }
            OutlinedTextField(
                value = state.farmerQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Search farmer by name, Aadhaar, or village...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bill_farmer_search_input")
            )
        }

        if (farmers.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.PersonSearch,
                    title = "No Farmers Found",
                    description = "Register a farmer profile first so their Aadhaar & bank details are frozen onto the bill.",
                    actionLabel = "Register Farmer",
                    onAction = onQuickAddFarmer
                )
            }
        } else {
            items(farmers.take(15), key = { it.farmerUuid }) { farmer ->
                val selected = state.draft.selectedFarmer?.farmerUuid == farmer.farmerUuid
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectFarmer(farmer) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selected) BhoomgaadiLightGreen else CardWhite),
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) BhoomgaadiGreen else BorderGray)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(farmer.farmerName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${farmer.village} • ${maskAadhaar(farmer.aadhaarNumber)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (selected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = BhoomgaadiGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepWeighmentItems(
    state: CreateBillUiState,
    products: List<ProductEntity>,
    onAddLine: (ProductEntity, Int, Double, Double, Double?, String, String) -> Unit,
    onRemoveLine: (String) -> Unit
) {
    var selectedProduct by remember(products) { mutableStateOf(products.firstOrNull()) }
    var bagsText by remember { mutableStateOf("1") }
    var grossText by remember { mutableStateOf("") }
    var tareText by remember { mutableStateOf("0.5") }
    var rateText by remember(selectedProduct) {
        mutableStateOf(selectedProduct?.ratePerKg?.let { formatQty(it) } ?: "")
    }
    var grade by remember { mutableStateOf("Grade A") }
    var moisture by remember { mutableStateOf("") }

    val grossVal = grossText.toDoubleOrNull() ?: 0.0
    val tareVal = tareText.toDoubleOrNull() ?: 0.0
    val netVal = (grossVal - tareVal).coerceAtLeast(0.0)
    val rateVal = rateText.toDoubleOrNull() ?: (selectedProduct?.ratePerKg ?: 0.0)
    val previewAmount = netVal * rateVal

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "Add Weighed Produce Item") {
                SimpleDropdown(
                    label = "Produce / Stock Item *",
                    selectedText = selectedProduct?.let { "${it.stockItemName} (₹${formatQty(it.ratePerKg)}/kg)" }
                        ?: "Select Produce",
                    items = products,
                    itemLabel = { "${it.stockItemName} — ₹${formatQty(it.ratePerKg)}/kg" },
                    onSelected = { prod ->
                        selectedProduct = prod
                        rateText = formatQty(prod.ratePerKg)
                    },
                    testTag = "bill_product_dropdown"
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = bagsText,
                        onValueChange = { bagsText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Bags") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.7f)
                    )
                    OutlinedTextField(
                        value = grossText,
                        onValueChange = { grossText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Gross Wt (kg) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("bill_gross_weight_input")
                    )
                    OutlinedTextField(
                        value = tareText,
                        onValueChange = { tareText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Tare (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Rate ₹/kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = moisture,
                        onValueChange = { moisture = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) },
                        label = { Text("Moisture %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Grade A", "Grade B", "FAQ").forEach { g ->
                        FilterChip(
                            selected = grade == g,
                            onClick = { grade = g },
                            label = { Text(g) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = BhoomgaadiLightGreen,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Net Weight", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                "${formatQty(netVal)} kg",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = BhoomgaadiDarkGreen
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Line Payable", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                formatCurrency(previewAmount),
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ProcurementOrange
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FieldActionButton(
                    text = "Add Produce to Bill",
                    icon = Icons.Filled.Add,
                    enabled = selectedProduct != null && netVal > 0.0,
                    testTag = "bill_add_line_button",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val prod = selectedProduct ?: return@FieldActionButton
                        onAddLine(
                            prod,
                            bagsText.toIntOrNull() ?: 1,
                            grossVal,
                            tareVal,
                            rateText.toDoubleOrNull(),
                            grade,
                            moisture
                        )
                        grossText = ""
                        moisture = ""
                    }
                )
            }
        }

        if (state.draft.lines.isNotEmpty()) {
            item {
                Text(
                    text = "Added Produce Lines (${state.draft.lines.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = BhoomgaadiDarkGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            items(state.draft.lines, key = { it.id }) { line ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.product.stockItemName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${line.bagCount} bag(s) • Gross ${formatQty(line.grossWeightKg)} - Tare ${formatQty(line.tareWeightKg)} = Net ${formatQty(line.netQuantityKg)} kg",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${line.qualityGrade} • Rate ₹${formatQty(line.ratePerKg)}/kg",
                                style = MaterialTheme.typography.labelMedium,
                                color = BhoomgaadiDarkGreen
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                formatCurrency(line.lineAmount),
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            IconButton(onClick = { onRemoveLine(line.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove item", tint = DangerRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepReviewAndConfirm(
    state: CreateBillUiState,
    nextBillNumber: String,
    onUpdateMeta: (String, String) -> Unit
) {
    val d = state.draft
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionCard(title = "Voucher Summary", containerColor = BhoomgaadiLightGreen) {
            InfoRow("Bill Number", nextBillNumber, boldValue = true, monoValue = true)
            InfoRow("Farmer", d.selectedFarmer?.farmerName.orEmpty(), boldValue = true)
            InfoRow("Village", d.selectedFarmer?.village.orEmpty())
            InfoRow("Center", d.selectedLocation?.locationName.orEmpty())
            InfoRow("Godown", d.selectedGodown?.godownName.orEmpty())
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BhoomgaadiGreen.copy(alpha = 0.25f))
            InfoRow("Total Bags", "${d.totalBags}", boldValue = true, monoValue = true)
            InfoRow("Total Net Weight", "${formatQty(d.totalQuantityKg)} kg", boldValue = true, monoValue = true)
            InfoRow("Grand Total Payable", formatCurrency(d.grandTotalAmount), boldValue = true, monoValue = true)
        }

        SectionCard(title = "Items Breakdown") {
            d.lines.forEachIndexed { idx, line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${idx + 1}. ${line.product.stockItemName}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatQty(line.netQuantityKg)} kg × ₹${formatQty(line.ratePerKg)} (${line.qualityGrade})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        formatCurrency(line.lineAmount),
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (idx < d.lines.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
            }
        }

        SectionCard(title = "Optional Slip Reference & Remarks") {
            OutlinedTextField(
                value = d.weighingSlipRef,
                onValueChange = { onUpdateMeta(it, d.remarks) },
                label = { Text("Manual Weighing Slip / Token Ref") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = d.remarks,
                onValueChange = { onUpdateMeta(d.weighingSlipRef, it) },
                label = { Text("Field Remarks") },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
