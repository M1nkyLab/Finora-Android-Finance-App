package com.dime.app.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.dime.app.ui.components.bounceClick
import com.dime.app.ui.components.shimmerEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dime.app.domain.model.TimePeriod
import com.dime.app.domain.model.label
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Autorenew
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.dime.app.util.LocalCurrency
import kotlin.math.abs
import com.dime.app.data.local.entity.AccountEntity
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle

import androidx.compose.foundation.isSystemInDarkTheme

// ── Palette (resolved per-theme at runtime) ───────────────────────────────────
// Dark: premium OLED fintech palette. Light: Material3 colour tokens.
private data class DashColors(
    val bgDeep: Color,
    val bgCard: Color,
    val bgCardAlt: Color,
    val accentPurple: Color,
    val accentBlue: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val textPrimary: Color,
    val textSub: Color,
    val divider: Color
)

@Composable
private fun dashColors(): DashColors {
    val dark = isSystemInDarkTheme()
    val cs   = MaterialTheme.colorScheme
    return if (dark) DashColors(
        bgDeep       = Color(0xFF000000),
        bgCard       = Color(0xFF0F0F14),
        bgCardAlt    = Color(0xFF14141C),
        accentPurple = Color(0xFF9B6FFF),
        accentBlue   = Color(0xFF5B8FFF),
        accentGreen  = Color(0xFF34D399),
        accentRed    = Color(0xFFFF5C5C),
        textPrimary  = Color(0xFFF0F0F5),
        textSub      = Color(0xFF7A7A8C),
        divider      = Color(0xFF2A2A35)
    ) else DashColors(
        bgDeep       = cs.background,
        bgCard       = cs.surface,
        bgCardAlt    = cs.surfaceVariant,
        accentPurple = cs.primary,
        accentBlue   = cs.secondary,
        accentGreen  = Color(0xFF1B8A5A),
        accentRed    = Color(0xFFD32F2F),
        textPrimary  = cs.onBackground,
        textSub      = cs.onSurfaceVariant,
        divider      = cs.outlineVariant
    )
}


// ── Screen ─────────────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val selectedAccId by viewModel.selectedAccountId.collectAsStateWithLifecycle()
    val c = dashColors()

    val scope = rememberCoroutineScope()
    var showAccountSheet by remember { mutableStateOf(false) }

    Surface(color = c.bgDeep, modifier = Modifier.fillMaxSize()) {
        when (val s = uiState) {
            is DashboardUiState.Loading -> LoadingState(c)
            is DashboardUiState.Ready  -> ReadyContent(
                state = s,
                period = period,
                accounts = accounts,
                selectedAccountId = selectedAccId,
                colors = c,
                onPeriodChange = viewModel::selectPeriod,
                onAccountClick = { showAccountSheet = true }
            )
        }
    }

    if (showAccountSheet) {
        AccountPickerSheet(
            accounts = accounts,
            selectedAccountId = selectedAccId,
            colors = c,
            onSelectAccount = { id ->
                viewModel.selectAccount(id)
                showAccountSheet = false
            },
            onAddAccount = { name, balance ->
                scope.launch { viewModel.addAccount(name, balance) }
                showAccountSheet = false
            },
            onDismiss = { showAccountSheet = false }
        )
    }
}

// ── Loading skeleton ────────────────────────────────────────────────────────────

@Composable
private fun LoadingState(c: DashColors) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .shimmerEffect()
        )
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).shimmerEffect())
                Column(Modifier.weight(1f)) {
                    Box(modifier = Modifier.height(16.dp).fillMaxWidth(0.7f).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.height(12.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                }
                Box(modifier = Modifier.height(20.dp).width(60.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
        }
    }
}

// ── Main scrollable content ────────────────────────────────────────────────────

@Composable
private fun ReadyContent(
    state: DashboardUiState.Ready,
    period: TimePeriod,
    accounts: List<AccountEntity>,
    selectedAccountId: String?,
    colors: DashColors,
    onPeriodChange: (TimePeriod) -> Unit,
    onAccountClick: () -> Unit
) {
    val c = colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroKpiCard(
                net = state.net,
                spent = state.totalSpent,
                income = state.totalIncome,
                period = period,
                accounts = accounts,
                selectedAccId = selectedAccountId,
                colors = c,
                onPeriodClick = {
                    val entries = TimePeriod.entries
                    val nextIndex = (entries.indexOf(period) + 1) % entries.size
                    onPeriodChange(entries[nextIndex])
                },
                onAccountClick = onAccountClick
            )
        }

        if (state.upcomingTransactions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                    Text(
                        text = "UPCOMING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textSub,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = c.divider)
                }
            }
            items(state.upcomingTransactions, key = { "up_" + it.template.id }) { item ->
                UpcomingRow(item, c)
            }
        }

        if (state.dailyTransactions.isNotEmpty()) {
            state.dailyTransactions.forEach { group ->
                item(key = "day_${group.date}") {
                    DayHeader(date = group.date, net = group.dailyNet, c = c)
                }
                items(group.transactions, key = { it.transaction.id }) { item ->
                    TransactionRow(item = item, onClick = { }, showDate = false, c = c)
                }
            }
        } else if (state.upcomingTransactions.isEmpty()) {
            item { EmptyState(c) }
        }
    }
}

