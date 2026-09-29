# Technical Guide for Developers - Arabic Bible NDI v1.5

This document explains the internal architecture and NDI 6 implementation details for developers wishing to extend or modify the application (`com.arabicchristianmedia`).

## 🏗 Architecture Overview

The app follows a modern Clean Architecture pattern with a focus on real-time networking and ultra-low latency broadcasting:

1.  **UI Layer (Jetpack Compose)**:
    *   `BibleNdiViewModel`: The central state machine. Manages dual NDI source states, Keep Screen Awake flags, HTTP server lifecycle, and Bible navigation.
    *   `BibleReaderScreen`: Implements gesture-based horizontal chapter swiping (`pointerInput`), scrollable scripture selection, and verse cued state management.
    *   `TemplateEditorScreen`: Handles independent Lower Third and Full Show customization, 16:9 preview viewport, and Color Grade Spectrum with debounced real-time updates.
2.  **Data Layer (SQLite & Repository)**:
    *   `BibleRepository`: Queries local `.db` files using raw SQL for maximum query performance.
    *   `TemplateRepository`: Persists JSON-serialized design templates in `SharedPreferences`.
3.  **Networking Layer (NDI & HTTP)**:
    *   `NdiBroadcastServer`: Multi-threaded socket server generating dynamic HTML/CSS/JS overlays and persistent SSE event streams with throttled template update broadcasts.
    *   `NdiDiscoveryBeacon`: Handles mDNS/DNS-SD registration using NDI 6 specifications.

## 🔌 NDI 6 Implementation & Latency Optimizations

Unlike standard NDI wrappers, this app uses a **direct JNI bridge** to the NDI 6 SDK for maximum performance.

### Four sources, two tiers
`NdiNativeSender` manages four independent sources (`Bible-NDI-Lower`, `Bible-NDI-Full`, `Bible-NDI-Lower-HX`, `Bible-NDI-Full-HX`). Each source is user-configurable from the NDI tab for resolution and frame rate in `NdiSourceSpec`.

### Smart Frame Caching & Editor Throttling
* **Bitmap Caching**: `NdiBroadcastServer` caches pre-rendered bitmaps to eliminate Garbage Collection (GC) pauses during streaming.
* **Slider Debouncing**: `NdiNativeSender` includes a 120ms debounce on `triggerFrame`, and `NdiBroadcastServer` throttles template update broadcasts. This prevents frame duplication and feed congestion during rapid slider adjustments in the editor tab.

### Native Bridge (`ndi_wrapper.cpp`)
* **Buffer Management**: Uses `NdiSenderContext` to manage memory regions for multiple senders.
* **Memory Safety**: Implements `std::mutex` locking during pixel-copying.
* **Zero-copy pixels**: `nativeSendVideoBitmap` locks Android `Bitmap` pixels directly via `AndroidBitmap_lockPixels`.
