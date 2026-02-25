package com.dime.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.dime.app.data.repository.DimeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DimeWidgetReceiver : GlanceAppWidgetReceiver() {
    
    @Inject
    lateinit var repository: DimeRepository
    
    override val glanceAppWidget: GlanceAppWidget
        get() = DimeAppWidget(repository)
}
