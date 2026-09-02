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

class PaperpilotWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(context)
        }
    }

    @Composable
    fun WidgetContent(context: Context) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFFF8FAFC)))
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Paperpilot • CEE",
                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color(0xFF4F46E5)))
                )
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = "Tap to open • Pull to get next question",
                style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color(0xFF334155)))
            )
            Spacer(modifier = GlanceModifier.height(12.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(Color(0xFF4F46E5)))
                    .padding(10.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open Paperpilot",
                    style = TextStyle(fontSize = 13.sp, color = ColorProvider(Color.White))
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "↻ Next  •  👁 Show Answer",
                    style = TextStyle(fontSize = 11.sp, color = ColorProvider(Color(0xFF64748B))),
                    modifier = GlanceModifier.clickable(actionRunCallback<WidgetActions.NextQuestionAction>())
                )
            }
        }
    }
}

class PaperpilotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaperpilotWidget()
}
