package com.paperpilot.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paperpilot.PaperpilotApp

class WidgetWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = (applicationContext as PaperpilotApp).database
        // Touch DB to ensure widget has data
        val count = db.questionDao().count()
        if (count == 0) return Result.success()
        // Update all widgets
        PaperpilotWidget().updateAll(applicationContext)
        return Result.success()
    }
}
