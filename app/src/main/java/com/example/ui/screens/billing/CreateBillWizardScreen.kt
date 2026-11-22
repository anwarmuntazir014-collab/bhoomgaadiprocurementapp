package com.example.ui.screens.billing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entities.FarmerEntity
import com.example.data.local.entities.ProductEntity
import com.example.data.local.entities.UserEntity
import com.example.domain.model.MembershipStatus
import com.example.pdf.BillPdfGenerator
import com.example.pdf.PdfPrintShareHelper
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBillWizardScreen(
    viewModel: CreateBillViewModel,
    currentUser: UserEntity,
    onBackToHome: () -> Unit,
    onQuickAddFarmer: (prefillAadhaar: String) -> Unit,
    onEditFarmer: (farmerUuid: String) -> Unit,
    onOpenReceiptPreview: (billUuid: String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val products by viewModel.activeProducts.collectAsStateWithLifecycle()
    val locations by viewModel.activeLocations.collectAsStateWithLifecycle()
    val godowns by viewModel.godownsForSelectedLocation.collectAsStateWithLifecycle()
    val matchingFarmers by viewModel.matchingFarmers.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    fun handleBack() {
        when (state.step) {
            BillWizardStep.FIND_FARMER -> onBackToHome()
            BillWizardStep.VERIFY_FARMER -> {
                if (state.draft.isEditMode || state.startedWithPreselectedFarmer) onBackToHome()
                else viewModel.navigateStep(BillWizardStep.FIND_FARMER)
            }
            BillWizardStep.BILL_INFO -> {
                if (state.draft.isEditMode || state.startedWithPreselectedFarmer) onBackToHome()
                else viewModel.navigateStep(BillWizardStep.VERIFY_FARMER)
            }
            BillWizardStep.ADD_ITEM -> {
                if (state.draft.lines.isNotEmpty()) viewModel.navigateStep(BillWizardStep.ITEMS_LIST)
                else viewModel.navigateStep(BillWizardStep.BILL_INFO)
            }
            BillWizardStep.ITEMS_LIST -> viewModel.navigateStep(BillWizardStep.BILL_INFO)
            BillWizardStep.MEMBERSHIP -> viewModel.navigateStep(BillWizardStep.ITEMS_LIST)
            BillWizardStep.SUMMARY -> {
                val farmer = state.draft.selectedFarmer
                if (farmer != null && farmer.membershipStatus == MembershipStatus.NON_MEMBER) {
                    viewModel.navigateStep(BillWizardStep.MEMBERSHIP)
                } else {
                    viewModel.navigateStep(BillWizardStep.ITEMS_LIST)
                }
            }
            BillWizardStep.CREATED_SUCCESS -> onBackToHome()
        }
    }

    BackHandler { handleBack() }

    val (topTitle, topSubtitle) = when (state.step) {
        BillWizardStep.FIND_FARMER ->
            (if (state.draft.isEditMode) strings.editBill else strings.createNewBill) to "Find Farmer"
        BillWizardStep.VERIFY_FARMER ->
            "Farmer Found" to "Verify Farmer"
        BillWizardStep.BILL_INFO ->
            strings.billInformation to "Step 1: Procurement Details"
        BillWizardStep.ADD_ITEM ->
            strings.addProcurementItem to "Step 2: Enter Produce Details"
        BillWizardStep.ITEMS_LIST ->
            strings.itemsInCurrentBill to "Step 2: Items in Current Bill"
        BillWizardStep.MEMBERSHIP ->
            strings.membershipDetails to "Step 3: Membership"
        BillWizardStep.SUMMARY ->
            strings.billSummary to if (state.draft.isEditMode) "Edit Bill ${state.draft.editingBillNumber}" else "Step 4: Review Before Final Save"
        BillWizardStep.CREATED_SUCCESS ->
            (if (state.draft.isEditMode) "Bill Updated Successfully" else strings.billCreated) to state.createdBill?.header?.billNumber
    }

    BhoomgaadiScaffold(
        title = topTitle,
        subtitle = topSubtitle,
        onBack = { handleBack() },
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.step) {
                    BillWizardStep.FIND_FARMER -> FindFarmerStep(
                        aadhaarInput = state.aadhaarInput,
                        farmerNotFoundAadhaar = state.farmerNotFoundAadhaar,
                        matchingFarmers = matchingFarmers,
                        onAadhaarChange = viewModel::updateAadhaarInput,
                        onSearch = viewModel::searchFarmerByAadhaar,
                        onSelectFarmer = viewModel::selectFarmerFromList,
                        onAddNewFarmer = onQuickAddFarmer,
                        onBackToHome = onBackToHome
                    )

                    BillWizardStep.VERIFY_FARMER -> {
                        val farmer = state.draft.selectedFarmer
                        if (farmer != null) {
                            VerifyFarmerStep(
                                farmer = farmer,
                                onEditFarmer = { onEditFarmer(farmer.farmerUuid) },
                                onContinue = { viewModel.navigateStep(BillWizardStep.BILL_INFO) },
                                onBack = { viewModel.navigateStep(BillWizardStep.FIND_FARMER) }
                            )
                        }
                    }

                    BillWizardStep.BILL_INFO -> BillInfoStep(
                        farmer = state.draft.selectedFarmer,
                        procurementDateMillis = state.draft.procurementDateMillis,
                        locations = locations,
                        selectedLocation = state.draft.selectedLocation,
                        godowns = godowns,
                        selectedGodown = state.draft.selectedGodown,
                        signatory = viewModel.authorizedSignatory,
                        onDateChange = viewModel::updateProcurementDate,
                        onSelectLocation = viewModel::selectLocation,
                        onSelectGodown = viewModel::selectGodown,
                        onBack = { handleBack() },
                        onNext = {
                            if (state.draft.lines.isNotEmpty()) viewModel.navigateStep(BillWizardStep.ITEMS_LIST)
                            else viewModel.navigateStep(BillWizardStep.ADD_ITEM)
                        }
                    )

                    BillWizardStep.ADD_ITEM -> AddItemStep(
                        farmer = state.draft.selectedFarmer,
                        godownName = state.draft.selectedGodown?.godownName.orEmpty(),
                        products = products,
                        editingLine = state.draft.lines.find { it.id == state.editingLineId },
                        onSaveLine = viewModel::saveLineItem,
                        onBackToList = {
                            if (state.draft.lines.isNotEmpty()) viewModel.navigateStep(BillWizardStep.ITEMS_LIST)
                            else viewModel.navigateStep(BillWizardStep.BILL_INFO)
                        }
                    )

                    BillWizardStep.ITEMS_LIST -> ItemsListStep(
                        draft = state.draft,
                        onAddAnother = { viewModel.navigateStep(BillWizardStep.ADD_ITEM) },
                        onEditLine = viewModel::startEditLine,
                        onDeleteLine = viewModel::removeLineItem,
                        onContinue = { viewModel.navigateStep(BillWizardStep.MEMBERSHIP) }
                    )

                    BillWizardStep.MEMBERSHIP -> MembershipStep(
                        draft = state.draft,
                        feeInr = viewModel.membershipFeeInr,
                        onSetEnroll = viewModel::setMembershipEnrolled,
                        onBack = { viewModel.navigateStep(BillWizardStep.ITEMS_LIST) },
                        onContinue = { viewModel.navigateStep(BillWizardStep.SUMMARY) }
                    )

                    BillWizardStep.SUMMARY -> BillSummaryStep(
                        draft = state.draft,
                        signatory = viewModel.authorizedSignatory,
                        billNoPreview = viewModel.nextBillNumberPreview(),
                        isSubmitting = state.isSubmitting,
                        onUpdateMeta = viewModel::updateMeta,
                        onEditItems = { viewModel.navigateStep(BillWizardStep.ITEMS_LIST) },
                        onConfirm = { viewModel.confirmAndSaveBill(currentUser) }
                    )

                    BillWizardStep.CREATED_SUCCESS -> {
                        val saved = state.createdBill
                        if (saved != null) {
                            BillCreatedSuccessStep(
                                saved = saved,
                                isRevision = state.draft.isEditMode,
                                onPrint3Copies = {
                                    val pdf = File(saved.header.pdfFilePath)
                                    if (pdf.exists()) {
                                        PdfPrintShareHelper.printPdf(
                                            context,
                                            pdf,
                                            "${saved.header.billNumber} (3 Copies)"
                                        )
                                    }
                                },
                                onReprint = {
                                    val pdf = File(saved.header.pdfFilePath)
                                    if (pdf.exists()) {
                                        PdfPrintShareHelper.printPdf(context, pdf, saved.header.billNumber)
                                    }
                                },
                                onPreviewReceipt = { onOpenReceiptPreview(saved.header.billUuid) },
                                onBackToHome = onBackToHome
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindFarmerStep(
    aadhaarInput: String,
    farmerNotFoundAadhaar: String?,
    matchingFarmers: List<FarmerEntity>,
    onAadhaarChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectFarmer: (FarmerEntity) -> Unit,
    onAddNewFarmer: (String) -> Unit,
    onBackToHome: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showQuickDirectory by remember { mutableStateOf(false) }

    SectionCard {
        Text(strings.enterAadhaar, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = aadhaarInput,
            onValueChange = { onAadhaarChange(it.filter { c -> c.isDigit() || c == ' ' }.take(14)) },
            placeholder = { Text("1234 5678 9012") },
            leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bill_aadhaar_input")
        )
        Spacer(Modifier.height(12.dp))
        FieldActionButton(
            text = strings.searchFarmer,
            icon = Icons.Filled.Search,
            color = BhoomgaadiDarkGreen,
            testTag = "bill_search_farmer_btn",
            modifier = Modifier.fillMaxWidth(),
            onClick = onSearch
        )
        Spacer(Modifier.height(12.dp))
        Surface(
            color = BhoomgaadiMintTint,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Enter 12-digit Aadhaar number to search for farmer in the offline database. Existing Master Data farmers load instantly without re-registration.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    if (farmerNotFoundAadhaar != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DangerLightRed),
            border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.PersonOff, contentDescription = null, tint = DangerRed, modifier = Modifier.size(34.dp))
                Text(strings.farmerNotFound, fontWeight = FontWeight.Bold, color = DangerRed)
                Text(
                    "No farmer found for Aadhaar ${formatAadhaarSpaced(farmerNotFoundAadhaar)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                FieldActionButton(
                    text = strings.addFarmer,
                    icon = Icons.Filled.PersonAdd,
                    color = SecondaryBlue,
                    testTag = "bill_not_found_add_farmer_btn",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onAddNewFarmer(farmerNotFoundAadhaar) }
                )
            }
        }
    }

    OutlinedButton(
        onClick = { showQuickDirectory = !showQuickDirectory },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("bill_toggle_farmer_list"),
        shape = RoundedCornerShape(10.dp)
    ) {
        Icon(Icons.Filled.Groups, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (showQuickDirectory) "Hide Farmer Directory" else "Select from Registered Farmers (${matchingFarmers.size})")
    }

    if (showQuickDirectory) {
        SectionCard(title = "Tap Farmer to Select") {
            if (matchingFarmers.isEmpty()) {
                Text("No farmers found. Import Excel Master Data or add a new farmer.", color = TextSecondary)
            } else {
                matchingFarmers.take(15).forEach { farmer ->
                    Surface(
                        color = BhoomgaadiMintTint,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectFarmer(farmer) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(farmer.farmerName, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatAadhaarSpaced(farmer.aadhaarNumber)} • ${farmer.village}, ${farmer.block}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            StatusPill(
                                text = farmer.membershipStatus.label,
                                bgColor = if (farmer.membershipStatus == MembershipStatus.MEMBER) BhoomgaadiLightGreen else ProcurementLightOrange,
                                fgColor = if (farmer.membershipStatus == MembershipStatus.MEMBER) BhoomgaadiDarkGreen else ProcurementOrange
                            )
                        }
                    }
                }
            }
        }
    }

    FieldActionButton(
        text = strings.backToHome,
        icon = Icons.Filled.Home,
        color = SlateTileBg,
        contentColor = TextPrimary,
        modifier = Modifier.fillMaxWidth(),
        onClick = onBackToHome
    )
}

@Composable
private fun VerifyFarmerStep(
    farmer: FarmerEntity,
    onEditFarmer: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showDocsDialog by remember { mutableStateOf(false) }

    Surface(
        color = BhoomgaadiLightGreen,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BhoomgaadiGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BhoomgaadiGreen)
            Spacer(Modifier.width(8.dp))
            Text(strings.farmerFound, fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen)
        }
    }

    KeyValueTable(
        rows = listOf(
            "Aadhaar Number" to formatAadhaarSpaced(farmer.aadhaarNumber),
            "Farmer Name" to farmer.farmerName,
            "Father/Husband" to farmer.fatherOrHusbandName,
            strings.block to farmer.block,
            strings.cluster to farmer.cluster,
            strings.village to farmer.village,
            "SHG Name" to farmer.shgName,
            "Membership Status" to farmer.membershipStatus.label,
            "Membership No." to farmer.bmgdMemberCode,
            "Bank Name" to farmer.bankName,
            "Account Number" to farmer.fullAccountNumber,
            "IFSC Code" to farmer.ifsc
        )
    )

    FieldActionButton(
        text = "View / Update Details",
        icon = Icons.Filled.Edit,
        color = SecondaryLightBlue,
        contentColor = SecondaryBlue,
        modifier = Modifier.fillMaxWidth(),
        onClick = onEditFarmer
    )

    FieldActionButton(
        text = strings.viewDocuments,
        icon = Icons.Filled.Description,
        color = SlateTileBg,
        contentColor = TextPrimary,
        modifier = Modifier.fillMaxWidth(),
        onClick = { showDocsDialog = true }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FieldActionButton(
            text = "Continue to Bill",
            icon = Icons.Filled.ArrowForward,
            color = BhoomgaadiDarkGreen,
            testTag = "bill_verify_continue_btn",
            modifier = Modifier.weight(1.4f),
            onClick = onContinue
        )
        FieldActionButton(
            text = strings.back,
            color = SlateTileBg,
            contentColor = TextPrimary,
            modifier = Modifier.weight(0.9f),
            onClick = onBack
        )
    }

    if (showDocsDialog) {
        AlertDialog(
            onDismissRequest = { showDocsDialog = false },
            title = { Text(strings.viewDocuments, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Farmer's Photo", fontWeight = FontWeight.SemiBold)
                    if (farmer.farmerPhotoPath.isNotBlank() && File(farmer.farmerPhotoPath).exists()) {
                        AsyncImage(
                            model = File(farmer.farmerPhotoPath),
                            contentDescription = "Farmer photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Text("Not uploaded yet", color = TextSecondary)
                    }
                    Text("Aadhaar / ID Proof", fontWeight = FontWeight.SemiBold)
                    if (farmer.idDocPhotoPath.isNotBlank() && File(farmer.idDocPhotoPath).exists()) {
                        AsyncImage(
                            model = File(farmer.idDocPhotoPath),
                            contentDescription = "Aadhaar photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Text("Not uploaded yet", color = TextSecondary)
                    }
                    Text("Bank Passbook", fontWeight = FontWeight.SemiBold)
                    if (farmer.bankPassbookPhotoPath.isNotBlank() && File(farmer.bankPassbookPhotoPath).exists()) {
                        AsyncImage(
                            model = File(farmer.bankPassbookPhotoPath),
                            contentDescription = "Passbook photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Text("Not uploaded yet", color = TextSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDocsDialog = false }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillInfoStep(
    farmer: FarmerEntity?,
    procurementDateMillis: Long,
    locations: List<com.example.data.local.entities.LocationEntity>,
    selectedLocation: com.example.data.local.entities.LocationEntity?,
    godowns: List<com.example.data.local.entities.GodownEntity>,
    selectedGodown: com.example.data.local.entities.GodownEntity?,
    signatory: String,
    onDateChange: (Long) -> Unit,
    onSelectLocation: (com.example.data.local.entities.LocationEntity) -> Unit,
    onSelectGodown: (com.example.data.local.entities.GodownEntity) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showDatePicker by remember { mutableStateOf(false) }

    if (farmer != null) {
        Surface(
            color = BhoomgaadiLightGreen,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Farmer: ${farmer.farmerName}", fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen)
                Text(
                    "Aadhaar: ${formatAadhaarSpaced(farmer.aadhaarNumber)} • ${farmer.village}, ${farmer.block}" +
                        if (farmer.bmgdMemberCode.isNotBlank()) " • ${farmer.bmgdMemberCode}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }

    SectionCard {
        Text("Procurement Date", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = formatShortDate(procurementDateMillis),
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.CalendarToday, contentDescription = "Pick Date")
                }
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .testTag("bill_date_field")
        )

        Spacer(Modifier.height(12.dp))
        SimpleDropdown(
            label = "Procurement Location *",
            selectedText = selectedLocation?.locationName ?: "Select Location",
            items = locations,
            itemLabel = { it.locationName },
            onSelected = onSelectLocation,
            testTag = "bill_location_dropdown"
        )

        Spacer(Modifier.height(12.dp))
        SimpleDropdown(
            label = "Temporary Godown *",
            selectedText = selectedGodown?.godownName
                ?: if (selectedLocation == null) "Select Location first" else "Select Temporary Godown",
            items = godowns,
            itemLabel = { it.godownName },
            onSelected = onSelectGodown,
            enabled = selectedLocation != null,
            testTag = "bill_godown_dropdown"
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = signatory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Authorized Signatory (From Settings)") },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Surface(
            color = SecondaryLightBlue,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = SecondaryBlue)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Permanent Godown will be filled later by the logistics team on the printed receipt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryBlue
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FieldActionButton(
            text = strings.back,
            color = SlateTileBg,
            contentColor = TextPrimary,
            modifier = Modifier.weight(0.8f),
            onClick = onBack
        )
        FieldActionButton(
            text = "Start Procurement",
            icon = Icons.Filled.PlayArrow,
            color = BhoomgaadiDarkGreen,
            testTag = "bill_start_procurement_btn",
            modifier = Modifier.weight(1.4f),
            onClick = onNext
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = procurementDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let(onDateChange)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun AddItemStep(
    farmer: FarmerEntity?,
    godownName: String,
    products: List<ProductEntity>,
    editingLine: com.example.domain.model.BillDraftLineItem?,
    onSaveLine: (ProductEntity, Int, Double, String, Double?) -> Unit,
    onBackToList: () -> Unit
) {
    var selectedProduct by remember(editingLine?.id, products) {
        mutableStateOf(editingLine?.product ?: products.firstOrNull())
    }
    var bagsText by remember(editingLine?.id) { mutableStateOf(editingLine?.bagCount?.toString() ?: "") }
    var wtPerBagText by remember(editingLine?.id) {
        mutableStateOf(editingLine?.weightPerBagKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }
    var moistureText by remember(editingLine?.id) { mutableStateOf(editingLine?.moisturePercent ?: "") }
    var rateText by remember(editingLine?.id, selectedProduct?.productId) {
        mutableStateOf(
            if (editingLine != null && editingLine.product.productId == selectedProduct?.productId) {
                String.format(java.util.Locale.US, "%.2f", editingLine.ratePerKg)
            } else {
                selectedProduct?.let { String.format(java.util.Locale.US, "%.2f", it.ratePerKg) }.orEmpty()
            }
        )
    }

    val bags = bagsText.toIntOrNull() ?: 0
    val wtPerBag = wtPerBagText.toDoubleOrNull() ?: 0.0
    val rate = rateText.toDoubleOrNull() ?: (selectedProduct?.ratePerKg ?: 0.0)
    val liveTotalWeight = bags * wtPerBag
    val liveAmount = liveTotalWeight * rate

    if (farmer != null) {
        Surface(
            color = BhoomgaadiLightGreen,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Farmer: ${farmer.farmerName}", fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen)
                Text(
                    "Aadhaar: ${formatAadhaarSpaced(farmer.aadhaarNumber)}  |  Village: ${farmer.village}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    "Temporary Godown: $godownName",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }

    SectionCard {
        SimpleDropdown(
            label = "Product *",
            selectedText = selectedProduct?.stockItemName ?: "Select Product",
            items = products,
            itemLabel = { "${it.stockItemName} (₹${formatQty(it.ratePerKg)}/kg)" },
            onSelected = { prod ->
                selectedProduct = prod
                rateText = String.format(java.util.Locale.US, "%.2f", prod.ratePerKg)
            },
            testTag = "bill_item_product_dropdown"
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = bagsText,
            onValueChange = { bagsText = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text("No. of Bags *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bill_item_bags_input")
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = wtPerBagText,
            onValueChange = { wtPerBagText = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
            label = { Text("Weight per Bag (kg) *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bill_item_wt_per_bag_input")
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = moistureText,
                onValueChange = { moistureText = it.filter { c -> c.isDigit() || c == '.' || c == '%' }.take(6) },
                label = { Text("Moisture (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("bill_item_moisture_input")
            )
            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
                label = { Text("Rate (₹/kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("bill_item_rate_input")
            )
        }
        Text(
            text = "Rate auto-filled from Rate Master (editable if needed)",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = BhoomgaadiLightGreen,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Total Weight (kg)", style = MaterialTheme.typography.labelMedium, color = BhoomgaadiDarkGreen)
                    Text(
                        formatQty(liveTotalWeight),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = JetBrainsMono,
                        color = BhoomgaadiDarkGreen
                    )
                }
            }
            Surface(
                color = BhoomgaadiLightGreen,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Amount (₹)", style = MaterialTheme.typography.labelMedium, color = BhoomgaadiDarkGreen)
                    Text(
                        formatCurrency(liveAmount),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = JetBrainsMono,
                        color = BhoomgaadiDarkGreen
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        FieldActionButton(
            text = if (editingLine != null) "Update Item" else "Add to Bill",
            icon = Icons.Filled.AddShoppingCart,
            color = BhoomgaadiDarkGreen,
            testTag = "bill_add_item_submit_btn",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val prod = selectedProduct ?: return@FieldActionButton
                onSaveLine(prod, bags, wtPerBag, moistureText, rateText.toDoubleOrNull())
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FieldActionButton(
                text = "Clear",
                color = SlateTileBg,
                contentColor = TextPrimary,
                modifier = Modifier.weight(1f),
                onClick = {
                    bagsText = ""
                    wtPerBagText = ""
                    moistureText = ""
                }
            )
            FieldActionButton(
                text = "Back",
                color = SlateTileBg,
                contentColor = TextPrimary,
                modifier = Modifier.weight(1f),
                onClick = onBackToList
            )
        }
    }
}

@Composable
private fun ItemsListStep(
    draft: com.example.domain.model.BillDraft,
    onAddAnother: () -> Unit,
    onEditLine: (String) -> Unit,
    onDeleteLine: (String) -> Unit,
    onContinue: () -> Unit
) {
    val farmer = draft.selectedFarmer
    if (farmer != null) {
        Surface(
            color = BhoomgaadiLightGreen,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Farmer: ${farmer.farmerName}", fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen)
                Text(
                    "Aadhaar: ${formatAadhaarSpaced(farmer.aadhaarNumber)}  |  Village: ${farmer.village}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    "Temporary Godown: ${draft.selectedGodown?.godownName.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }

    // Compact table matching Layout 2 Screen 6
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .background(CardWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateTileBg)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("#", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.4f))
            Text("Product", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.9f))
            Text("Bags", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.7f))
            Text("Wt", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("Amount", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
            Text("Act", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1.1f))
        }

        if (draft.lines.isEmpty()) {
            Text(
                "No items added yet. Tap '+ Add Another Item'.",
                color = TextSecondary,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            draft.lines.forEachIndexed { idx, line ->
                HorizontalDivider(color = BorderGray.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${idx + 1}", fontSize = 12.sp, modifier = Modifier.weight(0.4f))
                    Column(modifier = Modifier.weight(1.9f)) {
                        Text(
                            line.product.stockItemName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${formatQty(line.weightPerBagKg)}kg/bag" +
                                if (line.moisturePercent.isNotBlank()) " • M:${line.moisturePercent}%" else "",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Text("${line.bagCount}", fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.7f))
                    Text(formatQty(line.netQuantityKg), fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(
                        formatCurrencyWhole(line.lineAmount),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1.2f)
                    )
                    Row(
                        modifier = Modifier.weight(1.1f),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = { onEditLine(line.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit item", tint = SecondaryBlue, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { onDeleteLine(line.id) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete item", tint = DangerRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    KeyValueTable(
        rows = listOf(
            "Total Bags" to draft.totalBags.toString(),
            "Total Weight (kg)" to formatQty(draft.totalQuantityKg),
            "Total Amount (₹)" to formatCurrency(draft.procurementAmountInr)
        )
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        FieldActionButton(
            text = "+ Add Item",
            icon = Icons.Filled.Add,
            color = SecondaryBlue,
            testTag = "bill_add_another_item_btn",
            modifier = Modifier.weight(1f),
            onClick = onAddAnother
        )
        FieldActionButton(
            text = "Continue",
            icon = Icons.Filled.ArrowForward,
            color = BhoomgaadiDarkGreen,
            enabled = draft.lines.isNotEmpty(),
            testTag = "bill_items_continue_btn",
            modifier = Modifier.weight(1f),
            onClick = onContinue
        )
    }
}

@Composable
private fun MembershipStep(
    draft: com.example.domain.model.BillDraft,
    feeInr: Double,
    onSetEnroll: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val farmer = draft.selectedFarmer
    Surface(
        color = BhoomgaadiLightGreen,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Farmer: ${farmer?.farmerName.orEmpty()}", fontWeight = FontWeight.Bold, color = BhoomgaadiDarkGreen)
            Text("Aadhaar: ${formatAadhaarSpaced(farmer?.aadhaarNumber.orEmpty())}", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Current Status:", style = MaterialTheme.typography.bodySmall)
                StatusPill(
                    text = farmer?.membershipStatus?.label ?: "Non-Member",
                    bgColor = ProcurementLightOrange,
                    fgColor = ProcurementOrange
                )
            }
        }
    }

    SectionCard(title = "Does the farmer want to become a member?") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSetEnroll(true) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = draft.membershipEnrolled,
                onClick = { onSetEnroll(true) },
                modifier = Modifier.testTag("bill_membership_yes")
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Yes, register as member (${formatCurrencyWhole(feeInr)})",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSetEnroll(false) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = !draft.membershipEnrolled,
                onClick = { onSetEnroll(false) },
                modifier = Modifier.testTag("bill_membership_no")
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "No, do not want membership (₹ 0)",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(Modifier.height(10.dp))
        Surface(
            color = SecondaryLightBlue,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = SecondaryBlue)
                Spacer(Modifier.width(8.dp))
                Text(
                    "If you select Yes, the farmer's membership status will automatically update to Member in the Farmer Master after the bill is saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryBlue
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(
            color = BhoomgaadiLightGreen,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Membership Amount (₹)", style = MaterialTheme.typography.labelMedium, color = BhoomgaadiDarkGreen)
                Text(
                    formatCurrencyWhole(draft.appliedMembershipAmount),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = JetBrainsMono,
                    color = BhoomgaadiDarkGreen
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        FieldActionButton(
            text = "Back",
            color = SlateTileBg,
            contentColor = TextPrimary,
            modifier = Modifier.weight(0.9f),
            onClick = onBack
        )
        FieldActionButton(
            text = "Continue",
            icon = Icons.Filled.ArrowForward,
            color = BhoomgaadiDarkGreen,
            testTag = "bill_membership_continue_btn",
            modifier = Modifier.weight(1.3f),
            onClick = onContinue
        )
    }
}

@Composable
private fun BillSummaryStep(
    draft: com.example.domain.model.BillDraft,
    signatory: String,
    billNoPreview: String,
    isSubmitting: Boolean,
    onUpdateMeta: (String, String) -> Unit,
    onEditItems: () -> Unit,
    onConfirm: () -> Unit
) {
    val farmer = draft.selectedFarmer
    SectionCard(title = "Farmer & Procurement Details") {
        KeyValueTable(
            rows = listOf(
                "Bill Number" to billNoPreview,
                "Date" to formatShortDate(draft.procurementDateMillis),
                "Aadhaar Number" to formatAadhaarSpaced(farmer?.aadhaarNumber.orEmpty()),
                "Farmer Name" to farmer?.farmerName.orEmpty(),
                "Father/Husband" to farmer?.fatherOrHusbandName.orEmpty(),
                "Village / Block" to "${farmer?.village.orEmpty()}, ${farmer?.block.orEmpty()}",
                "Membership" to if (draft.membershipEnrolled) "New Member (${formatCurrencyWhole(draft.appliedMembershipAmount)})" else (farmer?.membershipStatus?.label.orEmpty()),
                "Location" to draft.selectedLocation?.locationName.orEmpty(),
                "Temp. Godown" to draft.selectedGodown?.godownName.orEmpty(),
                "Permanent Godown" to "(Filled by logistics)",
                "Authorized Signatory" to signatory
            )
        )
    }

    SectionCard(title = "Items (${draft.lines.size})") {
        draft.lines.forEachIndexed { idx, line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${idx + 1}. ${line.product.stockItemName}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${line.bagCount} bags × ${formatQty(line.weightPerBagKg)} kg = ${formatQty(line.netQuantityKg)} kg @ ₹${formatQty(line.ratePerKg)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Text(formatCurrency(line.lineAmount), fontWeight = FontWeight.Bold, fontFamily = JetBrainsMono)
            }
            if (idx < draft.lines.lastIndex) HorizontalDivider(color = BorderGray.copy(alpha = 0.5f))
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = BhoomgaadiDarkGreen)
        Spacer(Modifier.height(8.dp))
        InfoRow("Total Bags", draft.totalBags.toString(), boldValue = true)
        InfoRow("Total Weight (kg)", formatQty(draft.totalQuantityKg), boldValue = true, monoValue = true)
        InfoRow("Procurement Amount (₹)", formatCurrency(draft.procurementAmountInr), boldValue = true, monoValue = true)
        InfoRow("Membership Amount (₹)", formatCurrency(draft.appliedMembershipAmount), boldValue = true, monoValue = true)

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
                Text("TOTAL PAYABLE (₹)", fontWeight = FontWeight.ExtraBold, color = BhoomgaadiDarkGreen)
                Text(
                    formatCurrency(draft.grandTotalAmount),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = JetBrainsMono,
                    color = BhoomgaadiDarkGreen
                )
            }
        }
    }

    SectionCard(title = if (draft.isEditMode) "Mandatory Edit Reason & Remarks" else "Remarks (Optional)") {
        if (draft.isEditMode) {
            OutlinedTextField(
                value = draft.editReason,
                onValueChange = { onUpdateMeta(draft.remarks, it) },
                label = { Text("Reason for Editing Bill *") },
                placeholder = { Text("e.g., Corrected bag count after re-weighment") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bill_edit_reason_input")
            )
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(
            value = draft.remarks,
            onValueChange = { onUpdateMeta(it, draft.editReason) },
            label = { Text("Remarks (Optional)") },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        FieldActionButton(
            text = "Edit Items",
            color = SlateTileBg,
            contentColor = TextPrimary,
            modifier = Modifier.weight(0.9f),
            onClick = onEditItems
        )
        FieldActionButton(
            text = when {
                isSubmitting -> "Saving..."
                draft.isEditMode -> "Save Revised Bill"
                else -> "Confirm & Generate Bill"
            },
            icon = Icons.Filled.CheckCircle,
            color = BhoomgaadiDarkGreen,
            enabled = !isSubmitting,
            testTag = "bill_confirm_generate_btn",
            modifier = Modifier.weight(1.5f),
            onClick = onConfirm
        )
    }
}

@Composable
private fun BillCreatedSuccessStep(
    saved: com.example.data.local.entities.BillWithItems,
    isRevision: Boolean,
    onPrint3Copies: () -> Unit,
    onReprint: () -> Unit,
    onPreviewReceipt: () -> Unit,
    onBackToHome: () -> Unit
) {
    val h = saved.header
    val strings = LocalAppStrings.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(BhoomgaadiGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Text(
            text = if (isRevision) "Bill Revised & PDF Updated!" else strings.billCreated,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = BhoomgaadiDarkGreen
        )

        KeyValueTable(
            rows = listOf(
                "Bill Number" to h.billNumber,
                "Date" to formatShortDate(h.billDateMillis),
                "Farmer Name" to h.farmerNameSnapshot,
                "Aadhaar" to formatAadhaarSpaced(h.farmerAadhaarSnapshot),
                "Total Weight (kg)" to formatQty(h.totalQuantityKg),
                "Total Payable (₹)" to formatCurrency(h.totalAmountInr)
            )
        )

        Surface(
            color = SecondaryLightBlue,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = SecondaryBlue)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Saved offline as ${h.billNumber}.pdf in ${h.publicPdfDisplayPath.ifBlank { "${BillPdfGenerator.PUBLIC_FOLDER_DISPLAY}/${h.billNumber}.pdf" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryBlue
                )
            }
        }

        FieldActionButton(
            text = strings.printReceipt3Copies,
            icon = Icons.Filled.Print,
            color = SecondaryBlue,
            testTag = "bill_success_print3_btn",
            modifier = Modifier.fillMaxWidth(),
            onClick = onPrint3Copies
        )
        FieldActionButton(
            text = strings.reprintReceipt,
            icon = Icons.Filled.Refresh,
            color = SlateTileBg,
            contentColor = TextPrimary,
            testTag = "bill_success_reprint_btn",
            modifier = Modifier.fillMaxWidth(),
            onClick = onReprint
        )
        FieldActionButton(
            text = strings.receiptPreview,
            icon = Icons.Filled.Visibility,
            color = BhoomgaadiGreen,
            testTag = "bill_success_preview_btn",
            modifier = Modifier.fillMaxWidth(),
            onClick = onPreviewReceipt
        )
        FieldActionButton(
            text = strings.backToHome,
            icon = Icons.Filled.Home,
            color = BhoomgaadiDarkGreen,
            testTag = "bill_success_home_btn",
            modifier = Modifier.fillMaxWidth(),
            onClick = onBackToHome
        )
    }
}
