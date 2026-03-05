package com.dime.app.ui.addtransaction

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dime.app.data.local.entity.AccountEntity
import com.dime.app.ui.components.bounceClick
import com.dime.app.util.LocalCurrency
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Palette ────────────────────────────────────────────────────────────────────
private val BgDeep      = Color(0xFF000000)
private val BgCard      = Color(0xFF0F0F14)
private val IncomeGreen = Color(0xFF34D399)
private val ExpenseRed  = Color(0xFFFF5C5C)
private val TextPrimary = Color(0xFFF0F0F5)
private val TextSub     = Color(0xFF6B6B80)
private val DividerCol  = Color(0xFF1E1E28)

// ── Sheet ──────────────────────────────────────────────────────────────────────

@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    val state   by viewModel.uiState.collectAsStateWithLifecycle()
    val currency = LocalCurrency.current

    // Auto-dismiss on success
    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) {
            delay(900)
            viewModel.reset()
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .background(BgDeep)
            .imePadding()
            .padding(bottom = 32.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────────
        SheetHeader()

        // ── Success Banner ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.savedSuccessfully,
            enter   = slideInVertically() + fadeIn(),
            exit    = slideOutVertically() + fadeOut()
        ) {
            StatusBanner(
                message = "Transaction saved!",
                color   = IncomeGreen,
                icon    = Icons.Rounded.CheckCircle
            )
        }

        // ── Error Banner ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.errorMessage != null,
            enter   = slideInVertically() + fadeIn(),
            exit    = slideOutVertically() + fadeOut()
        ) {
            StatusBanner(
                message = state.errorMessage ?: "",
                color   = ExpenseRed,
                icon    = Icons.Rounded.ErrorOutline,
                onDismiss = viewModel::dismissError
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Type Toggle ──────────────────────────────────────────────────────
        TypeToggle(
            isIncome   = state.isIncome,
            onSelect   = viewModel::setIsIncome,
            modifier   = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(20.dp))

        // ── Amount ───────────────────────────────────────────────────────────
        AmountInput(
            value    = state.amountText,
            isIncome = state.isIncome,
            onChange = viewModel::setAmount,
            symbol   = currency.symbol,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(16.dp))

        // ── Note ─────────────────────────────────────────────────────────────
        NoteInput(
            value    = state.note,
            onChange = viewModel::setNote,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(16.dp))

        // ── Category Picker ──────────────────────────────────────────────────
        SectionLabel("Category", modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        if (state.filteredCategories.isNotEmpty()) {
            CategoryRow(
                categories = state.filteredCategories,
                selectedId = state.selectedCategoryId,
                isIncome   = state.isIncome,
                onSelect   = viewModel::selectCategory
            )
        } else {
            Text(
                "No categories yet",
                color    = TextSub,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Account Picker ───────────────────────────────────────────────────
        if (state.accounts.isNotEmpty()) {
            SectionLabel("Account", modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            AccountRow(
                accounts   = state.accounts,
                selectedId = state.selectedAccountId,
                onSelect   = viewModel::selectAccount
            )
            Spacer(Modifier.height(16.dp))
        }

        // ── Date ─────────────────────────────────────────────────────────────
        SectionLabel("Date", modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        DateSelector(
            date     = state.date,
            onSelect = viewModel::setDate,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(24.dp))

        // ── Save Button ──────────────────────────────────────────────────────
        SaveButton(
            enabled  = state.isValid && !state.isSaving && !state.savedSuccessfully,
            isIncome = state.isIncome,
            isSaving = state.isSaving,
            onClick  = viewModel::save,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun SheetHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DividerCol),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint     = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Add Transaction",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Text(
                "Enter transaction details below",
                fontSize = 12.sp,
                color    = TextSub
            )
        }
    }
}

// ── Status Banner ──────────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(
    message: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            fontSize = 14.sp,
            color    = color,
            modifier = Modifier.weight(1f)
        )
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Type Toggle ────────────────────────────────────────────────────────────────

@Composable
private fun TypeToggle(
    isIncome: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .padding(4.dp)
    ) {
        TypeOption(
            label    = "Expense",
            selected = !isIncome,
            color    = ExpenseRed,
            modifier = Modifier.weight(1f),
            onClick  = { onSelect(false) }
        )
        TypeOption(
            label    = "Income",
            selected = isIncome,
            color    = IncomeGreen,
            modifier = Modifier.weight(1f),
            onClick  = { onSelect(true) }
        )
    }
}

@Composable
private fun TypeOption(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else Color.Transparent)
            .bounceClick { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize   = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color      = if (selected) color else TextSub
        )
    }
}

// ── Amount Input ───────────────────────────────────────────────────────────────

@Composable
private fun AmountInput(
    value: String,
    isIncome: Boolean,
    onChange: (String) -> Unit,
    symbol: String,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isIncome) IncomeGreen else ExpenseRed

    Column(
        modifier          = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                symbol,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Light,
                color      = TextSub
            )
            Spacer(Modifier.width(4.dp))
            // Large display-style amount
            BasicAmountField(
                value    = value,
                color    = accentColor,
                onChange = onChange
            )
        }
        // Thin colored underline
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0f), accentColor.copy(alpha = 0.7f), accentColor.copy(alpha = 0f))
                    )
                )
        )
    }
}

