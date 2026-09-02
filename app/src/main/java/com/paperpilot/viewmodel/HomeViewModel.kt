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

    fun addPdf(uri: Uri, subject: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repo.addPdf(uri, subject)
            _isLoading.value = false
            _message.value = res.fold(
                onSuccess = { "Added ${it.fileName} • ${it.pageCount} pages" },
                onFailure = { "Failed: ${it.message}" }
            )
        }
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
