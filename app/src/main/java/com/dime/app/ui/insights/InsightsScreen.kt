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

// ── Palette ────────────────────────────────────────────────────────────────────
private val BgDeep    = Color(0xFF0D0D0F)
private val BgCard    = Color(0xFF17171C)
private val BgChip    = Color(0xFF232329)
private val AccentPurple = Color(0xFF9B6FFF)
private val GreenInc  = Color(0xFF34C759)
private val RedExp    = Color(0xFFFF453A)
private val TextPrim  = Color(0xFFF0F0F5)
private val TextSub   = Color(0xFF7A7A8C)
private val TextHint  = Color(0xFF4A4A5A)
private val AccentGreen = Color(0xFF3ECF72)
private val AccentRed   = Color(0xFFFF4D4D)

// ── Root screen ───────────────────────────────────────────────────────────────

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isEmpty) {
        InsightsEmptyState()
        return
    }

    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .background(BgDeep),
        contentPadding      = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item { InsightsHeader(state.timeFrame.label, onCycleTimeFrame = viewModel::cycleTimeFrame) }
        item { PeriodSummaryCard(state, viewModel) }
        item { Spacer(Modifier.height(16.dp)) }
        item { QuickSummaryCards(state) }
        item { Spacer(Modifier.height(16.dp)) }
        item { BarChartCard(state, viewModel) }

        // ── Transaction History ──────────────────────────────────────────
        if (state.dailyTransactions.isNotEmpty()) {
            item {
                Text(
                    text = "TRANSACTION HISTORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSub,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 4.dp)
                )
            }
            state.dailyTransactions.forEach { group ->
                item(key = "ins_day_${group.date}") {
                    InsightsDayHeader(date = group.date, net = group.dailyNet)
                }
                items(group.transactions, key = { txItem -> "ins_tx_" + txItem.transaction.id }) { txItem ->
                    InsightsTransactionRow(item = txItem)
                }
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun InsightsHeader(
    timeFrameLabel: String,
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
            color      = TextPrim
        )
        // Time-frame chip — tap to cycle Week → Month → Year
        Surface(
            onClick        = onCycleTimeFrame,
            shape          = RoundedCornerShape(20.dp),
            color          = BgChip,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier            = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(timeFrameLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrim)
                Text("↕", fontSize = 11.sp, color = TextSub)
            }
        }
    }
}

// ── Period navigator ───────────────────────────────────────────────────────────

@Composable
private fun PeriodNavigator(
    state: InsightsUiState
) {
    val nf = remember { 
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    val currency = LocalCurrency.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = state.periodLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSub,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (state.timeFrame == InsightsTimeFrame.YEAR) "AVG / MTH" else "SPENT / DAY"),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextHint
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currency.code + " ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextHint
                )
                Text(
                    text = nf.format(state.current.avgPerDay),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPurple
                )
            }
        }
    }
}

@Composable
private fun QuickSummaryCards(state: InsightsUiState) {
    val s = state.current
    val currency = LocalCurrency.current
    val nf = remember { 
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
 

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Income Card
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("INCOME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSub)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currency.code + " ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSub)
                com.dime.app.ui.components.AnimatedAmountText(
                    amount = s.totalIncome.toFloat(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrim
                )
            }
        }

        // Expenses Card
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("EXPENSES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSub)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currency.code + " ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSub)
                com.dime.app.ui.components.AnimatedAmountText(
                    amount = s.totalSpent.toFloat(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrim
                )
            }
        }
    }
}

// ── Period summary card (income + expense blocks) ─────────────────────────────

@Composable
private fun PeriodSummaryCard(
    state: InsightsUiState,
    vm: InsightsViewModel
) {
    val s = state.current
    val currency = LocalCurrency.current
    val nf = remember { 
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
 

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Date + Net Balance
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = state.periodLabel.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSub,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "NET BALANCE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextHint
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (s.netPositive) "+" else "−") + currency.code + " ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextHint,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    com.dime.app.ui.components.AnimatedAmountText(
                        amount = abs(s.net).toFloat(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (s.netPositive) AccentGreen else AccentRed,
                        letterSpacing = (-1).sp
                    )
                }
            }

            // Right: Spent Label + Pace
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (state.timeFrame == InsightsTimeFrame.YEAR) "AVG/MTH" else "SPENT/DAY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSub,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currency.code + " ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextHint,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    com.dime.app.ui.components.AnimatedAmountText(
                        amount = s.avgPerDay.toFloat(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrim
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
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            color = TextSub,
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
                    Text(bar.label, fontSize = 13.sp, color = TextSub, fontWeight = FontWeight.Medium)
                    Text(formatAmount(bar.amount), fontSize = 13.sp, color = AccentPurple, fontWeight = FontWeight.Bold)
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
                    isSelected   -> AccentPurple
                    entry.isToday -> AccentPurple.copy(alpha = .55f)
                    else          -> BgChip
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
                    color    = TextSub,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // AVG/DAY footer removed as per request
    }
}

// ── Category breakdown card ────────────────────────────────────────────────────

@Composable
private fun CategoryBreakdownCard(
    state: InsightsUiState,
    isIncome: Boolean
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
            .background(BgCard)
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
                color      = TextSub
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
            CategoryRow(slice, accentColor, i)
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
    index: Int
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
            color      = TextPrim,
            modifier   = Modifier.weight(1f),
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )

        Text(
            text     = formatAmount(slice.amount),
            fontSize = 13.sp,
            color    = TextSub,
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
private fun InsightsEmptyState() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(BgDeep),
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
                color      = TextPrim
            )
            Text(
                "As transactions start piling up",
                fontSize = 14.sp,
                color    = TextSub
            )
        }
    }
}


// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun formatAmount(amount: Double): String {
    return LocalCurrency.current.format(amount)
}

/** Generate a visually spread palette anchored to the accent colour. */
private fun generatePalette(count: Int, accent: Color): List<Color> {
    val hue = android.graphics.Color.RGBToHSV(
        (accent.red * 255).toInt(),
        (accent.green * 255).toInt(),
        (accent.blue * 255).toInt(),
        FloatArray(3)
    ).let { 0f }.let {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (accent.red * 255).toInt(),
            (accent.green * 255).toInt(),
            (accent.blue * 255).toInt(),
            hsv
        )
        hsv[0]
    }
    return (0 until count).map { i ->
        val h = (hue + i * (300f / count.coerceAtLeast(1))) % 360f
        val hsv = floatArrayOf(h, 0.7f, 0.85f)
        val argb = android.graphics.Color.HSVToColor(hsv)
        Color(argb)
    }
}

// ── Transaction History Composables ────────────────────────────────────────────

private val insDateFmt = DateTimeFormatter.ofPattern("d MMM")
private val insTimeFmt = DateTimeFormatter.ofPattern("h:mm a")

private fun insFormatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(insTimeFmt)

@Composable
private fun InsightsDayHeader(date: Long, net: Double) {
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
                text = "NET " + (if (net >= 0) "+" else "\u2212") + currency.format(abs(net)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSub
            )
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 0.5.dp, color = TextHint.copy(alpha = 0.3f))
    }
}

@Composable
private fun InsightsTransactionRow(
    item: TransactionWithCategory
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
                color = TextPrim,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (tx.onceRecurring) {
                Icon(
                    imageVector = Icons.Rounded.Autorenew,
                    contentDescription = "Recurring",
                    tint = AccentPurple,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Text(
            text = (if (tx.income) "+" else "\u2212") + currency.format(tx.amount),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextSub
        )
    }
}
