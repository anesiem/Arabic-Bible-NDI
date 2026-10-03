# Technical Guide for Developers - Arabic Bible NDI v1.7

This document explains the internal architecture and NDI 6 implementation details for developers wishing to extend or modify the application (`com.arabicchristianmedia`).

## 🏗 Architecture Overview

1.  **UI Layer (Jetpack Compose)**:
    *   `BibleNdiViewModel`: The central state machine. Manages two NDI source states (Lower/Full, each with enabled/resolution/fps/motion), Keep Screen Awake flag, HTTP server lifecycle, Bible navigation, and the per-tab style system (working templates, highlight ids, dirty flags).
    *   `BibleReaderScreen`: gesture-based horizontal chapter swiping, scrollable scripture selection, verse cue state.
    *   `TemplateEditorScreen`: independent Lower Third / Full Show tabs; per-tab style collections with strict `isFullScreen` partitioning; Save As New; long-press delete; independent text-shadow and card-glow controls; 16:9 preview viewport with color-accurate shadow/glow preview.
    *   `BroadcastControlScreen`: exactly two NDI source rows (Lower, Full) with enable switch, resolution/fps dropdowns, and motion toggle; advanced NDI diagnostics (error log, per-source status).
2.  **Data Layer (SQLite & Repository)**:
    *   `BibleRepository`: queries local `.db` files using raw SQL.
    *   `TemplateRepository`: persists JSON-serialized style templates plus per-tab working templates, highlight ids (`null` = modified/unsaved), and dirty flags in `SharedPreferences`. Legacy `showDropShadow` migrates to the independent `textShadowEnabled`/`cardGlowEnabled` flags (both on, black). Legacy `tpl_modern_glass` highlight migrates to `tpl_transparent_alpha`.
3.  **Networking Layer (NDI & HTTP)**:
    *   `NdiNativeSender`: JNI bridge to the NDI 6 SDK managing exactly two senders (`Bible-NDI-Lower`, `Bible-NDI-Full`). Per-feed `SenderSession` coroutines do dirty-frame sending + 2s heartbeat; motion mode re-renders at up to ~30fps. Per-feed locks serialize render+send across the send loop and `triggerFrame()`.
    *   `NdiBroadcastServer`: hand-rolled HTTP server generating HTML/CSS/JS overlays (`/overlay`, `/overlay/full`), MJPEG, snapshots, status API, remote trigger API, and SSE event streams with leading-edge throttle + trailing-edge delivery and a monotonic `stateVersion` per message for reconnect re-sync.
    *   NDI discovery is handled by the NDI 6 runtime itself — there is no in-app discovery beacon (the fake mDNS/UDP/TCP beacon was deleted in v1.6).

## 🔌 NDI 6 Implementation

### Two genuine senders, no HX
`NdiNativeSender.ALL_FEEDS = [Bible-NDI-Lower, Bible-NDI-Full]`. Source names on the wire are `<Build.MODEL> - <FeedKey>` (never localhost/IP). Each feed's `NdiSourceSpec` (resolution, fps, motion) is user-configurable and validated against `RESOLUTION_OPTIONS` (7 × 16:9 up to 1080p) and `FPS_OPTIONS` (8 integer rates) — nothing is hardcoded in the UI.

### Smart sending
* **Static (motion off, default)**: frames are pushed only when `frameVersion` changes (dirty-frame detection) plus a 2000ms heartbeat. Static verses cost ~zero bandwidth.
* **Motion on**: if the template has animated content, the feed re-renders every 33ms (up to ~30fps) into dedicated per-feed reused bitmaps (`motionLowerBitmap`/`motionShowBitmap`), never the shared static cache. Declared NDI metadata keeps the user's chosen fps. Verse/template changes trigger an immediate re-render via debounced `triggerFrame()` (120ms) rather than waiting for the tick.

### Thread safety
* Per-feed `Any` locks (`feedLocks`) in `NdiNativeSender` wrap provider-render + `sendBitmapToPtr` in both the send loop and `triggerFrame()`, and guard teardown recycling in `stopSource()`/`stopAll()`. Lock order is always `activeSenders → feedLock`, never the reverse. Lower and Full never block each other.
* The native send (`nativeSendVideoBitmap`) locks `Bitmap` pixels directly via `AndroidBitmap_lockPixels` — zero-copy, no `IntArray` round-trip. Reused downscale bitmaps per session for reduced resolutions.

### Native Bridge (`ndi_wrapper.cpp`)
Feed-agnostic: `nativeInitialize`, `nativeCreateSender(name, fps)`, `nativeDestroySender(ptr)`, `nativeSendVideoBitmap(ptr, bitmap, w, h)`. JNI names match `com.arabicchristianmedia.server.NdiNativeSender`.

## 🌐 HTTP/SSE details

* `broadcastStateThrottled()`: leading-edge 100ms throttle; a trailing job guarantees the final state is delivered ~100ms after the last change (payload built at fire time, so it always carries the latest state).
* `buildJsonState()` includes `stateVersion` (monotonic `frameVersion`, bumped on every verse/template/live change). Browser clients keep `lastStateVersion` and drop stale/out-of-order messages; after an SSE reconnect the first message re-syncs.
* Full Show edits go through `updateShowTemplate()` → same pipeline as Lower Third (previously only the NDI feed refreshed).

## 🧪 Tests

* `NdiSourceSpecTest` (plain JUnit): feed list, resolution/fps option lists, default spec, display-name format.
* `TemplateRepositoryTest` (Robolectric): style partitioning, per-tab working/dirty/highlight persistence, null-highlight round-trip, legacy highlight migration, legacy `showDropShadow` migration, shadow/glow round-trip.
* `ArabicFontsTest` (plain JUnit): font registry integrity (57 families, no duplicates, System-first display names) and that every referenced TTF exists under `src/main/assets/fonts`.
* `TemplateRepositoryTest` (Robolectric): style export/import JSON round-trip and rejection of invalid payloads.

### 🔤 Bundled offline fonts
* All 57 Arabic-subset Google Fonts (OFL) ship as TTFs in `app/src/main/assets/fonts/` (`<id>-400.ttf`, plus `-700.ttf` where the family has a true bold) — ~11 MB.
* Single source of truth: `model/ArabicFonts.kt` drives the editor dropdown, the NDI canvas loader (`getBestTypeface` → `Typeface.createFromAsset`, cached per family+style in a `ConcurrentHashMap`), and the HTTP overlay `@font-face` rules.
* The tablet serves its own fonts at `/fonts/<file>.ttf` (allowlisted, `font/ttf`, immutable cache), so `/overlay` and `/overlay/full` overlays render the exact same typefaces with no internet access.
* Adding a font = drop the TTFs in `assets/fonts/` + one `BundledFont(...)` line; everything else follows automatically.

## 🔨 Build & pre-ship checklist

Per `AI_DEVELOPMENT_PREFERENCES.md` (repo root, authoritative): no hardcoded user options; zero compiler/lint warnings; run `testDebugUnitTest`, `assembleDebug`, and `assembleRelease` in Android Studio (this environment has no Android SDK); update README, this guide, and release notes; no push/tag/release without Ashraf's explicit approval.
