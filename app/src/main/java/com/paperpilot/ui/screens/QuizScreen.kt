package com.paperpilot.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperpilot.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(pdfId: Long, onBack: () -> Unit, vm: HomeViewModel = viewModel()) {
    val questions by vm.questions.collectAsState()
    val filtered = questions.filter { it.pdfId == pdfId }
    var currentIndex by remember { mutableStateOf(0) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var showAnswer by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }

    val current = filtered.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz • ${filtered.size} Qs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = {
                        currentIndex = 0; selectedOption = null; showAnswer = false; score = 0
                    }) { Icon(Icons.Default.Refresh, null) }
                }
            )
        }
    ) { padding ->
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No questions for this PDF")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { vm.generateQuiz(pdfId) }) { Text("Generate Quiz") }
                }
            }
            return@Scaffold
        }
        if (current == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.padding(24.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Quiz Completed!", style = MaterialTheme.typography.titleLarge)
                        Text("Score: $score / ${filtered.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { currentIndex = 0; score = 0; selectedOption = null; showAnswer = false }) { Text("Restart") }
                        TextButton(onClick = onBack) { Text("Back to Home") }
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                LinearProgressIndicator(progress = { (currentIndex + 1).toFloat() / filtered.size }, modifier = Modifier.fillMaxWidth())
                Text("${currentIndex + 1} / ${filtered.size} • ${current.subject} • ${current.difficulty}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(current.questionText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        current.sourcePage?.let { Text("Source: Page $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            itemsIndexed(listOf("A" to current.optionA, "B" to current.optionB, "C" to current.optionC, "D" to current.optionD)) { _, (key, text) ->
                val isSelected = selectedOption == key
                val isCorrect = current.correctOption == key
                val bg = when {
                    !showAnswer -> if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    isCorrect -> MaterialTheme.colorScheme.primaryContainer
                    isSelected && !isCorrect -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    onClick = {
                        if (!showAnswer) selectedOption = key
                    }
                ) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isSelected, onClick = { if (!showAnswer) selectedOption = key })
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("$key) $text", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (showAnswer && isCorrect) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                if (!showAnswer) {
                    Button(
                        onClick = {
                            if (selectedOption != null) {
                                showAnswer = true
                                val correct = selectedOption == current.correctOption
                                if (correct) score++
                                vm.markAnswer(current.id, correct)
                            }
                        },
                        enabled = selectedOption != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Check Answer") }
                } else {
                    AnimatedVisibility(visible = showAnswer) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(colors = CardDefaults.cardColors(containerColor = if (selectedOption == current.correctOption) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        if (selectedOption == current.correctOption) "Correct! 🎉" else "Incorrect. Correct is ${current.correctOption}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Explanation: ${current.explanation}", style = MaterialTheme.typography.bodyMedium)
                                    current.whyOtherWrong?.let {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Why others wrong: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    currentIndex++
                                    selectedOption = null
                                    showAnswer = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (currentIndex == filtered.size - 1) "Finish" else "Next Question") }
                        }
                    }
                }
            }
        }
    }
}