// ── Period selector ────────────────────────────────────────────────────────────



// ── Hero KPI card ──────────────────────────────────────────────────────────────

@Composable
private fun HeroKpiCard(
    net: Double,
    spent: Double,
    income: Double,
    period: TimePeriod,
    accounts: List<AccountEntity>,
    selectedAccId: String?,
    colors: DashColors,
    onPeriodClick: () -> Unit,
    onAccountClick: () -> Unit
) {
    val c = colors
    val isPositive  = net >= 0
    val accountLabel = if (selectedAccId == null) "All Accounts"
                       else accounts.find { it.id == selectedAccId }?.accountName ?: "All Accounts"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onAccountClick,
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, c.divider),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = accountLabel.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textSub,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Switch account",
                            tint = c.textSub,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Surface(
                    onClick = onPeriodClick,
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, c.divider),
                    color = Color.Transparent
                ) {
                    Text(
                        text = period.label.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textSub,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            val currency = LocalCurrency.current
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = (if (isPositive) "+" else "\u2212") + " " + currency.code + " ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.accentGreen.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp),
                    letterSpacing = 0.sp
                )
                com.dime.app.ui.components.AnimatedAmountText(
                    amount = kotlin.math.abs(net).toFloat(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.accentGreen,
                    letterSpacing = (-1.5).sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accentGreen)
                    com.dime.app.ui.components.AnimatedAmountText(
                        amount = income.toFloat(), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = c.accentGreen
                    )
                }
                Text("  |  ", fontSize = 13.sp, color = c.divider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\u2212", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accentRed)
                    com.dime.app.ui.components.AnimatedAmountText(
                        amount = spent.toFloat(), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = c.accentRed
                    )
                }
            }
        }
    }
}



// ── Transaction row ────────────────────────────────────────────────────────────

@Composable
private fun TransactionRow(
    item: com.dime.app.data.local.relation.TransactionWithCategory,
    onClick: () -> Unit,
    showDate: Boolean = true,
    c: DashColors
) {
    val tx  = item.transaction
    val cat = item.category

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = c.bgCard,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = cat?.emoji ?: "💸", fontSize = 20.sp)
            }
        }

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = tx.note.ifBlank { cat?.name ?: "Transaction" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tx.onceRecurring) {
                    Icon(
                        imageVector = Icons.Rounded.Autorenew,
                        contentDescription = "Recurring",
                        tint = c.accentPurple,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = formatTime(tx.date), fontSize = 12.sp, color = c.textSub)
                if (showDate) {
                    Text("·", fontSize = 12.sp, color = c.textSub.copy(alpha = 0.5f))
                    Text(text = formatDate(tx.date), fontSize = 12.sp, color = c.textSub)
                }
            }
        }

        val currency = LocalCurrency.current
        Text(
            text = (if (tx.income) "+" else "\u2212") + currency.format(tx.amount),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (tx.income) c.accentGreen else c.accentRed
        )
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(c: DashColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("💸", fontSize = 36.sp)
        Text("No transactions yet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
        Text("Tap + below to log your first entry", fontSize = 14.sp, color = c.textSub)
    }
}

// ── Typography helpers ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp
    )
}

// ── Formatting ─────────────────────────────────────────────────────────────────

private val dateFmt = DateTimeFormatter.ofPattern("d MMM")
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFmt)

private fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)

// ── Smart Section Components ──────────────────────────────────────────────────