@Composable
private fun BasicAmountField(
    value: String,
    color: Color,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        placeholder   = {
            Text(
                "0.00",
                fontSize   = 56.sp,
                fontWeight = FontWeight.Bold,
                color      = color.copy(alpha = 0.25f),
                textAlign  = TextAlign.Center
            )
        },
        textStyle     = androidx.compose.ui.text.TextStyle(
            fontSize   = 56.sp,
            fontWeight = FontWeight.Bold,
            color      = color,
            textAlign  = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine    = true,
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor          = color
        ),
        modifier = Modifier.widthIn(min = 80.dp, max = 300.dp)
    )
}

// ── Note Input ─────────────────────────────────────────────────────────────────

@Composable
private fun NoteInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        placeholder   = { Text("Note (e.g. Lunch, Grab ride…)", color = TextSub, fontSize = 14.sp) },
        singleLine    = true,
        shape         = RoundedCornerShape(14.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            focusedBorderColor   = DividerCol,
            unfocusedBorderColor = DividerCol,
            cursorColor          = TextPrimary,
            focusedContainerColor   = BgCard,
            unfocusedContainerColor = BgCard
        ),
        modifier      = modifier.fillMaxWidth(),
        leadingIcon   = {
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = TextSub, modifier = Modifier.size(18.dp))
        }
    )
}

// ── Section Label ──────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text     = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color    = TextSub,
        modifier = modifier
    )
}

// ── Category Row ───────────────────────────────────────────────────────────────

@Composable
private fun CategoryRow(
    categories: List<com.dime.app.data.local.entity.CategoryEntity>,
    selectedId: String?,
    isIncome: Boolean,
    onSelect: (String) -> Unit
) {
    val accentColor = if (isIncome) IncomeGreen else ExpenseRed

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.width(12.dp))
        categories.forEach { cat ->
            val selected = cat.id == selectedId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) accentColor.copy(alpha = 0.20f) else BgCard
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accentColor.copy(alpha = 0.6f) else DividerCol,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .bounceClick { onSelect(cat.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(cat.emoji, fontSize = 16.sp)
                    Text(
                        cat.name,
                        fontSize   = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (selected) accentColor else TextPrimary,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
    }
}

// ── Account Row ────────────────────────────────────────────────────────────────

@Composable
private fun AccountRow(
    accounts: List<AccountEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier.width(12.dp))
        accounts.forEach { acc ->
            val selected = acc.id == selectedId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color(0xFF1A2A3A) else BgCard)
                    .border(
                        1.dp,
                        if (selected) Color(0xFF4A90D9).copy(alpha = 0.6f) else DividerCol,
                        RoundedCornerShape(12.dp)
                    )
                    .bounceClick { onSelect(acc.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.AccountBalance,
                        contentDescription = null,
                        tint     = if (selected) Color(0xFF4A90D9) else TextSub,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        acc.accountName,
                        fontSize   = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (selected) Color(0xFF4A90D9) else TextPrimary,
                        maxLines   = 1
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
    }
}

// ── Date Selector ──────────────────────────────────────────────────────────────

@Composable
private fun DateSelector(
    date: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    Row(
        modifier          = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Today button
        DateChip(
            label    = "Today",
            selected = date == LocalDate.now(),
            onClick  = { onSelect(LocalDate.now()) }
        )
        // Yesterday button
        DateChip(
            label    = "Yesterday",
            selected = date == LocalDate.now().minusDays(1),
            onClick  = { onSelect(LocalDate.now().minusDays(1)) }
        )
        // Custom date chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(BgCard)
                .border(1.dp, DividerCol, RoundedCornerShape(10.dp))
                .bounceClick { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = TextSub, modifier = Modifier.size(14.dp))
                Text(
                    if (date != LocalDate.now() && date != LocalDate.now().minusDays(1))
                        date.format(fmt) else "Pick date",
                    fontSize = 12.sp,
                    color    = TextSub
                )
            }
        }
    }

    if (showPicker) {
        DatePickerModal(
            initialDate = date,
            onConfirm   = { onSelect(it); showPicker = false },
            onDismiss   = { showPicker = false }
        )
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) TextPrimary.copy(alpha = 0.12f) else BgCard)
            .border(
                1.dp,
                if (selected) TextPrimary.copy(alpha = 0.3f) else DividerCol,
                RoundedCornerShape(10.dp)
            )
            .bounceClick { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (selected) TextPrimary else TextSub
        )
    }
}

// ── Date Picker Modal ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMs = initialDate
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant().toEpochMilli()

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selectedMs = datePickerState.selectedDateMillis
                if (selectedMs != null) {
                    val pickedDate = java.time.Instant.ofEpochMilli(selectedMs)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    onConfirm(pickedDate)
                } else {
                    onDismiss()
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState)
    }
}

// ── Save Button ────────────────────────────────────────────────────────────────

@Composable
private fun SaveButton(
    enabled: Boolean,
    isIncome: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (isIncome) IncomeGreen else ExpenseRed

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else color.copy(alpha = 0.3f))
            .then(if (enabled) Modifier.bounceClick { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                color    = Color.White,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(
                    "Save Transaction",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
        }
    }
}
