package com.dime.app.ui.insights

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import com.dime.app.util.LocalCurrency
import com.dime.app.data.local.relation.TransactionWithCategory
import com.dime.app.ui.components.bounceClick
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Semantic colors (same in both themes) ───────────────────────────────────────────
private val GreenInc     = Color(0xFF34D399)
private val RedExp       = Color(0xFFFF5C5C)
private val AccentPurple = Color(0xFF9B6FFF)

// ── Theme-adaptive palette ─────────────────────────────────────────────────────────
private data class InsightsColors(
    val bgDeep   : Color,
    val bgCard   : Color,
    val bgChip   : Color,
    val textPrim : Color,
    val textSub  : Color,
    val textHint : Color
)

@Composable
private fun insightsColors(): InsightsColors {
    val cs = MaterialTheme.colorScheme
    return InsightsColors(
        bgDeep   = cs.background,
        bgCard   = cs.surface,
        bgChip   = cs.surfaceVariant,
        textPrim = cs.onBackground,
        textSub  = cs.secondary,
        textHint = cs.onSurfaceVariant
    )
}

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val ic    = insightsColors()

    if (state.isEmpty) {
        InsightsEmptyState(ic)
        return
    }

    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .background(ic.bgDeep),
        contentPadding      = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item { InsightsHeader(state.timeFrame.label, ic, onCycleTimeFrame = viewModel::cycleTimeFrame) }
        item { PeriodSummaryCard(state, ic, viewModel) }
        item { Spacer(Modifier.height(16.dp)) }
        item { QuickSummaryCards(state, ic) }
        item { Spacer(Modifier.height(16.dp)) }
        item { BarChartCard(state, ic, viewModel) }

        // ── Category Breakdown ───────────────────────────────────────────
        item { Spacer(Modifier.height(16.dp)) }
        item { CategoryBreakdownCard(state, isIncome = false, ic = ic) }
        item { Spacer(Modifier.height(12.dp)) }
        item { CategoryBreakdownCard(state, isIncome = true, ic = ic) }

        // ── Transaction History ──────────────────────────────────────────

        if (state.dailyTransactions.isNotEmpty()) {
            item {
                Text(
                    text = "TRANSACTION HISTORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ic.textSub,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 4.dp)
                )
            }
            state.dailyTransactions.forEach { group ->
                item(key = "ins_day_${group.date}") {
                    InsightsDayHeader(date = group.date, net = group.dailyNet, ic = ic)
                }
                items(group.transactions, key = { txItem -> "ins_tx_" + txItem.transaction.id }) { txItem ->
                    InsightsTransactionRow(item = txItem, ic = ic)
                }
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun InsightsHeader(
    timeFrameLabel: String,
    ic: InsightsColors,
    onCycleTimeFrame: () -> Unit
) {
    Row(
        modifier        = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text       = "Insights",
            fontSize   = 26.sp,
            fontWeight = FontWeight.Bold,
            color      = ic.textPrim
        )
        // Time-frame chip — tap to cycle Week → Month → Year
        Surface(
            onClick        = onCycleTimeFrame,
            shape          = RoundedCornerShape(20.dp),
            color          = ic.bgChip,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier            = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(timeFrameLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ic.textPrim)
                Text("↕", fontSize = 11.sp, color = ic.textSub)
            }
        }
    }
}

// ── Period navigator ───────────────────────────────────────────────────────────

@Composable
private fun PeriodNavigator(
    state: InsightsUiState,
    ic: InsightsColors
) {
    val currency = LocalCurrency.current
    val nf = remember(currency.showCents) { 
        NumberFormat.getNumberInstance().apply {
            val decimals = if (currency.showCents) 2 else 0
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
    }
    val netBalance = state.current.totalIncome - state.current.totalSpent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "NET BALANCE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ic.textHint
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currency.code + " ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ic.textSub
                )
                Text(
                    text = nf.format(netBalance as Any),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ic.textPrim
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (state.timeFrame == InsightsTimeFrame.YEAR) "AVG / MTH" else "SPENT / DAY"),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ic.textHint
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currency.code + " ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ic.textSub
                )
                Text(
                    text = nf.format(state.current.avgPerDay as Any),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ic.textPrim
                )
            }
        }
    }
}

