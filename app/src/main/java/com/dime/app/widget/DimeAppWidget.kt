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
import com.dime.app.util.CurrencyConfig
import kotlinx.coroutines.flow.first

class DimeAppWidget(private val repository: DimeRepository) : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val transactions = repository.getAllTransactionEntities().first()
        val totalSpent = transactions.filter { !it.income }.sumOf { it.amount }
        val currencyFormatter = CurrencyConfig()
        
        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E26))
                    .padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Spent",
                    style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 14.sp)
                )
                Text(
                    text = currencyFormatter.format(totalSpent),
                    style = TextStyle(
                        color = ColorProvider(Color.White), 
                        fontSize = 28.sp, 
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(top = 8.dp)
                )
            }
        }
    }
}
