# Bible NDI (Version 1.3 - Latest Release)

[![Latest Release](https://img.shields.io/github/v/release/anesiem/Arabic-Bible-NDI?color=10B981&label=Latest%20Release)](https://github.com/anesiem/Arabic-Bible-NDI/releases/tag/v1.3)
[![License](https://img.shields.io/badge/License-NDI%206-3B82F6)](https://github.com/anesiem/Arabic-Bible-NDI)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-F59E0B)](https://github.com/anesiem/Arabic-Bible-NDI)

Professional Arabic/English Bible broadcast controller for Android. Transform your tablet or phone into a high-quality NDI 6 source and HTTP overlay server optimized for church presentations, live streaming, and OBS/vMix broadcast integration.

---

## 🚀 What's New in Version 1.3 (Latest Release)

* 📺 **Restored NDI Device Model Naming**: NDI protocol sources now broadcast your tablet/phone device model name cleanly (e.g., `SM-X238U - Bible-NDI-Lower` and `SM-X238U - Bible-NDI-Full`).
* 🎨 **Restored 16:9 Editor Preview Canvas**: Lower Third preview viewport maintains a 16:9 canvas aspect ratio, ensuring Lower Third cards are 100% visible and crisp against Studio, Pure Black, Chroma Green, or Transparent Checkerboard backdrops.
* 📌 **Perfect Full Show / Projector Centering**: Mathematical vertical & horizontal flexbox layout for `/show` feeds and native NDI frames.
* ⚡ **Startup Active NDI Feeds**: Native NDI sources for both Lower Third and Full Show start active immediately upon program startup.
* ☀️ **Keep Device Awake**: Integrated `FLAG_KEEP_SCREEN_ON` toggle prevents phone/tablet sleep, dimming, or CPU throttling during continuous live broadcasts.
* 👉 **Gesture-Based Chapter Navigation**: Horizontal swipe gestures on the main scripture reader view allow seamless navigation between chapters (Swipe Left for Next Chapter, Swipe Right for Previous Chapter).
* 📱 **Scrollable Scripture Selector**: Responsive hierarchy picker for Testament, Book, Chapter, and Verse picking on all mobile and tablet screen sizes.

---

## 📺 Core Features

### 📺 Dual NDI 6 Native Outputs
* **Lower Third Feed**: Optimized for broadcast streaming (OBS/vMix) with automatic RTL Arabic support and LTR English alignment.
* **Full Show Feed**: Dedicated full-screen output for projectors with vertical and horizontal centering.
* **Zero Interference**: Uses independent native buffer pools to allow both feeds to run simultaneously without flashing.
* **Discovery**: Automatically identified on the network by device model name (e.g., `SM-X238U - Bible-NDI-Lower`).

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
* **Color Grade Spectrum**: 31 Preset Broadcast Color Grades + Hue Spectrum Cycle + Custom Hex Input + Recent Colors bar with deletion `x` buttons.
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