@Composable
private fun QuickSummaryCards(state: InsightsUiState, ic: InsightsColors) {
    val s = state.current
    val currency = LocalCurrency.current
    val nf = remember(currency.showCents) { 
        NumberFormat.getNumberInstance().apply {
            val decimals = if (currency.showCents) 2 else 0
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
    }
 

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Income Card — green tint + border
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(GreenInc.copy(alpha = 0.07f))
                .border(1.dp, GreenInc.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("INCOME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenInc.copy(alpha = 0.8f), letterSpacing = 0.5.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currency.code + " ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenInc.copy(alpha = 0.6f))
                com.dime.app.ui.components.AnimatedAmountText(
                    amount = s.totalIncome.toFloat(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GreenInc
                )
            }
        }

        // Expenses Card — red tint + border
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(RedExp.copy(alpha = 0.07f))
                .border(1.dp, RedExp.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("EXPENSES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedExp.copy(alpha = 0.8f), letterSpacing = 0.5.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currency.code + " ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedExp.copy(alpha = 0.6f))
                com.dime.app.ui.components.AnimatedAmountText(
                    amount = s.totalSpent.toFloat(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedExp
                )
            }
        }
    }
}

// ── Period summary card (income + expense blocks) ─────────────────────────────

@Composable
private fun PeriodSummaryCard(
    state: InsightsUiState,
    ic: InsightsColors,
    vm: InsightsViewModel
) {
    val s = state.current
    val currency = LocalCurrency.current
    val nf = remember(currency.showCents) { 
        NumberFormat.getNumberInstance().apply {
            val decimals = if (currency.showCents) 2 else 0
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
    }
 

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        // Date on top
        Text(
            text = state.periodLabel.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = ic.textSub,
            letterSpacing = 0.3.sp
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Net Balance
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "NET BALANCE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ic.textHint,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = (if (s.netPositive) "+" else "−") + currency.code,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ic.textHint,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    com.dime.app.ui.components.AnimatedAmountText(
                        amount = abs(s.net).toFloat(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (s.netPositive) GreenInc else RedExp,
                        letterSpacing = (-1).sp
                    )
                }
            }

            // Right: Spent/Day
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (state.timeFrame == InsightsTimeFrame.YEAR) "AVG/MTH" else "SPENT/DAY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ic.textHint,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = currency.code,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ic.textHint,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    com.dime.app.ui.components.AnimatedAmountText(
                        amount = s.avgPerDay.toFloat(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ic.textPrim,
                        letterSpacing = (-1).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryBlock(
    label: String,
    amount: Double,
    color: Color,
    selected: Boolean, // Kept parameter to avoid breaking references, though styling is now flat
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    ic: InsightsColors
) {
    Column(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            color = ic.textSub,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatAmount(amount),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ── Bar chart card ─────────────────────────────────────────────────────────────

@Composable
private fun BarChartCard(
    state: InsightsUiState,
    ic: InsightsColors,
    vm: InsightsViewModel
) {
    if (state.bars.isEmpty()) return

    val maxAmount = state.bars.maxOf { it.amount }.takeIf { it > 0 } ?: 1.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(vertical = 8.dp)
    ) {
        // Selected bar info
        AnimatedVisibility(state.selectedBarIndex != null) {
            val idx = state.selectedBarIndex ?: 0
            val bar = state.bars.getOrNull(idx)
            if (bar != null) {
                Row(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(bar.label, fontSize = 13.sp, color = ic.textSub, fontWeight = FontWeight.Medium)
                    Text(formatAmount(bar.amount), fontSize = 13.sp, color = ic.textPrim, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bars
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .height(140.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.bars.forEachIndexed { idx, entry ->
                val fraction   by animateFloatAsState(
                    targetValue   = (entry.amount / maxAmount).toFloat().coerceIn(0f, 1f),
                    animationSpec = tween(500, easing = EaseOut),
                    label         = "barFraction"
                )
                val isSelected = idx == state.selectedBarIndex
                val barColor   = when {
                    isSelected    -> AccentPurple
                    entry.isToday -> ic.textSub.copy(alpha = 0.6f)
                    else          -> ic.bgChip
                }

                Column(
                    modifier              = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { vm.selectBar(idx) },
                    verticalArrangement   = Arrangement.Bottom,
                    horizontalAlignment   = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .fillMaxHeight()
                            .drawWithContent {
                                val currentFrac = fraction.coerceAtLeast(0.04f)
                                val rectHeight = size.height * currentFrac
                                val cornerPx = 5.dp.toPx()
                                val path = Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            left = 0f,
                                            top = size.height - rectHeight,
                                            right = size.width,
                                            bottom = size.height,
                                            topLeftCornerRadius = CornerRadius(cornerPx),
                                            topRightCornerRadius = CornerRadius(cornerPx),
                                            bottomLeftCornerRadius = CornerRadius(0f),
                                            bottomRightCornerRadius = CornerRadius(0f)
                                        )
                                    )
                                }
                                drawPath(path = path, color = barColor)
                            }
                    )
                }
            }
        }

        // Labels
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.bars.forEach { entry ->
                Text(
                    text     = entry.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    color    = ic.textSub,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ── Category breakdown card ────────────────────────────────────────────────────

@Composable
private fun CategoryBreakdownCard(
    state: InsightsUiState,
    isIncome: Boolean,
    ic: InsightsColors
) {
    val slices = if (isIncome) state.incomeSlices else state.expenseSlices
    if (slices.isEmpty()) return

    val accentColor = if (isIncome) GreenInc else RedExp
    val total       = slices.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ic.bgCard)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Header
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Text(
                text       = if (isIncome) "Income" else "Expenses",
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = ic.textSub
            )
            Text(
                text       = formatAmount(total),
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = accentColor
            )
        }

        Spacer(Modifier.height(12.dp))

        // Horizontal segmented bar
        HorizontalSegmentedBar(slices, accentColor)

        Spacer(Modifier.height(16.dp))

        // Category rows
        slices.forEachIndexed { i, slice ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            CategoryRow(slice, accentColor, i, ic)
        }
    }
}

@Composable
private fun HorizontalSegmentedBar(
    slices: List<CategorySlice>,
    accent: Color
) {
    val palette = remember(slices.size) { generatePalette(slices.size, accent) }

    val animatedFractions = slices.map { slice ->
        animateFloatAsState(
            targetValue   = slice.percent.toFloat(),
            animationSpec = tween(600, easing = EaseOut),
            label         = "segFrac"
        )
    }

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        var currentX = 0f
        slices.forEachIndexed { i, _ ->
            val animFrac = animatedFractions[i].value
            val sliceWidth = size.width * animFrac
            if (sliceWidth > 0.005f * size.width) {
                drawRect(
                    color = palette.getOrElse(i) { accent },
                    topLeft = androidx.compose.ui.geometry.Offset(currentX, 0f),
                    size = androidx.compose.ui.geometry.Size(sliceWidth, size.height)
                )
                currentX += sliceWidth
            }
        }
    }
}

@Composable
private fun CategoryRow(
    slice: CategorySlice,
    accent: Color,
    index: Int,
    ic: InsightsColors
) {
    val palette = remember(1) { generatePalette(10, accent) }
    val color   = palette.getOrElse(index) { accent }
    val pct     = (slice.percent * 100).roundToInt()

    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Colour dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )

        Text(
            text       = "${slice.category.emoji} ${slice.category.name}",
            fontSize   = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color      = ic.textPrim,
            modifier   = Modifier.weight(1f),
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )

        Text(
            text     = formatAmount(slice.amount),
            fontSize = 13.sp,
            color    = ic.textSub,
            fontWeight = FontWeight.Medium
        )

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = .18f)
        ) {
            Text(
                text     = "$pct%",
                fontSize = 11.sp,
                color    = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun InsightsEmptyState(ic: InsightsColors) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(ic.bgDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("📊", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Analyse Your Expenditure",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = ic.textPrim
            )
            Text(
                "As transactions start piling up",
                fontSize = 14.sp,
                color    = ic.textSub
            )
        }
    }
}


// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun formatAmount(amount: Double): String {
    return LocalCurrency.current.format(amount)
}

