package com.dime.app.ui.budget

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dime.app.data.local.entity.CategoryEntity
import com.dime.app.util.LocalCurrency

// ── Semantic colors ───────────────────────────────────────────────────────────
private val ExpenseRed = Color(0xFFFF5C5C)
private val GreenInc   = Color(0xFF34D399)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.sheet.isOpen) {
        if (state.sheet.isOpen) sheetState.show() else sheetState.hide()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.isEmpty) {
            EmptyBudgetView(
                onAddOverall   = { viewModel.openNewBudget(isOverall = true) },
                onAddCategory  = { viewModel.openNewBudget(isOverall = false) }
            )
        } else {
            BudgetListContent(
                state    = state,
                onEdit   = viewModel::openEditBudget,
                onDelete = viewModel::deleteBudget,
                onAdd    = { viewModel.openNewBudget(isOverall = false) }
            )
        }

        // FAB — only show when not empty so user can add more
        if (!state.isEmpty) {
            FloatingActionButton(
                onClick        = { viewModel.openNewBudget(isOverall = false) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
                shape          = CircleShape,
                elevation      = FloatingActionButtonDefaults.elevation(0.dp),
                modifier       = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 100.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add budget", modifier = Modifier.size(26.dp))
            }
        }
    }

    // ── Bottom sheet for new / edit budget ────────────────────────────────────
    if (state.sheet.isOpen) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSheet,
            sheetState       = sheetState,
            containerColor   = MaterialTheme.colorScheme.surface,
            dragHandle       = null,
            shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            NewBudgetSheet(
                sheet      = state.sheet,
                categories = state.expenseCategories,
                onDismiss  = viewModel::dismissSheet,
                onSave     = viewModel::saveBudget,
                onTypeChange     = viewModel::setSheetType,
                onCategoryChange = viewModel::setSheetCategory,
                onTimeFrameChange = viewModel::setSheetTimeFrame,
                onAmountChange   = viewModel::setSheetAmount,
                onToggleGreen    = viewModel::toggleSheetGreen
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyBudgetView(
    onAddOverall: () -> Unit,
    onAddCategory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Header icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Wallet,
                contentDescription = null,
                tint   = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("No budgets yet", color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Set up a budget to track your spending\nagainst limits you define.",
            color     = MaterialTheme.colorScheme.secondary,
            fontSize  = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(36.dp))
        DimeButton(
            text    = "Set Overall Budget",
            onClick  = onAddOverall,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick         = onAddCategory,
            modifier        = Modifier.fillMaxWidth(),
            shape           = RoundedCornerShape(14.dp),
            colors          = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border          = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            Text("Add Category Budget", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Main list ─────────────────────────────────────────────────────────────────

@Composable
private fun BudgetListContent(
    state: BudgetUiState,
    onEdit: (BudgetDisplayItem) -> Unit,
    onDelete: (BudgetDisplayItem) -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        // ── Section header ─────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Budgets",
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        // ── Overall budget card ────────────────────────────────────────────
        state.overallBudget?.let { ob ->
            item {
                BudgetCard(
                    item     = ob,
                    onEdit   = { onEdit(ob) },
                    onDelete = { onDelete(ob) }
                )
            }
        }

        // ── Category budgets ───────────────────────────────────────────────
        if (state.categoryBudgets.isNotEmpty()) {
            item {
                Text(
                    "Category Budgets",
                    color      = MaterialTheme.colorScheme.secondary,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(state.categoryBudgets, key = { it.id }) { item ->
                BudgetCard(
                    item     = item,
                    onEdit   = { onEdit(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
    }
}

// ── Single budget card  (swipeable) ──────────────────────────────────────────

@Composable
private fun BudgetCard(
    item: BudgetDisplayItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val revealThreshold = -200f
    val revealed = offsetX < revealThreshold / 2

    val animatedOffset by animateFloatAsState(
        targetValue = if (revealed) revealThreshold else offsetX.coerceIn(revealThreshold, 0f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe"
    )

    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(18.dp))
    ) {
        // Delete reveal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .background(ExpenseRed, RoundedCornerShape(18.dp))
                .clickable { onDelete() }
                .padding(end = 20.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(22.dp))
        }

        // Card content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX < revealThreshold / 2) revealThreshold else 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            offsetX = (offsetX + delta).coerceIn(revealThreshold, 0f)
                        }
                    )
                }
                .clickable { onEdit() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape  = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            BudgetCardContent(item = item)
        }
    }
}


@Composable
private fun BudgetCardContent(item: BudgetDisplayItem) {
    val barColor = when {
        item.isOverBudget -> ExpenseRed
        item.showGreen    -> GreenInc
        else              -> MaterialTheme.colorScheme.primary
    }

    // Animated progress
    var progressTarget by remember { mutableStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue    = progressTarget,
        animationSpec  = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label          = "progress"
    )
    LaunchedEffect(item.progress) { progressTarget = item.progress.toFloat() }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Emoji / icon
            item.category?.let { cat ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(cat.emoji, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
            } ?: run {
                // Overall budget icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Wallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.category?.name ?: "Overall Budget",
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    item.timeFrame.label,
                    color    = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp
                )
            }

            // Amount info
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(item.spent),
                    color      = if (item.isOverBudget) ExpenseRed else MaterialTheme.colorScheme.onSurface,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "of ${formatMoney(item.amount)}",
                    color    = MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Progress bar — track + fill rendered separately to avoid stacking bug
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }

        if (item.isOverBudget) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Over by ${formatMoney(item.spent - item.amount)}",
                color    = ExpenseRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatMoney(item.amount - item.spent)} remaining",
                color    = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp
            )
        }
    }
}

