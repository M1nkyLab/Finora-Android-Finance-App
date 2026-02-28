package com.dime.app.ui.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dime.app.data.local.relation.TransactionWithCategory
import com.dime.app.data.repository.DimeRepository
import com.dime.app.domain.model.TimePeriod
import com.dime.app.domain.model.toDateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for the Log / Dashboard screen.
 *
 * Mirrors iOS DataController computed properties surfaced through
 * @FetchRequest / @Published variables.
 *
 * State is kept in [DashboardUiState] and exposed as a single StateFlow
 * so the Compose UI can collectAsStateWithLifecycle().
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DimeRepository
) : ViewModel() {

    // ── User-selectable time period (mirrors iOS LogView "type" Int) ───────────

    private val _period = MutableStateFlow(TimePeriod.MONTH)
    val period: StateFlow<TimePeriod> = _period.asStateFlow()

    fun selectPeriod(p: TimePeriod) { _period.value = p }

    // ── Derived UI state ───────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = _period
        .flatMapLatest { period ->
            combine(
                repository.transactions(period),
                repository.templates()
            ) { txList, templates ->
                buildUiState(period, txList, templates)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState.Loading
        )


    // ── Private helpers ────────────────────────────────────────────────────────

    private fun buildUiState(
        period: TimePeriod,
        transactions: List<TransactionWithCategory>,
        templates: List<com.dime.app.data.local.relation.TemplateWithCategory>
    ): DashboardUiState {
        if (transactions.isEmpty() && period != TimePeriod.ALL_TIME) {
            // Return ready state with zeros (not "Loading")
        }

        val spent = transactions.filter { !it.transaction.income }
            .sumOf { it.transaction.amount }
        val income = transactions.filter { it.transaction.income }
            .sumOf { it.transaction.amount }
        val net = income - spent


        // Recent transactions for the quick-summary list (latest 5)
        val recent = transactions.sortedByDescending { it.transaction.date }.take(5)

        // Chronological Feed: Group by normalized date
        val dailyGroups = transactions
            .groupBy { tx ->
                Instant.ofEpochMilli(tx.transaction.date)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
            .map { (date, txs) ->
                val dayNet = txs.sumOf { if (it.transaction.income) it.transaction.amount else -it.transaction.amount }
                DailyGroup(
                    date = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    transactions = txs.sortedByDescending { it.transaction.date },
                    dailyNet = dayNet
                )
            }
            .sortedByDescending { it.date }

        return DashboardUiState.Ready(
            period = period,
            totalSpent = spent,
            totalIncome = income,
            net = net,
            recentTransactions = recent,
            upcomingTransactions = templates.take(3), // Limit to top 3 for "Upcoming" preview
            dailyTransactions = dailyGroups
        )
    }
}

@Immutable
data class DailyGroup(
    val date: Long,
    val transactions: List<TransactionWithCategory>,
    val dailyNet: Double
)

// ── UI state sealed class ──────────────────────────────────────────────────────

sealed class DashboardUiState {
    object Loading : DashboardUiState()

    @Immutable
    data class Ready(
        val period: TimePeriod,
        val totalSpent: Double,
        val totalIncome: Double,
        val net: Double,
        val recentTransactions: List<TransactionWithCategory>,
        val upcomingTransactions: List<com.dime.app.data.local.relation.TemplateWithCategory> = emptyList(),
        val dailyTransactions: List<DailyGroup> = emptyList()
    ) : DashboardUiState()
}

