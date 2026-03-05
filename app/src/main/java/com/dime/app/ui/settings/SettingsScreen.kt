package com.dime.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Popular currencies ────────────────────────────────────────────────────────
private val popularCurrencies = listOf(
    Triple("USD", "$", "US Dollar"),
    Triple("EUR", "€", "Euro"),
    Triple("GBP", "£", "British Pound"),
    Triple("JPY", "¥", "Japanese Yen"),
    Triple("AUD", "A$", "Australian Dollar"),
    Triple("CAD", "C$", "Canadian Dollar"),
    Triple("CHF", "Fr", "Swiss Franc"),
    Triple("CNY", "¥", "Chinese Yuan"),
    Triple("INR", "₹", "Indian Rupee"),
    Triple("MXN", "$", "Mexican Peso"),
    Triple("BRL", "R$", "Brazilian Real"),
    Triple("KRW", "₩", "South Korean Won"),
    Triple("SGD", "S$", "Singapore Dollar"),
    Triple("MYR", "RM", "Malaysian Ringgit"),
    Triple("HKD", "HK$", "Hong Kong Dollar"),
    Triple("NZD", "NZ$", "New Zealand Dollar"),
    Triple("SEK", "kr", "Swedish Krona"),
    Triple("NOK", "kr", "Norwegian Krone"),
    Triple("ZAR", "R", "South African Rand"),
    Triple("AED", "د.إ", "UAE Dirham")
)

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item { SettingsHeader(state) }

            // ── Currency ────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Currency") {
                    SettingsRow(
                        icon       = Icons.Rounded.AttachMoney,
                        iconTint   = MaterialTheme.colorScheme.primary,
                        label      = "Currency",
                        value      = "${state.currencyCode}  ${state.currencySymbol}",
                        onClick    = viewModel::openCurrencyPicker
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon       = Icons.Rounded.Numbers,
                        iconTint   = MaterialTheme.colorScheme.primary,
                        label      = "Show cents",
                        sublabel   = "Display decimal places",
                        checked    = state.showCents,
                        onToggle   = viewModel::setShowCents
                    )
                }
            }

            // ── Appearance ──────────────────────────────────────────────────
            item {
                SettingsSection(title = "Appearance") {
                    SegmentedRow(
                        icon     = Icons.Rounded.DarkMode,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label    = "Theme",
                        options  = ColourScheme.entries.map { it.label },
                        selected = state.colourScheme.ordinal,
                        onSelect = { viewModel.setColourScheme(ColourScheme.entries[it]) }
                    )
                }
            }





            // ── AI ─────────────────────────────────────────────────────────
            item {
                SettingsSection(title = "AI Input") {
                    SettingsRow(
                        icon     = Icons.Rounded.AutoAwesome,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label    = "Gemini API Key",
                        value    = if (state.geminiApiKey.isNotBlank()) "••••${state.geminiApiKey.takeLast(4)}" else "Not set",
                        onClick  = viewModel::openApiKeyDialog
                    )
                }
            }

            // ── Data ────────────────────────────────────────────────────────
            item {
                SettingsSection(title = "Data") {
                    SettingsInfoRow(
                        icon     = Icons.Rounded.Receipt,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label    = "Transactions",
                        value    = "${state.transactionCount}"
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        icon     = Icons.Rounded.Category,
                        iconTint = MaterialTheme.colorScheme.primary,
                        label    = "Categories",
                        value    = "${state.categoryCount}"
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon     = Icons.Rounded.DeleteForever,
                        iconTint = Color.Red,
                        label    = "Erase all data",
                        labelColor = Color.Red,
                        onClick  = viewModel::openEraseConfirm
                    )
                }
            }

            // ── App info ────────────────────────────────────────────────────
            item {
                Text(
                    "Finora  •  v1.0",
                    color    = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                )
            }
        }

        // ── Snackbar for success messages ──────────────────────────────────
        val successMsg = state.eraseSuccessMessage
        if (successMsg != null) {
            LaunchedEffect(successMsg) {
                kotlinx.coroutines.delay(2500)
                viewModel.clearSuccessMessage()
            }
            Snackbar(
                modifier       = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor   = MaterialTheme.colorScheme.onSurface
            ) { Text(text = successMsg) }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (state.showCurrencyPicker) {
        CurrencyPickerDialog(
            current  = state.currencyCode,
            onSelect = { sym, code -> viewModel.setCurrency(sym, code) },
            onDismiss = viewModel::closeCurrencyPicker
        )
    }

    if (state.showEraseConfirm) {
        EraseDataDialog(
            confirmText = state.eraseConfirmText,
            isErasing   = state.isErasingData,
            onTextChange = viewModel::setEraseText,
            onConfirm   = viewModel::eraseAllData,
            onDismiss   = viewModel::closeEraseConfirm
        )
    }

    if (state.showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeApiKeyDialog,
            icon = {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text(
                        text = "Enter your Google Gemini API key to enable AI transaction input. Get one free at ai.google.dev",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.apiKeyDraftText,
                        onValueChange = viewModel::setApiKeyDraftText,
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveApiKey) {
                    Text("Save", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeApiKeyDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsHeader(state: SettingsUiState) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Settings", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatPill(value = "${state.transactionCount}", label = "Transactions")
            Box(modifier = Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            StatPill(value = "${state.categoryCount}", label = "Categories")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
    }
}

// ── Section container ─────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            title.uppercase(),
            color      = MaterialTheme.colorScheme.secondary,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier   = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface),
            content  = content
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ── Row types ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = labelColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
        }
        if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    sublabel: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            sublabel?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
        }
        Switch(
            checked  = checked,
            onCheckedChange = onToggle,
            colors   = SwitchDefaults.colors(
                checkedThumbColor  = Color.White,
                checkedTrackColor  = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun SegmentedRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon = icon, tint = iconTint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEachIndexed { idx, opt ->
                val sel = idx == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        opt,
                        color      = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        fontSize   = 12.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 62.dp)  // indent past icon
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

// ── Currency picker dialog ─────────────────────────────────────────────────────

@Composable
private fun CurrencyPickerDialog(
    current: String,
    onSelect: (symbol: String, code: String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCurrencies = remember(searchQuery) {
        if (searchQuery.isBlank()) popularCurrencies
        else popularCurrencies.filter { (code, _, name) ->
            code.contains(searchQuery, ignoreCase = true) ||
                    name.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape         = RoundedCornerShape(20.dp),
            color         = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier      = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Select Currency", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search currency...", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 420.dp)
                ) {
                    if (filteredCurrencies.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No currencies found", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                        }
                    } else {
                        filteredCurrencies.forEachIndexed { idx, (code, symbol, name) ->
                            val selected = code == current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable { onSelect(symbol, code) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    symbol,
                                    color      = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontSize   = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.width(36.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                    Text(code, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                                }
                                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            if (idx < filteredCurrencies.lastIndex) {
                                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}


// ── Erase all data dialog ─────────────────────────────────────────────────────

@Composable
private fun EraseDataDialog(
    confirmText: String,
    isErasing: Boolean,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape         = RoundedCornerShape(20.dp),
            color         = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier      = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Warning icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.15f))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Erase All Data",
                    color      = Color.Red,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This will permanently delete all your transactions. This action cannot be undone.",
                    color     = MaterialTheme.colorScheme.secondary,
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier  = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Text("Type \"delete\" to confirm:", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = confirmText,
                    onValueChange = onTextChange,
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("delete", color = MaterialTheme.colorScheme.secondary) },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
                        cursorColor          = Color.Red
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel", color = MaterialTheme.colorScheme.secondary) }

                    Button(
                        onClick  = onConfirm,
                        enabled  = confirmText.lowercase() == "delete" && !isErasing,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        if (isErasing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Erase", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
