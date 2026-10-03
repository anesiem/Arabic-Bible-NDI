# Arabic Bible NDI (Version 1.7 - Latest Release)

Professional Arabic/English Bible broadcast controller for Android. Transform your tablet or phone into a high-quality NDI 6 source and HTTP overlay server optimized for church presentations, live streaming, and OBS/vMix broadcast integration.

**Fully offline** — no internet needed at the venue. Fonts, Bible data, and all resources are bundled; your media comes from the device.

---

## 🚀 What's New in Version 1.7 (Latest Release)

* 😊 **Custom emoji/symbol emblem**: the old cross toggle is now a free text field — type any emoji or symbol (empty = none). Saved per style, in style exports, rendered on NDI, HTTP, preview, and fullscreen.
* 🎬 **Real animated video backgrounds on NDI**: pick a local MP4 and it loops *behind* the card and text on the NDI canvas — fully animated, per-feed decoders. Motion ON animates (up to 30fps); Motion OFF freezes the frame. Videos are stored privately and served to browsers by secure ID (the old `?path=` file endpoint is removed).
* ⚡ **Motion cap raised to 30fps** (was 15).
* ↔️ **Direction-aware alignment**: Full Show no longer force-centers — LEFT/CENTER/RIGHT is obeyed everywhere, and English text is now bidi-correct.
* 🎨 **Better color chips** (larger swatch + hex on the chip) and **compact-phone / large-font layout fixes** across the editor and top bar.
* 🔍 **Filtered sub-1080p NDI** downscaling — no more blocky 720p/480p feeds.
* 🛡 **Under the hood**: race-free NDI teardown, truly persistent SSE overlays, atomic Bible DB install with background loading, Wi-Fi/multicast locks while broadcasting.

See [RELEASE_NOTES_v1.7.md](RELEASE_NOTES_v1.7.md) for the full list.

## 🚀 What's New in Version 1.6

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
* `/lower` — Lower Third overlay, `/full` — Full Show overlay, plus MJPEG stream (`/stream`), snapshots, remote API, and vMix XML feed.
* Live updates over Server-Sent Events with reconnect-safe state versions.
* **v1.8**: Remote trigger API (`/api/trigger?ref=John+3:16`), phone-friendly `/remote` control page, and `/bibleshow.xml` vMix feed.

### ⚙️ Per-Source Customization (Broadcast tab)
Each NDI source is independently configurable from dropdown lists:
* **Enabled** on/off, **Resolution** (7 options up to 1080p, all 16:9, filtered downscaling below 1080p), **Frame rate** (8 integer rates), **Motion** on/off (up to 30fps).
* Changing any setting restarts that source immediately; settings persist across restarts.

### 🎨 Template Editor
* Separate Lower Third / Full Show tabs, each with its own style collection and working values.
* Live preview (16:9), workflow tips, modified badge, Save As New, long-press delete, per-tab reset.
* **Style sharing:** long-press any style for Export (share sheet), Update from import (paste JSON or pick a .json file), or Delete; an Import button in the style header imports shared JSON as a brand-new style. Full control, device-to-device.
* Independent text shadow and card glow controls with full color pickers; custom emoji/symbol emblem; bilingual Arabic/English typography with direction-aware alignment; animated motion backgrounds; custom looping video backgrounds with opacity control.
* **57 bundled Arabic Google Fonts** (SIL Open Font License) in a dropdown — the same TTFs render on the NDI canvas, the HTTP overlays, and the editor preview, fully offline. No internet needed at the venue.

---

## 📲 Install

Requires Android 7.0+. Download the APK from the [Releases page](https://github.com/anesiem/Arabic-Bible-NDI/releases) and install.

> **Note:** v1.5 changed the application ID to `com.arabicchristianmedia`, so it installs as a separate app from v1.4 and earlier (templates do not migrate automatically). v1.7 (versionCode 7) installs cleanly over v1.5/v1.6.

---

## ❤️ Sponsor

Arabic Bible NDI is built for churches and ministries. If it serves your congregation, consider sponsoring its continued development — sponsor links will appear here.
