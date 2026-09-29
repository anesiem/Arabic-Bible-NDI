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

### 📶 Dual-Tier NDI: Full + HX (Bandwidth Saver)
Each feed (Lower Third and Full Show) can broadcast in **two independent tiers**, each with its own on/off switch in the app:
* **Full NDI** (`… - Bible-NDI-Lower`, `… - Bible-NDI-Full`): BGRA with 100% alpha transparency — designed for **overlaying verses** in OBS/vMix. Best quality for fast local networks.
* **HX** (`… - Bible-NDI-Lower-HX`, `… - Bible-NDI-Full-HX`): bandwidth-saver tier for slow/congested Wi-Fi. Same NDI protocol, so OBS/vMix discover it with zero extra setup, at a fraction of the bandwidth.
* **Smart sending**: frames are pushed to the NDI network only when the verse/template actually changes (plus a 2-second keep-alive heartbeat), instead of re-encoding identical frames 15×/second — dramatically lower CPU, battery, and network use during static verses.

### ⚙️ Per-Source Customization (NDI Link tab)
Every NDI source is user-configurable from dropdown lists in the app's NDI tab — nothing is hardcoded:
* **Resolution**: 1920×1080, 1280×720, 960×540, 854×480, 640×360 (defaults: 1920×1080 Full tier, 960×540 HX tier).
* **Frame rate**: 30, 25, 24, 15, 10, 5 fps (defaults: 30 fps Full tier, 15 fps HX tier).
Changing either setting restarts that source immediately with the new spec, and the discovery beacons (mDNS + directory JSON) re-advertise the new resolution/fps.

### 🛠 Advanced Diagnostics (NDI Link tab)
The NDI tab has an **Advanced** toggle. When enabled it shows:
* per-source live status (configured resolution/fps + running/stopped),
* a timestamped log of NDI errors (init failures, sender-creation failures, send errors — capped at 50, oldest dropped).
If any error is logged while Advanced is off, a red banner still appears in the NDI section so the user knows something went wrong.

### 🌐 HTTP & Web Overlays
* **HTML5 Overlays**: Transparent browser sources for OBS at `/ndi` and projector views at `/show`.
* **Zero Latency (SSE)**: Persistent Server-Sent Events push live state changes instantly.
* **Motion Graphics**: Built-in animated backgrounds (Golden Divine Rays, Ethereal Blue Waves, Candle Liturgical Glow, Royal Purple Silk, Particle Stars) and support for custom local MP4 video loops.

#### All available URLs (also listed in the app's NDI tab, each with a copy button)
| URL path | Purpose |
|---|---|
| `/ndi` | Transparent HTML overlay (OBS Browser Source) |
| `/show` | Full-show HTML page (projector) |
| `/ndi/stream` | MJPEG video stream |
| `/ndi/stream.png` | PNG snapshot of the current frame |
| `/ndi/events` | Server-Sent Events (live verse updates) |
| `/ndi/status` | JSON status |
| `/ndi/api/verse` | Verse data API |

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
