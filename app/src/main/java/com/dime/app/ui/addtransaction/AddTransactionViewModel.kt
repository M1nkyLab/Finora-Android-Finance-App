package com.dime.app.ui.addtransaction

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dime.app.data.local.entity.AccountEntity
import com.dime.app.data.local.entity.CategoryEntity
import com.dime.app.data.local.entity.TransactionEntity
import com.dime.app.data.repository.DimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class AddTransactionUiState(
    // Form inputs
    val isIncome: Boolean = false,
    val amountText: String = "",
    val note: String = "",
    val selectedCategoryId: String? = null,
    val selectedAccountId: String? = null,
    val date: LocalDate = LocalDate.now(),

    // Loaded data
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),

    // Status
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val errorMessage: String? = null
) {
    val amount: Double get() = amountText.toDoubleOrNull() ?: 0.0
    val isValid: Boolean get() = amount > 0.0
    val filteredCategories: List<CategoryEntity>
        get() = categories.filter { it.income == isIncome }
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    application: Application,
    private val repo: DimeRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AddTransactionUiState())
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Load categories and accounts in parallel
            launch {
                repo.allCategories().collect { cats ->
                    _uiState.update { it.copy(categories = cats) }
                }
            }
            launch {
                repo.accounts().collect { accs ->
                    _uiState.update { it.copy(accounts = accs) }
                }
            }
        }
    }

    fun setAccountContext(accounts: List<AccountEntity>, defaultAccountId: String?) {
        _uiState.update { state ->
            state.copy(
                accounts = accounts.ifEmpty { state.accounts },
                selectedAccountId = state.selectedAccountId ?: defaultAccountId
            )
        }
    }

    fun setIsIncome(isIncome: Boolean) {
        _uiState.update { it.copy(isIncome = isIncome, selectedCategoryId = null) }
    }

    fun setAmount(text: String) {
        // Allow digits and a single decimal point only
        val filtered = text.filter { it.isDigit() || it == '.' }
        _uiState.update { it.copy(amountText = filtered) }
    }

    fun setNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun selectCategory(id: String) {
        _uiState.update { it.copy(selectedCategoryId = id) }
    }

    fun selectAccount(id: String) {
        _uiState.update { it.copy(selectedAccountId = id) }
    }

    fun setDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.isValid) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val date = state.date
                val dateMs  = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() +
                              System.currentTimeMillis() % 86_400_000L   // add current time-of-day ms
                val dayMs   = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val monthMs = date.withDayOfMonth(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                // Auto-pick first matching category if none selected
                val categoryId = state.selectedCategoryId
                    ?: state.filteredCategories.firstOrNull()?.id

                repo.saveTransaction(
                    TransactionEntity(
                        amount      = state.amount,
                        date        = dateMs,
                        day         = dayMs,
                        month       = monthMs,
                        note        = state.note.trim(),
                        income      = state.isIncome,
                        categoryId  = categoryId,
                        accountId   = state.selectedAccountId
                    )
                )
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "Save failed") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun reset() {
        _uiState.update {
            AddTransactionUiState(
                categories        = it.categories,
                accounts          = it.accounts,
                selectedAccountId = it.selectedAccountId
            )
        }
    }
}
