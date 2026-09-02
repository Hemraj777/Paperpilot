package com.paperpilot.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paperpilot.viewmodel.HomeViewModel
import com.paperpilot.util.Subjects

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenQuiz: (Long) -> Unit,
    onOpenPdf: (Long) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val pdfs by vm.pdfs.collectAsState()
    val questions by vm.questions.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val message by vm.message.collectAsState()
    var selectedSubject by remember { mutableStateOf(Subjects.list[0]) }
    var showSubjectPicker by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingUri = it; showSubjectPicker = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paperpilot", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "image/*")) }) {
                Icon(Icons.Default.Add, contentDescription = "Upload")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Upload. Learn. Remember.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                        Text("${pdfs.size} PDFs • ${questions.size} Questions generated", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                        if (isLoading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        message?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Your PDFs", style = MaterialTheme.typography.titleMedium)
                    AssistChip(onClick = { launcher.launch(arrayOf("application/pdf")) }, label = { Text("Add PDF") }, leadingIcon = { Icon(Icons.Default.Add, null) })
                }
            }
            if (pdfs.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Help, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No PDFs yet", style = MaterialTheme.typography.titleMedium)
                            Text("Upload your CEE notes, textbooks or scanned handwritten notes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(pdfs) { pdf ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenPdf(pdf.id) }, elevation = CardDefaults.cardElevation(2.dp)) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pdf.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${pdf.subject} • ${pdf.pageCount} pages • ${pdf.fileSize / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = pdf.isSelectedForQuiz, onCheckedChange = { vm.toggleSelection(pdf.id, it) })
                                    Text("Use for widget", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                IconButton(onClick = { vm.generateQuiz(pdf.id) }) { Icon(Icons.Default.Help, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { vm.deletePdf(pdf.id) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
            item {
                Text("Recent Questions (${questions.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(questions.take(5)) { q ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenQuiz(q.pdfId) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(q.questionText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("A) ${q.optionA}  •  B) ${q.optionB}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Answer: ${q.correctOption} • ${q.subject}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showSubjectPicker && pendingUri != null) {
        AlertDialog(
            onDismissRequest = { showSubjectPicker = false },
            title = { Text("Select Subject") },
            text = {
                Column {
                    Text("Choose subject for this PDF (for widget filtering)")
                    Spacer(modifier = Modifier.height(12.dp))
                    Subjects.list.forEach { subj ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedSubject = subj }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedSubject == subj, onClick = { selectedSubject = subj })
                            Text(subj, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    pendingUri?.let { vm.addPdf(it, selectedSubject) }
                    showSubjectPicker = false
                    pendingUri = null
                }) { Text("Upload") }
            },
            dismissButton = { TextButton(onClick = { showSubjectPicker = false }) { Text("Cancel") } }
        )
    }
}
