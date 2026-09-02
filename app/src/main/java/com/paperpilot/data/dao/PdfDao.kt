package com.paperpilot.data.dao

import androidx.room.*
import com.paperpilot.data.entity.PdfDocument
import com.paperpilot.data.entity.Question
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_documents ORDER BY uploadDate DESC")
    fun getAllPdfs(): Flow<List<PdfDocument>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    suspend fun getPdfById(id: Long): PdfDocument?

    @Query("SELECT * FROM pdf_documents WHERE isSelectedForQuiz = 1")
    suspend fun getSelectedPdfs(): List<PdfDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: PdfDocument): Long

    @Delete
    suspend fun deletePdf(pdf: PdfDocument)

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deletePdfById(id: Long)

    @Query("UPDATE pdf_documents SET isSelectedForQuiz = :selected WHERE id = :id")
    suspend fun updateSelection(id: Long, selected: Boolean)

    @Query("UPDATE pdf_documents SET subject = :subject WHERE id = :id")
    suspend fun updateSubject(id: Long, subject: String)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    fun getAllQuestions(): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE pdfId = :pdfId ORDER BY createdAt DESC")
    fun getQuestionsForPdf(pdfId: Long): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE pdfId IN (:pdfIds) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestions(pdfIds: List<Long>, limit: Int = 10): List<Question>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionById(id: Long): Question?

    @Query("SELECT * FROM questions ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomQuestion(): Question?

    @Query("SELECT * FROM questions WHERE pdfId IN (:pdfIds) ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomQuestionForPdfs(pdfIds: List<Long>): Question?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: Question): Long

    @Query("DELETE FROM questions WHERE pdfId = :pdfId")
    suspend fun deleteQuestionsForPdf(pdfId: Long)

    @Query("UPDATE questions SET isAnswered = 1, lastAnswerCorrect = :correct, timesSeen = timesSeen + 1, timesCorrect = timesCorrect + :incCorrect WHERE id = :id")
    suspend fun markAnswered(id: Long, correct: Boolean, incCorrect: Int)

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun count(): Int

    @Query("SELECT * FROM questions WHERE lastAnswerCorrect = 0 ORDER BY RANDOM() LIMIT 1")
    suspend fun getWrongQuestion(): Question?
}
