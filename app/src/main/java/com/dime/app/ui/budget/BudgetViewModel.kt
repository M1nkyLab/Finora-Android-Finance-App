package com.dime.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dime.app.data.local.entity.BudgetEntity
import com.dime.app.data.local.entity.CategoryEntity
import com.dime.app.data.local.entity.MainBudgetEntity
import com.dime.app.data.local.relation.BudgetWithCategory
import com.dime.app.data.repository.DimeRepository
import com.dime.app.domain.model.TimePeriod
import com.dime.app.domain.model.toDateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── Budget time frame (mirrors iOS type 0-3) ──────────────────────────────────
enum class BudgetTimeFrame(val label: String, val roomType: Int) {
    DAILY("Daily", 0),
    WEEKLY("Weekly", 1),
    MONTHLY("Monthly", 2),
    YEARLY("Yearly", 3);

    companion object {
        fun fromRoomType(t: Int) = entries.firstOrNull { it.roomType == t } ?: MONTHLY
    }
}

// ── Per-budget display row ────────────────────────────────────────────────────
data class BudgetDisplayItem(
    val id: String,
    val amount: Double,
    val spent: Double,
    val progress: Float,            // 0.0–1.0
    val isOverBudget: Boolean,
    val showGreen: Boolean,
    val timeFrame: BudgetTimeFrame,
    val category: CategoryEntity?,  // null → overall budget
    val entity: BudgetEntity?,      // null for MainBudget
    val mainEntity: MainBudgetEntity?
)

// ── New-budget sheet UI state ─────────────────────────────────────────────────
data class NewBudgetSheetState(
    val isOpen: Boolean             = false,
    val editingBudget: BudgetDisplayItem? = null,  // null = create mode
    val isOverall: Boolean          = true,         // overall vs category
    val selectedCategoryId: String? = null,
    val timeFrame: BudgetTimeFrame  = BudgetTimeFrame.MONTHLY,
    val amount: Double              = 0.0,
    val amountText: String          = "",
    val showGreen: Boolean          = false,
    val errorMessage: String?       = null,
    val isSaving: Boolean           = false
)

