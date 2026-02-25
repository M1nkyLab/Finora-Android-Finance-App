package com.dime.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dime.app.MainActivity
import com.dime.app.data.repository.DimeRepository
import com.dime.app.ui.settings.PrefKeys
import com.dime.app.ui.settings.dataStore
import com.dime.app.util.CurrencyConfig
import kotlinx.coroutines.flow.first

class DimeAppWidget(private val repository: DimeRepository) : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val transactions = repository.getAllTransactionEntities().first()
        val totalSpent = transactions.filter { !it.income }.sumOf { it.amount }
        
        val prefs = context.dataStore.data.first()
        val symbol = prefs[PrefKeys.CURRENCY_SYMBOL] ?: "$"
        val code = prefs[PrefKeys.CURRENCY_CODE] ?: "USD"
        val showCents = prefs[PrefKeys.SHOW_CENTS] ?: true
        val currencyFormatter = CurrencyConfig(symbol, code, showCents)
        
        val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isSystemDark = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themeOrdinal = prefs[PrefKeys.COLOUR_SCHEME] ?: 0
        val isDark = when(themeOrdinal) {
            1 -> false
            2 -> true
            else -> isSystemDark
        }
        
        val bgColor = if (isDark) Color(0xFF1E1E26) else Color(0xFFF3F4F6)
        val titleColor = if (isDark) Color.LightGray else Color.DarkGray
        val amountColor = if (isDark) Color.White else Color.Black
        
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Spent",
                    style = TextStyle(color = ColorProvider(titleColor), fontSize = 14.sp)
                )
                Text(
                    text = currencyFormatter.format(totalSpent),
                    style = TextStyle(
                        color = ColorProvider(amountColor), 
                        fontSize = 28.sp, 
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(top = 8.dp)
                )
            }
        }
    }
}
