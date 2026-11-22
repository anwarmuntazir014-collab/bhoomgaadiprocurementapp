package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entities.UserEntity
import com.example.domain.model.AppLanguage
import com.example.domain.model.UserRole
import com.example.ui.components.BhoomgaadiScaffold
import com.example.ui.components.LanguageToggleChip
import com.example.ui.components.StatusPill
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    currentUser: UserEntity,
    currentLanguage: AppLanguage,
    onToggleLanguage: () -> Unit,
    onSearchFarmer: () -> Unit,
    onAddFarmer: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val strings = LocalAppStrings.current

    BhoomgaadiScaffold(
        title = strings.appTitle,
        subtitle = "${currentUser.displayName} • ${if (currentLanguage == AppLanguage.HINDI) currentUser.role.hindiLabel else currentUser.role.label}",
        actions = {
            LanguageToggleChip(currentLanguage = currentLanguage, onToggle = onToggleLanguage)
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
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                // Brand Header matching Layouts 1-3
                Box(
                    modifier = Modifier
                        .size(64.dp)
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

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = strings.brandName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = BhoomgaadiDarkGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.brandSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        StatusPill(
                            text = if (currentUser.role == UserRole.ADMIN) "ADMIN" else "OFFICER",
                            bgColor = if (currentUser.role == UserRole.ADMIN) BhoomgaadiLightGreen else SecondaryLightBlue,
                            fgColor = if (currentUser.role == UserRole.ADMIN) BhoomgaadiDarkGreen else SecondaryBlue
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Exact Simple Vertical Color-Coded Menu Buttons from Layouts 1-3
                HomeMenuButton(
                    text = strings.searchFarmer,
                    icon = Icons.Filled.Search,
                    containerColor = BhoomgaadiGreen,
                    contentColor = Color.White,
                    testTag = "home_btn_search_farmer",
                    onClick = onSearchFarmer
                )

                HomeMenuButton(
                    text = strings.addFarmer,
                    icon = Icons.Filled.PersonAdd,
                    containerColor = SecondaryBlue,
                    contentColor = Color.White,
                    testTag = "home_btn_add_farmer",
                    onClick = onAddFarmer
                )

                HomeMenuButton(
                    text = strings.dashboard,
                    icon = Icons.Filled.BarChart,
                    containerColor = DashboardPurple,
                    contentColor = Color.White,
                    testTag = "home_btn_dashboard",
                    onClick = onOpenDashboard
                )

                HomeMenuButton(
                    text = strings.procurementHistory,
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    containerColor = SlateTileBg,
                    contentColor = TextPrimary,
                    testTag = "home_btn_history",
                    onClick = onOpenHistory
                )

                HomeMenuButton(
                    text = strings.exportData,
                    icon = Icons.Filled.CloudUpload,
                    containerColor = SlateTileBg,
                    contentColor = TextPrimary,
                    testTag = "home_btn_export",
                    onClick = onOpenExport
                )

                HomeMenuButton(
                    text = strings.settings,
                    icon = Icons.Filled.Settings,
                    containerColor = SlateTileBg,
                    contentColor = TextPrimary,
                    testTag = "home_btn_settings",
                    onClick = onOpenSettings
                )

                HomeMenuButton(
                    text = strings.logout,
                    icon = Icons.AutoMirrored.Filled.Logout,
                    containerColor = DangerLightRed,
                    contentColor = DangerRed,
                    borderColor = DangerRed.copy(alpha = 0.35f),
                    testTag = "home_btn_logout",
                    onClick = onLogout
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Version 2.0 • ${currentUser.username} (${currentUser.role.label})",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun HomeMenuButton(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = borderColor?.let { BorderStroke(1.dp, it) },
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
