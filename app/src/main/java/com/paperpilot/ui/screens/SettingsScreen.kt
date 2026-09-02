package com.paperpilot.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("paperpilot_prefs", android.content.Context.MODE_PRIVATE) }
    val debugPrefs = remember { context.getSharedPreferences("paperpilot_debug", android.content.Context.MODE_PRIVATE) }
    var geminiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
    var saved by remember { mutableStateOf(prefs.contains("gemini_key") && !prefs.getString("gemini_key","").isNullOrBlank()) }
    var showKey by remember { mutableStateOf(false) }

    val lastLen = debugPrefs.getInt("last_extraction_len", 0)
    val lastQuality = debugPrefs.getInt("last_extraction_quality", 0)
    val lastScanned = debugPrefs.getBoolean("last_is_scanned", false)

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("CamScanner Handwritten Fix", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("If your PDFs are CamScanner scans of HANDWRITTEN notes and you see only 'CamScanner' in extracted preview, MLKit cannot read handwriting well.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Text("✅ SOLUTION: Add Gemini API key below → enables Gemini Vision OCR (reads handwritten accurately, ignores watermark). Typed PDFs work offline without key.", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Text("How to get key: aistudio.google.com/app/apikey → Create API key → paste below. It's free.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Gemini API Key", style = MaterialTheme.typography.titleMedium)
                        if (saved) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("For high-quality CEE questions + handwritten CamScanner OCR. Leave blank for offline mock.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it; saved = false },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("AIza...") },
                        singleLine = true,
                        visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            prefs.edit().putString("gemini_key", geminiKey.trim()).apply()
                            saved = geminiKey.trim().isNotBlank()
                            Toast.makeText(context, if (saved) "Gemini key saved! Re-upload PDF for better OCR." else "Key cleared", Toast.LENGTH_SHORT).show()
                        }) { Text(if (saved) "Saved ✓" else "Save Key") }
                        if (saved) {
                            OutlinedButton(onClick = {
                                prefs.edit().remove("gemini_key").apply()
                                geminiKey = ""
                                saved = false
                                Toast.makeText(context, "Key removed", Toast.LENGTH_SHORT).show()
                            }) { Text("Remove") }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (saved) "✓ Gemini Vision will now transcribe CamScanner handwritten notes accurately." else "⚠️ No key: CamScanner handwritten may show only watermark. Add key or use typed PDFs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Last Extraction Debug", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Length: $lastLen chars", style = MaterialTheme.typography.bodyMedium)
                    Text("Quality: $lastQuality / 100 ${when { lastQuality >= 60 -> "Excellent ✓"; lastQuality >= 45 -> "Good"; lastQuality >= 30 -> "Fair"; lastQuality > 0 -> "Poor ✗"; else -> "No data" }}", style = MaterialTheme.typography.bodyMedium)
                    Text("Type: ${if (lastScanned) "Scanned (OCR)" else "Typed (PDFBox)"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    val preview = debugPrefs.getString("last_extraction_text", null)?.take(500) ?: "No extraction yet. Upload a PDF on Home."
                    Text("Preview: ${preview.take(300)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (preview.contains("CamScanner", ignoreCase = true) && preview.length < 800) {
                        Text("⚠️ Detected watermark-only extraction. Handwritten needs Gemini key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Widget Help", style = MaterialTheme.typography.titleMedium)
                    Text("Shows real question from your selected PDFs. Flip to see answer.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Long press home → Widgets → Paperpilot Quiz → Add\n• Select PDFs in app → check 'Use for widget'\n• Widget shows question + A/B/C/D\n• Tap 👁 Show Answer to flip\n• Tap ↻ Next for new random question\n• Updates every 30min automatically", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tips for Best Results", style = MaterialTheme.typography.titleMedium)
                    Text("• For CamScanner: scan at HIGH quality, good lighting, avoid shadows\n• Handwritten: use dark pen, clear writing, or set Gemini key\n• Typed PDFs: ensure text is selectable (not image)\n• Check 'View Extracted' after upload to verify reading\n• If preview shows 'CamScanner' only, re-scan or add Gemini key", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Text("Paperpilot v1.0.6 (fixed)\nFor CEE Students • Content-aware quiz + live widget\nOpen source: github.com/Hemraj777/Paperpilot", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
