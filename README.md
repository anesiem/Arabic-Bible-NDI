# Arabic Bible NDI (Version 1.6 - Latest Release)

Professional Arabic/English Bible broadcast controller for Android. Transform your tablet or phone into a high-quality NDI 6 source and HTTP overlay server optimized for church presentations, live streaming, and OBS/vMix broadcast integration.

---

## 🚀 What's New in Version 1.6 (Latest Release)

* 📺 **Two genuine NDI sources**: exactly `… - Bible-NDI-Lower` (Lower Third) and `… - Bible-NDI-Full` (Full Show). The previous "HX" tiers were **not** genuine NDI|HX encoding (that requires the paid NDI Advanced SDK, which this project does not use) — they were smaller plain-NDI streams with a misleading name, so they were removed.
* 🛰 **Fake discovery removed**: the in-app mDNS/UDP/TCP "discovery beacon" never spoke the real NDI discovery protocol. Genuine NDI discovery is handled by the NDI 6 runtime itself — the fake beacon is deleted, and the two real senders appear in NDI Studio Monitor by device model name (e.g. `SM-X238U - Bible-NDI-Lower`).
* 🎞 **Per-feed Motion toggle**: each feed can continuously re-render animated template backgrounds (capped at 15fps internally to save battery; the NDI metadata still reports your chosen frame rate). Motion off (default) keeps zero-bandwidth dirty-frame sending + 2s heartbeat.
* 🎨 **Independent style collections**: Lower Third and Full Show now have completely separate style lists. Selecting a style restores all its saved values; editing without saving shows a "Modified" badge and clears the highlight; **Save As New** never overwrites; long-press deletes with confirmation. Each tab's working values persist across restarts.
* ✨ **Text shadow & card glow are independent**: separate on/off switches and colors (both default black). Card glow only applies where a card exists (never on full-bleed Full Show).
* 🔄 **Trailing-edge SSE**: template slider drags now always deliver the final state to HTTP overlays (~100ms after the last change), and every SSE message carries a monotonic `stateVersion` so browser sources re-sync correctly after reconnects.
* ⚙️ **More resolutions & frame rates**: 1920×1080, 1600×900, 1360×768, 1280×720, 960×540, 854×480, 640×360 (all 16:9) and 60, 50, 30, 25, 24, 15, 10, 5 fps — per feed, independently persisted.
* 🧵 **Thread-safe sending**: per-feed locks serialize render+send so the send loop and verse-change triggers can never draw into / recycle the same bitmap concurrently.

---

## 📺 Core Features

### 📺 Two NDI 6 Native Outputs
* **Lower Third Feed** (`… - Bible-NDI-Lower`): optimized for broadcast streaming (OBS/vMix) with automatic RTL Arabic support and LTR English alignment.
* **Full Show Feed** (`… - Bible-NDI-Full`): dedicated full-screen output for projectors with vertical and horizontal centering.
* **Zero Interference**: independent native buffer pools and per-feed locks let both feeds run simultaneously without flashing or torn frames.
* **Discovery**: the NDI 6 runtime advertises both sources under your device model name — look for them directly in NDI Studio Monitor, OBS, or vMix.

### 🌐 HTTP Overlays (for OBS / vMix browser sources)
* `/ndi` — Lower Third overlay, `/show` — Full Show overlay, plus MJPEG stream, snapshots, and a status API.
* Live updates over Server-Sent Events with reconnect-safe state versions.

### ⚙️ Per-Source Customization (Broadcast tab)
Each NDI source is independently configurable from dropdown lists:
* **Enabled** on/off, **Resolution** (7 options up to 1080p, all 16:9), **Frame rate** (8 integer rates), **Motion** on/off.
* Changing any setting restarts that source immediately; settings persist across restarts.

### 🎨 Template Editor
* Separate Lower Third / Full Show tabs, each with its own style collection and working values.
* Live preview (16:9), workflow tips, modified badge, Save As New, long-press delete, per-tab reset.
* **Style sharing:** long-press any style for Export (share sheet), Update from import (paste JSON or pick a .json file), or Delete; an Import button in the style header imports shared JSON as a brand-new style. Full control, device-to-device.
* Independent text shadow and card glow controls with full color pickers; bilingual Arabic/English typography; animated motion backgrounds; custom video backgrounds.
* **57 bundled Arabic Google Fonts** (SIL Open Font License) in a dropdown — the same TTFs render on the NDI canvas, the HTTP overlays, and the editor preview, fully offline. No internet needed at the venue.

---

## 📲 Install

Requires Android 7.0+. Download the APK from the [Releases page](https://github.com/anesiem/Arabic-Bible-NDI/releases) and install.

> **Note:** v1.5 changed the application ID to `com.arabicchristianmedia`, so it installs as a separate app from v1.4 and earlier (templates do not migrate automatically). v1.6 (versionCode 6) installs cleanly over v1.5.
