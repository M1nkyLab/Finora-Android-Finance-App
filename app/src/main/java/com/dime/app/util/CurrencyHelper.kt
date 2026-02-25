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
            format.minimumFractionDigits = 2
            format.maximumFractionDigits = 2
            
            // If the symbol in the chosen code doesn't match our 'symbol' (custom), 
            // we might want to override. But usually user picks standard codes.
            // For now, let's use the standard formatter with the selected currency code.
            
            var result = format.format(amount)
            
            // If user explicitly provided a custom symbol that differs from the default for that code
            // (rare in standard apps but possible in Dime's UI), we could do string replacement.
            // However, sticking to the standard Currency instance is safer for localizations.
            
            if (!showCents && amount % 1.0 == 0.0) {
                result = result.substringBefore(".")
                // Note: This is a bit naive for locales with different decimal separators.
                // A better way is setting fraction digits:
                format.maximumFractionDigits = 0
                result = format.format(amount)
            }
            result
        } catch (e: Exception) {
            // Fallback
            "${symbol}${String.format("%.2f", amount)}"
        }
    }
}

val LocalCurrency = compositionLocalOf { CurrencyConfig() }
