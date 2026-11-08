# BACUFlix Native (Android Kotlin + Jetpack Compose + Media3)

**BACUFlix Native** (`com.bacuflix.app`) is the 100% native Android Kotlin port of the `bacuflix` streaming platform, built with **Jetpack Compose**, **Kotlin Coroutines & StateFlow**, and **AndroidX Media3 (ExoPlayer + MediaSessionService)** for Android phones, tablets, and Android TV / Google TV devices.

---

## Key Features

### 1. Catalog & Multi-Source API Integration
- **KKPhim + OPhim + Nguonc Multi-Source API (`KkphimApiService.kt`)**:
  - New releases (`/danh-sach/phim-moi-cap-nhat`), Categories (`phim-le`, `phim-bo`, `hoat-hinh`, `tv-shows`), Genres (`/v1/api/the-loai/{slug}`), Countries (`/v1/api/quoc-gia/{slug}`), Search (`/v1/api/tim-kiem`), and Movie Details (`/phim/{slug}`).
  - Automatic multi-source server aggregation: merges backup stream servers from **OPhim (`ophim1.com`)** and **Nguonc (`phim.nguonc.com`)** alongside **KKPhim (`phimapi.com`)**.
- **GitHub Raw Markdown Catalog (`CatalogRepository.kt` & `MovieMarkdownParser.kt`)**:
  - Fetches `movies.md` from GitHub Raw with a 10-minute cache TTL and falls back seamlessly to bundled `assets/data/movies.md`.

### 2. Google Suggest Trending & On-Device ML Recommendation Engine
- **Real-Time Trending (`GoogleTrendingService.kt`)**:
  - Queries Google Suggest autocomplete endpoints (`suggestqueries.google.com`) with Vietnamese movie seed queries, extracts candidate titles, matches them against KKPhim, and ranks results with a 10-minute cache.
- **Hybrid Content-Based + Naive Bayes ML Engine (`MlRecommendationService.kt`)**:
  - Vietnamese diacritic-normalized unigram + bigram tokenizer.
  - Multinomial Naive Bayes latent genre classifier across 12 genre dimensions.
  - Time-decayed user preference profile (14-day half-life) trained from Watch History, Favorites, Search Queries, and Interaction Logs.
  - Cosine similarity + genre affinity + recency + completion boost scoring, calibrated to `[80%..99%]` match badges (`96% Phù hợp`), plus **Item-to-Item Similar Movies (`getSimilarMovies`)**.

### 3. Responsive Cinema UI, Multi-Filter & Backup/Restore (Mobile + Tablet + Android TV)
- **Adaptive Navigation (`HomeScreen.kt`)**:
  - Collapsible left sidebar on wide screens / Android TV (`>= 840dp`), plus mobile bottom navigation bar on compact screens.
  - TV Remote D-Pad friendly QWERTY & Quick-Chip virtual keyboard (`TvSearchKeyboard.kt`).
  - Double-back to exit protection with native toast feedback.
- **Multi-Filter & Sort Bar (`SettingsAndFilterComponents.kt`)**:
  - Filter catalog movies simultaneously by **Country** (Trung Quốc, Hàn Quốc, Âu Mỹ, Nhật Bản, Việt Nam, Thái Lan...), **Genre**, **Release Year**, **Language** (`Vietsub` / `Thuyết minh & Lồng tiếng`), and **Sort Order** (Mới cập nhật, Năm mới nhất, `% Phù hợp ML`, Tên A→Z).
- **Backup & Restore JSON (`BackupRestoreManager.kt` & `SettingsAndFilterComponents.kt`)**:
  - One-tap JSON Export (Copy to Clipboard or Share/Save file) and Restore for Favorites, Watch History (including per-episode resume timestamps), and ML User Profile.
- **Hero Banner & 7 Horizontal Carousels (`HeroCarousel.kt`, `MovieCarousels.kt`)**:
  - Auto-advancing 5-slide Hero Banner (7-second interval) with asynchronously enriched plot descriptions.
  - Horizontal rows: *Phim mới cập nhật*, *Top 10 hôm nay*, *Tiếp tục xem* (with progress bar & remove button), *Phim thịnh hành*, *Phim đề xuất*, *Thể loại*, and *Phim bộ nổi bật*.
- **Movie Detail Modal (`MovieDetailModal.kt`)**:
  - Full-bleed backdrop, poster, metadata badges (`HD`, `Vietsub`, year, episode status, server count), clickable **Actor / Category / Country** tags, cleaned HTML synopsis, `Tiếp tục Tập X (mm:ss)` smart resume button, Favorite toggle, Share deep-link button, Multi-Source Server selector tabs, Episode range chunks (`1–50`, `51–100`...) with quick episode search, and a **"Phim tương tự cùng thể loại"** carousel.

