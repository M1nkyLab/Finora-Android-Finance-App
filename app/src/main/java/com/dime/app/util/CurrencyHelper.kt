package com.dime.app.util

import androidx.compose.runtime.compositionLocalOf
import java.text.NumberFormat
import java.util.Locale
import java.util.Currency

/**
 * Global currency configuration observed via CompositionLocal.
 */
data class CurrencyConfig(
    val symbol: String = "$",
    val code: String = "USD",
    val showCents: Boolean = true
) {
    /**
     * Format a double value into a string with the configured currency.
     */
    fun format(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
            format.currency = Currency.getInstance(code)
            
            if (showCents) {
                format.minimumFractionDigits = 2
                format.maximumFractionDigits = 2
            } else {
                format.minimumFractionDigits = 0
                format.maximumFractionDigits = 0
            }
            
            format.format(amount)
        } catch (e: Exception) {
            val decimals = if (showCents) 2 else 0
            "${symbol}${String.format("%.${decimals}f", amount)}"
        }
    }
}

val LocalCurrency = compositionLocalOf { CurrencyConfig() }
