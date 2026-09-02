package com.paperpilot.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.paperpilot.MainActivity
import com.paperpilot.PaperpilotApp
import com.paperpilot.data.entity.Question

class PaperpilotWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("paperpilot_widget", Context.MODE_PRIVATE)
        var qId = prefs.getLong("widget_q_id", 0L)
        val showAnswer = prefs.getBoolean("widget_show_answer", false)

        val db = (context.applicationContext as? PaperpilotApp)?.database
        var question: Question? = null

        if (db != null) {
            try {
                // Try to load stored question
                if (qId != 0L) {
                    question = db.questionDao().getQuestionById(qId)
                }
                // If no stored or not found, fetch random from selected PDFs or any
                if (question == null) {
                    val selected = try { db.pdfDao().getSelectedPdfs() } catch (_: Exception) { emptyList() }
                    val ids = selected.map { it.id }
                    question = if (ids.isNotEmpty()) {
                        db.questionDao().getRandomQuestionForPdfs(ids) ?: db.questionDao().getRandomQuestion()
                    } else {
                        db.questionDao().getRandomQuestion()
                    }
                    // Store for flip persistence
                    if (question != null) {
                        prefs.edit().putLong("widget_q_id", question.id).putBoolean("widget_show_answer", false).apply()
                    }
                }
            } catch (e: Exception) {
                // Fallback: ignore
            }
        }

        provideContent {
            WidgetContent(context, question, showAnswer)
        }
    }

    @Composable
    fun WidgetContent(context: Context, question: Question?, showAnswer: Boolean) {
        // Outer container
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFFF8FAFC)))
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paperpilot • CEE",
                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFF4F46E5)))
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = if (question != null) "${question.subject} • ${question.difficulty}" else "",
                    style = TextStyle(fontSize = 9.sp, color = ColorProvider(Color(0xFF64748B)))
                )
            }
            Spacer(modifier = GlanceModifier.height(6.dp))

            if (question == null) {
                // Empty state
                Column(
                    modifier = GlanceModifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No questions yet",
                        style = TextStyle(fontSize = 13.sp, color = ColorProvider(Color(0xFF334155)))
                    )
                    Text(
                        text = "Upload PDFs & generate quiz in app",
                        style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color(0xFF64748B)))
                    )
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(ColorProvider(Color(0xFF4F46E5)))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Open Paperpilot",
                            style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.White))
                        )
                    }
                }
            } else {
                // Question
                Text(
                    text = question.questionText.take(220),
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF0F172A))),
                    maxLines = 4
                )
                question.sourcePage?.let {
                    Text(
                        text = "Page ~$it",
                        style = TextStyle(fontSize = 9.sp, color = ColorProvider(Color(0xFF94A3B8)))
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))

                // Options 2x2 grid
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        OptionBox("A) ${question.optionA.take(35)}", showAnswer && question.correctOption == "A", showAnswer && question.correctOption != "A", modifier = GlanceModifier.defaultWeight())
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        OptionBox("B) ${question.optionB.take(35)}", showAnswer && question.correctOption == "B", showAnswer && question.correctOption != "B", modifier = GlanceModifier.defaultWeight())
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        OptionBox("C) ${question.optionC.take(35)}", showAnswer && question.correctOption == "C", showAnswer && question.correctOption != "C", modifier = GlanceModifier.defaultWeight())
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        OptionBox("D) ${question.optionD.take(35)}", showAnswer && question.correctOption == "D", showAnswer && question.correctOption != "D", modifier = GlanceModifier.defaultWeight())
                    }
                }

                if (showAnswer) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(ColorProvider(Color(0xFFEEF2FF)))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "✓ ${question.correctOption}: ${question.explanation.take(180)}",
                            style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFF1E40AF))),
                            maxLines = 3
                        )
                        question.whyOtherWrong?.let {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "✗ Others: ${it.take(140)}",
                                style = TextStyle(fontSize = 9.sp, color = ColorProvider(Color(0xFF64748B))),
                                maxLines = 2
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))
                // Action row: Next + Toggle
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .background(ColorProvider(Color(0xFF4F46E5)))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable(actionRunCallback<WidgetActions.NextQuestionAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↻ Next",
                            style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color.White))
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(ColorProvider(if (showAnswer) Color(0xFF64748B) else Color(0xFF0EA5E9)))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable(actionRunCallback<WidgetActions.ToggleAnswerAction>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showAnswer) "Hide" else "👁 Show Answer",
                            style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color.White))
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Open →",
                            style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFF4F46E5)))
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun OptionBox(text: String, isCorrect: Boolean, isDim: Boolean, modifier: GlanceModifier = GlanceModifier) {
        val bg = when {
            isCorrect -> Color(0xFFDCFCE7) // green highlight for correct when revealed
            isDim -> Color(0xFFF1F5F9)
            else -> Color.White
        }
        val borderColor = if (isCorrect) Color(0xFF16A34A) else Color(0xFFE2E8F0)
        Box(
            modifier = modifier
                .background(ColorProvider(bg))
                .padding(6.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 10.sp,
                    color = ColorProvider(if (isCorrect) Color(0xFF15803D) else Color(0xFF334155))
                ),
                maxLines = 2
            )
        }
    }
}

class PaperpilotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaperpilotWidget()
}
