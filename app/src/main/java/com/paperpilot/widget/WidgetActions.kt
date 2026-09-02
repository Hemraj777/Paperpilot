package com.paperpilot.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.paperpilot.PaperpilotApp

class WidgetActions {
    class NextQuestionAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val prefs = context.getSharedPreferences("paperpilot_widget", Context.MODE_PRIVATE)
            val db = (context.applicationContext as? PaperpilotApp)?.database
            var nextId: Long? = null
            if (db != null) {
                try {
                    val selected = db.pdfDao().getSelectedPdfs()
                    val ids = selected.map { it.id }
                    val q = if (ids.isNotEmpty()) {
                        db.questionDao().getRandomQuestionForPdfs(ids) ?: db.questionDao().getRandomQuestion()
                    } else {
                        db.questionDao().getRandomQuestion()
                    }
                    nextId = q?.id
                } catch (_: Exception) {}
            }
            // If we got a new question, store it and hide answer; otherwise just hide answer
            prefs.edit()
                .putLong("widget_q_id", nextId ?: 0L)
                .putBoolean("widget_show_answer", false)
                .apply()
            PaperpilotWidget().updateAll(context)
        }
    }

    class ToggleAnswerAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val prefs = context.getSharedPreferences("paperpilot_widget", Context.MODE_PRIVATE)
            val current = prefs.getBoolean("widget_show_answer", false)
            prefs.edit().putBoolean("widget_show_answer", !current).apply()
            PaperpilotWidget().updateAll(context)
        }
    }

    // Legacy alias for backwards compatibility
    class ShowAnswerAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            ToggleAnswerAction().onAction(context, glanceId, parameters)
        }
    }
}
