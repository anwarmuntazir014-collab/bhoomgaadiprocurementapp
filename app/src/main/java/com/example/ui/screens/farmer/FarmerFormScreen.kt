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
import com.example.domain.model.BankAccountType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.RelativeRelationship
import com.example.ui.components.*
import com.example.ui.theme.*
import java.io.File

private enum class DocTarget { ID_PROOF, PASSBOOK }

@Composable
fun FarmerFormScreen(
    viewModel: FarmerViewModel,
    onBack: () -> Unit,
    onSaved: (farmerUuid: String) -> Unit
) {
    val state by viewModel.formState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingTarget by remember { mutableStateOf<DocTarget?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(state.savedFarmer) {
        val saved = state.savedFarmer
        if (saved != null) {
            viewModel.clearSavedFarmer()
            onSaved(saved.farmerUuid)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
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

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingTarget
        if (granted && target != null) {
            launchCameraFor(target)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val target = pendingTarget
        if (uri != null && target != null) {
            val copied = copyUriToInternalDocs(context, uri, target.name.lowercase())
            if (copied != null) {
                applyDocPath(viewModel, target, copied.absolutePath)
            }
        }
    }

    fun requestCameraCapture(target: DocTarget) {
        pendingTarget = target
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraFor(target)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchPhotoPicker(target: DocTarget) {
        pendingTarget = target
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    BhoomgaadiScaffold(
        title = if (state.isEditMode) "Edit Farmer Profile" else "Register New Farmer",
        subtitle = "Unique Aadhaar • Offline Verified",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        bottomBar = {
            Surface(
                color = CardWhite,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FieldActionButton(
                        text = if (state.isSaving) "Saving..." else if (state.isEditMode) "Update Farmer Record" else "Save Farmer Record",
                        icon = Icons.Filled.CheckCircle,
                        enabled = !state.isSaving,
                        testTag = "farmer_form_save_button",
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp),
                        onClick = viewModel::saveFarmer
                    )
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DangerLightRed),
                        border = BorderStroke(1.dp, DangerRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = DangerRed)
                            Spacer(Modifier.width(8.dp))
                            Text(state.errorMessage.orEmpty(), color = DangerRed, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Section 1: Identity & Village
                SectionCard(title = "1. Farmer Identity & Village") {
                    OutlinedTextField(
                        value = state.aadhaarNumber,
                        onValueChange = { input ->
                            viewModel.updateForm { it.copy(aadhaarNumber = input.filter { ch -> ch.isDigit() }.take(12)) }
                        },
                        label = { Text("12-Digit Aadhaar Number *") },
                        supportingText = { Text("${state.aadhaarNumber.length}/12 digits • Unique key") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("farmer_aadhaar_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.farmerName,
                        onValueChange = { v -> viewModel.updateForm { it.copy(farmerName = v) } },
                        label = { Text("Farmer Full Name *") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("farmer_name_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.fatherOrHusbandName,
                        onValueChange = { v -> viewModel.updateForm { it.copy(fatherOrHusbandName = v) } },
                        label = { Text("Father / Husband Name") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.mobileNumber,
                        onValueChange = { v ->
                            viewModel.updateForm { it.copy(mobileNumber = v.filter { ch -> ch.isDigit() }.take(10)) }
                        },
                        label = { Text("Mobile Number (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.village,
                            onValueChange = { v -> viewModel.updateForm { it.copy(village = v) } },
                            label = { Text("Village *") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("farmer_village_input")
                        )
                        OutlinedTextField(
                            value = state.block,
                            onValueChange = { v -> viewModel.updateForm { it.copy(block = v) } },
                            label = { Text("Block") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.cluster,
                            onValueChange = { v -> viewModel.updateForm { it.copy(cluster = v) } },
                            label = { Text("Cluster") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.shgName,
                            onValueChange = { v -> viewModel.updateForm { it.copy(shgName = v) } },
                            label = { Text("SHG Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Section 2: Membership
                SectionCard(title = "2. Bhoomgaadi Membership") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MembershipStatus.entries.forEach { status ->
                            FilterChip(
                                selected = state.membershipStatus == status,
                                onClick = { viewModel.updateForm { it.copy(membershipStatus = status) } },
                                label = { Text(status.label) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }
                    if (state.membershipStatus == MembershipStatus.MEMBER) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.bmgdMemberCode,
                            onValueChange = { v -> viewModel.updateForm { it.copy(bmgdMemberCode = v) } },
                            label = { Text("BMGD Member Code") },
                            placeholder = { Text("e.g. BMGD-DTW-1042") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Section 3: Bank Details
                SectionCard(title = "3. Bank Payment Account") {
                    SimpleDropdown(
                        label = "Bank Account Type",
                        selectedText = state.bankAccountType.label,
                        items = BankAccountType.entries,
                        itemLabel = { it.label },
                        onSelected = { type -> viewModel.updateForm { it.copy(bankAccountType = type) } },
                        testTag = "farmer_bank_type_dropdown"
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
                            label = { Text("Account Holder Name") },
                            placeholder = { Text("Defaults to Farmer Name if Own Account") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.bankName,
                            onValueChange = { v -> viewModel.updateForm { it.copy(bankName = v) } },
                            label = { Text("Bank Name") },
                            placeholder = { Text("e.g. State Bank of India, CRGB") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.fullAccountNumber,
                            onValueChange = { v -> viewModel.updateForm { it.copy(fullAccountNumber = v.filter { ch -> ch.isDigit() }) } },
                            label = { Text("Full Account Number *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("farmer_bank_ac_input")
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = state.ifsc,
                                onValueChange = { v -> viewModel.updateForm { it.copy(ifsc = v.uppercase().take(11)) } },
                                label = { Text("IFSC Code *") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("farmer_ifsc_input")
                            )
                            OutlinedTextField(
                                value = state.branch,
                                onValueChange = { v -> viewModel.updateForm { it.copy(branch = v) } },
                                label = { Text("Branch") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 4: Document Photos
                SectionCard(title = "4. KYC & Passbook Photos (Offline)") {
                    DocumentPhotoPickerRow(
                        label = "Aadhaar / ID Proof Photo",
                        photoPath = state.idDocPhotoPath,
                        onCaptureCamera = { requestCameraCapture(DocTarget.ID_PROOF) },
                        onPickGallery = { launchPhotoPicker(DocTarget.ID_PROOF) },
                        onClear = { viewModel.updateForm { it.copy(idDocPhotoPath = "") } }
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = BorderGray.copy(alpha = 0.6f))
                    Spacer(Modifier.height(14.dp))
                    DocumentPhotoPickerRow(
                        label = "Bank Passbook Front Page",
                        photoPath = state.bankPassbookPhotoPath,
                        onCaptureCamera = { requestCameraCapture(DocTarget.PASSBOOK) },
                        onPickGallery = { launchPhotoPicker(DocTarget.PASSBOOK) },
                        onClear = { viewModel.updateForm { it.copy(bankPassbookPhotoPath = "") } }
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DocumentPhotoPickerRow(
    label: String,
    photoPath: String,
    onCaptureCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        if (photoPath.isNotBlank() && File(photoPath).exists()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = CardWhite)
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
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Camera")
            }
            OutlinedButton(
                onClick = onPickGallery,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Photo Picker")
            }
        }
    }
}

private fun applyDocPath(viewModel: FarmerViewModel, target: DocTarget, path: String) {
    viewModel.updateForm {
        when (target) {
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
