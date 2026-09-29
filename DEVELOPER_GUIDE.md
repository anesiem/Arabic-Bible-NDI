# Technical Guide for Developers - Bible NDI v1.3

This document explains the internal architecture and NDI 6 implementation details for developers wishing to extend or modify the application.

## 🏗 Architecture Overview

The app follows a modern Clean Architecture pattern with a focus on real-time networking and ultra-low latency broadcasting:

1.  **UI Layer (Jetpack Compose)**:
    *   `BibleNdiViewModel`: The central state machine. Manages dual NDI source states, Keep Screen Awake flags, HTTP server lifecycle, and Bible navigation.
    *   `BibleReaderScreen`: Implements gesture-based horizontal chapter swiping (`pointerInput`), scrollable scripture selection, and verse cued state management.
    *   `TemplateEditorScreen`: Handles the complex logic for independent Lower Third and Full Show customization, 16:9 preview viewport, and Color Grade Spectrum with recent colors management.
2.  **Data Layer (SQLite & Repository)**:
    *   `BibleRepository`: Queries local `.db` files using raw SQL for maximum query performance.
    *   `TemplateRepository`: Persists JSON-serialized design templates in `SharedPreferences`.
3.  **Networking Layer (NDI & HTTP)**:
    *   `NdiBroadcastServer`: Multi-threaded socket server generating dynamic HTML/CSS/JS overlays and persistent SSE event streams. Features zero-allocation bitmap caching (`cachedLowerBitmap`/`cachedShowBitmap`) and `TCP_NODELAY` socket optimizations.
    *   `NdiDiscoveryBeacon`: Handles mDNS/DNS-SD registration using NDI 6 specifications (`$deviceModel - Bible-NDI-Lower` and `$deviceModel - Bible-NDI-Full`).

## 🔌 NDI 6 Implementation & Latency Optimizations

Unlike standard NDI wrappers, this app uses a **direct JNI bridge** to the NDI 6 SDK for maximum performance.

### Four sources, two tiers
`NdiNativeSender` manages four independent sources: `Bible-NDI-Lower` / `Bible-NDI-Full`
(Full NDI tier, BGRA + alpha for verse overlays) and `Bible-NDI-Lower-HX` / `Bible-NDI-Full-HX`
(bandwidth-saver tier for slow networks). Each source is user-configurable from the NDI tab:
resolution (1920×1080 / 1280×720 / 960×540 / 854×480 / 640×360) and frame rate
(30/25/24/15/10/5 fps), held in `NdiSourceSpec`. Changing a dropdown restarts that source
immediately with the new spec, and the discovery beacons re-advertise it.

### Smart Frame Caching
To eliminate Garbage Collection (GC) pauses during streaming, `NdiBroadcastServer` caches
pre-rendered bitmaps. Rather than allocating 8.3 MB bitmaps 30-60 times per second, bitmaps
are rendered once per state change and re-used seamlessly. The renderer's frame version
is monotonic; senders only push a frame when the version changed, plus a 2-second
keep-alive heartbeat.

### Native Bridge (`ndi_wrapper.cpp`)
*   **Buffer Management**: Uses a custom `NdiSenderContext` (now also carrying the
    user-selected `fps_n`/`fps_d` written into the NDI video frame) to manage separate
    memory regions for multiple senders.
*   **Memory Safety**: Implements `std::mutex` locking during the pixel-copy phase to
    prevent pixel corruption (flashing) when multiple threads access the native bridge.
*   **Zero-copy pixels**: `nativeSendVideoBitmap` locks the Android `Bitmap` pixels with
    `AndroidBitmap_lockPixels` — no `IntArray` round-trip.

### Error reporting
`NdiNativeSender` records NDI errors (init failures, sender-creation failures, send errors)
as timestamped `NdiErrorEvent`s (capped at 50, send-loop errors throttled to one per 10 s).
The ViewModel exposes them as `ndiErrorEvents`; the NDI tab's **Advanced** toggle shows
the diagnostics panel (per-source status + error log), and a red banner appears even when
Advanced is off.

