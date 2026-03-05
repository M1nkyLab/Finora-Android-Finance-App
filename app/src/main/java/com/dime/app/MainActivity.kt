package com.dime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dime.app.ui.MainScreen
import com.dime.app.ui.settings.ColourScheme
import com.dime.app.ui.settings.PrefKeys
import com.dime.app.ui.settings.dataStore
import com.dime.app.ui.theme.DimeTheme
import dagger.hilt.android.AndroidEntryPoint
import com.dime.app.util.CurrencyConfig
import com.dime.app.util.LocalCurrency
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.map

import android.content.Intent
import androidx.compose.runtime.mutableStateOf

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val openAddTransactionState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()  // Render behind system bars — removes opaque white nav bar background
        handleIntent(intent)

        setContent {
            val colourSchemeOrdinal by dataStore.data
                .map { it[PrefKeys.COLOUR_SCHEME] ?: 0 }
                .collectAsState(initial = 0)

            val darkTheme = when (ColourScheme.fromOrdinal(colourSchemeOrdinal)) {
                ColourScheme.SYSTEM -> isSystemInDarkTheme()
                ColourScheme.LIGHT -> false
                ColourScheme.DARK -> true
            }

            val currencyConfig by dataStore.data
                .map { prefs ->
                    CurrencyConfig(
                        symbol = prefs[PrefKeys.CURRENCY_SYMBOL] ?: "$",
                        code = prefs[PrefKeys.CURRENCY_CODE] ?: "USD",
                        showCents = prefs[PrefKeys.SHOW_CENTS] ?: true
                    )
                }
                .collectAsState(initial = CurrencyConfig())

            DimeTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalCurrency provides currencyConfig) {
                    MainScreen(
                        startAddTransaction = openAddTransactionState.value,
                        onAddTransactionOpened = { openAddTransactionState.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("OPEN_ADD_TRANSACTION", false) == true) {
            openAddTransactionState.value = true
        }
    }
}

