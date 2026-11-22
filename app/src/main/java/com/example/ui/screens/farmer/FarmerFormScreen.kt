package com.example.ui.screens.farmer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.local.entities.UserEntity
import com.example.domain.model.BankAccountType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.RelativeRelationship
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import java.io.File

private enum class DocTarget { FARMER_PHOTO, ID_PROOF, PASSBOOK }

@Composable
fun FarmerFormScreen(
    viewModel: FarmerViewModel,
    currentUser: UserEntity,
    onBack: () -> Unit,
    onCreateBillForFarmer: (farmerUuid: String) -> Unit,
    onGoHome: () -> Unit
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val blocks by viewModel.allBlocks.collectAsStateWithLifecycle()
    val clusters by viewModel.clustersForSelectedBlock.collectAsStateWithLifecycle()
    val villages by viewModel.villagesForSelectedCluster.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingTarget by remember { mutableStateOf<DocTarget?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var showAddVillageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        val target = pendingTarget
        if (success && file != null && file.exists() && target != null) {
            applyDocPath(viewModel, target, file.absolutePath)
        }
    }

    val launchCameraFor: (DocTarget) -> Unit = { target ->
        val docFile = createDocPhotoFile(context, target.name.lowercase())
        pendingTarget = target
        pendingCameraFile = docFile
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", docFile)
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingTarget
        if (granted && target != null) launchCameraFor(target)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val target = pendingTarget
        if (uri != null && target != null) {
            copyUriToInternalDocs(context, uri, target.name.lowercase())?.let { copied ->
                applyDocPath(viewModel, target, copied.absolutePath)
            }
        }
    }

    fun requestCameraCapture(target: DocTarget) {
        pendingTarget = target
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCameraFor(target) else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun launchPhotoPicker(target: DocTarget) {
        pendingTarget = target
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val stepTitle = when (state.step) {
        0 -> if (state.isEditMode) "Update Farmer — Basic Details" else "Add New Farmer"
        1 -> strings.bankDetails
        2 -> strings.uploadDocuments
        else -> "Confirm Farmer Details"
    }

    BhoomgaadiScaffold(
        title = stepTitle,
        subtitle = if (state.step < 3) "Step ${state.step + 1} of 3" else "Saved to Offline Master",
        onBack = {
            when {
                state.step == 3 -> {
                    viewModel.clearSavedFarmer()
                    onGoHome()
                }
                state.step > 0 -> viewModel.goToStep(state.step - 1)
                else -> onBack()
            }
        },
        snackbarHostState = snackbarHostState,
        bottomBar = {
            if (state.step < 3) {
                Surface(color = CardWhite, shadowElevation = 6.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FieldActionButton(
                            text = if (state.step == 0) strings.cancel else strings.back,
                            color = SlateTileBg,
                            contentColor = TextPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (state.step == 0) onBack() else viewModel.goToStep(state.step - 1)
                            }
                        )
                        if (state.step < 2) {
                            FieldActionButton(
                                text = strings.next,
                                color = SecondaryBlue,
                                testTag = "farmer_step_next_button",
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.goToStep(state.step + 1) }
                            )
                        } else {
                            FieldActionButton(
                                text = if (state.isSaving) "Saving..." else if (state.isEditMode) "Save Changes" else "Save Farmer",
                                icon = Icons.Filled.CheckCircle,
                                color = BhoomgaadiGreen,
                                enabled = !state.isSaving,
                                testTag = "farmer_form_save_button",
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.saveFarmer(currentUser.displayName) }
                            )
                        }
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
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DangerLightRed),
                        border = BorderStroke(1.dp, DangerRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = DangerRed)
                            Spacer(Modifier.width(8.dp))
                            Text(state.errorMessage.orEmpty(), color = DangerRed, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                when (state.step) {
                    0 -> {
                        SectionCard(title = strings.basicDetails) {
                            OutlinedTextField(
                                value = state.aadhaarNumber,
                                onValueChange = { v ->
                                    viewModel.updateForm { it.copy(aadhaarNumber = v.filter { c -> c.isDigit() }.take(12)) }
                                },
                                label = { Text("Aadhaar Number (12 Digits) *") },
                                supportingText = { Text("${state.aadhaarNumber.length}/12 digits") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("farmer_aadhaar_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = state.farmerName,
                                onValueChange = { v -> viewModel.updateForm { it.copy(farmerName = v) } },
                                label = { Text("Farmer Name *") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("farmer_name_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = state.fatherOrHusbandName,
                                onValueChange = { v -> viewModel.updateForm { it.copy(fatherOrHusbandName = v) } },
                                label = { Text("Father / Husband Name *") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))

                            // STRICT CASCADING SEQUENCE: 1. Block -> 2. Cluster -> 3. Village
                            Text(
                                text = "Location Hierarchy (${strings.block} → ${strings.cluster} → ${strings.village})",
                                style = MaterialTheme.typography.labelMedium,
                                color = BhoomgaadiDarkGreen
                            )
                            Spacer(Modifier.height(6.dp))

                            SimpleDropdown(
                                label = "1. ${strings.block} *",
                                selectedText = state.block.ifBlank { "Select Block" },
                                items = blocks,
                                itemLabel = { it.blockName },
                                onSelected = viewModel::onSelectBlock,
                                testTag = "farmer_block_dropdown"
                            )
                            Spacer(Modifier.height(10.dp))

                            SimpleDropdown(
                                label = "2. ${strings.cluster} *",
                                selectedText = state.cluster.ifBlank {
                                    if (state.selectedBlockId.isBlank()) "Select Block first" else "Select Cluster"
                                },
                                items = clusters,
                                itemLabel = { it.clusterName },
                                onSelected = viewModel::onSelectCluster,
                                enabled = state.selectedBlockId.isNotBlank() && clusters.isNotEmpty(),
                                testTag = "farmer_cluster_dropdown"
                            )
                            Spacer(Modifier.height(10.dp))

                            SimpleDropdown(
                                label = "3. ${strings.village} *",
                                selectedText = state.village.ifBlank {
                                    if (state.selectedClusterId.isBlank()) "Select Cluster first" else "Select Village"
                                },
                                items = villages,
                                itemLabel = { it.villageName },
                                onSelected = viewModel::onSelectVillage,
                                enabled = state.selectedClusterId.isNotBlank() && villages.isNotEmpty(),
                                testTag = "farmer_village_dropdown"
                            )
                            if (state.selectedClusterId.isNotBlank()) {
                                TextButton(
                                    onClick = { showAddVillageDialog = true },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add New Village in ${state.cluster}")
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = state.shgName,
                                onValueChange = { v -> viewModel.updateForm { it.copy(shgName = v) } },
                                label = { Text("SHG Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = state.mobileNumber,
                                onValueChange = { v ->
                                    viewModel.updateForm { it.copy(mobileNumber = v.filter { c -> c.isDigit() }.take(10)) }
                                },
                                label = { Text("Mobile Number (Optional)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Membership Status *", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                MembershipStatus.entries.forEach { status ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = state.membershipStatus == status,
                                            onClick = { viewModel.updateForm { it.copy(membershipStatus = status) } }
                                        )
                                        Text(status.label)
                                    }
                                }
                            }
                            if (state.membershipStatus == MembershipStatus.MEMBER) {
                                OutlinedTextField(
                                    value = state.bmgdMemberCode,
                                    onValueChange = { v -> viewModel.updateForm { it.copy(bmgdMemberCode = v) } },
                                    label = { Text("Membership No.") },
                                    placeholder = { Text("Leave blank if not assigned yet") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    1 -> {
                        SectionCard(title = "Bank Information") {
                            SimpleDropdown(
                                label = "Bank Account Type",
                                selectedText = state.bankAccountType.label,
                                items = BankAccountType.entries,
                                itemLabel = { it.label },
                                onSelected = { type -> viewModel.updateForm { it.copy(bankAccountType = type) } }
                            )
                            if (state.bankAccountType == BankAccountType.RELATIVE) {
                                Spacer(Modifier.height(10.dp))
                                SimpleDropdown(
                                    label = "Relative Relationship *",
                                    selectedText = state.relativeRelationship?.label ?: "Select Relationship",
                                    items = RelativeRelationship.entries,
                                    itemLabel = { it.label },
                                    onSelected = { rel -> viewModel.updateForm { it.copy(relativeRelationship = rel) } }
                                )
                            }
                            if (state.bankAccountType != BankAccountType.NONE) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = state.accountHolderName,
                                    onValueChange = { v -> viewModel.updateForm { it.copy(accountHolderName = v) } },
                                    label = { Text("Account Holder Name *") },
                                    placeholder = { Text("Defaults to Farmer Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = state.bankName,
                                    onValueChange = { v -> viewModel.updateForm { it.copy(bankName = v) } },
                                    label = { Text("Bank Name *") },
                                    placeholder = { Text("State Bank of India / CRGB") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = state.fullAccountNumber,
                                    onValueChange = { v ->
                                        viewModel.updateForm { it.copy(fullAccountNumber = v.filter { c -> c.isDigit() }) }
                                    },
                                    label = { Text("Account Number *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("farmer_bank_ac_input")
                                )
                                Spacer(Modifier.height(10.dp))
                                val mismatch = state.confirmAccountNumber.isNotEmpty() &&
                                    state.confirmAccountNumber != state.fullAccountNumber
                                OutlinedTextField(
                                    value = state.confirmAccountNumber,
                                    onValueChange = { v ->
                                        viewModel.updateForm { it.copy(confirmAccountNumber = v.filter { c -> c.isDigit() }) }
                                    },
                                    label = { Text("Confirm Account Number *") },
                                    isError = mismatch,
                                    supportingText = {
                                        if (mismatch) Text("Account numbers do not match", color = DangerRed)
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("farmer_bank_confirm_ac_input")
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = state.ifsc,
                                    onValueChange = { v -> viewModel.updateForm { it.copy(ifsc = v.uppercase().take(11)) } },
                                    label = { Text("IFSC Code (11 chars) *") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("farmer_ifsc_input")
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = state.branch,
                                    onValueChange = { v -> viewModel.updateForm { it.copy(branch = v) } },
                                    label = { Text("Branch Name") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    2 -> {
                        SectionCard(title = strings.uploadDocuments) {
                            DocumentPhotoRow(
                                label = "Farmer's Photo (Optional)",
                                photoPath = state.farmerPhotoPath,
                                onCaptureCamera = { requestCameraCapture(DocTarget.FARMER_PHOTO) },
                                onPickGallery = { launchPhotoPicker(DocTarget.FARMER_PHOTO) },
                                onClear = { viewModel.updateForm { it.copy(farmerPhotoPath = "") } }
                            )
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = BorderGray)
                            Spacer(Modifier.height(14.dp))
                            DocumentPhotoRow(
                                label = "Aadhaar / ID Document",
                                photoPath = state.idDocPhotoPath,
                                onCaptureCamera = { requestCameraCapture(DocTarget.ID_PROOF) },
                                onPickGallery = { launchPhotoPicker(DocTarget.ID_PROOF) },
                                onClear = { viewModel.updateForm { it.copy(idDocPhotoPath = "") } }
                            )
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = BorderGray)
                            Spacer(Modifier.height(14.dp))
                            DocumentPhotoRow(
                                label = "Bank Passbook / Bank Document",
                                photoPath = state.bankPassbookPhotoPath,
                                onCaptureCamera = { requestCameraCapture(DocTarget.PASSBOOK) },
                                onPickGallery = { launchPhotoPicker(DocTarget.PASSBOOK) },
                                onClear = { viewModel.updateForm { it.copy(bankPassbookPhotoPath = "") } }
                            )
                        }
                    }

                    else -> {
                        // Step 3: Confirmation Screen matching Layout 1 Screen 9
                        val saved = state.savedFarmer
                        Surface(
                            color = BhoomgaadiLightGreen,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BhoomgaadiGreen.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BhoomgaadiGreen, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        if (state.isEditMode) "Farmer Details Updated!" else "Farmer Added Successfully",
                                        fontWeight = FontWeight.Bold,
                                        color = BhoomgaadiDarkGreen,
                                        fontSize = 16.sp
                                    )
                                    Text("Saved offline to SQLite Farmer Master.", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (saved != null) {
                            if (saved.farmerPhotoPath.isNotBlank() && File(saved.farmerPhotoPath).exists()) {
                                AsyncImage(
                                    model = File(saved.farmerPhotoPath),
                                    contentDescription = "Farmer Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                            KeyValueTable(
                                rows = listOf(
                                    "Aadhaar Number" to formatAadhaarSpaced(saved.aadhaarNumber),
                                    "Farmer Name" to saved.farmerName,
                                    "Father / Husband" to saved.fatherOrHusbandName,
                                    "Block" to saved.block,
                                    "Cluster" to saved.cluster,
                                    "Village" to saved.village,
                                    "SHG Name" to saved.shgName,
                                    "Membership Status" to saved.membershipStatus.label,
                                    "Bank Account" to if (saved.fullAccountNumber.isBlank()) "None"
                                    else "******${saved.fullAccountNumber.takeLast(4)} (${saved.bankName.ifBlank { saved.ifsc }})"
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FieldActionButton(
                                    text = strings.createNewBill,
                                    color = BhoomgaadiGreen,
                                    testTag = "farmer_added_create_bill_btn",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        viewModel.clearSavedFarmer()
                                        onCreateBillForFarmer(saved.farmerUuid)
                                    }
                                )
                                FieldActionButton(
                                    text = strings.backToHome,
                                    color = SlateTileBg,
                                    contentColor = TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        viewModel.clearSavedFarmer()
                                        onGoHome()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddVillageDialog) {
        var newVillageText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddVillageDialog = false },
            title = { Text("Add Village to ${state.cluster}", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newVillageText,
                    onValueChange = { newVillageText = it },
                    label = { Text("Village Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newVillageText.isNotBlank()) {
                        viewModel.addCustomVillageForCurrentCluster(newVillageText)
                        showAddVillageDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddVillageDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DocumentPhotoRow(
    label: String,
    photoPath: String,
    onCaptureCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        if (photoPath.isNotBlank() && File(photoPath).exists()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceSubtle)
            ) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                FilledIconButton(
                    onClick = onClear,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = DangerRed),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCaptureCamera,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (photoPath.isBlank()) "Camera" else "Retake")
            }
            OutlinedButton(
                onClick = onPickGallery,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Gallery")
            }
        }
    }
}

private fun applyDocPath(viewModel: FarmerViewModel, target: DocTarget, path: String) {
    viewModel.updateForm {
        when (target) {
            DocTarget.FARMER_PHOTO -> it.copy(farmerPhotoPath = path)
            DocTarget.ID_PROOF -> it.copy(idDocPhotoPath = path)
            DocTarget.PASSBOOK -> it.copy(bankPassbookPhotoPath = path)
        }
    }
}

private fun createDocPhotoFile(context: Context, prefix: String): File {
    val dir = File(context.filesDir, "farmer_docs").apply { mkdirs() }
    return File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
}

private fun copyUriToInternalDocs(context: Context, uri: Uri, prefix: String): File? = runCatching {
    val outFile = createDocPhotoFile(context, prefix)
    context.contentResolver.openInputStream(uri)?.use { input ->
        outFile.outputStream().use { output -> input.copyTo(output) }
    }
    outFile
}.getOrNull()
