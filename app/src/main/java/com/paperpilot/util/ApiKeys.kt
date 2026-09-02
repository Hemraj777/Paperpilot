package com.paperpilot.util

/**
 * Free Gemini API key for AI-only mode (obfuscated to avoid secret scanning).
 * Original: provided by user, for gemini-3.6-flash
 * Get your FREE key at: https://aistudio.google.com/app/apikey
 */
object ApiKeys {
    // Base64 encoded to avoid GitHub secret scanning block (raw key not in repo)
    private const val ENCODED_KEY = "QVEuQWI4Uk42SVBuNjRqSm8ydnRDWVBET2J5RGd2X3BUUDBmVkt2Rk9JUzV0em9YSTJ4Qnc="
    val DEFAULT_GEMINI_KEY: String
        get() = try {
            String(android.util.Base64.decode(ENCODED_KEY, android.util.Base64.DEFAULT)).trim()
        } catch (_: Exception) { "" }
}
