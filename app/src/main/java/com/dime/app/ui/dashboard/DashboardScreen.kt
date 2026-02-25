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

// ── Palette (Dark, low-exposure aesthetic) ─────────────────────────────────────
private val BgDeep       = Color(0xFF0D0D0F)   // near-black canvas
private val BgCard       = Color(0xFF19191E)   // card surface
private val BgCardAlt    = Color(0xFF1F1F27)   // subtle variation
private val AccentPurple = Color(0xFF9B6FFF)   // primary accent
private val AccentBlue   = Color(0xFF5B8FFF)   // secondary accent
private val AccentGreen  = Color(0xFF3ECF72)   // income / positive
private val AccentRed    = Color(0xFFFF5C5C)   // expense / negative
private val TextPrimary  = Color(0xFFF0F0F5)
private val TextSub      = Color(0xFF7A7A8C)
private val Divider      = Color(0xFF2A2A35)


// ── Screen ─────────────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()

    Surface(color = BgDeep, modifier = Modifier.fillMaxSize()) {
        when (val s = uiState) {
            is DashboardUiState.Loading -> LoadingState()
            is DashboardUiState.Ready -> ReadyContent(
                state = s,
                period = period,
                onPeriodChange = viewModel::selectPeriod
            )
        }
    }
}

// ── Loading skeleton ────────────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero card skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .shimmerEffect()
        )
        // List items skeleton
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).shimmerEffect()
                )
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
    onPeriodChange: (TimePeriod) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp), // clear bottom nav bar
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header + Period Selector ──────────────────────────────────────
        item {
            DashboardHeader()
        }

        // ── Hero KPI card (net balance) ────────────────────────────────────────
        item {
            HeroKpiCard(
                net = state.net,
                spent = state.totalSpent,
                income = state.totalIncome,
                period = period,
                onPeriodClick = {
                    val entries = TimePeriod.entries
                    val nextIndex = (entries.indexOf(period) + 1) % entries.size
                    onPeriodChange(entries[nextIndex])
                }
            )
        }


        // ── Upcoming Section (Predictive) ──────────────────────────────────
        if (state.upcomingTransactions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                    Text(
                        text = "UPCOMING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSub,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = Divider)
                }
            }
            items(state.upcomingTransactions, key = { "up_" + it.template.id }) { item ->
                UpcomingRow(item)
            }
        }

        // ── Chronological Feed (Grouped by Date) ──────────────────────────────
        if (state.dailyTransactions.isNotEmpty()) {
            state.dailyTransactions.forEach { group ->
                item(key = "day_${group.date}") {
                    DayHeader(date = group.date, net = group.dailyNet)
                }
                items(group.transactions, key = { it.transaction.id }) { item ->
                    TransactionRow(
                        item = item,
                        onClick = { },
                        showDate = false // Date shown in header
                    )
                }
            }
        } else if (state.upcomingTransactions.isEmpty()) {
            item { EmptyState() }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(onSearchClick: () -> Unit = {}, onFilterClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp).bounceClick { onSearchClick() }
            )
        }

        IconButton(
            onClick = onFilterClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Filter",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp).bounceClick { onFilterClick() }
            )
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
    onPeriodClick: () -> Unit
) {
    val isPositive = net >= 0
    val netColor = if (isPositive) AccentGreen else AccentRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
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
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Net Total",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Surface(
                    onClick = onPeriodClick,
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, Divider),
                    color = Color.Transparent
                ) {
                    Text(
                        text = period.label.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSub,
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
                    text = (if (isPositive) "+" else "−") + " " + currency.code + " ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSub,
                    modifier = Modifier.padding(top = 4.dp) // Subtle offset for visual alignment
                )
                com.dime.app.ui.components.AnimatedAmountText(
                    amount = abs(net).toFloat(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-1.5).sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "+",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                    com.dime.app.ui.components.AnimatedAmountText(
                        amount = income.toFloat(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                }
                Text(
                    text = "  |  ",
                    fontSize = 13.sp,
                    color = Divider
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "−",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed
                    )
                    com.dime.app.ui.components.AnimatedAmountText(
                        amount = spent.toFloat(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed
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
    showDate: Boolean = true
) {
    val tx = item.transaction
    val cat = item.category

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Emoji avatar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgCard,
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
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tx.onceRecurring) {
                    Icon(
                        imageVector = Icons.Rounded.Autorenew,
                        contentDescription = "Recurring",
                        tint = AccentPurple,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatTime(tx.date),
                    fontSize = 12.sp,
                    color = TextSub
                )
                if (showDate) {
                    Text(
                        text = "·",
                        fontSize = 12.sp,
                        color = TextSub.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatDate(tx.date),
                        fontSize = 12.sp,
                        color = TextSub
                    )
                }
            }
        }

        val currency = LocalCurrency.current
        Text(
            text = (if (tx.income) "+" else "−") + currency.format(tx.amount),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (tx.income) AccentGreen else AccentRed
        )
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("✨", fontSize = 36.sp)
        Text("No logs yet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text("Tap the AI sparkle to log your first entry", fontSize = 14.sp, color = TextSub)
    }
}

// ── Typography helpers ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = TextSub,
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
private fun UpcomingRow(item: com.dime.app.data.local.relation.TemplateWithCategory) {
    val temp = item.template
    val cat = item.category

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Subtle indicator for "Upcoming"
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(cat?.colour?.let { Color(android.graphics.Color.parseColor(it)) }?.copy(alpha = 0.15f) ?: BgCard),
            contentAlignment = Alignment.Center
        ) {
            Text(cat?.emoji ?: "📑", fontSize = 18.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = temp.note.ifBlank { cat?.name ?: "Recurring" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary.copy(alpha = 0.7f)
            )
            Text(
                text = "Scheduled Subscription",
                fontSize = 12.sp,
                color = AccentPurple
            )
        }

        val currency = LocalCurrency.current
        Text(
            text = (if (temp.income) "+" else "−") + currency.format(temp.amount),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (temp.income) AccentGreen else AccentRed
        )
    }
}

@Composable
private fun DayHeader(date: Long, net: Double) {
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
            Text(
                text = dayLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSub,
                letterSpacing = 1.sp
            )
            Text(
                text = (if (net >= 0) "+" else "−") + currency.format(abs(net)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSub
            )
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Divider)
    }
}
