package com.dime.app.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dime.app.BuildConfig
import com.dime.app.data.ai.BrainDumpItem
import com.dime.app.data.ai.BrainDumpResult
import com.dime.app.data.ai.BrainDumpService
import com.dime.app.data.local.entity.TransactionEntity
import com.dime.app.data.repository.DimeRepository
import com.dime.app.ui.settings.PrefKeys
import com.dime.app.ui.settings.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

// ── UI State ───────────────────────────────────────────────────────────────────

data class BrainDumpUiState(
    val textInput: String = "",
    val isProcessing: Boolean = false,
    /** True while the debounce timer is ticking (user still typing) */
    val isWaitingToProcess: Boolean = false,
    /** Items that have been detected so far (staggered reveal) */
    val visibleItems: List<BrainDumpItem> = emptyList(),
    /** Full parsed list; items are added to visibleItems one by one with delay */
    val allParsedItems: List<BrainDumpItem> = emptyList(),
    val errorMessage: String? = null,
    val savedCount: Int = 0,
    val savedSuccessfully: Boolean = false,
    val geminiApiKey: String = ""
) {
    val totalExpense: Double get() = visibleItems.filter { it.type == "expense" }.sumOf { it.amount }
    val totalIncome: Double  get() = visibleItems.filter { it.type == "income"  }.sumOf { it.amount }
    val itemCount: Int       get() = visibleItems.size
    val hasResults: Boolean  get() = visibleItems.isNotEmpty()
}

// ── ViewModel ──────────────────────────────────────────────────────────────────

@HiltViewModel
class BrainDumpViewModel @Inject constructor(
    application: Application,
    private val repo: DimeRepository,
    private val brainDumpService: BrainDumpService
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BrainDumpUiState())
    val uiState: StateFlow<BrainDumpUiState> = _uiState.asStateFlow()

    // Holds the pending debounce job so we can cancel it on every new keystroke
    private var debounceJob: Job? = null

    // How long to wait after the user stops typing before firing the AI call
    private val debounceMs = 800L

    init {
        viewModelScope.launch {
            application.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .collect { prefs ->
                    val stored = prefs[PrefKeys.GEMINI_API_KEY] ?: ""
                    val effective = stored.ifBlank { BuildConfig.GEMINI_API_KEY }
                    _uiState.update { it.copy(geminiApiKey = effective) }
                }
        }
    }

    // ── Text input ─────────────────────────────────────────────────────────────

    fun onTextChange(value: String) {
        _uiState.update { it.copy(textInput = value, errorMessage = null) }

        // Cancel any previous debounce timer
        debounceJob?.cancel()

        if (value.isBlank()) {
            // Clear results instantly when field is emptied
            _uiState.update {
                it.copy(
                    isWaitingToProcess = false,
                    isProcessing       = false,
                    visibleItems       = emptyList(),
                    allParsedItems     = emptyList()
                )
            }
            return
        }

        // Show "waiting" state so the UI can display a subtle typing indicator
        _uiState.update { it.copy(isWaitingToProcess = true) }

        // Relaunch debounce timer
        debounceJob = viewModelScope.launch {
            delay(debounceMs)
            fireParseRequest(value)
        }
    }

    // ── Internal parse trigger (called after debounce) ─────────────────────────

    private suspend fun fireParseRequest(input: String) {
        val apiKey = _uiState.value.geminiApiKey
        if (input.isBlank()) return

        _uiState.update {
            it.copy(
                isWaitingToProcess = false,
                isProcessing       = true,
                errorMessage       = null,
                visibleItems       = emptyList(),
                allParsedItems     = emptyList(),
                savedSuccessfully  = false
            )
        }

        val result = brainDumpService.parseBrainDump(
            apiKey    = apiKey,
            userInput = input
        )

        when (result) {
            is BrainDumpResult.Success -> {
                _uiState.update {
                    it.copy(isProcessing = false, allParsedItems = result.items)
                }
                // Staggered reveal: add each item with a spring-like delay
                result.items.forEachIndexed { index, item ->
                    delay(if (index == 0) 80L else 120L)
                    _uiState.update { state ->
                        state.copy(visibleItems = state.visibleItems + item)
                    }
                }
            }
            is BrainDumpResult.Error -> {
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = result.message)
                }
            }
        }
    }

    // ── Manual trigger (send button fallback) ──────────────────────────────────

    fun processInput() {
        debounceJob?.cancel()
        val text = _uiState.value.textInput
        if (text.isBlank()) return
        _uiState.update { it.copy(isWaitingToProcess = false) }
        viewModelScope.launch { fireParseRequest(text) }
    }

    // ── Item removal (user can deselect before saving) ─────────────────────────

    fun removeItem(itemId: String) {
        _uiState.update { state ->
            state.copy(
                visibleItems   = state.visibleItems.filter { it.id != itemId },
                allParsedItems = state.allParsedItems.filter { it.id != itemId }
            )
        }
    }

    // ── Confirm & Save All ─────────────────────────────────────────────────────

    fun confirmAndSaveAll() {
        val items = _uiState.value.visibleItems
        if (items.isEmpty()) return

        viewModelScope.launch {
            val allCats = repo.allCategories().first()

            var savedCount = 0
            items.forEach { item ->
                val isIncome = item.type == "income"

                var categoryId = allCats
                    .find { it.name.equals(item.category, ignoreCase = true) }?.id
                if (categoryId == null) {
                    categoryId = allCats.find { it.income == isIncome }?.id
                        ?: allCats.firstOrNull()?.id ?: ""
                }

                val transactionTime = item.date
                    .atTime(LocalTime.now())
                    .atZone(ZoneId.systemDefault())
                val dateMs  = transactionTime.toInstant().toEpochMilli()
                val dayMs   = item.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val monthMs = item.date.withDayOfMonth(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val recurType = when (item.recurring) {
                    "daily"   -> RecurringType.DAILY
                    "weekly"  -> RecurringType.WEEKLY
                    "monthly" -> RecurringType.MONTHLY
                    "yearly"  -> RecurringType.YEARLY
                    else      -> RecurringType.NONE
                }

                repo.saveTransaction(
                    TransactionEntity(
                        amount               = item.amount,
                        date                 = dateMs,
                        day                  = dayMs,
                        month                = monthMs,
                        note                 = item.description,
                        income               = isIncome,
                        categoryId           = categoryId,
                        onceRecurring        = recurType != RecurringType.NONE,
                        recurringType        = recurType,
                        recurringCoefficient = if (recurType != RecurringType.NONE) 1 else 0
                    )
                )
                savedCount++
            }

            _uiState.update {
                it.copy(savedSuccessfully = true, savedCount = savedCount)
            }
        }
    }

    // ── Reset ──────────────────────────────────────────────────────────────────

    fun reset() {
        debounceJob?.cancel()
        debounceJob = null
        _uiState.value = BrainDumpUiState(geminiApiKey = _uiState.value.geminiApiKey)
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
