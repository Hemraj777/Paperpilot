package com.paperpilot.service

import android.util.Log
import com.paperpilot.data.entity.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

class QuizGenerator(
    private val geminiApiKey: String? = null
) {
    // Resolve effective key: constructor param > Settings prefs > hardcoded free demo key
    private fun effectiveKey(): String? {
        if (!geminiApiKey.isNullOrBlank()) return geminiApiKey
        // Try hardcoded free demo key
        val demo = com.paperpilot.util.ApiKeys.DEFAULT_GEMINI_KEY
        if (demo.isNotBlank()) return demo
        return null
    }

    suspend fun generateQuestions(
        text: String,
        subject: String,
        pdfId: Long,
        count: Int = 10
    ): List<Question> = withContext(Dispatchers.IO) {
        val key = effectiveKey()
        if (key.isNullOrBlank()) {
            throw IllegalStateException("AI key missing: Add your FREE Gemini API key in Settings → Gemini API Key (get in 30 sec at aistudio.google.com/app/apikey). AI-only mode - no offline fallback.")
        }
        if (text.length < 200) {
            throw IllegalStateException("Not enough text extracted (${text.length} chars). For CamScanner, ensure High-quality scan + Gemini key in Settings, or upload typed PDF. Check View Extracted preview.")
        }
        if (text.length < 500) {
            Log.w("QuizGenerator", "Short text ${text.length}, but proceeding with AI")
        }
        // AI-ONLY: always use Gemini for complex CEE questions
        val aiQuestions = try {
            generateViaGemini(text, subject, count)
        } catch (e: Exception) {
            Log.e("QuizGenerator", "Gemini failed: ${e.message}", e)
            throw IllegalStateException("AI generation failed: ${e.message}. Check internet + valid Gemini key. No offline fallback in AI-only mode.")
        }
        if (aiQuestions.isEmpty()) {
            throw IllegalStateException("AI returned no questions. Try clearer PDF or different subject. Check View Extracted shows real content, not watermark.")
        }
        Log.d("QuizGenerator", "Gemini generated ${aiQuestions.size} complex CEE questions via AI")
        aiQuestions.map { it.copy(pdfId = pdfId, subject = subject) }
    }

    private fun generateContentAwareMock(text: String, subject: String, pdfId: Long, count: Int): List<Question> {
        // Step 1: Deep sentence extraction and cleaning
        val rawSentences = text
            .replace("\r", " ")
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length in 40..400 }
            .filter { it.count { c -> c.isLetter() } > it.length * 0.6 } // mostly letters, not garbled
            .filterNot { it.contains("CamScanner", ignoreCase = true) }
            .filterNot { it.contains("Scanned", ignoreCase = true) && it.length < 80 }
            .distinct()
            .shuffled(Random(System.currentTimeMillis()))

        if (rawSentences.isEmpty()) {
            return (0 until count).map { i ->
                Question(
                    pdfId = pdfId, subject = subject,
                    questionText = "Q${i+1} ($subject): Which statement best describes concept ${i+1} from your uploaded document?",
                    optionA = "Correct concept from document section ${i+1}",
                    optionB = "Misinterpreted concept A",
                    optionC = "Incomplete understanding B",
                    optionD = "Factually incorrect C",
                    correctOption = listOf("A","B","C","D").random(),
                    explanation = "This was a fallback due to insufficient text extraction. Please re-upload a clearer PDF.",
                    whyOtherWrong = "Other options are distractors not supported by document.",
                    sourcePage = (i % 5) + 1,
                    difficulty = "Medium"
                )
            }
        }

        // Extract keywords pool for plausible distractors
        val keywordPool = rawSentences.flatMap { extractKeywords(it) }.distinct().shuffled().take(50)

        val questions = mutableListOf<Question>()
        var sentenceIdx = 0

        for (i in 0 until count) {
            val sentence = rawSentences[sentenceIdx % rawSentences.size]
            sentenceIdx++

            // Analyze sentence complexity and type
            val (qType, difficulty) = classifySentence(sentence)
            val page = ((sentenceIdx.toFloat() / rawSentences.size) * 15).toInt().coerceIn(1, 30)

            val qa = when (qType) {
                "definition" -> generateDefinitionQuestion(sentence, keywordPool, subject, i)
                "cause_effect" -> generateCauseEffectQuestion(sentence, keywordPool, subject, i)
                "comparison" -> generateComparisonQuestion(sentence, keywordPool, subject, i)
                "list" -> generateListQuestion(sentence, keywordPool, subject, i)
                "numerical" -> generateNumericalQuestion(sentence, keywordPool, subject, i)
                else -> generateInferenceQuestion(sentence, keywordPool, subject, i)
            }

            questions.add(
                qa.copy(
                    pdfId = pdfId,
                    subject = subject,
                    sourcePage = page,
                    difficulty = difficulty
                )
            )
        }
        return questions
    }

    private fun classifySentence(sentence: String): Pair<String, String> {
        val lower = sentence.lowercase()
        val wordCount = sentence.split(" ").size
        val difficulty = when {
            wordCount > 30 || lower.contains("equation") || lower.contains("formula") || lower.contains("%") -> "Hard"
            wordCount > 18 -> "Medium"
            else -> "Easy"
        }
        val type = when {
            lower.contains(Regex("\\b(is|are|means|defined as|called|refers to|known as)\\b")) -> "definition"
            lower.contains(Regex("\\b(because|due to|caused|leads to|results in|therefore|hence|consequence)\\b")) -> "cause_effect"
            lower.contains(Regex("\\b(compared|difference|than|versus|while|whereas|contrast|similar)\\b")) -> "comparison"
            lower.contains(Regex("\\b(include|consists|contains|types|examples|properties|characteristics|classified)\\b")) -> "list"
            lower.contains(Regex("\\d+")) && (lower.contains("%") || lower.contains("=") || lower.contains("value") || lower.contains("number")) -> "numerical"
            else -> "inference"
        }
        return type to difficulty
    }

    private fun extractKeywords(sentence: String): List<String> {
        val stopwords = setOf("the","is","are","was","were","be","been","being","a","an","and","or","but","in","on","at","to","for","of","with","as","by","from","that","this","these","those","it","its","it","has","have","had","will","would","can","could","should","may","might")
        return sentence.split(Regex("[^a-zA-Z0-9]+"))
            .map { it.trim() }
            .filter { it.length > 3 && it.lowercase() !in stopwords }
            .map { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            .distinct()
            .take(4)
    }

    private fun makeDistractors(correct: String, pool: List<String>, count: Int = 3): List<String> {
        val distractors = pool.filter { it.lowercase() != correct.lowercase() }.shuffled().take(count).toMutableList()
        // Make them plausible by adding variations
        while (distractors.size < count) {
            distractors.add(listOf("None of the above", "All of the above", "Data insufficient", "Contradicts the document")[distractors.size % 4])
        }
        // Add slight mutations to make harder
        return distractors.mapIndexed { idx, d ->
            when (idx) {
                0 -> d
                1 -> "Partially correct but incomplete: $d"
                2 -> "Misconception: $d is not supported by document"
                else -> d
            }
        }
    }

    private fun shuffleOptions(correct: String, distractors: List<String>): Pair<List<String>, String> {
        val all = (distractors + correct).shuffled()
        val correctIdx = all.indexOf(correct)
        val correctLetter = listOf("A","B","C","D")[correctIdx]
        return all to correctLetter
    }

    private fun generateDefinitionQuestion(sentence: String, pool: List<String>, subject: String, idx: Int): Question {
        // Extract term before "is/are" as focus
        val term = sentence.split(Regex("\\b(is|are|means|defined as)\\b"), limit = 2).firstOrNull()?.trim()?.take(60) ?: "the concept"
        val cleanTerm = term.split(",").first().take(50)
        val correct = sentence.take(120).trim()
        val distractors = makeDistractors(correct.take(30), pool)
        val (options, correctLetter) = shuffleOptions(correct.take(100), distractors)
        return Question(
            pdfId = 0, subject = subject,
            questionText = "Q${idx+1} ($subject - Definition): According to your document, how is \"$cleanTerm\" defined? ${complexifyPrompt(subject)}",
            optionA = options.getOrElse(0){""}, optionB = options.getOrElse(1){""}, optionC = options.getOrElse(2){""}, optionD = options.getOrElse(3){""},
            correctOption = correctLetter,
            explanation = "Document states: \"$sentence\". This directly defines \"$cleanTerm\".",
            whyOtherWrong = "Other options either describe different terms from the document or introduce external information not in the uploaded content (e.g., \"${distractors.firstOrNull()}\").",
            difficulty = "Hard"
        )
    }

    private fun generateCauseEffectQuestion(sentence: String, pool: List<String>, subject: String, idx: Int): Question {
        val causePart = sentence.split(Regex("\\b(because|due to|caused by|as a result|therefore|hence)\\b"), limit = 2).firstOrNull()?.trim()?.take(80) ?: sentence.take(80)
        val correct = sentence.take(130)
        val distractors = makeDistractors(correct.take(30), pool)
        val (options, correctLetter) = shuffleOptions(correct.take(110), distractors)
        return Question(
            pdfId = 0, subject = subject,
            questionText = "Q${idx+1} ($subject - Cause & Effect): Based on your uploaded PDF, what is the cause/effect relationship described in: \"${causePart}...\"?",
            optionA = options[0], optionB = options[1], optionC = options[2], optionD = options[3],
            correctOption = correctLetter,
            explanation = "The PDF explicitly states: \"$sentence\". This establishes the cause-effect link.",
            whyOtherWrong = "Distractors reverse the causality or attribute it to concepts not mentioned on this page.",
            difficulty = "Hard"
        )
    }

    private fun generateComparisonQuestion(sentence: String, pool: List<String>, subject: String, idx: Int): Question {
        val correct = sentence.take(130)
        val distractors = makeDistractors(correct.take(30), pool)
        val (options, correctLetter) = shuffleOptions(correct.take(110), distractors)
        return Question(
            pdfId = 0, subject = subject,
            questionText = "Q${idx+1} ($subject - Compare & Contrast): Your document compares concepts as: \"${sentence.take(90)}...\". Which statement is ACCURATE per the document?",
            optionA = options[0], optionB = options[1], optionC = options[2], optionD = options[3],
            correctOption = correctLetter,
            explanation = "Direct quote: \"$sentence\". The comparison is explicit.",
            whyOtherWrong = "Other options swap the compared entities or misstate the relationship.",
            difficulty = "Medium"
        )
    }

    private fun generateListQuestion(sentence: String, pool: List<String>, subject: String, idx: Int): Question {
        val listFragment = sentence.take(100)
        val correct = sentence.take(130)
        val distractors = makeDistractors(listFragment.take(30), pool)
        val (options, correctLetter) = shuffleOptions(correct, distractors)
        return Question(
            pdfId = 0, subject = subject,
            questionText = "Q${idx+1} ($subject - List/Types): The PDF lists: \"$listFragment...\" Which option correctly reflects the document's list?",
            optionA = options[0], optionB = options[1], optionC = options[2], optionD = options[3],
            correctOption = correctLetter,
            explanation = "Full context: \"$sentence\". Only the correct option matches all items from the list.",
            whyOtherWrong = "Others omit items or include types not listed in your PDF.",
            difficulty = "Medium"
        )
    }

    private fun generateNumericalQuestion(sentence: String, pool: List<String>, subject: String, idx: Int): Question {
        val number = Regex("\\d+[.,]?\\d*%?").find(sentence)?.value ?: "value"
        val correct = sentence.take(130)
        // Create numerical distractors by varying the number
        val distractors = listOf(
            correct.replace(number, (number.toDoubleOrNull()?.let { it * 1.5 }?.toString() ?: "incorrect value")),
            pool.getOrElse(0){ "Alternative value not in document" },
            "Not mentioned in document"
        ).take(3)
        val (options, correctLetter) = shuffleOptions(correct.take(110), distractors)
        return Question(
            pdfId = 0, subject = subject,
            questionText = "Q${idx+1} ($subject - Numerical/Factual): Your PDF mentions \"$number\" in: \"${sentence.take(80)}...\". What is correct per the document?",
            optionA = options[0], optionB = options[1], optionC = options[2], optionD = options[3],
            correctOption = correctLetter,
            explanation = "Exact statement: \"$sentence\".",
            whyOtherWrong = "Numerical distractors are off by factor or unit; check document page for exact figure.",
            difficulty = "Hard"
        )
    }

    private fun generateInferenceQuestion(sentence: String, pool: List<String>, subject: String, idx: Int): Question {
        val triggers = listOf(
            "What can be INFERRED from the document: \"${sentence.take(90)}...\"?",
            "Which statement is TRUE according to the uploaded PDF excerpt: \"${sentence.take(80)}...\"?",
            "Based on \"${sentence.take(85)}...\" what would be the most logical conclusion for CEE?",
            "Your notes state: \"${sentence.take(90)}...\" — Which application follows?"
        )
        val prompt = triggers.random()
        val correct = sentence.take(130)
        // For inference, distractors should be plausible but subtly wrong
        val distractors = makeDistractors(correct.take(35), pool).map { "Inference: $it (not supported)" }
        val (options, correctLetter) = shuffleOptions(correct.take(110), distractors)
        return Question(
            pdfId = 0, subject = subject,
            questionText = "Q${idx+1} ($subject - Inference/Analysis): $prompt",
            optionA = options[0], optionB = options[1], optionC = options[2], optionD = options[3],
            correctOption = correctLetter,
            explanation = "Direct evidence: \"$sentence\". This is stated, while others require assumptions not in PDF.",
            whyOtherWrong = "Distractors are common CEE traps - they sound correct but aren't in your uploaded material.",
            difficulty = if (sentence.length > 120) "Hard" else "Medium"
        )
    }

    private fun String.toDoubleOrNull(): Double? = try { this.replace("%","").replace(",","").toDouble() } catch (_:Exception) { null }

    private fun complexifyPrompt(subject: String): String {
        return when (Random.nextInt(3)) {
            0 -> "(CEE: Remember + Understand level)"
            1 -> "(CEE: Apply the concept)"
            else -> "(Choose the MOST accurate)"
        }
    }

    private fun generateViaGemini(text: String, subject: String, count: Int): List<Question> {
        val prompt = buildPrompt(text, subject, count)
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiApiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 35000
        }
        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))))
            put("generationConfig", JSONObject().put("temperature", 0.75).put("maxOutputTokens", 8192))
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val response = if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().readText() else conn.errorStream?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        if (response.isBlank()) return emptyList()
        return parseGeminiResponse(response)
    }

    private fun buildPrompt(text: String, subject: String, count: Int): String {
        val truncated = text.take(15000)
        return """
You are an ELITE CEE (Common Entrance Exam Nepal - MBBS/BE) paper setter for $subject. You MUST create $count HIGH-COMPLEXITY, CONTENT-LOCKED MCQs ONLY from the DOCUMENT below. No outside knowledge.

STRICT RULES:
- Use ONLY facts, sentences, numbers, definitions from DOCUMENT. Cite page estimate.
- Bloom's levels: 30% Remember, 30% Understand, 25% Apply, 15% Analyze. Make them TRICKY (CEE trap options).
- Each MCQ: stem must quote or paraphrase DOCUMENT, 4 options (A-D) plausible, 1 correct, 3 distractors from DOCUMENT's other parts (not random).
- Distractors must be subtle: e.g., swapped values, reversed cause-effect, half-true.
- Provide deep explanation: why correct is correct with DOCUMENT quote, and why EACH other option is wrong.
- Difficulty: Hard for 12th-grade CEE.
- Return ONLY valid JSON array, no markdown, no extra text.

FORMAT (strict):
[
  {
    "question": "Complex stem referencing document ...?",
    "optionA": "...",
    "optionB": "...",
    "optionC": "...",
    "optionD": "...",
    "correct": "A|B|C|D",
    "explanation": "Document says '...' so ...",
    "whyOtherWrong": "B is wrong because document says ...; C is ...; D is ...",
    "page": 3,
    "difficulty": "Hard"
  }
]

DOCUMENT (uploaded PDFs, OCR-cleaned):
$truncated
""".trimIndent()
    }

    private fun parseGeminiResponse(response: String): List<Question> {
        return try {
            val json = JSONObject(response)
            if (!json.has("candidates")) return emptyList()
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) return emptyList()
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text")
            val start = text.indexOf("[")
            val end = text.lastIndexOf("]")
            if (start == -1 || end == -1) return emptyList()
            val arrayStr = text.substring(start, end + 1)
            val arr = org.json.JSONArray(arrayStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Question(
                    pdfId = 0,
                    questionText = o.getString("question"),
                    optionA = o.getString("optionA"),
                    optionB = o.getString("optionB"),
                    optionC = o.getString("optionC"),
                    optionD = o.getString("optionD"),
                    correctOption = o.getString("correct"),
                    explanation = o.optString("explanation", ""),
                    whyOtherWrong = o.optString("whyOtherWrong", ""),
                    sourcePage = o.optInt("page", 1),
                    difficulty = o.optString("difficulty","Hard")
                )
            }
        } catch (e: Exception) {
            Log.e("QuizGenerator", "Parse failed: ${e.message}")
            emptyList()
        }
    }
}
