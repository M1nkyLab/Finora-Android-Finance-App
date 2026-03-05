package com.dime.app.ui.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dime.app.data.local.entity.AccountEntity
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
 * ViewModel for the Dashboard screen.
 *
 * Manages both the time period filter and the active account selection.
 * When selectedAccountId == null the dashboard shows the aggregate of ALL accounts.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DimeRepository
) : ViewModel() {

    // ── User-selectable time period ─────────────────────────────────────────────

    private val _period = MutableStateFlow(TimePeriod.MONTH)
    val period: StateFlow<TimePeriod> = _period.asStateFlow()

    fun selectPeriod(p: TimePeriod) { _period.value = p }

    // ── Account selection (null = "All Accounts") ───────────────────────────────

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()

    val accounts: StateFlow<List<AccountEntity>> = repository.accounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Switch to a specific account, or pass null to show "All Accounts". */
    fun selectAccount(id: String?) { _selectedAccountId.value = id }

    suspend fun addAccount(name: String, startingBalance: Double) {
        repository.saveAccount(
            AccountEntity(accountName = name, startingBalance = startingBalance)
        )
    }

    // ── Derived UI state ────────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> =
        combine(_period, _selectedAccountId) { period, accountId -> period to accountId }
            .flatMapLatest { (period, accountId) ->
                combine(
                    repository.transactions(period, accountId = accountId),
                    repository.templates(),
                    repository.accountNetBalance(accountId)
                ) { txList, templates, netBalance ->
                    buildUiState(period, txList, templates, netBalance)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DashboardUiState.Loading
            )

    // ── Private helpers ─────────────────────────────────────────────────────────

    private fun buildUiState(
        period: TimePeriod,
        transactions: List<TransactionWithCategory>,
        templates: List<com.dime.app.data.local.relation.TemplateWithCategory>,
        accountNetBalance: Double
    ): DashboardUiState {

        val spent  = transactions.filter { !it.transaction.income }.sumOf { it.transaction.amount }
        val income = transactions.filter {  it.transaction.income }.sumOf { it.transaction.amount }

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
                val dayNet = txs.sumOf {
                    if (it.transaction.income) it.transaction.amount else -it.transaction.amount
                }
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
            net = accountNetBalance,           // Uses starting_balance logic (Scenario A & B)
            recentTransactions = recent,
            upcomingTransactions = templates.take(3),
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

// ── UI state sealed class ───────────────────────────────────────────────────────

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
