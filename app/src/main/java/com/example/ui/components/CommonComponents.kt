package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BhoomgaadiScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(hostState = snackbarHostState)
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BhoomgaadiDarkGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("topbar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFC8E6C9),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = actions
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content
    )
}

@Composable
fun LanguageToggleChip(
    currentLanguage: AppLanguage,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onToggle,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("language_toggle_button")
    ) {
        Surface(
            color = Color(0x33FFFFFF),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = if (currentLanguage == AppLanguage.ENGLISH) "EN / हिं" else "हिं / EN",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (title != null || trailing != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = BhoomgaadiDarkGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun KeyValueTable(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .background(CardWhite)
    ) {
        rows.forEachIndexed { idx, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (idx % 2 == 0) CardWhite else BhoomgaadiMintTint)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(0.44f)
                )
                Text(
                    text = value.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(0.56f)
                )
            }
            if (idx < rows.lastIndex) {
                HorizontalDivider(color = BorderGray.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
fun FieldActionButton(
    text: String,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    testTag: String = "",
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val taggedModifier = if (testTag.isNotBlank()) modifier.testTag(testTag) else modifier
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = taggedModifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = contentColor
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    bgColor: Color,
    fgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fgColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    boldValue: Boolean = false,
    monoValue: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (boldValue) FontWeight.Bold else FontWeight.Medium,
                fontFamily = if (monoValue) JetBrainsMono else MaterialTheme.typography.bodyLarge.fontFamily
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SimpleDropdown(
    label: String,
    selectedText: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean = true,
    testTag: String = "",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val baseMod = if (testTag.isNotBlank()) modifier.testTag(testTag) else modifier
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = baseMod.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BhoomgaadiLightGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BhoomgaadiDarkGreen,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                FieldActionButton(text = actionLabel, onClick = onAction, testTag = "empty_state_action")
            }
        }
    }
}

fun formatCurrency(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount)
}

fun formatCurrencyWhole(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }
    return formatter.format(amount)
}

fun formatQty(qty: Double): String = String.format(Locale.US, "%.2f", qty)

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US).format(Date(millis))

fun formatShortDate(millis: Long): String =
    SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date(millis))

fun formatAadhaarSpaced(aadhaar: String): String {
    val digits = aadhaar.filter { it.isDigit() }
    return digits.chunked(4).joinToString(" ")
}

fun maskAadhaar(aadhaar: String): String {
    val digits = aadhaar.filter { it.isDigit() }
    return if (digits.length >= 4) "XXXX-XXXX-${digits.takeLast(4)}" else aadhaar
}
