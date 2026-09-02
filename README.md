# Paperpilot — CEE Quiz from PDFs 📚

**Upload any PDF → AI extracts contents → Generates CEE-style MCQs → Revise from homescreen widget**

For students preparing for CEE (Common Entrance Examination - Medical/Engineering).

![Android](https://img.shields.io/badge/Android-34-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple)
![Compose](https://img.shields.io/badge/Compose-Material3-blue)

### ✨ Features

- **Upload Anything:** PDFs, DOCX, PPT, Images, even *handwritten scanned notes* (ML Kit OCR)
- **Smart Quiz Engine:** MCQs with 4 options + Explanation why correct + Why other 3 are wrong + Page reference
- **Subject Tagging:** Physics / Chemistry / Biology / Math — filter for widget
- **Homescreen Widget (Glance):** Flashcard flip animation • Tap to reveal answer • Pull ↻ for next question • Only selected PDFs
- **In-App Quiz:** Timed, progress, wrong-answer bucket with spaced repetition
- **Offline First:** Room DB, works without internet after generation
- **AI Optional:** Works with mock generator offline; plug Gemini 1.5 Flash key for better questions

### 📸 App Flow

`Home (PDF list) → Detail (generate) → Quiz (A/B/C/D + explanation) → Widget (homescreen flip)`

### 🛠 Tech Stack

- **Native Android (Kotlin)** + Jetpack Compose + Material3
- **Glance** for widget, **WorkManager** for updates, **Room** for DB, **DataStore** for prefs
- **PDF:** Android PdfRenderer + ML Kit Text Recognition for scanned
- **AI:** Gemini 1.5 Flash API (or mock fallback)

### 🚀 Build APK

**Local (Termux / Linux):**
```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

**GitHub Actions (Auto):**
1. Push to `main` → Action builds APK
2. Download from **Actions → Artifacts** or **Releases**
3. Install on device

### 📦 Release to GitHub Public Repo

```bash
# 1. Create new public repo on GitHub named Paperpilot
# 2. Then:
git remote add origin https://github.com/<your-username>/Paperpilot.git
git branch -M main
git push -u origin main
# 3. APK auto-builds & attaches to Release v1.0.<run_number>
```

### 🔧 Setup

1. Open in Android Studio Hedgehog+
2. Sync Gradle (AGP 8.7.3, Kotlin 2.0.21)
3. Optional: Add `GEMINI_API_KEY` in `local.properties`:
   ```
   GEMINI_API_KEY=AIza...
   ```
   Or set in app Settings screen.
4. Run on device (minSdk 26)

### 📲 Widget Setup

1. Install APK
2. Long press homescreen → **Widgets** → **Paperpilot Quiz** → Add
3. Select PDFs in app → Check "Use for widget"
4. Widget shows random Q → Tap to flip → ↻ for next

### 🗂 Project Structure

```
app/src/main/java/com/paperpilot/
 ├─ data/entity, dao, repository
 ├─ service/PdfTextExtractor, QuizGenerator
 ├─ widget/PaperpilotWidget (Glance)
 ├─ ui/theme, screens (Home, Detail, Quiz, Settings)
 ├─ viewmodel/HomeViewModel
 └─ MainActivity
```

### 📄 License

MIT — free for students.

---
**Built for CEE aspirants. Upload. Learn. Remember.**
