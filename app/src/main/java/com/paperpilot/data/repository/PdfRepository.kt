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
    private val quizGenerator: QuizGenerator = QuizGenerator(getGeminiKey(context))
) {
    companion object {
        fun getGeminiKey(ctx: Context): String? {
            return try {
                ctx.getSharedPreferences("paperpilot_prefs", Context.MODE_PRIVATE)
                    .getString("gemini_key", null)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        }
    }
    fun getAllPdfs(): Flow<List<PdfDocument>> = db.pdfDao().getAllPdfs()
    fun getAllQuestions(): Flow<List<Question>> = db.questionDao().getAllQuestions()
    fun getQuestionsForPdf(pdfId: Long): Flow<List<Question>> = db.questionDao().getQuestionsForPdf(pdfId)

    suspend fun addPdf(uri: Uri, subject: String = "General"): Result<PdfDocument> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(uri) ?: "document.pdf"
            val fileSize = getFileSize(uri)
            val extraction = extractor.extractText(uri)
            // Save extraction debug info to prefs for preview
            try {
                context.getSharedPreferences("paperpilot_debug", Context.MODE_PRIVATE).edit()
                    .putString("last_extraction_text", extraction.text.take(8000))
                    .putInt("last_extraction_quality", extraction.quality)
                    .putInt("last_extraction_len", extraction.text.length)
                    .putBoolean("last_is_scanned", extraction.isScanned)
                    .apply()
            } catch (_: Exception) {}
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
            // auto-generate quiz if we have text - use quality threshold
            if (extraction.text.length > 300 && extraction.quality >= 25) {
                generateQuizForPdf(saved.id, extraction.text, subject, count = 10)
            } else if (extraction.text.length > 100) {
                // Still try but will produce fallback with warning; better than nothing
                generateQuizForPdf(saved.id, extraction.text, subject, count = 8)
            }
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateQuizForPdf(pdfId: Long, text: String? = null, subject: String = "General", count: Int = 10): Result<List<Question>> = withContext(Dispatchers.IO) {
        try {
            val pdf = db.pdfDao().getPdfById(pdfId) ?: return@withContext Result.failure(Exception("PDF not found"))
            val rawContent = text ?: extractor.extractText(Uri.parse(pdf.fileUri)).text
            val cleanedForQuiz = extractor.filterWatermark(rawContent)
            val quality = extractor.scoreExtraction(cleanedForQuiz)
            if (cleanedForQuiz.length < 80) return@withContext Result.failure(Exception("Not enough text extracted (only ${cleanedForQuiz.length} chars, quality $quality). For CamScanner handwritten, add Gemini API key in Settings or upload clearer scan."))
            if (quality < 20) {
                // Still generate but warn - quiz will indicate low quality
                android.util.Log.w("PdfRepository", "Low quality extraction $quality, len ${cleanedForQuiz.length}")
            }
            // Recreate generator with latest Gemini key (in case user just set it)
            val freshGenerator = QuizGenerator(getGeminiKey(context))
            val questions = freshGenerator.generateQuestions(cleanedForQuiz, subject, pdfId, count)
            if (questions.isEmpty()) return@withContext Result.failure(Exception("Failed to generate questions from content"))
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