### Source Discovery
Each active source is registered via `NDIlib_send_create` (canonical name
`"<TabletModel> - <FeedName>"`) and additionally advertised through the hand-rolled
beacon (`NdiDiscoveryBeacon`): mDNS `_ndi._tcp` records per source, a TCP directory on
port 5960 (JSON + plain-text source list), and an NDI 6 discovery server on port 5959 —
all reflecting the current per-source resolution/fps.

## 📱 16 KB Page Size Support (Android 15+)
To ensure future-proofing for Android 15+, the project is configured with:
*   `max-page-size=16384` linker flags in `CMakeLists.txt`.
*   Static linking of required STL components to avoid alignment issues on devices with larger memory pages.

## 🛠 Extending the App

### Adding a new Bible Version
1.  Add the `.db` file to `app/src/main/assets/bible/`.
2.  Update `BibleVersion.kt` enum with the new metadata.

### Adding a new Visual Style
1.  Define the style in `TemplateStyle.kt`.
2.  Add the corresponding CSS/JS rendering logic in the `generateBroadcastHtml` function within `NdiBroadcastServer.kt`.
3.  Implement the native rendering logic in `renderCurrentFrame` using the Android `Canvas` API for NDI output.

## 🧪 Debugging
*   **Logcat Filter**: Use `NdiNativeSender|NdiBroadcastServer|NDI_Wrapper` to see the full network stack activity.
*   **Network Check**: Ensure the tablet does not have "AP Isolation" enabled on its Wi-Fi settings.
*   **In-app diagnostics**: NDI tab → enable **Advanced** to see per-source status and the timestamped NDI error log (refresh/clear buttons included).

## ✅ Testing a change before pushing to GitHub

This project has no Android SDK in CI here — verify locally with Android Studio before pushing.

1.  **Build** (Android Studio, JDK 17, NDK `28.2.13676358`):
    ```bash
    ./gradlew app:assembleDebug
    ./gradlew testDebugUnitTest
    ```
    Fix every compiler error/warning first — Kotlin warnings are treated as signals, not noise.

2.  **Install on a physical tablet** (NDI needs a real LAN; the emulator will not do):
    `./gradlew app:installDebug` or drag the APK onto the device. Tablet and test PC must be on the **same Wi-Fi** (no AP isolation).

3.  **Smoke test the NDI tab** (last tab, "NDI Link (البث)"):
    * Toggle each of the 4 sources on/off independently — in OBS (Tools → NDI) or NDI Studio Monitor you should see exactly `<TabletModel> - Bible-NDI-Lower`, `…-Lower-HX`, `…-Full`, `…-Full-HX`.
    * Change a resolution dropdown (e.g. Lower-HX → 640×360) and an fps dropdown (→ 5 fps): the source should restart and keep working; check Studio Monitor's stats for the new size/rate.
    * Tap a verse → the overlay updates within a second on all active sources.
    * Enable **Advanced**: per-source lines show the configured spec and running/stopped state; the error log should be empty ("لا توجد أخطاء").
    * The red error banner must NOT appear during normal operation.

4.  **URLs card**: from the PC browser on the same LAN, open each listed URL — `/ndi` (transparent overlay), `/show`, `/ndi/stream` (MJPEG), `/ndi/stream.png`, `/ndi/events`, `/ndi/status`, `/ndi/api/verse` — and confirm each loads.

5.  **Bandwidth sanity**: with a static verse on screen, the tablet's CPU should stay low — frames are only sent on change + a 2 s heartbeat. Watch for flicker/flashing (bitmap ownership bug would show here).

6.  **Release build** before any release commit: `./gradlew app:assembleRelease`.

Only after all of the above passes, commit on your feature branch and push / open a PR — never push straight to `master`.
