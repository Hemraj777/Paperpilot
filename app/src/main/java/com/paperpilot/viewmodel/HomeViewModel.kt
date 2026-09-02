package com.paperpilot.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperpilot.PaperpilotApp
import com.paperpilot.data.entity.PdfDocument
import com.paperpilot.data.entity.Question
import com.paperpilot.data.repository.PdfRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val db = PaperpilotApp.instance.database
    private val repo = PdfRepository(PaperpilotApp.instance, db)

    val pdfs: StateFlow<List<PdfDocument>> = repo.getAllPdfs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questions: StateFlow<List<Question>> = repo.getAllQuestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _lastExtraction = MutableStateFlow<String?>(null)
    val lastExtraction: StateFlow<String?> = _lastExtraction

    fun addPdf(uri: Uri, subject: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repo.addPdf(uri, subject)
            _isLoading.value = false
            // Load debug info
            try {
                val prefs = PaperpilotApp.instance.getSharedPreferences("paperpilot_debug", android.content.Context.MODE_PRIVATE)
                val len = prefs.getInt("last_extraction_len", 0)
                val quality = prefs.getInt("last_extraction_quality", 0)
                val isScanned = prefs.getBoolean("last_is_scanned", false)
                val preview = prefs.getString("last_extraction_text", "")?.take(300) ?: ""
                _lastExtraction.value = prefs.getString("last_extraction_text", null)
                val qualityMsg = when {
                    quality >= 60 -> "Excellent"
                    quality >= 45 -> "Good"
                    quality >= 30 -> "Fair"
                    else -> "Poor (handwritten? Set Gemini key in Settings)"
                }
                _message.value = res.fold(
                    onSuccess = { "Added ${it.fileName} • ${it.pageCount} pages • $len chars ($qualityMsg, q=$quality, scanned=$isScanned). Preview: ${preview.take(80)}..." },
                    onFailure = { "Failed: ${it.message}" }
                )
            } catch (e: Exception) {
                _message.value = res.fold(
                    onSuccess = { "Added ${it.fileName} • ${it.pageCount} pages" },
                    onFailure = { "Failed: ${it.message}" }
                )
            }
        }
    }

    fun getLastExtraction(): String? {
        return try {
            PaperpilotApp.instance.getSharedPreferences("paperpilot_debug", android.content.Context.MODE_PRIVATE)
                .getString("last_extraction_text", null)
        } catch (_: Exception) { null }
    }

    fun generateQuiz(pdfId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repo.generateQuizForPdf(pdfId)
            _isLoading.value = false
            _message.value = res.fold(
                onSuccess = { "Generated ${it.size} questions!" },
                onFailure = { "Failed: ${it.message}" }
            )
        }
    }

    fun deletePdf(id: Long) {
        viewModelScope.launch { repo.deletePdf(id) }
    }

    fun toggleSelection(id: Long, selected: Boolean) {
        viewModelScope.launch { repo.toggleSelection(id, selected) }
    }

    fun markAnswer(questionId: Long, correct: Boolean) {
        viewModelScope.launch { repo.markAnswer(questionId, correct) }
    }

    fun clearMessage() { _message.value = null }
}
