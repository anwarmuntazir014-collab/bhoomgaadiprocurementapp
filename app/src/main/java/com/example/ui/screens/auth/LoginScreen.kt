package com.example.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.UserRole
import com.example.ui.components.*
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel
) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    val language by authViewModel.language.collectAsStateWithLifecycle()
    val activeUsers by authViewModel.activeUsers.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current

    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    // Pre-fill first available username on Login screen for quick selection
    LaunchedEffect(activeUsers, state.needsInitialAdminSetup) {
        if (!state.needsInitialAdminSetup && username.isBlank() && activeUsers.isNotEmpty()) {
            username = activeUsers.first().username
        }
    }

    BhoomgaadiScaffold(
        title = strings.appTitle,
        actions = {
            LanguageToggleChip(currentLanguage = language, onToggle = authViewModel::toggleLanguage)
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BhoomgaadiDarkGreen)
            }
            return@BhoomgaadiScaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Bhoomgaadi Brand Emblem
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(BhoomgaadiDarkGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bhoomgaadi_icon_fg_1795197116085),
                        contentDescription = strings.brandName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = strings.brandName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = BhoomgaadiDarkGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.brandSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                if (state.errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DangerLightRed),
                        border = BorderStroke(1.dp, DangerRed),
                        shape = RoundedCornerShape(10.dp),
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

                if (state.needsInitialAdminSetup) {
                    SectionCard(title = strings.adminSetupTitle) {
                        Text(
                            text = "Set up the primary Admin account to unlock offline procurement. Afterward, Admin can create Procurement Officer accounts & PINs in Settings.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it.trim()
                                authViewModel.clearError()
                            },
                            label = { Text("${strings.username} (e.g. admin)") },
                            leadingIcon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_admin_username")
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("${strings.displayName} (e.g. Ravi Kumar)") },
                            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_admin_display_name")
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = {
                                pin = it.filter { c -> c.isDigit() }.take(4)
                                authViewModel.clearError()
                            },
                            label = { Text(strings.pin4Digit) },
                            leadingIcon = { Icon(Icons.Filled.Pin, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_admin_pin")
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = {
                                confirmPin = it.filter { c -> c.isDigit() }.take(4)
                                authViewModel.clearError()
                            },
                            label = { Text(strings.confirmPin) },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_admin_confirm_pin")
                        )
                        Spacer(Modifier.height(14.dp))
                        FieldActionButton(
                            text = if (state.isBusy) "Creating..." else strings.createAdminAccount,
                            icon = Icons.Filled.CheckCircle,
                            color = BhoomgaadiDarkGreen,
                            enabled = !state.isBusy,
                            testTag = "setup_admin_submit",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                authViewModel.createInitialAdmin(
                                    username = username.ifBlank { "admin" },
                                    displayName = displayName,
                                    pin = pin,
                                    confirmPin = confirmPin
                                )
                            }
                        )
                    }
                } else {
                    SectionCard(title = strings.loginTitle) {
                        if (activeUsers.isNotEmpty()) {
                            Text(
                                text = "Select User Account",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(6.dp))
                            SimpleDropdown(
                                label = "Account (Admin / Officer)",
                                selectedText = activeUsers.find { it.username.equals(username, ignoreCase = true) }
                                    ?.let { "${it.displayName} (${it.username}) — ${it.role.label}" }
                                    ?: username.ifBlank { "Select User" },
                                items = activeUsers,
                                itemLabel = { "${it.displayName} (${it.username}) — ${it.role.label}" },
                                onSelected = { user ->
                                    username = user.username
                                    pin = ""
                                    authViewModel.clearError()
                                },
                                testTag = "login_user_dropdown"
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it.trim()
                                authViewModel.clearError()
                            },
                            label = { Text(strings.username) },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_username_input")
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = {
                                pin = it.filter { c -> c.isDigit() }.take(4)
                                authViewModel.clearError()
                            },
                            label = { Text(strings.pin4Digit) },
                            leadingIcon = { Icon(Icons.Filled.Pin, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_pin_input")
                        )
                        Spacer(Modifier.height(14.dp))
                        FieldActionButton(
                            text = if (state.isBusy) "Signing in..." else strings.signIn,
                            icon = Icons.Filled.Login,
                            color = BhoomgaadiDarkGreen,
                            enabled = !state.isBusy && username.isNotBlank() && pin.length == 4,
                            testTag = "login_submit_button",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { authViewModel.login(username, pin) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "100% Offline SQLite • Version 2.0",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
