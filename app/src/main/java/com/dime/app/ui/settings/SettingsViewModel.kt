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

    val COLOUR_SCHEME       = intPreferencesKey("colour_scheme")  // 0=system,1=light,2=dark

    val GEMINI_API_KEY      = stringPreferencesKey("gemini_api_key")
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

    val colourScheme: ColourScheme = ColourScheme.SYSTEM,

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

                            colourScheme     = ColourScheme.fromOrdinal(p[PrefKeys.COLOUR_SCHEME] ?: 0),

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
        // Count all categories
        viewModelScope.launch {
            repo.allCategories().collect { list ->
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



    fun setColourScheme(scheme: ColourScheme) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { p -> p[PrefKeys.COLOUR_SCHEME] = scheme.ordinal }
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
