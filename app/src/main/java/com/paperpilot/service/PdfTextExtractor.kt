package com.paperpilot.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ExtractionResult(val text: String, val pageCount: Int, val quality: Int = 0, val isScanned: Boolean = false)

class PdfTextExtractor(private val context: Context) {

    init {
        try { PDFBoxResourceLoader.init(context) } catch (_: Exception) {}
    }

    private fun getGeminiKey(): String? {
        // Check SharedPreferences first, then local.properties BuildConfig
        return try {
            val prefs = context.getSharedPreferences("paperpilot_prefs", Context.MODE_PRIVATE)
            prefs.getString("gemini_key", null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    suspend fun extractText(uri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        Log.d("PdfTextExtractor", "Starting extraction for $uri")
        // 1. Try PDFBox (for text-based PDFs)
        val pdfBoxRaw = tryExtractWithPdfBox(uri)
        val pdfBoxCleaned = filterWatermark(pdfBoxRaw.text)
        val pdfBoxScore = scoreExtraction(pdfBoxCleaned)
        Log.d("PdfTextExtractor", "PDFBox raw ${pdfBoxRaw.text.length} cleaned ${pdfBoxCleaned.length} score $pdfBoxScore pages ${pdfBoxRaw.pageCount}")

        // If PDFBox gives high quality text, use it directly (typed PDFs)
        if (pdfBoxCleaned.length > 800 && pdfBoxScore >= 60) {
            Log.d("PdfTextExtractor", "Using PDFBox - high quality typed PDF")
            return@withContext ExtractionResult(pdfBoxCleaned.take(120000), pdfBoxRaw.pageCount, pdfBoxScore, false)
        }
        if (pdfBoxCleaned.length > 400 && pdfBoxScore >= 50) {
            return@withContext ExtractionResult(pdfBoxCleaned.take(120000), pdfBoxRaw.pageCount, pdfBoxScore, false)
        }

        // 2. Scanned PDF detected - try OCR
        // Try MLKit OCR first
        val ocrRaw = tryExtractWithMlKitOcr(uri)
        val ocrCleaned = filterWatermark(ocrRaw.text)
        val ocrScore = scoreExtraction(ocrCleaned)
        Log.d("PdfTextExtractor", "MLKit raw ${ocrRaw.text.length} cleaned ${ocrCleaned.length} score $ocrScore")

        // If MLKit did well (printed scans), use it
        if (ocrCleaned.length > 500 && ocrScore >= 45) {
            Log.d("PdfTextExtractor", "Using MLKit OCR - good printed scan")
            return@withContext ExtractionResult(ocrCleaned.take(120000), ocrRaw.pageCount, ocrScore, true)
        }

        // 3. Handwritten / low MLKit score -> try Gemini Vision if key available (BEST for CamScanner handwritten)
        val geminiKey = getGeminiKey()
        if (!geminiKey.isNullOrBlank()) {
            val geminiRaw = tryExtractWithGeminiVision(uri, geminiKey)
            val geminiCleaned = filterWatermark(geminiRaw.text)
            val geminiScore = scoreExtraction(geminiCleaned)
            Log.d("PdfTextExtractor", "Gemini raw ${geminiRaw.text.length} cleaned ${geminiCleaned.length} score $geminiScore")
            if (geminiCleaned.length > 300 && geminiScore >= 40) {
                Log.d("PdfTextExtractor", "Using Gemini Vision - best for handwritten")
                // Gemini is most accurate for handwritten, prefer it even if length slightly less
                if (geminiCleaned.length > ocrCleaned.length * 0.7 && geminiScore > ocrScore) {
                    return@withContext ExtractionResult(geminiCleaned.take(120000), geminiRaw.pageCount, geminiScore, true)
                }
                if (geminiCleaned.length > ocrCleaned.length) {
                    return@withContext ExtractionResult(geminiCleaned.take(120000), geminiRaw.pageCount, geminiScore, true)
                }
            }
            // If Gemini succeeded but MLKit was empty, use Gemini
            if (geminiCleaned.length > 500 && ocrCleaned.length < 300) {
                return@withContext ExtractionResult(geminiCleaned.take(120000), geminiRaw.pageCount, geminiScore, true)
            }
        } else {
            Log.w("PdfTextExtractor", "No Gemini key - skipping Vision OCR. For CamScanner handwritten, set Gemini key in Settings for better results.")
        }

        // 4. Choose best among available, even if low quality
        val candidates = listOf(
            ExtractionResult(pdfBoxCleaned, pdfBoxRaw.pageCount, pdfBoxScore, false),
            ExtractionResult(ocrCleaned, ocrRaw.pageCount, ocrScore, true)
        ).filter { it.text.isNotBlank() }

        if (candidates.isEmpty()) {
            val pageCount = tryGetPageCount(uri)
            return@withContext ExtractionResult("", pageCount, 0, true)
        }

        // Pick highest scoring, but prefer longer if scores close
        val best = candidates.maxByOrNull { it.quality * 10 + (it.text.length / 200) }!!
        Log.d("PdfTextExtractor", "Using best candidate score ${best.quality} len ${best.text.length}")

        // If best is still poor (<30) and is scanned, keep watermark-filtered but mark low quality
        // QuizGenerator will handle low quality by asking user to re-upload clearer PDF or set Gemini key
        ExtractionResult(best.text.take(120000), best.pageCount, best.quality, best.isScanned)
    }

    fun filterWatermark(text: String): String {
        if (text.isBlank()) return ""
        val watermarkRegex = Regex("(?i)(cam\\s*scanner|scanned\\s*(with|by)|camscanner\\.com|\\bcs\\b.*scanner|shot\\s*on|scan\\s*document)")
        val lines = text.lines()
        val filtered = lines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { watermarkRegex.containsMatchIn(it) && it.length < 80 } // filter only short watermark lines, keep long lines that happen to contain word
            .filterNot { it.equals("CamScanner", ignoreCase = true) }
            .filterNot { it.equals("Scanner", ignoreCase = true) }

        // Remove repeated watermark lines that appear many times (e.g., CamScanner footer on every page)
        val freq = filtered.groupingBy { it }.eachCount()
        val withoutRepeatedWatermark = filtered.filter { line ->
            val count = freq[line] ?: 0
            // If a short line repeats >3 times, it's likely header/footer watermark
            !(count > 3 && line.length < 40 && watermarkRegex.containsMatchIn(line))
        }

        // Remove empty and very short garbage lines (<3 chars)
        val cleaned = withoutRepeatedWatermark.filter { it.length >= 3 }

        // Join and also remove inline watermark fragments
        var joined = cleaned.joinToString("\n")
        // Remove inline "CamScanner" fragments within sentences (but keep sentence)
        joined = joined.replace(Regex("(?i)\\bCamScanner\\b[\\s\\-]*"), "")
        joined = joined.replace(Regex("(?i)Scanned\\s+with\\s+CamScanner"), "")
        joined = joined.replace(Regex("\\n{3,}"), "\n\n")

        // If after filtering we removed too much but text was mostly watermark, keep original but score will be low
        return joined.trim()
    }

    fun scoreExtraction(text: String): Int {
        if (text.isBlank()) return 0
        if (text.length < 100) return 10
        val lower = text.lowercase()
        val watermarks = Regex("(?i)cam\\s*scanner").findAll(text).count()
        val watermarkRatio = watermarks.toFloat() / (text.split(Regex("\\s+")).size.coerceAtLeast(1))
        if (watermarkRatio > 0.15) return 15 // too much watermark

        val sentences = text.split(Regex("(?<=[.!?])\\s+|\\n+")).filter { it.trim().length > 30 }
        val wordCount = text.split(Regex("\\s+")).size
        val avgWordLen = text.filter { it.isLetter() }.length.toFloat() / wordCount.coerceAtLeast(1)

        // Check for gibberish (low vowel ratio indicates garbled binary read)
        val vowels = text.count { it.lowercaseChar() in "aeiou" }
        val letters = text.count { it.isLetter() }
        val vowelRatio = if (letters > 0) vowels.toFloat() / letters else 0f

        var score = 50
        if (sentences.size >= 8) score += 20
        else if (sentences.size >= 4) score += 10
        else score -= 10

        if (wordCount > 200) score += 10
        if (wordCount > 500) score += 10
        if (avgWordLen in 3.5..7.0) score += 5 else score -= 5
        if (vowelRatio in 0.35..0.55) score += 10 else score -= 10
        if (watermarkRatio > 0.05) score -= 15
        if (text.contains(Regex("[A-Z][a-z]+\\s+[A-Z][a-z]+"))) score += 5 // has proper phrases

        return score.coerceIn(0, 100)
    }

    private fun tryExtractWithPdfBox(uri: Uri): ExtractionResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffered = BufferedInputStream(input)
                PDDocument.load(buffered).use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    stripper.startPage = 1
                    stripper.endPage = minOf(doc.numberOfPages, 60)
                    val text = stripper.getText(doc)
                    val cleaned = text.replace("\r", "").trim()
                    ExtractionResult(cleaned, doc.numberOfPages)
                }
            } ?: ExtractionResult("", 0)
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "PDFBox failed: ${e.message}", e)
            ExtractionResult("", 0)
        }
    }

    private suspend fun tryExtractWithMlKitOcr(uri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        val pfd = try { context.contentResolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null }
            ?: return@withContext ExtractionResult("", 0)
        val renderer = try { PdfRenderer(pfd) } catch (e: Exception) {
            try { pfd.close() } catch (_: Exception) {}
            return@withContext ExtractionResult("", 0)
        }
        val pageCount = renderer.pageCount
        val maxPages = minOf(pageCount, 12)
        val sb = StringBuilder()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            for (i in 0 until maxPages) {
                val page = try { renderer.openPage(i) } catch (_: Exception) { continue }
                // Render at 3x for handwritten clarity (was 2x)
                val scale = 3
                val width = page.width * scale
                val height = page.height * scale
                // Cap size to avoid OOM
                val maxDim = 3000
                val clampedWidth = minOf(width, maxDim)
                val clampedHeight = minOf(height, maxDim * height / width)
                val bitmap = Bitmap.createBitmap(clampedWidth, clampedHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Enhance for OCR: grayscale + contrast + remove watermark area (bottom 5%)
                val enhanced = enhanceBitmapForOcr(bitmap)

                try {
                    val image = InputImage.fromBitmap(enhanced, 0)
                    val result = recognizer.process(image).await()
                    if (result.text.isNotBlank()) {
                        sb.append(result.text).append("\n\n--- Page ${i+1} ---\n\n")
                        Log.d("PdfTextExtractor", "MLKit page ${i+1}: ${result.text.take(80)}...")
                    }
                } catch (e: Exception) {
                    Log.e("PdfTextExtractor", "MLKit page $i failed: ${e.message}")
                } finally {
                    if (enhanced != bitmap) enhanced.recycle()
                    bitmap.recycle()
                }
                if (sb.length > 30000) break
                // Small delay to avoid overloading
                kotlinx.coroutines.delay(100)
            }
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "OCR loop failed: ${e.message}", e)
        } finally {
            try { renderer.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
            try { recognizer.close() } catch (_: Exception) {}
        }
        ExtractionResult(sb.toString(), pageCount)
    }

    private fun enhanceBitmapForOcr(src: Bitmap): Bitmap {
        return try {
            // Crop bottom 4% where CamScanner watermark usually lives, but keep if needed
            // Instead, we keep full but enhance contrast
            val width = src.width
            val height = src.height
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()

            // Grayscale + contrast + brightness
            val cm = ColorMatrix()
            // First, saturation 0 (grayscale)
            cm.setSaturation(0f)
            // Then contrast 1.4, brightness 10
            val contrast = 1.4f
            val brightness = 10f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrastMatrix)

            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(src, 0f, 0f, paint)
            result
        } catch (_: Exception) {
            src
        }
    }

    private suspend fun tryExtractWithGeminiVision(uri: Uri, apiKey: String): ExtractionResult = withContext(Dispatchers.IO) {
        val pfd = try { context.contentResolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null }
            ?: return@withContext ExtractionResult("", 0)
        val renderer = try { PdfRenderer(pfd) } catch (_: Exception) {
            try { pfd.close() } catch (_: Exception) {}
            return@withContext ExtractionResult("", 0)
        }
        val pageCount = renderer.pageCount
        val maxPages = minOf(pageCount, 6) // Gemini Vision: fewer pages due to API cost/latency, but higher accuracy
        val sb = StringBuilder()
        try {
            for (i in 0 until maxPages) {
                val page = try { renderer.openPage(i) } catch (_: Exception) { continue }
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val base64 = bitmapToBase64Jpeg(bitmap, 85)
                bitmap.recycle()
                if (base64 == null) continue

                val text = callGeminiVision(base64, apiKey)
                if (text.isNotBlank()) {
                    sb.append(text).append("\n\n--- Page ${i+1} (Gemini) ---\n\n")
                    Log.d("PdfTextExtractor", "Gemini page ${i+1}: ${text.take(80)}...")
                }
                // Rate limit: 1.5s between calls
                kotlinx.coroutines.delay(1500)
                if (sb.length > 30000) break
            }
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "Gemini Vision failed: ${e.message}", e)
        } finally {
            try { renderer.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
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
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
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
                            put("text", "Extract ALL text from this image EXACTLY as written, preserving line breaks and structure. This is a scanned handwritten/printed document for CEE medical entrance exam. Ignore watermark 'CamScanner' / 'Scanned with CamScanner' / 'CS' logo. Focus on the main handwritten/printed educational content (Physics/Chemistry/Biology). If handwritten, transcribe accurately even if messy. Return ONLY the extracted text, no explanation.")
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
                Log.e("PdfTextExtractor", "Gemini Vision error ${conn.responseCode}: $err")
                ""
            }
            conn.disconnect()
            if (response.isBlank()) return ""
            val json = JSONObject(response)
            if (!json.has("candidates")) return ""
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) return ""
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            parts.getJSONObject(0).getString("text")
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "Gemini Vision call failed: ${e.message}", e)
            ""
        }
    }

    private fun tryGetPageCount(uri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            } ?: 0
        } catch (_: Exception) { 0 }
    }

    suspend fun ocrBitmap(image: InputImage): String {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            recognizer.close()
            filterWatermark(result.text)
        } catch (e: Exception) { "" }
    }
}
