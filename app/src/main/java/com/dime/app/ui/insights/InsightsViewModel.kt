package com.dime.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dime.app.data.local.entity.CategoryEntity
import com.dime.app.data.local.entity.TransactionEntity
import com.dime.app.data.local.relation.TransactionWithCategory
import com.dime.app.data.repository.DimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil

import androidx.compose.runtime.Immutable

// ── Time-frame type (mirrors iOS chartType 1/2/3) ──────────────────────────────
enum class InsightsTimeFrame(val label: String) {
    WEEK("week"), MONTH("month"), YEAR("year");

    fun next(): InsightsTimeFrame = when (this) {
        WEEK  -> MONTH
        MONTH -> YEAR
        YEAR  -> WEEK
    }
}

// ── Bar entry for the spending chart ──────────────────────────────────────────
@Immutable
data class BarEntry(
    val label: String,          // "Mon", "15", "Jan" …
    val amount: Double,
    val isToday: Boolean = false
)

// ── Category slice for the horizontal breakdown ───────────────────────────────
@Immutable
data class CategorySlice(
    val category: CategoryEntity,
    val amount: Double,
    val percent: Double          // 0.0 – 1.0
)

// ── A self-contained summary for a period ────────────────────────────────────
@Immutable
data class PeriodSummary(
    val totalSpent: Double  = 0.0,
    val totalIncome: Double = 0.0,
    val net: Double         = 0.0,
    val netPositive: Boolean = true,
    val avgPerDay: Double   = 0.0,
    val periodLabel: String = ""
)

// ── Main UI state ─────────────────────────────────────────────────────────────
data class InsightsUiState(
    val timeFrame: InsightsTimeFrame        = InsightsTimeFrame.WEEK,
    val periodLabel: String                 = "",
    val current: PeriodSummary              = PeriodSummary(),
    val previous: PeriodSummary             = PeriodSummary(),
    val bars: List<BarEntry>                = emptyList(),
    val expenseSlices: List<CategorySlice>  = emptyList(),
    val incomeSlices: List<CategorySlice>   = emptyList(),
    val selectedBarIndex: Int?              = null,
    val selectedBarDate: Long?              = null,
    val selectedBarAmount: Double           = 0.0,
    val showIncomeBar: Boolean              = false,     // toggled by income/expense summary taps
    val isIncomeFocus: Boolean              = false,
    val isEmpty: Boolean                    = true,
    val canGoForward: Boolean               = false,    // false when showing current period
    val dailyTransactions: List<DailyInsightsGroup> = emptyList()
)

