# Bible NDI (Version 1.2)

Professional Arabic/English Bible broadcast controller for Android. Transform your tablet or phone into a high-quality NDI 6 source and HTTP overlay server optimized for church presentations, live streaming, and OBS/vMix broadcast integration.

---

## 🚀 What's New in Version 1.2

* **⚡ Ultra-Low Latency NDI & SSE Architecture**: Zero-allocation bitmap caching eliminates garbage collection (GC) pauses (`0ms` allocation-free rendering when idle). Socket-level `TCP_NODELAY` and 64 KB buffers deliver instantaneous (<5ms) verse updates across the network.
* **☀️ Keep Device Awake (Keep Screen On)**: Integrated `FLAG_KEEP_SCREEN_ON` toggle in the settings and NDI Link tab. Prevents phone/tablet sleep, screen dimming, or CPU throttling during continuous live broadcasts.
* **👉 Gesture-Based Chapter Navigation**: Horizontal swipe gestures on the main scripture view allow seamless navigation between chapters (Swipe Left for Next Chapter, Swipe Right for Previous Chapter).
* **📱 Scrollable Scripture Selector**: Responsive, scrollable hierarchy selector for Testament, Book, Chapter, and Verse picking on all mobile and tablet screen sizes.
* **📌 Centered Full Show / Projector Feed**: Refactored CSS/JS layout for `/show` feeds ensuring verse text and citation badges are centered vertically in the middle of the screen.
* **🎯 Focused Library UI**: Streamlined scripture reader UI for rapid verse cued selection and live broadcast control.

---

## 📺 Key Features

### 📺 Dual NDI 6 Native Outputs
* **Lower Third Feed**: Optimized for broadcast streaming (OBS/vMix) with automatic RTL Arabic support and LTR English alignment.
* **Full Show Feed**: Dedicated full-screen output for projectors with vertical and horizontal centering.
* **Zero Interference**: Uses independent native buffer pools to allow both feeds to run simultaneously without flashing.
* **Discovery**: Automatically identified on the network by device model name (e.g., `SM-X238U (Bible-NDI-Lower)`).

### 🌐 HTTP & Web Overlays
* **HTML5 Overlays**: Transparent browser sources for OBS at `/ndi` and projector views at `/show`.
* **Zero Latency (SSE)**: Persistent Server-Sent Events push live state changes instantly.
* **Motion Graphics**: Built-in animated backgrounds (Golden Divine Rays, Ethereal Blue Waves, Candle Liturgical Glow, Royal Purple Silk, Particle Stars) and support for custom local MP4 video loops.

### 📖 Scripture Management
* **Bilingual Support**: Display Arabic (SVD) and English (ASV/KJV/WEB) simultaneously.
* **Gesture & Touch Navigation**: Swipe horizontally or tap chapters to open full chapters without initial verse selection.
* **Integrated Search**: Instant navigation to any verse across the entire Bible.
* **SQLite Powered**: High-performance local database access for instantaneous verse retrieval.

### 🎨 Design & Customization
* **Total Typography Control**: Independent font families, sizes, colors, and styles (Bold/Italic) for both languages.
* **Visual Styles**: Glassmorphism, classic banners, liturgical gold, and 100% pure transparent alpha modes.
* **Bilingual Spacing**: Fine-tune the vertical gap between languages for maximum readability.
* **Dark Mode Ready**: Optimized UI with high-contrast verse highlighting.

---

## 📡 Broadcast & Connectivity Best Practices

Ensure your Android tablet/phone and production PC (OBS / vMix) are on the same local network (preferably 5 GHz Wi-Fi or Ethernet).

* **Overlay URL**: `http://[TABLET_IP]:8080/ndi` (Best for OBS Studio Browser Source)
* **Projector URL**: `http://[TABLET_IP]:8080/show` (Best for Full-Screen Projectors)
* **Raw Stream URL**: `http://[TABLET_IP]:8080/ndi/stream.mjpg?raw=1` (Raw MJPEG Video Feed)

> **💡 Best Practice Tip**: For zero-latency and crystal-clear rendering in OBS Studio, add an **OBS Browser Source** using `http://[TABLET_IP]:8080/ndi` with `Width: 1920` and `Height: 1080`.

---

## 🛠 Developer Setup & Building

### Prerequisites
* Android Studio Ladybug (2024.2.1) or newer.
* NDI 6 SDK for Android.
* Android NDK Version `28.2.13676358` or higher.
* Minimum SDK: 24 (Android 7.0).
* Target SDK: 36 (Android 15 ready, supports 16 KB page sizes).

### Building
```bash
# Debug Build
./gradlew app:assembleDebug

# Release APK / Bundle
./gradlew app:assembleRelease
./gradlew app:bundleRelease
```

---

## ⚖️ License
This project uses the NDI® SDK. NDI® is a registered trademark of Vizrt NDI AB. Please refer to the NDI SDK License Agreement for usage terms.
