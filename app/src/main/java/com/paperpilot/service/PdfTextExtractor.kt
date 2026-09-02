package com.paperpilot.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
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
import java.io.BufferedInputStream

data class ExtractionResult(val text: String, val pageCount: Int)

class PdfTextExtractor(private val context: Context) {

    init {
        try { PDFBoxResourceLoader.init(context) } catch (_: Exception) {}
    }

    suspend fun extractText(uri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        // Try PDFBox first (best for text-based PDFs)
        val pdfBoxResult = tryExtractWithPdfBox(uri)
        if (pdfBoxResult.text.length > 500) {
            Log.d("PdfTextExtractor", "PDFBox extracted ${pdfBoxResult.text.length} chars, ${pdfBoxResult.pageCount} pages")
            return@withContext pdfBoxResult
        }
        // If PDFBox gave little text, it's likely scanned -> try OCR
        if (pdfBoxResult.text.length in 100..500) {
            // Still return PDFBox if somewhat valid
            return@withContext pdfBoxResult
        }
        // Try OCR for scanned PDFs
        val ocrResult = tryExtractWithOcr(uri)
        if (ocrResult.text.length > 200) {
            Log.d("PdfTextExtractor", "OCR extracted ${ocrResult.text.length} chars")
            return@withContext ocrResult
        }
        // Fallback to PDFBox result even if short, or OCR
        if (pdfBoxResult.text.isNotBlank()) return@withContext pdfBoxResult
        if (ocrResult.text.isNotBlank()) return@withContext ocrResult
        // Last resort: try renderer page count only
        val pageCount = tryGetPageCount(uri)
        ExtractionResult(pdfBoxResult.text.ifBlank { ocrResult.text }, pageCount)
    }

    private fun tryExtractWithPdfBox(uri: Uri): ExtractionResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffered = BufferedInputStream(input)
                PDDocument.load(buffered).use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    stripper.startPage = 1
                    stripper.endPage = minOf(doc.numberOfPages, 50) // limit to 50 pages for speed
                    val text = stripper.getText(doc)
                    val cleaned = text.replace("\r", "").trim()
                    ExtractionResult(cleaned.take(120000), doc.numberOfPages)
                }
            } ?: ExtractionResult("", 0)
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "PDFBox failed: ${e.message}")
            ExtractionResult("", 0)
        }
    }

    private suspend fun tryExtractWithOcr(uri: Uri): ExtractionResult = withContext(Dispatchers.IO) {
        val pfd = try { context.contentResolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null }
            ?: return@withContext ExtractionResult("", 0)
        val renderer = try { PdfRenderer(pfd) } catch (e: Exception) {
            pfd.close()
            return@withContext ExtractionResult("", 0)
        }
        val pageCount = renderer.pageCount
        val maxPages = minOf(pageCount, 10) // OCR first 10 pages max for performance
        val sb = StringBuilder()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            for (i in 0 until maxPages) {
                val page = renderer.openPage(i)
                // Create bitmap at 2x scale for better OCR
                val width = page.width * 2
                val height = page.height * 2
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                try {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val result = recognizer.process(image).await()
                    if (result.text.isNotBlank()) {
                        sb.append(result.text).append("\n\n")
                    }
                } catch (_: Exception) {}
                bitmap.recycle()
                // Early exit if we have enough text
                if (sb.length > 20000) break
            }
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "OCR failed: ${e.message}")
        } finally {
            try { renderer.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
            try { recognizer.close() } catch (_: Exception) {}
        }
        ExtractionResult(sb.toString().take(120000), pageCount)
    }

    private fun tryGetPageCount(uri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            } ?: 0
        } catch (_: Exception) { 0 }
    }

    // Kept for compatibility but not used directly
    suspend fun ocrBitmap(image: InputImage): String {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            recognizer.close()
            result.text
        } catch (e: Exception) { "" }
    }
}