@Immutable
data class DailyInsightsGroup(
    val date: Long,
    val transactions: List<TransactionWithCategory>,
    val dailyNet: Double
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repo: DimeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    // Reference date = start of currently-displayed period
    private var periodStart: Long = startOfCurrentPeriod(InsightsTimeFrame.WEEK)

    // Kept so cycleTimeFrame() can cancel the old collector before starting a new one
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun cycleTimeFrame() {
        val next = _uiState.value.timeFrame.next()
        periodStart = startOfCurrentPeriod(next)
        _uiState.update { it.copy(timeFrame = next, selectedBarIndex = null, selectedBarDate = null) }
        refresh()
    }


    fun selectBar(index: Int) {
        val bars = _uiState.value.bars
        if (index < 0 || index >= bars.size) return

        val current = _uiState.value
        if (current.selectedBarIndex == index) {
            // Deselect
            _uiState.update { it.copy(selectedBarIndex = null, selectedBarDate = null, selectedBarAmount = 0.0) }
        } else {
            _uiState.update {
                it.copy(
                    selectedBarIndex  = index,
                    selectedBarAmount = bars[index].amount
                )
            }
        }
    }


    // ── Data loading ───────────────────────────────────────────────────────────

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            repo.allTransactions()
                .collectLatest { allWithCat ->
                    val rawAll = allWithCat.map { it.transaction }
                    computeState(rawAll, allWithCat)
                }
        }
    }

    private fun computeState(all: List<TransactionEntity>, allWithCat: List<TransactionWithCategory>) {
        val tf      = _uiState.value.timeFrame
        val income  = _uiState.value.showIncomeBar
        val start   = periodStart
        val end     = endOfPeriod(start, tf)
        val now     = System.currentTimeMillis()

        // Heavy computation offloaded to Default dispatcher
        viewModelScope.launch {
            val newState = withContext(Dispatchers.Default) {
                val inPeriod = all.filter { it.date in start until minOf(end, now) }
                val expenses = inPeriod.filter { !it.income }
                val incomes  = inPeriod.filter { it.income }

                val totalSpent  = expenses.sumOf { it.amount }
                val totalIncome = incomes.sumOf { it.amount }
                val net         = totalIncome - totalSpent

                // Previous period for delta
                val prevStart = shiftPeriod(start, tf, forward = false)
                val prevEnd   = endOfPeriod(prevStart, tf)
                val prevInPeriod = all.filter { it.date in prevStart until minOf(prevEnd, now) }
                val prevSpent    = prevInPeriod.filter { !it.income }.sumOf { it.amount }

                val daysInPeriod = when (tf) { InsightsTimeFrame.WEEK -> 7; InsightsTimeFrame.MONTH -> 30; InsightsTimeFrame.YEAR -> 365 }
                
                val elapsedDays = if (end > now && start <= now) {
                    val diffMs = now - start
                    val diffDays = (diffMs / 86_400_000L).toInt() + 1
                    diffDays.coerceIn(1, daysInPeriod)
                } else {
                    daysInPeriod
                }
                
                val avgPerDay = totalSpent / elapsedDays.toDouble()

                val bars = buildBars(all, start, end, tf, income)
                val (expSlices, incSlices) = buildSlices(all, allWithCat, start, end)

                // Build daily transaction groups for the history section (excluding subscriptions)
                val dailyGroups = buildDailyGroups(allWithCat, start, end)

                InsightsUiState(
                    timeFrame     = tf,
                    periodLabel   = buildPeriodLabel(start, tf),
                    current       = PeriodSummary(totalSpent, totalIncome, net, net >= 0, avgPerDay),
                    previous      = PeriodSummary(prevSpent),
                    bars          = bars,
                    expenseSlices = expSlices,
                    incomeSlices  = incSlices,
                    canGoForward  = start < startOfCurrentPeriod(tf),
                    isEmpty       = all.isEmpty(),
                    dailyTransactions = dailyGroups,
                    selectedBarIndex = _uiState.value.selectedBarIndex,
                    selectedBarDate = _uiState.value.selectedBarDate,
                    selectedBarAmount = _uiState.value.selectedBarAmount,
                    showIncomeBar = _uiState.value.showIncomeBar,
                    isIncomeFocus = _uiState.value.isIncomeFocus
                )
            }
            _uiState.value = newState
        }
    }

    private fun buildDailyGroups(
        allWithCat: List<TransactionWithCategory>,
        start: Long, end: Long
    ): List<DailyInsightsGroup> {
        val now = System.currentTimeMillis()
        val inPeriod = allWithCat.filter {
            it.transaction.date in start until minOf(end, now) && !it.transaction.onceRecurring
        }

        val cal = java.util.Calendar.getInstance()
        return inPeriod
            .groupBy { txWithCat ->
                cal.timeInMillis = txWithCat.transaction.date
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            .map { (dayMs, txs) ->
                val dayNet = txs.sumOf { if (it.transaction.income) it.transaction.amount else -it.transaction.amount }
                DailyInsightsGroup(
                    date = dayMs,
                    transactions = txs.sortedByDescending { it.transaction.date },
                    dailyNet = dayNet
                )
            }
            .sortedByDescending { it.date }
    }

    // ── Bar chart builder ──────────────────────────────────────────────────────

    private fun buildBars(
        all: List<TransactionEntity>,
        start: Long, end: Long,
        tf: InsightsTimeFrame,
        incomeMode: Boolean
    ): List<BarEntry> {
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()

        return when (tf) {
            InsightsTimeFrame.WEEK -> {
                (0..6).map { dayOffset ->
                    cal.timeInMillis = start
                    cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                    val dayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    val dayEnd   = dayStart + 86_400_000L
                    val label    = SimpleDayLabel(dayStart)
                    val amount   = all.filter { it.date in dayStart until minOf(dayEnd, now) && it.income == incomeMode }.sumOf { it.amount }
                    BarEntry(label, amount, isToday = isSameDay(dayStart, System.currentTimeMillis()))
                }
            }
            InsightsTimeFrame.MONTH -> {
                // Group by week within the month (up to 5 bars)
                val weeks = mutableListOf<BarEntry>()
                var cursor = start
                var weekNum = 1
                while (cursor < end) {
                    val wStart = cursor
                    val wEnd   = minOf(cursor + 7 * 86_400_000L, end)
                    val amount = all.filter { it.date in wStart until minOf(wEnd, now) && it.income == incomeMode }.sumOf { it.amount }
                    cal.timeInMillis = wStart
                    val label = "W$weekNum"
                    weeks += BarEntry(label, amount, isToday = wStart <= now && now < wEnd)
                    cursor = wEnd
                    weekNum++
                }
                weeks
            }
            InsightsTimeFrame.YEAR -> {
                (0..11).map { monthOffset ->
                    cal.timeInMillis = start
                    cal.add(Calendar.MONTH, monthOffset)
                    val mStart = cal.apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    val mEnd   = cal.timeInMillis
                    val label  = SimpleMonthLabel(mStart)
                    val amount = all.filter { it.date in mStart until minOf(mEnd, now) && it.income == incomeMode }.sumOf { it.amount }
                    BarEntry(label, amount, isToday = isSameMonth(mStart, System.currentTimeMillis()))
                }
            }
        }
    }

    // ── Category slices ────────────────────────────────────────────────────────

    private fun buildSlices(
        all: List<TransactionEntity>,
        allWithCat: List<TransactionWithCategory>,
        start: Long, end: Long
    ): Pair<List<CategorySlice>, List<CategorySlice>> {
        val now      = System.currentTimeMillis()
        val inPeriod = allWithCat.filter { it.transaction.date in start until minOf(end, now) }

        fun slices(income: Boolean): List<CategorySlice> {
            val subset = inPeriod.filter { it.transaction.income == income }
            val total  = subset.sumOf { it.transaction.amount }.takeIf { it > 0 } ?: 1.0
            return subset
                .groupBy { it.transaction.categoryId }
                .map { (_, txns) ->
                    val amt      = txns.sumOf { it.transaction.amount }
                    // Use the real joined CategoryEntity; fall back to a placeholder if null
                    val catEntity = txns.firstOrNull()?.category
                        ?: CategoryEntity(id = "?", name = "Uncategorised", income = income)
                    CategorySlice(
                        category = catEntity,
                        amount   = amt,
                        percent  = amt / total
                    )
                }
                .sortedByDescending { it.percent }
        }

        return slices(false) to slices(true)
    }

    // ── Period helpers ─────────────────────────────────────────────────────────

    private fun startOfCurrentPeriod(tf: InsightsTimeFrame): Long {
        val cal = Calendar.getInstance()
        return when (tf) {
            InsightsTimeFrame.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            }
            InsightsTimeFrame.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            }
            InsightsTimeFrame.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            }
        }
    }

    private fun endOfPeriod(start: Long, tf: InsightsTimeFrame): Long {
        val cal = Calendar.getInstance().also { it.timeInMillis = start }
        return when (tf) {
            InsightsTimeFrame.WEEK  -> { cal.add(Calendar.DAY_OF_YEAR, 7);  cal.timeInMillis }
            InsightsTimeFrame.MONTH -> { cal.add(Calendar.MONTH, 1);         cal.timeInMillis }
            InsightsTimeFrame.YEAR  -> { cal.add(Calendar.YEAR, 1);          cal.timeInMillis }
        }
    }

    private fun shiftPeriod(start: Long, tf: InsightsTimeFrame, forward: Boolean): Long {
        val cal   = Calendar.getInstance().also { it.timeInMillis = start }
        val delta = if (forward) 1 else -1
        when (tf) {
            InsightsTimeFrame.WEEK  -> cal.add(Calendar.DAY_OF_YEAR, delta * 7)
            InsightsTimeFrame.MONTH -> cal.add(Calendar.MONTH, delta)
            InsightsTimeFrame.YEAR  -> cal.add(Calendar.YEAR, delta)
        }
        return cal.timeInMillis
    }

    private fun buildPeriodLabel(start: Long, tf: InsightsTimeFrame): String {
        val cal = Calendar.getInstance().also { it.timeInMillis = start }
        return when (tf) {
            InsightsTimeFrame.WEEK -> {
                val endCal = Calendar.getInstance().also { it.timeInMillis = start + 6 * 86_400_000L }
                val sdf1   = java.text.SimpleDateFormat("d MMM", Locale.getDefault())
                val sdf2   = java.text.SimpleDateFormat("d MMM", Locale.getDefault())
                "${sdf1.format(cal.time)} – ${sdf2.format(endCal.time)}"
            }
            InsightsTimeFrame.MONTH -> java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
            InsightsTimeFrame.YEAR  -> java.text.SimpleDateFormat("yyyy", Locale.getDefault()).format(cal.time)
        }
    }

    private fun SimpleDayLabel(epochMs: Long): String {
        val cal = Calendar.getInstance().also { it.timeInMillis = epochMs }
        return java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time).take(1).uppercase()
    }

    private fun SimpleMonthLabel(epochMs: Long): String {
        val cal = Calendar.getInstance().also { it.timeInMillis = epochMs }
        return java.text.SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time).take(3)
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().also { it.timeInMillis = a }
        val cb = Calendar.getInstance().also { it.timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
               ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameMonth(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().also { it.timeInMillis = a }
        val cb = Calendar.getInstance().also { it.timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
               ca.get(Calendar.MONTH) == cb.get(Calendar.MONTH)
    }
}
