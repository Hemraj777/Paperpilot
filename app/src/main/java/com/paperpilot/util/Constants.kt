package com.paperpilot.util

object Constants {
    const val PREF_SELECTED_PDFS = "selected_pdfs"
    const val PREF_GEMINI_KEY = "gemini_key"
    const val PREF_WIDGET_QUESTION_ID = "widget_question_id"
    const val PREF_WIDGET_SHOW_ANSWER = "widget_show_answer"
    const val ACTION_NEXT_QUESTION = "com.paperpilot.NEXT_QUESTION"
    const val ACTION_SHOW_ANSWER = "com.paperpilot.SHOW_ANSWER"
    const val ACTION_ANSWER_PREFIX = "com.paperpilot.ANSWER_"
    const val EXTRA_QUESTION_ID = "question_id"
}

object Subjects {
    val list = listOf("Physics", "Chemistry", "Biology", "Zoology", "Botany", "Mathematics", "English", "General")
}
