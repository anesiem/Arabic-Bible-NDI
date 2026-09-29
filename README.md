# Arabic Bible NDI (Version 1.5 - Latest Release)

[![Latest Release](https://img.shields.io/github/v/release/anesiem/Arabic-Bible-NDI?color=10B981&label=Latest%20Release)](https://github.com/anesiem/Arabic-Bible-NDI/releases/tag/v1.5)
[![License](https://img.shields.io/badge/License-NDI%206-3B82F6)](https://github.com/anesiem/Arabic-Bible-NDI)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-F59E0B)](https://github.com/anesiem/Arabic-Bible-NDI)

Professional Arabic/English Bible broadcast controller for Android. Transform your tablet or phone into a high-quality NDI 6 source and HTTP overlay server optimized for church presentations, live streaming, and OBS/vMix broadcast integration.

---

## 🚀 What's New in Version 1.5 (Latest Release)

* 🛠 **Package & Branding Update**: Refactored package name to `com.arabicchristianmedia` and official app branding to **Arabic Bible NDI**.
* ⚡ **Editor Font Size Anti-Duplication / Throttling**: Implemented intelligent debouncing and throttling for template editor sliders and NDI frame generation loops. This eliminates frame duplication and feed congestion on `Bible NDI Lower HX` and `Bible NDI Full HX` feeds during real-time font size and template adjustments.
* 📺 **Restored NDI Device Model Naming**: NDI protocol sources cleanly broadcast your device model name (e.g., `SM-X238U - Bible-NDI-Lower` and `SM-X238U - Bible-NDI-Full`).
* 🎨 **Restored 16:9 Editor Preview Canvas**: Lower Third preview viewport maintains a 16:9 canvas aspect ratio, ensuring Lower Third cards are 100% visible and crisp.
* 📌 **Perfect Full Show / Projector Centering**: Mathematical vertical & horizontal flexbox layout for `/show` feeds and native NDI frames.
* ☀️ **Keep Device Awake**: Integrated `FLAG_KEEP_SCREEN_ON` toggle prevents tablet sleep or CPU throttling during continuous live broadcasts.
* 👉 **Gesture-Based Chapter Navigation**: Horizontal swipe gestures on the main scripture reader view allow seamless navigation between chapters.

---

## 📺 Core Features

### 📺 Dual NDI 6 Native Outputs
* **Lower Third Feed**: Optimized for broadcast streaming (OBS/vMix) with automatic RTL Arabic support and LTR English alignment.
* **Full Show Feed**: Dedicated full-screen output for projectors with vertical and horizontal centering.
* **Zero Interference**: Uses independent native buffer pools to allow both feeds to run simultaneously without flashing.
* **Discovery**: Automatically identified on the network by device model name.

### 📶 Dual-Tier NDI: Full + HX (Bandwidth Saver)
Each feed (Lower Third and Full Show) can broadcast in **two independent tiers**:
* **Full NDI** (`… - Bible-NDI-Lower`, `… - Bible-NDI-Full`): BGRA with 100% alpha transparency — designed for **overlaying verses** in OBS/vMix.
* **HX** (`… - Bible-NDI-Lower-HX`, `… - Bible-NDI-Full-HX`): bandwidth-saver tier for congested Wi-Fi.
* **Smart sending**: frames are pushed to the NDI network only when the verse/template actually changes (plus keep-alive heartbeats).

### ⚙️ Per-Source Customization (NDI Link tab)
Every NDI source is user-configurable from dropdown lists in the app's NDI tab:
* **Resolution**: 1920×1080, 1280×720, 960×540, 854×480, 640×360.
* **Frame rate**: 30, 25, 24, 15, 10, 5 fps.