@Composable
private fun UpcomingRow(item: com.dime.app.data.local.relation.TemplateWithCategory, c: DashColors) {
    val temp = item.template
    val cat  = item.category

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(cat?.colour?.let { Color(android.graphics.Color.parseColor(it)) }?.copy(alpha = 0.15f) ?: c.bgCard),
            contentAlignment = Alignment.Center
        ) {
            Text(cat?.emoji ?: "📑", fontSize = 18.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = temp.note.ifBlank { cat?.name ?: "Recurring" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary.copy(alpha = 0.7f)
            )
            Text(text = "Scheduled Subscription", fontSize = 12.sp, color = c.accentPurple)
        }

        val currency = LocalCurrency.current
        Text(
            text = (if (temp.income) "+" else "\u2212") + currency.format(temp.amount),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (temp.income) c.accentGreen else c.accentRed
        )
    }
}

@Composable
private fun DayHeader(date: Long, net: Double, c: DashColors) {
    val currency = LocalCurrency.current
    val dt = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()
    val dayLabel = dt.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)).uppercase()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = dayLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = c.textSub, letterSpacing = 1.sp)
            Text(
                text = (if (net >= 0) "+" else "\u2212") + currency.format(kotlin.math.abs(net)),
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.textSub
            )
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 0.5.dp, color = c.divider)
    }
}

// ── Account Picker Sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerSheet(
    accounts: List<AccountEntity>,
    selectedAccountId: String?,
    colors: DashColors,
    onSelectAccount: (String?) -> Unit,
    onAddAccount: (String, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val c = colors
    val currency = LocalCurrency.current
    var isAddingNew  by remember { mutableStateOf(false) }
    var newName      by remember { mutableStateOf("") }
    var newBalance   by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.bgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.divider) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Select Data Source",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AccountItem(
                        name = "All Accounts",
                        balanceLabel = "Aggregate View",
                        icon = Icons.Rounded.AccountBalance,
                        isSelected = selectedAccountId == null,
                        colors = c,
                        onClick = { onSelectAccount(null) }
                    )
                }

                items(accounts) { acc ->
                    AccountItem(
                        name = acc.accountName,
                        balanceLabel = "${currency.code} ${currency.format(acc.startingBalance)}",
                        icon = Icons.Rounded.AccountBalance,
                        isSelected = selectedAccountId == acc.id,
                        colors = c,
                        onClick = { onSelectAccount(acc.id) }
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    if (isAddingNew) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.bgCardAlt)
                                .border(1.dp, c.divider, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("New Account", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Account Name", color = c.textSub) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = c.textPrimary,
                                    unfocusedTextColor = c.textPrimary,
                                    focusedBorderColor = c.accentGreen,
                                    unfocusedBorderColor = c.divider
                                ),
                                keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
                            )
                            OutlinedTextField(
                                value = newBalance,
                                onValueChange = { newBalance = it },
                                label = { Text("Starting Balance", color = c.textSub) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = c.textPrimary,
                                    unfocusedTextColor = c.textPrimary,
                                    focusedBorderColor = c.accentGreen,
                                    unfocusedBorderColor = c.divider
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { isAddingNew = false }) {
                                    Text("Cancel", color = c.textSub)
                                }
                                Button(
                                    onClick = {
                                        if (newName.isNotBlank()) {
                                            val bal = newBalance.toDoubleOrNull() ?: 0.0
                                            onAddAccount(newName, bal)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accentGreen)
                                ) {
                                    Text("Save", color = c.bgDeep, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { isAddingNew = true }
                                .border(1.dp, c.divider, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add", tint = c.textSub, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add Account", color = c.textSub, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountItem(
    name: String,
    balanceLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    colors: DashColors,
    onClick: () -> Unit
) {
    val c = colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) c.accentGreen.copy(alpha = 0.1f) else c.bgCardAlt)
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isSelected) c.accentGreen.copy(alpha = 0.3f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) c.accentGreen.copy(alpha = 0.2f) else c.bgDeep),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null,
                tint = if (isSelected) c.accentGreen else c.textSub,
                modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) c.accentGreen else c.textPrimary
            )
            Text(
                text = balanceLabel,
                fontSize = 13.sp,
                color = if (isSelected) c.accentGreen.copy(alpha = 0.8f) else c.textSub
            )
        }
        if (isSelected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected",
                tint = c.accentGreen, modifier = Modifier.size(24.dp))
        }
    }
}
