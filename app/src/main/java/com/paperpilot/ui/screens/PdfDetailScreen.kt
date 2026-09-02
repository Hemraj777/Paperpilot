package com.paperpilot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperpilot.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDetailScreen(pdfId: Long, onBack: () -> Unit, onStartQuiz: () -> Unit, vm: HomeViewModel = viewModel()) {
    val pdfs by vm.pdfs.collectAsState()
    val questions by vm.questions.collectAsState()
    val pdf = pdfs.find { it.id == pdfId }
    val qs = questions.filter { it.pdfId == pdfId }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(pdf?.fileName ?: "PDF Detail") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        },
        floatingActionButton = {
            if (qs.isNotEmpty()) FloatingActionButton(onClick = onStartQuiz) { Icon(Icons.Default.PlayArrow, null) }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(pdf?.fileName ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                        Text("Subject: ${pdf?.subject ?: "-"} • ${pdf?.pageCount} pages", style = MaterialTheme.typography.bodyMedium)
                        Text("Selected for widget: ${pdf?.isSelectedForQuiz}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.generateQuiz(pdfId) }) { Text("Regenerate Quiz") }
                            OutlinedButton(onClick = onStartQuiz, enabled = qs.isNotEmpty()) { Text("Start Quiz (${qs.size})") }
                        }
                    }
                }
            }
            item { Text("Questions", style = MaterialTheme.typography.titleMedium) }
            items(qs) { q ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(q.questionText, style = MaterialTheme.typography.bodyMedium)
                        Text("A) ${q.optionA}", style = MaterialTheme.typography.bodySmall)
                        Text("B) ${q.optionB}", style = MaterialTheme.typography.bodySmall)
                        Text("C) ${q.optionC}", style = MaterialTheme.typography.bodySmall)
                        Text("D) ${q.optionD}", style = MaterialTheme.typography.bodySmall)
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        Text("Ans: ${q.correctOption} • ${q.explanation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
