# Technical Guide for Developers - Bible NDI v1.2

This document explains the internal architecture and NDI 6 implementation details for developers wishing to extend or modify the application.

## 🏗 Architecture Overview

The app follows a modern Clean Architecture pattern with a focus on real-time networking and ultra-low latency broadcasting:

1.  **UI Layer (Jetpack Compose)**:
    *   `BibleNdiViewModel`: The central state machine. Manages dual NDI source states, Keep Screen Awake flags, HTTP server lifecycle, and Bible navigation.
    *   `BibleReaderScreen`: Implements gesture-based horizontal chapter swiping (`pointerInput`), scrollable scripture selection, and verse cued state management.
    *   `TemplateEditorScreen`: Handles the complex logic for independent Lower Third and Full Show customization.
2.  **Data Layer (SQLite & Repository)**:
    *   `BibleRepository`: Queries local `.db` files using raw SQL for maximum query performance.
    *   `TemplateRepository`: Persists JSON-serialized design templates in `SharedPreferences`.
3.  **Networking Layer (NDI & HTTP)**:
    *   `NdiBroadcastServer`: Multi-threaded socket server generating dynamic HTML/CSS/JS overlays and persistent SSE event streams. Features zero-allocation bitmap caching (`cachedLowerBitmap`/`cachedShowBitmap`) and `TCP_NODELAY` socket optimizations.
    *   `NdiDiscoveryBeacon`: Handles mDNS/DNS-SD registration using NDI 6 specifications.

## 🔌 NDI 6 Implementation & Latency Optimizations

Unlike standard NDI wrappers, this app uses a **direct JNI bridge** to the NDI 6 SDK for maximum performance.

### Smart Frame Caching
To eliminate Garbage Collection (GC) pauses during streaming, `NdiBroadcastServer` caches pre-rendered 1920x1080 bitmaps. Rather than allocating 8.3 MB bitmaps 30-60 times per second, bitmaps are rendered once per state change and re-used seamlessly.

### Native Bridge (`ndi_wrapper.cpp`)
*   **Buffer Management**: Uses a custom `NdiSenderContext` to manage separate memory regions for multiple senders.
*   **Memory Safety**: Implements `std::mutex` locking during the `memcpy` phase to prevent pixel corruption (flashing) when multiple threads (Lower Third and Full Show) access the native bridge.
*   **Color Conversion**: Handled natively to ensure Android's little-endian `IntArray` (ARGB) correctly maps to NDI's `BGRA` memory layout.

### Source Discovery
The app registers two separate NDI sources using:
1.  **Native NDI Registration**: Via `NDIlib_send_create`.
2.  **Manual mDNS Beacon**: Via `NsdManager` in Kotlin to handle Android-specific network service discovery quirks and ensure visibility in modern production tools.

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
