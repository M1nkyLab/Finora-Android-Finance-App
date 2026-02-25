package com.dime.app.data.repository

import com.dime.app.data.local.entity.*
import com.dime.app.data.local.relation.*
import com.dime.app.domain.model.TimePeriod
import com.dime.app.domain.model.toDateRange
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository backed by Supabase (PostgREST).
 *
 * Strategy: local MutableStateFlows act as an in-memory cache.
 * On init the cache is loaded from Supabase. Every mutation
 * writes to Supabase AND updates the local cache so the UI
 * reacts instantly without waiting for a network round-trip.
 */
@Singleton
class DimeRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── In-memory cache ────────────────────────────────────────────────────────
    private val _categories   = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private val _budgets      = MutableStateFlow<List<BudgetEntity>>(emptyList())
    private val _mainBudget   = MutableStateFlow<MainBudgetEntity?>(null)
    private val _templates    = MutableStateFlow<List<TemplateTransactionEntity>>(emptyList())

    init {
        android.util.Log.d("DimeRepository", "Init repo and refreshAll")
        scope.launch { refreshAll() }
    }

    // ── Refresh (load from Supabase) ───────────────────────────────────────────

    private suspend fun refreshAll() {
        refreshCategories()
        refreshTransactions()
        refreshBudgets()
        refreshMainBudget()
        refreshTemplates()
    }

    private suspend fun refreshCategories() {
        runCatching {
            _categories.value = supabase.from("categories")
                .select()
                .decodeList<CategoryEntity>()
        }.onFailure {
            android.util.Log.e("DimeRepository", "Error fetching categories", it)
        }
    }

    private suspend fun refreshTransactions() {
        runCatching {
            _transactions.value = supabase.from("transactions")
                .select()
                .decodeList<TransactionEntity>()
        }.onFailure { android.util.Log.e("DimeRepository", "Error fetching transactions", it) }
    }

    private suspend fun refreshBudgets() {
        runCatching {
            _budgets.value = supabase.from("budgets")
                .select()
                .decodeList<BudgetEntity>()
        }.onFailure { android.util.Log.e("DimeRepository", "Error fetching budgets", it) }
    }

    private suspend fun refreshMainBudget() {
        runCatching {
            _mainBudget.value = supabase.from("main_budget")
                .select()
                .decodeList<MainBudgetEntity>()
                .firstOrNull()
        }
    }

    private suspend fun refreshTemplates() {
        runCatching {
            _templates.value = supabase.from("templates")
                .select()
                .decodeList<TemplateTransactionEntity>()
        }
    }

    // ── Categories ─────────────────────────────────────────────────────────────

    fun allCategories(): Flow<List<CategoryEntity>> = _categories

    fun categories(income: Boolean): Flow<List<CategoryEntity>> =
        _categories.map { list -> list.filter { it.income == income }.sortedBy { it.order } }

    suspend fun getCategoryById(id: String): CategoryEntity? =
        _categories.value.find { it.id == id }

    suspend fun saveCategory(category: CategoryEntity) {
        supabase.from("categories").insert(category)
        _categories.value = _categories.value + category
    }

    suspend fun updateCategory(category: CategoryEntity) {
        supabase.from("categories").update(category) { filter { eq("id", category.id) } }
        _categories.value = _categories.value.map { if (it.id == category.id) category else it }
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        supabase.from("categories").delete { filter { eq("id", category.id) } }
        _categories.value = _categories.value.filter { it.id != category.id }
    }

    suspend fun reorderCategories(categories: List<CategoryEntity>) {
        val reordered = categories.mapIndexed { i, c -> c.copy(order = i.toLong()) }
        reordered.forEach { cat ->
            runCatching { supabase.from("categories").update(cat) { filter { eq("id", cat.id) } } }
        }
        val ids = reordered.map { it.id }.toSet()
        _categories.value = _categories.value.filter { it.id !in ids } + reordered
    }

    // ── Transactions ───────────────────────────────────────────────────────────

    fun transactions(period: TimePeriod, income: Boolean? = null): Flow<List<TransactionWithCategory>> {
        val (start, end) = period.toDateRange()
        return _transactions.map { txList ->
            txList
                .filter { it.date in start until end }
                .let { filtered -> if (income != null) filtered.filter { it.income == income } else filtered }
                .map { tx -> TransactionWithCategory(tx, findCategory(tx.categoryId)) }
                .sortedByDescending { it.transaction.date }
        }
    }

    fun allTransactions(income: Boolean? = null): Flow<List<TransactionWithCategory>> =
        _transactions.map { txList ->
            val filtered = if (income != null) txList.filter { it.income == income } else txList
            filtered
                .map { tx -> TransactionWithCategory(tx, findCategory(tx.categoryId)) }
                .sortedByDescending { it.transaction.date }
        }

    fun getAllTransactionEntities(): Flow<List<TransactionEntity>> = _transactions

    suspend fun saveTransaction(transaction: TransactionEntity) {
        supabase.from("transactions").insert(transaction)
        _transactions.value = _transactions.value + transaction
    }

    suspend fun saveTransactions(transactions: List<TransactionEntity>) {
        supabase.from("transactions").insert(transactions)
        _transactions.value = _transactions.value + transactions
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        supabase.from("transactions").update(transaction) { filter { eq("id", transaction.id) } }
        _transactions.value = _transactions.value.map { if (it.id == transaction.id) transaction else it }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        supabase.from("transactions").delete { filter { eq("id", transaction.id) } }
        _transactions.value = _transactions.value.filter { it.id != transaction.id }
    }

    suspend fun deleteAllTransactions() {
        supabase.from("transactions").delete { filter { neq("id", "") } }
        _transactions.value = emptyList()
    }

    // ── Aggregate queries ──────────────────────────────────────────────────────

    suspend fun totalSpent(period: TimePeriod): Double {
        val (start, end) = period.toDateRange()
        return _transactions.value
            .filter { !it.income && it.date in start until end }
            .sumOf { it.amount }
    }

    suspend fun totalIncome(period: TimePeriod): Double {
        val (start, end) = period.toDateRange()
        return _transactions.value
            .filter { it.income && it.date in start until end }
            .sumOf { it.amount }
    }

    data class CategorySpend(val categoryId: String?, val total: Double)

    suspend fun spendByCategory(period: TimePeriod): List<CategorySpend> {
        val (start, end) = period.toDateRange()
        return _transactions.value
            .filter { !it.income && it.date in start until end }
            .groupBy { it.categoryId }
            .map { (catId, txns) -> CategorySpend(catId, txns.sumOf { it.amount }) }
    }

    suspend fun getRecurringTransactions(): List<TransactionEntity> =
        _transactions.value.filter { it.recurringType > 0 }

    // ── Budgets ────────────────────────────────────────────────────────────────

    fun budgets(): Flow<List<BudgetWithCategory>> =
        _budgets.map { list ->
            list.map { b -> BudgetWithCategory(b, findCategory(b.categoryId)) }
        }

    fun mainBudget(): Flow<MainBudgetEntity?> = _mainBudget

    suspend fun saveBudget(budget: BudgetEntity) {
        supabase.from("budgets").insert(budget)
        _budgets.value = _budgets.value + budget
    }

    suspend fun updateBudget(budget: BudgetEntity) {
        supabase.from("budgets").update(budget) { filter { eq("id", budget.id) } }
        _budgets.value = _budgets.value.map { if (it.id == budget.id) budget else it }
    }

    suspend fun deleteBudget(budget: BudgetEntity) {
        supabase.from("budgets").delete { filter { eq("id", budget.id) } }
        _budgets.value = _budgets.value.filter { it.id != budget.id }
    }

    suspend fun saveMainBudget(budget: MainBudgetEntity) {
        // Upsert — only one main budget row ever exists
        supabase.from("main_budget").upsert(budget)
        _mainBudget.value = budget
    }

    suspend fun updateMainBudget(budget: MainBudgetEntity) {
        supabase.from("main_budget").update(budget) { filter { eq("id", budget.id) } }
        _mainBudget.value = budget
    }

    suspend fun deleteMainBudget(budget: MainBudgetEntity) {
        supabase.from("main_budget").delete { filter { eq("id", budget.id) } }
        _mainBudget.value = null
    }

    // ── Templates ──────────────────────────────────────────────────────────────

    fun templates(): Flow<List<TemplateWithCategory>> =
        _templates.map { list ->
            list.map { t -> TemplateWithCategory(t, findCategory(t.categoryId)) }
        }

    suspend fun getTemplateByOrder(order: Int): TemplateTransactionEntity? =
        _templates.value.find { it.order == order }

    suspend fun saveTemplate(template: TemplateTransactionEntity) {
        supabase.from("templates").insert(template)
        _templates.value = _templates.value + template
    }

    suspend fun updateTemplate(template: TemplateTransactionEntity) {
        supabase.from("templates").update(template) { filter { eq("id", template.id) } }
        _templates.value = _templates.value.map { if (it.id == template.id) template else it }
    }

    suspend fun deleteTemplate(template: TemplateTransactionEntity) {
        supabase.from("templates").delete { filter { eq("id", template.id) } }
        _templates.value = _templates.value.filter { it.id != template.id }
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    suspend fun searchTransactions(query: String, income: Boolean? = null): List<TransactionWithCategory> {
        val q = query.lowercase()
        return _transactions.value
            .filter { tx ->
                tx.note.lowercase().contains(q) &&
                    (income == null || tx.income == income)
            }
            .map { tx -> TransactionWithCategory(tx, findCategory(tx.categoryId)) }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun findCategory(id: String?): CategoryEntity? =
        if (id == null) null else _categories.value.find { it.id == id }
}
