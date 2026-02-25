package com.dime.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dime.app.data.repository.DimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── DataStore singleton ───────────────────────────────────────────────────────
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dime_settings")

// ── Preference keys ───────────────────────────────────────────────────────────
object PrefKeys {
    val CURRENCY_SYMBOL     = stringPreferencesKey("currency_symbol")
    val CURRENCY_CODE       = stringPreferencesKey("currency_code")
    val SHOW_CENTS          = booleanPreferencesKey("show_cents")
    val FIRST_WEEKDAY       = intPreferencesKey("first_weekday")  // 1=Sun, 2=Mon
    val HAPTIC_TYPE         = intPreferencesKey("haptic_type")    // 0=none,1=light,2=heavy
    val COLOUR_SCHEME       = intPreferencesKey("colour_scheme")  // 0=system,1=light,2=dark
    val NOTIFICATIONS_ON    = booleanPreferencesKey("notifications_on")
    val GEMINI_API_KEY      = stringPreferencesKey("gemini_api_key")
}

// ── Haptic feedback levels ────────────────────────────────────────────────────
enum class HapticType(val label: String) {
    NONE("Off"), LIGHT("Light"), HEAVY("Heavy");

    companion object { fun fromOrdinal(o: Int) = entries.getOrElse(o) { LIGHT } }
}

// ── Colour scheme options ─────────────────────────────────────────────────────
enum class ColourScheme(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    companion object { fun fromOrdinal(o: Int) = entries.getOrElse(o) { SYSTEM } }
}

// ── Settings UI state ─────────────────────────────────────────────────────────
data class SettingsUiState(
    val currencySymbol: String  = "$",
    val currencyCode: String    = "USD",
    val showCents: Boolean      = true,
    val firstWeekday: Int       = 2,          // 2 = Monday
    val hapticType: HapticType  = HapticType.LIGHT,
    val colourScheme: ColourScheme = ColourScheme.SYSTEM,
    val notificationsEnabled: Boolean = false,
    val geminiApiKey: String    = "",
    // Stats shown in Settings header
    val transactionCount: Int   = 0,
    val categoryCount: Int      = 0,
    // Dialog / sub-sheet state
    val showCurrencyPicker: Boolean = false,
    val showEraseConfirm: Boolean   = false,
    val eraseConfirmText: String    = "",
    val isErasingData: Boolean      = false,
    val eraseSuccessMessage: String? = null,
    val showApiKeyDialog: Boolean   = false,
    val apiKeyDraftText: String     = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val repo: DimeRepository
) : AndroidViewModel(application) {

    private val prefs = application.dataStore.data

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        observeStats()
    }

    // ── Observe DataStore prefs ────────────────────────────────────────────────

    private fun observePreferences() {
        viewModelScope.launch {
            prefs.catch { emit(emptyPreferences()) }
                .collect { p ->
                    _uiState.update {
                        it.copy(
                            currencySymbol   = p[PrefKeys.CURRENCY_SYMBOL] ?: "$",
                            currencyCode     = p[PrefKeys.CURRENCY_CODE] ?: "USD",
                            showCents        = p[PrefKeys.SHOW_CENTS] ?: true,
                            firstWeekday     = p[PrefKeys.FIRST_WEEKDAY] ?: 2,
                            hapticType       = HapticType.fromOrdinal(p[PrefKeys.HAPTIC_TYPE] ?: 1),
                            colourScheme     = ColourScheme.fromOrdinal(p[PrefKeys.COLOUR_SCHEME] ?: 0),
                            notificationsEnabled = p[PrefKeys.NOTIFICATIONS_ON] ?: false,
                            geminiApiKey     = p[PrefKeys.GEMINI_API_KEY] ?: ""
                        )
                    }
                }
        }
    }

    private fun observeStats() {
        // Count transactions
        viewModelScope.launch {
            repo.allTransactions().collect { list ->
                _uiState.update { it.copy(transactionCount = list.size) }
            }
        }
        // Count expense categories
        viewModelScope.launch {
            repo.categories(income = false).collect { list ->
                _uiState.update { it.copy(categoryCount = list.size) }
            }
        }
    }

    // ── Setters ────────────────────────────────────────────────────────────────

    fun setCurrency(symbol: String, code: String) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { p ->
                p[PrefKeys.CURRENCY_SYMBOL] = symbol
                p[PrefKeys.CURRENCY_CODE]   = code
            }
            _uiState.update { it.copy(showCurrencyPicker = false) }
        }
    }

    fun setShowCents(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { p -> p[PrefKeys.SHOW_CENTS] = enabled }
        }
    }

    fun setFirstWeekday(day: Int) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { p -> p[PrefKeys.FIRST_WEEKDAY] = day }
        }
    }

    fun setHapticType(type: HapticType) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { p -> p[PrefKeys.HAPTIC_TYPE] = type.ordinal }
        }
    }

    fun setColourScheme(scheme: ColourScheme) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { p -> p[PrefKeys.COLOUR_SCHEME] = scheme.ordinal }
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { p -> p[PrefKeys.NOTIFICATIONS_ON] = enabled }
        }
    }

    // ── Currency picker ────────────────────────────────────────────────────────

    fun openCurrencyPicker() { _uiState.update { it.copy(showCurrencyPicker = true) } }
    fun closeCurrencyPicker() { _uiState.update { it.copy(showCurrencyPicker = false) } }

    // ── Erase data ────────────────────────────────────────────────────────────

    fun openEraseConfirm() { _uiState.update { it.copy(showEraseConfirm = true, eraseConfirmText = "", eraseSuccessMessage = null) } }
    fun closeEraseConfirm() { _uiState.update { it.copy(showEraseConfirm = false, eraseConfirmText = "") } }
    fun setEraseText(text: String) { _uiState.update { it.copy(eraseConfirmText = text) } }

    fun eraseAllData() {
        val state = _uiState.value
        if (state.eraseConfirmText.lowercase() != "delete") return

        _uiState.update { it.copy(isErasingData = true) }
        viewModelScope.launch {
            repo.deleteAllTransactions()
            _uiState.update {
                it.copy(
                    isErasingData    = false,
                    showEraseConfirm = false,
                    eraseConfirmText = "",
                    eraseSuccessMessage = "All transactions have been deleted."
                )
            }
        }
    }

    fun clearSuccessMessage() { _uiState.update { it.copy(eraseSuccessMessage = null) } }

    // ── API key dialog ─────────────────────────────────────────────────────────

    fun openApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = true, apiKeyDraftText = it.geminiApiKey) }
    }
    fun closeApiKeyDialog() {
        _uiState.update { it.copy(showApiKeyDialog = false, apiKeyDraftText = "") }
    }
    fun setApiKeyDraftText(text: String) {
        _uiState.update { it.copy(apiKeyDraftText = text) }
    }
    fun saveApiKey() {
        val key = _uiState.value.apiKeyDraftText.trim()
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { p -> p[PrefKeys.GEMINI_API_KEY] = key }
            _uiState.update { it.copy(showApiKeyDialog = false, apiKeyDraftText = "") }
        }
    }
}