/** Shades of accent only (B&W: no extra hues). */
private fun generatePalette(count: Int, accent: Color): List<Color> {
    return (0 until count).map { i ->
        val alpha = 0.4f + (0.6f * (i + 1) / count.coerceAtLeast(1))
        accent.copy(alpha = alpha)
    }
}

// ── Transaction History Composables ────────────────────────────────────────────

private val insDateFmt = DateTimeFormatter.ofPattern("d MMM")
private val insTimeFmt = DateTimeFormatter.ofPattern("h:mm a")

private fun insFormatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(insTimeFmt)

@Composable
private fun InsightsDayHeader(date: Long, net: Double, ic: InsightsColors) {
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
                color = ic.textSub,
                letterSpacing = 1.sp
            )
            Text(
                text = "NET " + (if (net >= 0) "+" else "\u2212") + currency.format(abs(net)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ic.textSub
            )
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 0.5.dp, color = ic.textHint.copy(alpha = 0.3f))
    }
}

@Composable
private fun InsightsTransactionRow(
    item: TransactionWithCategory,
    ic: InsightsColors
) {
    val tx = item.transaction
    val cat = item.category
    val currency = LocalCurrency.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Colour dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (tx.income) GreenInc else RedExp)
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = tx.note.ifBlank { cat?.name ?: "Transaction" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ic.textPrim,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (tx.onceRecurring) {
                Icon(
                    imageVector = Icons.Rounded.Autorenew,
                    contentDescription = "Recurring",
                    tint = ic.textSub,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Text(
            text = (if (tx.income) "+" else "\u2212") + currency.format(tx.amount),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (tx.income) GreenInc else RedExp
        )
    }
}
