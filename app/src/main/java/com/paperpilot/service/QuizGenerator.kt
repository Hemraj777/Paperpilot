package com.paperpilot.service

import com.paperpilot.data.entity.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

class QuizGenerator(
    private val geminiApiKey: String? = null // set via BuildConfig or Settings
) {

    suspend fun generateQuestions(
        text: String,
        subject: String,
        pdfId: Long,
        count: Int = 8
    ): List<Question> = withContext(Dispatchers.IO) {
        // Try Gemini if key provided, else fallback to mock
        if (!geminiApiKey.isNullOrBlank()) {
            try {
                val aiQuestions = generateViaGemini(text, subject, count)
                if (aiQuestions.isNotEmpty()) {
                    return@withContext aiQuestions.map { it.copy(pdfId = pdfId, subject = subject) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        generateMockQuestions(text, subject, pdfId, count)
    }

    private fun generateMockQuestions(text: String, subject: String, pdfId: Long, count: Int): List<Question> {
        val sentences = text.split(".", "\n").map { it.trim() }.filter { it.length > 30 }.shuffled().take(count * 2)
        val templates = listOf(
            Triple("What is the primary concept discussed in: \"%s\"?", "Core concept", "Related idea"),
            Triple("Which statement is TRUE regarding \"%s\"?", "Correct interpretation", "Misinterpretation"),
            Triple("According to the document, \"%s\" implies?", "Accurate implication", "Incorrect inference")
        )
        return (0 until count).map { i ->
            val sentence = sentences.getOrElse(i) { "Sample content for $subject - concept ${i + 1}" }.take(120)
            val template = templates.random()
            val correct = "A"
            val opts = listOf(
                template.second + " of the topic",
                template.third + " A",
                template.third + " B",
                "None of the above"
            ).shuffled(Random(i))

            // Ensure A is correct after shuffle – map correctly
            val correctIndex = Random.nextInt(4)
            val options = MutableList(4) { idx -> if (idx == correctIndex) template.second else opts[idx] }
            // simpler: fixed mock
            Question(
                pdfId = pdfId,
                subject = subject,
                questionText = "Q${i + 1}: ${template.first.format(sentence)}",
                optionA = if (correctIndex == 0) template.second else "Distractor A-${i}",
                optionB = if (correctIndex == 1) template.second else "Distractor B-${i}",
                optionC = if (correctIndex == 2) template.second else "Distractor C-${i}",
                optionD = if (correctIndex == 3) template.second else "Distractor D-${i}",
                correctOption = listOf("A", "B", "C", "D")[correctIndex],
                explanation = "The document states: \"$sentence\". Hence option ${listOf("A","B","C","D")[correctIndex]} is correct.",
                whyOtherWrong = "Other options misinterpret the context or are out of scope.",
                sourcePage = Random.nextInt(1, 10),
                difficulty = listOf("Easy", "Medium", "Hard").random()
            )
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
            readTimeout = 30000
        }
        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().put(JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))))
            put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 4096))
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return parseGeminiResponse(response)
    }

    private fun buildPrompt(text: String, subject: String, count: Int): String {
        val truncated = text.take(12000)
        return """
You are a CEE entrance exam expert for $subject. Generate $count MCQs from the DOCUMENT below.
Rules:
- Each MCQ must have 4 options (A,B,C,D), single correct.
- Provide explanation why correct is correct AND why other 3 are wrong.
- Provide source page estimate 1-10.
- Return ONLY valid JSON array. No markdown.

FORMAT:
[
  {
    "question": "...",
    "optionA": "...",
    "optionB": "...",
    "optionC": "...",
    "optionD": "...",
    "correct": "A|B|C|D",
    "explanation": "...",
    "whyOtherWrong": "...",
    "page": 1
  }
]

DOCUMENT:
$truncated
""".trimIndent()
    }

    private fun parseGeminiResponse(response: String): List<Question> {
        return try {
            val json = JSONObject(response)
            val candidates = json.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text")
            // Extract JSON array from text (may have ```json)
            val start = text.indexOf("[")
            val end = text.lastIndexOf("]")
            if (start == -1 || end == -1) return emptyList()
            val arrayStr = text.substring(start, end + 1)
            val arr = org.json.JSONArray(arrayStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Question(
                    pdfId = 0, // will be replaced
                    questionText = o.getString("question"),
                    optionA = o.getString("optionA"),
                    optionB = o.getString("optionB"),
                    optionC = o.getString("optionC"),
                    optionD = o.getString("optionD"),
                    correctOption = o.getString("correct"),
                    explanation = o.optString("explanation", ""),
                    whyOtherWrong = o.optString("whyOtherWrong", ""),
                    sourcePage = o.optInt("page", 1),
                    difficulty = "Medium"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
