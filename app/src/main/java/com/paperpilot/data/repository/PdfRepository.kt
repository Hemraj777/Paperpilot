package com.paperpilot.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.paperpilot.data.AppDatabase
import com.paperpilot.data.entity.PdfDocument
import com.paperpilot.data.entity.Question
import com.paperpilot.service.PdfTextExtractor
import com.paperpilot.service.QuizGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PdfRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val extractor: PdfTextExtractor = PdfTextExtractor(context),
    private val quizGenerator: QuizGenerator = QuizGenerator()
) {
    fun getAllPdfs(): Flow<List<PdfDocument>> = db.pdfDao().getAllPdfs()
    fun getAllQuestions(): Flow<List<Question>> = db.questionDao().getAllQuestions()
    fun getQuestionsForPdf(pdfId: Long): Flow<List<Question>> = db.questionDao().getQuestionsForPdf(pdfId)

    suspend fun addPdf(uri: Uri, subject: String = "General"): Result<PdfDocument> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(uri) ?: "document.pdf"
            val fileSize = getFileSize(uri)
            val extraction = extractor.extractText(uri)
            val doc = PdfDocument(
                fileName = fileName,
                fileUri = uri.toString(),
                fileSize = fileSize,
                subject = subject,
                pageCount = extraction.pageCount,
                extractedTextLength = extraction.text.length
            )
            val id = db.pdfDao().insertPdf(doc)
            val saved = doc.copy(id = id)
            // auto-generate quiz if we have text
            if (extraction.text.length > 200) {
                generateQuizForPdf(saved.id, extraction.text, subject)
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateQuizForPdf(pdfId: Long, text: String? = null, subject: String = "General", count: Int = 8): Result<List<Question>> = withContext(Dispatchers.IO) {
        try {
            val pdf = db.pdfDao().getPdfById(pdfId) ?: return@withContext Result.failure(Exception("PDF not found"))
            val content = text ?: extractor.extractText(Uri.parse(pdf.fileUri)).text
            if (content.length < 100) return@withContext Result.failure(Exception("Not enough text extracted"))
            val questions = quizGenerator.generateQuestions(content, subject, pdfId, count)
            db.questionDao().insertQuestions(questions)
            Result.success(questions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePdf(id: Long) = withContext(Dispatchers.IO) {
        db.questionDao().deleteQuestionsForPdf(id)
        db.pdfDao().deletePdfById(id)
    }

    suspend fun toggleSelection(id: Long, selected: Boolean) {
        db.pdfDao().updateSelection(id, selected)
    }

    suspend fun markAnswer(questionId: Long, correct: Boolean) {
        db.questionDao().markAnswered(questionId, correct, if (correct) 1 else 0)
    }

    private fun getFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) return cursor.getString(nameIndex)
        }
        return uri.lastPathSegment
    }

    private fun getFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) return cursor.getLong(sizeIndex)
        }
        return 0
    }
}
