package com.paperpilot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileUri: String,
    val fileSize: Long = 0,
    val subject: String = "General",
    val pageCount: Int = 0,
    val extractedTextLength: Int = 0,
    val uploadDate: Long = System.currentTimeMillis(),
    val isSelectedForQuiz: Boolean = true
)

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pdfId: Long,
    val subject: String = "General",
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOption: String, // A/B/C/D
    val explanation: String,
    val whyOtherWrong: String? = null,
    val sourcePage: Int? = null,
    val difficulty: String = "Medium", // Easy/Medium/Hard
    val isAnswered: Boolean = false,
    val lastAnswerCorrect: Boolean? = null,
    val timesSeen: Int = 0,
    val timesCorrect: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
