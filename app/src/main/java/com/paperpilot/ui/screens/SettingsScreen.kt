package com.paperpilot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var geminiKey by remember { mutableStateOf("") }
    var widgetInfo by remember { mutableStateOf("Widget shows random question from selected PDFs. Add widget from home screen > Widgets > Paperpilot Quiz") }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gemini API Key (Optional)", style = MaterialTheme.typography.titleMedium)
                    Text("If you add your Gemini key, AI will generate higher quality CEE questions. Otherwise mock generator is used.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = geminiKey, onValueChange = { geminiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("AIza...") })
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { /* save to datastore */ }) { Text("Save Key") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Widget Help", style = MaterialTheme.typography.titleMedium)
                    Text(widgetInfo, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Long press home screen → Widgets → Paperpilot\n• Widget flips to show answer\n• Pull ↻ for next question\n• Only selected PDFs are used", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Text("Paperpilot v1.0.0\nFor CEE Students • Built for offline revision\nOpen source on GitHub", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