### 4. Native AndroidX Media3 Video Player (`PlayerScreen.kt` & `PlayerController.kt`)
- **PNG-Wrapped MPEG-TS Stream Unwrapping (`PngStrippingDataSource.kt` & `SegmentInspector.kt`)**:
  - Custom `DataSource` for Media3 `ExoPlayer` that inspects non-`.m3u8` HLS segments, verifies PNG signatures & chunk CRCs up to `IEND`, confirms 188-byte `0x47` MPEG-TS packet alignment, and strips the PNG wrapper transparently before feeding bytes to ExoPlayer.
- **External SRT / WebVTT Subtitles (`SubtitleParser.kt`)**:
  - Fetches and parses `.vtt` and `.srt` subtitle files and renders live synchronized subtitle cues with a `Subs ON / OFF` toggle.
- **Advanced Cinema Player Controls**:
  - **Double-tap left/right** gesture zones (`-10s` / `+10s`) with cumulative seconds ripple feedback, plus center double-tap play/pause.
  - **Long-press `2.0x` Speed Boost** gesture (hold anywhere to fast-forward at `2.0x`, release to restore speed) + **Playback Speed Picker (`0.5x` – `2.0x`)**.
  - **Vertical Swipe Gestures**: swipe left side for **Screen Brightness HUD**, swipe right side for **Volume HUD**.
  - **Touch Screen Lock (`Khóa màn hình`)**, **Aspect Ratio Switcher (`Fit` / `Zoom` / `Fill`)**, **Skip Intro (`Bỏ qua Mở đầu +85s`)**, **Sleep Timer (`15p`, `30p`, `60p`, `Hết tập`)**, **In-Player Episode & Server Drawer**, **Native Picture-in-Picture (`PiP`)**, and a **5-second Auto-Next Episode countdown overlay**.
  - Fallback `WebView` embed player when an episode only provides an embed link.
- **Background & Lock-Screen Media Controls (`PlaybackMediaSessionService.kt`)**:
  - Foreground `MediaSessionService` exposing lock-screen and notification transport controls (`Play`, `Pause`, `-10s`, `+10s`, `Previous`, `Next`).

---

## Project Structure

```text
bacuflix-native/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── icon.png
│       │   │   └── data/movies.md
│       │   ├── java/com/bacuflix/app/
│       │   │   ├── BacuFlixApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   │   ├── CatalogRepository.kt
│       │   │   │   ├── CatalogSources.kt
│       │   │   │   ├── FavoritesRepository.kt
│       │   │   │   ├── GoogleTrendingService.kt
│       │   │   │   ├── KkphimApiService.kt
│       │   │   │   ├── MlRecommendationService.kt
│       │   │   │   ├── MovieMarkdownParser.kt
│       │   │   │   └── WatchHistoryRepository.kt
│       │   │   ├── model/
│       │   │   │   └── Models.kt
│       │   │   ├── player/
│       │   │   │   ├── PlaybackMediaSessionService.kt
│       │   │   │   ├── PlayerController.kt
│       │   │   │   ├── PngStrippingDataSource.kt
│       │   │   │   ├── SeekController.kt
│       │   │   │   ├── SegmentInspector.kt
│       │   │   │   └── SubtitleParser.kt
│       │   │   ├── ui/
│       │   │   │   ├── components/
│       │   │   │   │   ├── AsyncImagePoster.kt
│       │   │   │   │   ├── HeroCarousel.kt
│       │   │   │   │   ├── MovieCard.kt
│       │   │   │   │   ├── MovieCarousels.kt
│       │   │   │   │   └── TvSearchKeyboard.kt
│       │   │   │   ├── detail/
│       │   │   │   │   └── MovieDetailModal.kt
│       │   │   │   ├── home/
│       │   │   │   │   ├── HomeScreen.kt
│       │   │   │   │   └── HomeViewModel.kt
│       │   │   │   ├── player/
│       │   │   │   │   └── PlayerScreen.kt
│       │   │   │   └── theme/
│       │   │   │       └── Theme.kt
│       │   │   └── util/
│       │   │       ├── FormatTime.kt
│       │   │       ├── ImageLoader.kt
│       │   │       └── UrlUtils.kt
│       │   └── res/
│       └── test/java/com/bacuflix/app/
│           └── BacuFlixUnitTests.kt
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

---

## Building & Running

### Prerequisites
- **Android Studio Ladybug / Koala** (or newer)
- **JDK 17**
- **Android SDK 34** (`minSdk = 24`, `targetSdk = 34`)

### Build Debug APK via CLI
```bash
cd bacuflix-native
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```
