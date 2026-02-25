package com.dime.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dime.app.R
import com.dime.app.data.repository.DimeRepository
import com.dime.app.domain.model.TimePeriod
import com.dime.app.domain.model.toDateRange
import com.dime.app.ui.settings.PrefKeys
import com.dime.app.ui.settings.dataStore
import com.dime.app.util.CurrencyConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddWidget : AppWidgetProvider() {

    @Inject
    lateinit var repository: DimeRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_add)

        // Set up the Quick Add button intent
        val intent = Intent(context, com.dime.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_AI_INPUT", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_quick_add, pendingIntent)

        // Show a loading state first, then update with real data
        views.setTextViewText(R.id.tv_main_balance, "—")
        views.setTextViewText(R.id.tv_secondary_metric, "Loading…")
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // Load real data from repository
        scope.launch {
            try {
                val transactions = repository.getAllTransactionEntities().first()

                val (start, end) = TimePeriod.MONTH.toDateRange()
                val inMonth = transactions.filter { it.date in start until end }
                val totalIncome = inMonth.filter { it.income }.sumOf { it.amount }
                val totalSpent = inMonth.filter { !it.income }.sumOf { it.amount }
                val balance = totalIncome - totalSpent

                // Days remaining in current month
                val cal = java.util.Calendar.getInstance()
                val daysRemaining = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) -
                        cal.get(java.util.Calendar.DAY_OF_MONTH) + 1
                val safeToSpend = if (daysRemaining > 0 && balance > 0)
                    balance / daysRemaining else 0.0

                val prefs = context.dataStore.data.first()
                val symbol = prefs[PrefKeys.CURRENCY_SYMBOL] ?: "$"
                val code = prefs[PrefKeys.CURRENCY_CODE] ?: "USD"
                val showCents = prefs[PrefKeys.SHOW_CENTS] ?: true
                
                val formatter = CurrencyConfig(symbol, code, showCents)
                val balanceText = formatter.format(balance)
                val safeText = "Safe to spend: ${formatter.format(safeToSpend)}/day"
                
                val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                val isSystemDark = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val themeOrdinal = prefs[PrefKeys.COLOUR_SCHEME] ?: 0
                val isDark = when(themeOrdinal) {
                    1 -> false
                    2 -> true
                    else -> isSystemDark
                }

                val updatedViews = RemoteViews(context.packageName, R.layout.widget_quick_add)
                
                if (isDark) {
                    updatedViews.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_quick_add_dark)
                    updatedViews.setTextColor(R.id.tv_subtitle, android.graphics.Color.parseColor("#A0A0A0"))
                    updatedViews.setTextColor(R.id.tv_main_balance, android.graphics.Color.parseColor("#FFFFFF"))
                    updatedViews.setTextColor(R.id.tv_secondary_metric, android.graphics.Color.parseColor("#CCCCCC"))
                } else {
                    updatedViews.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_quick_add)
                    updatedViews.setTextColor(R.id.tv_subtitle, android.graphics.Color.parseColor("#666666"))
                    updatedViews.setTextColor(R.id.tv_main_balance, android.graphics.Color.parseColor("#0052FF"))
                    updatedViews.setTextColor(R.id.tv_secondary_metric, android.graphics.Color.parseColor("#333333"))
                }

                updatedViews.setTextViewText(R.id.tv_main_balance, balanceText)
                updatedViews.setTextViewText(R.id.tv_secondary_metric, safeText)
                updatedViews.setOnClickPendingIntent(R.id.btn_quick_add, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, updatedViews)
            } catch (e: Exception) {
                // Keep the loading placeholder if data can't be fetched
            }
        }
    }
}
