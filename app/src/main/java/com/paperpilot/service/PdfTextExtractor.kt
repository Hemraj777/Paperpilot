package com.paperpilot.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ExtractionResult(val text: String, val pageCount: Int, val quality: Int = 0, val isScanned: Boolean = true)

class PdfTextExtractor(private val context: Context) {

    init {
        try { PDFBoxResourceLoader.init(context) } catch (_: Exception) {}
    }

    private fun getGeminiKey(): String? {
        return try {
            val prefs = context.getSharedPreferences("paperpilot_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getString("gemini_key", null)?.takeIf { it.isNotBlank() }
            if (!saved.isNullOrBlank()) return saved
            val demo = com.paperpilot.util.ApiKeys.DEFAULT_GEMINI_KEY
            if (demo.isNotBlank()) return demo
            null
        } catch (_: Exception) {
            val demo = com.paperpilot.util.ApiKeys.DEFAULT_GEMINI_KEY
            if (demo.isNotBlank()) demo else null
        }
    }

    // AI-ONLY: Fully AI-driven reading, no offline fallback
    suspend fun extractText(uri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        Log.d("PdfTextExtractor", "AI-ONLY extraction for $uri")
        val geminiKey = getGeminiKey()
        if (geminiKey.isNullOrBlank()) {
            Log.e("PdfTextExtractor", "No Gemini key for AI extraction")
            throw IllegalStateException("AI key missing: Add FREE Gemini key in Settings (aistudio.google.com/app/apikey) - AI-only mode requires key")
        }
        // AI Vision is the ONLY reader - handles typed, printed, handwritten, CamScanner all via Gemini
        val geminiResult = tryExtractWithGeminiVision(uri, geminiKey)
        val cleaned = filterWatermark(geminiResult.text)
        val quality = scoreExtraction(cleaned)
        Log.d("PdfTextExtractor", "Gemini Vision AI extracted ${cleaned.length} chars, quality $quality, pages ${geminiResult.pageCount}")

        if (cleaned.isBlank()) {
            throw IllegalStateException("AI could not read PDF (empty). Try clearer scan, ensure PDF has visible text, check internet.")
        }
        if (cleaned.length < 80) {
            Log.w("PdfTextExtractor", "AI extracted very short ${cleaned.length}, but returning for quiz attempt")
        }
        // Always return AI result, no fallback
        ExtractionResult(cleaned.take(120000), geminiResult.pageCount, quality, true)
    }

    fun filterWatermark(text: String): String {
        if (text.isBlank()) return ""
        val watermarkRegex = Regex("(?i)(cam\\s*scanner|scanned\\s*(with|by)|camscanner\\.com|\\bcs\\b.*scanner|shot\\s*on|scan\\s*document)")
        val lines = text.lines()
        val filtered = lines.map { it.trim() }.filter { it.isNotBlank() }
            .filterNot { watermarkRegex.containsMatchIn(it) && it.length < 80 }
            .filterNot { it.equals("CamScanner", ignoreCase = true) }
            .filterNot { it.equals("Scanner", ignoreCase = true) }
        val freq = filtered.groupingBy { it }.eachCount()
        val withoutRepeatedWatermark = filtered.filter { line ->
            val count = freq[line] ?: 0
            !(count > 3 && line.length < 40 && watermarkRegex.containsMatchIn(line))
        }
        val cleaned = withoutRepeatedWatermark.filter { it.length >= 3 }
        var joined = cleaned.joinToString("\n")
        joined = joined.replace(Regex("(?i)\\bCamScanner\\b[\\s\\-]*"), "")
        joined = joined.replace(Regex("(?i)Scanned\\s+with\\s+CamScanner"), "")
        joined = joined.replace(Regex("\\n{3,}"), "\n\n")
        return joined.trim()
    }

    fun scoreExtraction(text: String): Int {
        if (text.isBlank()) return 0
        if (text.length < 100) return 10
        val watermarks = Regex("(?i)cam\\s*scanner").findAll(text).count()
        val watermarkRatio = watermarks.toFloat() / (text.split(Regex("\\s+")).size.coerceAtLeast(1))
        if (watermarkRatio > 0.15) return 15
        val sentences = text.split(Regex("(?<=[.!?])\\s+|\\n+")).filter { it.trim().length > 30 }
        val wordCount = text.split(Regex("\\s+")).size
        val avgWordLen = text.filter { it.isLetter() }.length.toFloat() / wordCount.coerceAtLeast(1)
        val vowels = text.count { it.lowercaseChar() in "aeiou" }
        val letters = text.count { it.isLetter() }
        val vowelRatio = if (letters > 0) vowels.toFloat() / letters else 0f
        var score = 50
        if (sentences.size >= 8) score += 20 else if (sentences.size >= 4) score += 10 else score -= 10
        if (wordCount > 200) score += 10
        if (wordCount > 500) score += 10
        if (avgWordLen in 3.5..7.0) score += 5 else score -= 5
        if (vowelRatio in 0.35..0.55) score += 10 else score -= 10
        if (watermarkRatio > 0.05) score -= 15
        if (text.contains(Regex("[A-Z][a-z]+\\s+[A-Z][a-z]+"))) score += 5
        return score.coerceIn(0, 100)
    }

    private suspend fun tryExtractWithGeminiVision(uri: Uri, apiKey: String): ExtractionResult = withContext(Dispatchers.IO) {
        val pfd = try { context.contentResolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null }
            ?: throw IllegalStateException("Cannot open PDF file")
        val renderer = try { PdfRenderer(pfd) } catch (e: Exception) {
            try { pfd.close() } catch (_: Exception) {}
            throw IllegalStateException("Invalid PDF: ${e.message}")
        }
        val pageCount = renderer.pageCount
        // AI reads max 8 pages (CEE PDFs usually 10-20, but 8 is balance of quota vs coverage)
        val maxPages = minOf(pageCount, 8)
        val sb = StringBuilder()
        var lastError: String? = null
        try {
            for (i in 0 until maxPages) {
                val page = try { renderer.openPage(i) } catch (e: Exception) {
                    lastError = e.message
                    continue
                }
                // Render at 2x for AI vision clarity
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val base64 = bitmapToBase64Jpeg(bitmap, 85)
                bitmap.recycle()
                if (base64 == null) {
                    lastError = "Bitmap encode failed page ${i+1}"
                    continue
                }
                val text = callGeminiVision(base64, apiKey)
                if (text.isNotBlank()) {
                    sb.append(text).append("\n\n--- Page ${i+1} ---\n\n")
                    Log.d("PdfTextExtractor", "AI page ${i+1} OK: ${text.take(80)}...")
                } else {
                    lastError = "AI returned empty for page ${i+1}"
                    Log.w("PdfTextExtractor", lastError!!)
                }
                // Respect free tier rate limit (60 req/min) -> 1s delay
                kotlinx.coroutines.delay(1100)
                if (sb.length > 40000) break
            }
        } finally {
            try { renderer.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        }
        if (sb.isEmpty()) {
            throw IllegalStateException(lastError ?: "AI Vision returned no text for any page. Check internet, PDF visibility, and API key quota.")
        }
        ExtractionResult(sb.toString(), pageCount)
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int): String? {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes = stream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    private fun callGeminiVision(base64Jpeg: String, apiKey: String): String {
        var lastErr = ""
        repeat(2) { attempt ->
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1/models/gemini-3.6-flash:generateContent?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 45000
                }
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Jpeg)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", "Extract ALL text from this image EXACTLY as written, preserving line breaks and structure. This is a scanned document for CEE medical entrance exam (Physics/Chemistry/Biology). Ignore watermark 'CamScanner' / 'Scanned with CamScanner' / 'CS' logo. Transcribe handwritten and printed content accurately, even if messy. Return ONLY extracted text, no explanation. If no text, return empty.")
                            })
                        })
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                        put("maxOutputTokens", 8192)
                    })
                }.toString()
                conn.outputStream.use { it.write(body.toByteArray()) }
                val response = if (conn.responseCode in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    lastErr = "HTTP ${conn.responseCode}: $err"
                    Log.e("PdfTextExtractor", "Gemini Vision error ${conn.responseCode}: $err")
                    // For quota 429, wait and retry
                    if (conn.responseCode == 429 && attempt == 0) {
                        Thread.sleep(5000)
                    }
                    ""
                }
                conn.disconnect()
                if (response.isBlank()) return@repeat
                val json = JSONObject(response)
                if (!json.has("candidates")) {
                    lastErr = "No candidates: $response"
                    return@repeat
                }
                val candidates = json.getJSONArray("candidates")
                if (candidates.length() == 0) {
                    lastErr = "Empty candidates"
                    return@repeat
                }
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")
                val text = parts.getJSONObject(0).getString("text")
                if (text.isNotBlank()) return text
            } catch (e: Exception) {
                lastErr = e.message ?: "unknown"
                Log.e("PdfTextExtractor", "Vision call failed attempt ${attempt+1}: $lastErr", e)
                if (attempt == 0) Thread.sleep(2000)
            }
        }
        Log.e("PdfTextExtractor", "All vision attempts failed: $lastErr")
        return ""
    }

    private fun tryGetPageCount(uri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            } ?: 0
        } catch (_: Exception) { 0 }
    }

    // Kept for interface but not used in AI-only
    suspend fun ocrBitmap(image: com.google.mlkit.vision.common.InputImage): String = ""
}
