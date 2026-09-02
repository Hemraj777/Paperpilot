package com.paperpilot.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paperpilot.PaperpilotApp

class WidgetWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = (applicationContext as? PaperpilotApp)?.database ?: return Result.success()
            val count = db.questionDao().count()
            if (count == 0) return Result.success()
            // Rotate to next random question for freshness
            val prefs = applicationContext.getSharedPreferences("paperpilot_widget", Context.MODE_PRIVATE)
            val selected = try { db.pdfDao().getSelectedPdfs() } catch (_: Exception) { emptyList() }
            val ids = selected.map { it.id }
            val q = if (ids.isNotEmpty()) {
                db.questionDao().getRandomQuestionForPdfs(ids) ?: db.questionDao().getRandomQuestion()
            } else {
                db.questionDao().getRandomQuestion()
            }
            if (q != null) {
                prefs.edit().putLong("widget_q_id", q.id).putBoolean("widget_show_answer", false).apply()
            }
            PaperpilotWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
