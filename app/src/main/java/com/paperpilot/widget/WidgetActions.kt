package com.paperpilot.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.paperpilot.PaperpilotApp

class WidgetActions {
    class NextQuestionAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            // Fetch next random question and update widget state
            val db = (context.applicationContext as PaperpilotApp).database
            val question = db.questionDao().getRandomQuestion()
            // Store in prefs and trigger update
            updateAppWidgetState(context, glanceId) { prefs ->
                // In real impl, use PreferencesGlanceStateDefinition
            }
            PaperpilotWidget().updateAll(context)
        }
    }

    class ShowAnswerAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            updateAppWidgetState(context, glanceId) { prefs ->
            }
            PaperpilotWidget().updateAll(context)
        }
    }

    class AnswerAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            val selected = parameters[ActionParameters.Key<String>("selected")] ?: return
            // Could mark answered
            PaperpilotWidget().updateAll(context)
        }
    }
}