// ── Main screen UI state ──────────────────────────────────────────────────────
data class BudgetUiState(
    val overallBudget: BudgetDisplayItem?   = null,
    val categoryBudgets: List<BudgetDisplayItem> = emptyList(),
    val totalSpent: Double                  = 0.0,
    val totalBudgeted: Double               = 0.0,
    val overallProgress: Float              = 0f,
    val isEmpty: Boolean                    = true,
    val sheet: NewBudgetSheetState          = NewBudgetSheetState(),
    // categories available for new budget creation
    val expenseCategories: List<CategoryEntity> = emptyList()
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val repo: DimeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    // ── Observe all live data ───────────────────────────────────────────────────

    private fun observeData() {
        // collectLatest cancels any in-flight rebuildState when new data arrives
        viewModelScope.launch {
            combine(
                repo.budgets(),
                repo.mainBudget(),
                repo.categories(income = false)
            ) { categoryBudgets, mainBudget, categories ->
                Triple(categoryBudgets, mainBudget, categories)
            }.collectLatest { (categoryBudgets, mainBudget, categories) ->
                rebuildState(categoryBudgets, mainBudget, categories)
            }
        }
    }

    private suspend fun rebuildState(
        budgetRows: List<BudgetWithCategory>,
        mainBudget: MainBudgetEntity?,
        expenseCategories: List<CategoryEntity>
    ) {
        val monthlyPeriod = TimePeriod.MONTH

        // Fetch spend data on IO thread, then update state on Main
        val (spendByCategory, overallSpent) = withContext(Dispatchers.IO) {
            val spend = repo.spendByCategory(monthlyPeriod).associate { it.categoryId to it.total }
            val total = repo.totalSpent(monthlyPeriod)
            spend to total
        }

        // ── Main (overall) budget ──────────────────────────────────────────────
        val overallItem = mainBudget?.let { mb ->
            val timeFrame = BudgetTimeFrame.fromRoomType(mb.type)
            val budgeted  = mb.amount
            val progress  = if (budgeted > 0) (overallSpent / budgeted).toFloat().coerceIn(0f, 1f) else 0f
            BudgetDisplayItem(
                id           = mb.id,
                amount       = budgeted,
                spent        = overallSpent,
                progress     = progress,
                isOverBudget = overallSpent > budgeted,
                showGreen    = mb.showGreen,
                timeFrame    = timeFrame,
                category     = null,
                entity       = null,
                mainEntity   = mb
            )
        }

        // ── Category budgets ───────────────────────────────────────────────────
        val catItems = budgetRows.map { bwc ->
            val timeFrame = BudgetTimeFrame.fromRoomType(bwc.budget.type)
            val budgeted  = bwc.budget.amount
            val spent     = spendByCategory[bwc.budget.categoryId] ?: 0.0
            val progress  = if (budgeted > 0) (spent / budgeted).toFloat().coerceIn(0f, 1f) else 0f
            BudgetDisplayItem(
                id           = bwc.budget.id,
                amount       = budgeted,
                spent        = spent,
                progress     = progress,
                isOverBudget = spent > budgeted,
                showGreen    = bwc.budget.showGreen,
                timeFrame    = timeFrame,
                category     = bwc.category,
                entity       = bwc.budget,
                mainEntity   = null
            )
        }

        val totalBudgeted = catItems.sumOf { it.amount } + (mainBudget?.amount ?: 0.0)
        val isEmpty = overallItem == null && catItems.isEmpty()

        _uiState.update {
            it.copy(
                overallBudget     = overallItem,
                categoryBudgets   = catItems,
                totalSpent        = overallSpent,
                totalBudgeted     = totalBudgeted,
                overallProgress   = overallItem?.progress ?: 0f,
                isEmpty           = isEmpty,
                expenseCategories = expenseCategories
            )
        }
    }

    // ── Sheet control ──────────────────────────────────────────────────────────

    fun openNewBudget(isOverall: Boolean = true) {
        _uiState.update {
            it.copy(
                sheet = NewBudgetSheetState(
                    isOpen    = true,
                    isOverall = isOverall
                )
            )
        }
    }

    fun openEditBudget(item: BudgetDisplayItem) {
        _uiState.update {
            it.copy(
                sheet = NewBudgetSheetState(
                    isOpen              = true,
                    editingBudget       = item,
                    isOverall           = item.mainEntity != null,
                    selectedCategoryId  = item.category?.id,
                    timeFrame           = item.timeFrame,
                    amount              = item.amount,
                    amountText          = formatAmount(item.amount),
                    showGreen           = item.showGreen
                )
            )
        }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(sheet = NewBudgetSheetState()) }
    }

    fun setSheetType(isOverall: Boolean) {
        _uiState.update { it.copy(sheet = it.sheet.copy(isOverall = isOverall, selectedCategoryId = null)) }
    }

    fun setSheetCategory(categoryId: String?) {
        _uiState.update { it.copy(sheet = it.sheet.copy(selectedCategoryId = categoryId)) }
    }

    fun setSheetTimeFrame(tf: BudgetTimeFrame) {
        _uiState.update { it.copy(sheet = it.sheet.copy(timeFrame = tf)) }
    }

    fun setSheetAmount(text: String) {
        val num = text.toDoubleOrNull() ?: 0.0
        _uiState.update { it.copy(sheet = it.sheet.copy(amountText = text, amount = num, errorMessage = null)) }
    }

    fun toggleSheetGreen() {
        _uiState.update { it.copy(sheet = it.sheet.copy(showGreen = !it.sheet.showGreen)) }
    }

    // ── Save / Delete ──────────────────────────────────────────────────────────

    fun saveBudget() {
        val sheet = _uiState.value.sheet
        if (sheet.amount <= 0.0) {
            _uiState.update { it.copy(sheet = it.sheet.copy(errorMessage = "Please enter a valid amount")) }
            return
        }
        if (!sheet.isOverall && sheet.selectedCategoryId == null) {
            _uiState.update { it.copy(sheet = it.sheet.copy(errorMessage = "Please select a category")) }
            return
        }

        _uiState.update { it.copy(sheet = it.sheet.copy(isSaving = true)) }

        viewModelScope.launch {
            val editing = sheet.editingBudget
            if (sheet.isOverall) {
                // Overall budget
                val existing = editing?.mainEntity
                if (existing != null) {
                    repo.updateMainBudget(existing.copy(amount = sheet.amount, type = sheet.timeFrame.roomType, showGreen = sheet.showGreen))
                } else {
                    repo.saveMainBudget(MainBudgetEntity(amount = sheet.amount, type = sheet.timeFrame.roomType, showGreen = sheet.showGreen))
                }
            } else {
                // Category budget
                val existing = editing?.entity
                if (existing != null) {
                    repo.updateBudget(existing.copy(amount = sheet.amount, type = sheet.timeFrame.roomType, showGreen = sheet.showGreen, categoryId = sheet.selectedCategoryId))
                } else {
                    repo.saveBudget(BudgetEntity(amount = sheet.amount, type = sheet.timeFrame.roomType, showGreen = sheet.showGreen, categoryId = sheet.selectedCategoryId))
                }
            }
            dismissSheet()
        }
    }

    fun deleteBudget(item: BudgetDisplayItem) {
        viewModelScope.launch {
            item.mainEntity?.let { repo.deleteMainBudget(it) }
            item.entity?.let { repo.deleteBudget(it) }
        }
    }

    // ── Formatting helpers ─────────────────────────────────────────────────────

    private fun formatAmount(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString()
        else "%.2f".format(amount)
}