// ── New / Edit budget bottom sheet ────────────────────────────────────────────

@Composable
private fun NewBudgetSheet(
    sheet: NewBudgetSheetState,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onTypeChange: (Boolean) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onTimeFrameChange: (BudgetTimeFrame) -> Unit,
    onAmountChange: (String) -> Unit,
    onToggleGreen: () -> Unit
) {
    val isEdit = sheet.editingBudget != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 32.dp)
    ) {
        // ── Handle + title ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, bottom = 20.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Text(
            if (isEdit) "Edit Budget" else "New Budget",
            color      = MaterialTheme.colorScheme.onSurface,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(20.dp))

        // ── Type selector (Overall vs Category) ────────────────────────────
        if (!isEdit) {
            SheetSection(title = "Budget Type") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp)
                ) {
                    listOf(true to "Overall", false to "Category").forEach { (overall, label) ->
                        val selected = sheet.isOverall == overall
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { onTypeChange(overall) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color      = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 14.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Category picker (when not overall) ────────────────────────────
        if (!sheet.isOverall) {
            SheetSection(title = "Category") {
                if (categories.isEmpty()) {
                    Text("No expense categories found. Add categories first.", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat ->
                            val selected = cat.id == sheet.selectedCategoryId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                                    .clickable { onCategoryChange(cat.id) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cat.emoji, fontSize = 14.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(cat.name, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Time frame ────────────────────────────────────────────────────
        SheetSection(title = "Period") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetTimeFrame.entries.forEach { tf ->
                    val selected = sheet.timeFrame == tf
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
                            .clickable { onTimeFrameChange(tf) }
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tf.label,
                            color      = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize   = 12.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Amount ────────────────────────────────────────────────────────
        SheetSection(title = "Amount") {
            OutlinedTextField(
                value         = sheet.amountText,
                onValueChange = onAmountChange,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("0.00", color = MaterialTheme.colorScheme.secondary) },
                leadingIcon   = { Text("$", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
                    cursorColor          = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
        Spacer(Modifier.height(16.dp))

        // ── Show green toggle ─────────────────────────────────────────────
        SheetSection(title = "Appearance") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onToggleGreen)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show green when under budget", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Bar turns green when you're on track", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                }
                Switch(
                    checked = sheet.showGreen,
                    onCheckedChange = { onToggleGreen() },
                    colors  = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GreenInc)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // ── Error ─────────────────────────────────────────────────────────
        sheet.errorMessage?.let { err ->
            Text(
                err,
                color    = ExpenseRed,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(12.dp))
        }

        // ── Save button ───────────────────────────────────────────────────
        DimeButton(
            text     = if (isEdit) "Save Changes" else "Create Budget",
            onClick  = onSave,
            loading  = sheet.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        if (isEdit) {
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

// ── Re-usable helpers ─────────────────────────────────────────────────────────

@Composable
private fun SheetSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(title.uppercase(), color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DimeButton(
    text: String,
    onClick: () -> Unit,
    loading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Button(
        onClick  = onClick,
        modifier = modifier.height(52.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        enabled  = !loading
    ) {
        if (loading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun formatMoney(amount: Double): String {
    return LocalCurrency.current.format(amount)
}
