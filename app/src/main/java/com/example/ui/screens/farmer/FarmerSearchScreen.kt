package com.example.ui.screens.farmer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.FarmerEntity
import com.example.domain.model.BankAccountType
import com.example.domain.model.MembershipStatus
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun FarmerSearchScreen(
    viewModel: FarmerViewModel,
    onBack: () -> Unit,
    onAddFarmer: (prefillAadhaar: String) -> Unit,
    onEditFarmer: (farmerUuid: String) -> Unit,
    onCreateBillForFarmer: (farmerUuid: String) -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val farmers by viewModel.farmers.collectAsStateWithLifecycle()

    BhoomgaadiScaffold(
        title = "Farmers Directory",
        subtitle = "${farmers.size} registered offline",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val digits = query.filter { it.isDigit() }
                    onAddFarmer(if (digits.length in 8..12) digits else "")
                },
                containerColor = BhoomgaadiGreen,
                contentColor = CardWhite,
                modifier = Modifier.testTag("farmers_add_fab")
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Farmer")
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
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = { Text("Search name, Aadhaar, village, SHG, code...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("farmer_search_input")
                )

                Spacer(Modifier.height(12.dp))

                if (farmers.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Filled.PeopleOutline,
                        title = if (query.isBlank()) "No Farmers Registered Yet" else "No Matching Farmers",
                        description = if (query.isBlank()) {
                            "Add farmers with their 12-digit Aadhaar, SHG/Village details, and bank passbook info to start billing."
                        } else {
                            "No farmer matched \"$query\". You can register them now in seconds."
                        },
                        actionLabel = "Register Farmer",
                        onAction = {
                            val digits = query.filter { it.isDigit() }
                            onAddFarmer(if (digits.length in 8..12) digits else "")
                        }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        items(farmers, key = { it.farmerUuid }) { farmer ->
                            FarmerDirectoryCard(
                                farmer = farmer,
                                onEdit = { onEditFarmer(farmer.farmerUuid) },
                                onBill = { onCreateBillForFarmer(farmer.farmerUuid) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmerDirectoryCard(
    farmer: FarmerEntity,
    onEdit: () -> Unit,
    onBill: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("farmer_card_${farmer.aadhaarNumber}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = farmer.farmerName,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (farmer.fatherOrHusbandName.isNotBlank()) {
                        Text(
                            text = "C/O ${farmer.fatherOrHusbandName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                val isMember = farmer.membershipStatus == MembershipStatus.MEMBER
                StatusPill(
                    text = if (isMember) "MEMBER" else "NON-MEMBER",
                    bgColor = if (isMember) BhoomgaadiLightGreen else SurfaceSubtle,
                    fgColor = if (isMember) BhoomgaadiDarkGreen else TextSecondary
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BorderGray.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))

            InfoRow("Aadhaar", maskAadhaar(farmer.aadhaarNumber), monoValue = true)
            InfoRow("Village / Block", listOf(farmer.village, farmer.block).filter { it.isNotBlank() }.joinToString(", "))
            if (farmer.shgName.isNotBlank()) {
                InfoRow("SHG / Cluster", listOf(farmer.shgName, farmer.cluster).filter { it.isNotBlank() }.joinToString(" • "))
            }
            if (farmer.bmgdMemberCode.isNotBlank()) {
                InfoRow("BMGD Code", farmer.bmgdMemberCode, monoValue = true)
            }

            val bankSummary = when (farmer.bankAccountType) {
                BankAccountType.NONE -> "No Bank Account"
                BankAccountType.OWN -> "${farmer.bankName.ifBlank { "Bank" }} • ${farmer.ifsc}"
                BankAccountType.RELATIVE -> "Relative (${farmer.relativeRelationship?.label ?: "Linked"}) • ${farmer.ifsc}"
            }
            InfoRow("Bank Settlement", bankSummary)

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("farmer_edit_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                FieldActionButton(
                    text = "Create Bill",
                    icon = Icons.Filled.Receipt,
                    color = ProcurementAmber,
                    testTag = "farmer_create_bill_btn",
                    modifier = Modifier.weight(1.3f),
                    onClick = onBill
                )
            }
        }
    }
}
