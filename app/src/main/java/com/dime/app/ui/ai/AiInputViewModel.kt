package com.dime.app.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dime.app.BuildConfig
import com.dime.app.data.ai.AiParseResult
import com.dime.app.data.ai.AiTransactionService
import com.dime.app.data.ai.ParsedTransaction
import com.dime.app.data.local.entity.TransactionEntity
import com.dime.app.data.repository.DimeRepository
import com.dime.app.ui.settings.PrefKeys
import com.dime.app.ui.settings.dataStore

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class AiInputUiState(
    val textInput: String = "",
    val isProcessing: Boolean = false,
    val parsedResult: ParsedTransaction? = null,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false,
    val geminiApiKey: String = ""
)

object RecurringType {
    const val NONE = 0
    const val DAILY = 1
    const val WEEKLY = 2
    const val MONTHLY = 3
    const val YEARLY = 4
}

// ── Suggestion chips ──────────────────────────────────────────────────────────

val quickSuggestions = listOf(
    "Lunch RM12",
    "Grab ride RM8",
    "Salary 3000",
    "Netflix RM45 monthly",
    "Groceries RM65 yesterday"
)

@HiltViewModel
class AiInputViewModel @Inject constructor(
    application: Application,
    private val repo: DimeRepository,
    private val aiService: AiTransactionService
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AiInputUiState())
    val uiState: StateFlow<AiInputUiState> = _uiState.asStateFlow()

    init {
        // Load API key from DataStore (user-configurable) or fall back to BuildConfig
        viewModelScope.launch {
            application.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .collect { prefs ->
                    val storedKey = prefs[PrefKeys.GEMINI_API_KEY] ?: ""
                    val effectiveKey = storedKey.ifBlank { BuildConfig.GEMINI_API_KEY }
                    _uiState.update { it.copy(geminiApiKey = effectiveKey) }
                }
        }
    }

    // ── Text input ────────────────────────────────────────────────────────────

    fun onTextChange(value: String) {
        _uiState.update { it.copy(textInput = value, errorMessage = null) }
    }

    // ── Process with AI ───────────────────────────────────────────────────────

    fun processInput() {
        val state = _uiState.value
        if (state.isProcessing || state.textInput.isBlank()) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null, parsedResult = null, savedSuccessfully = false) }

        viewModelScope.launch {
            val result = aiService.parseTransaction(
                apiKey = state.geminiApiKey,
                userInput = state.textInput
            )

            when (result) {
                is AiParseResult.Success -> {
                    _uiState.update { it.copy(
                        isProcessing = false,
                        parsedResult = result.transaction
                    )}
                }
                is AiParseResult.Error -> {
                    _uiState.update { it.copy(
                        isProcessing = false,
                        errorMessage = result.message
                    )}
                }
            }
        }
    }

    // ── Confirm & Save ────────────────────────────────────────────────────────

    fun confirmAndSave() {
        val parsed = _uiState.value.parsedResult ?: return

        viewModelScope.launch {
            val isIncome = parsed.type == "income"
            val categoryId = aiService.categoryMapping[parsed.category]

            // Resolve date to epoch millis
            val zdt = parsed.date.atStartOfDay(ZoneId.systemDefault())
            val dateMs = zdt.toInstant().toEpochMilli()
            val dayMs = zdt.toInstant().toEpochMilli()
            val monthMs = parsed.date.withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            // Map AI recurring string to RecurringType constant
            val recurType = when (parsed.recurring) {
                "daily"   -> RecurringType.DAILY
                "weekly"  -> RecurringType.WEEKLY
                "monthly" -> RecurringType.MONTHLY
                "yearly"  -> RecurringType.YEARLY
                else      -> RecurringType.NONE
            }

            val entity = TransactionEntity(
                amount = parsed.amount,
                date = dateMs,
                day = dayMs,
                month = monthMs,
                note = parsed.title,
                income = isIncome,
                categoryId = categoryId,
                onceRecurring = recurType != RecurringType.NONE,
                recurringType = recurType,
                recurringCoefficient = if (recurType != RecurringType.NONE) 1 else 0
            )

            repo.saveTransaction(entity)

            _uiState.update { it.copy(
                savedSuccessfully = true,
                parsedResult = null,
                textInput = ""
            )}
        }
    }

    // ── Edit parsed result before saving ──────────────────────────────────────

    fun editParsedAmount(newAmount: Double) {
        _uiState.update { state ->
            state.copy(parsedResult = state.parsedResult?.copy(amount = newAmount))
        }
    }

    fun editParsedTitle(newTitle: String) {
        _uiState.update { state ->
            state.copy(parsedResult = state.parsedResult?.copy(title = newTitle))
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        _uiState.value = AiInputUiState(geminiApiKey = _uiState.value.geminiApiKey)
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(savedSuccessfully = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
