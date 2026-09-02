package com.paperpilot.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.paperpilot.data.dao.PdfDao
import com.paperpilot.data.dao.QuestionDao
import com.paperpilot.data.entity.PdfDocument
import com.paperpilot.data.entity.Question

@Database(
    entities = [PdfDocument::class, Question::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao
    abstract fun questionDao(): QuestionDao
}
