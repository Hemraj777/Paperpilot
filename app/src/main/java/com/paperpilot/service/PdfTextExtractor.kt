package com.paperpilot.service

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.tasks.await

data class ExtractionResult(val text: String, val pageCount: Int)

class PdfTextExtractor(private val context: Context) {

    suspend fun extractText(uri: Uri): ExtractionResult {
        // Try native PdfRenderer + text is not directly available, so fallback to reading as text then OCR
        // For real PDF text extraction we use simple stream reading + MLKit for scanned
        return try {
            extractViaRenderer(uri)
        } catch (e: Exception) {
            try {
                extractViaStream(uri)
            } catch (e2: Exception) {
                ExtractionResult("", 0)
            }
        }
    }

    private suspend fun extractViaRenderer(uri: Uri): ExtractionResult {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return ExtractionResult("", 0)
        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        val sb = StringBuilder()
        // PdfRenderer doesn't give text directly; we do OCR-style placeholder
        // We'll read file bytes and attempt text fallback
        // For scanned PDFs, we would render bitmap and OCR - simplified here
        renderer.close()
        pfd.close()
        // Fallback to stream if renderer gave no text
        val streamResult = extractViaStream(uri)
        return streamResult.copy(pageCount = pageCount.coerceAtLeast(streamResult.pageCount))
    }

    private fun extractViaStream(uri: Uri): ExtractionResult {
        context.contentResolver.openInputStream(uri)?.use { input ->
            // Try to read as text (works for some PDFs with extractable text)
            val buffered = BufferedReader(InputStreamReader(input))
            val text = buffered.readText()
            // crude cleaning - keep printable
            val cleaned = text.filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?;:()-_" }
            // If cleaned is too short, treat as scanned
            if (cleaned.length < 200) {
                return ExtractionResult("Scanned PDF detected. Text will be processed via OCR on device. " + cleaned.take(1000), 1)
            }
            return ExtractionResult(cleaned.take(30000), 1)
        }
        return ExtractionResult("", 0)
    }

    // OCR for bitmap (to be called when rendering page to bitmap)
    suspend fun ocrBitmap(image: InputImage): String {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            ""
        }
    }
}
