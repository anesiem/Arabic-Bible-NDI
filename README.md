# Bible NDI (Version 1.0)

Professional Arabic/English Bible broadcast controller for Android. Transform your tablet or phone into a high-quality NDI 6 source and HTTP overlay server optimized for church presentations and live streaming.

## 🚀 Features

### 📺 Dual NDI 6 Native Outputs
*   **Lower Third Feed**: Optimized for broadcast streaming (OBS/vMix) with automatic RTL Arabic support and LTR English alignment.
*   **Full Show Feed**: Dedicated full-screen output for projectors with professional vertical/horizontal centering.
*   **Zero Interference**: Uses independent native buffer pools to allow both feeds to run simultaneously without flashing.
*   **Discovery**: Automatically identified on the network by device model name (e.g., `SM-X238U (Bible-NDI-Lower)`).

### 🌐 HTTP & Web Overlays
*   **HTML5 Overlays**: Transparent browser sources for OBS at `/ndi` and projector views at `/show`.
*   **Zero Latency**: Real-time updates via Server-Sent Events (SSE).
*   **Motion Graphics**: Built-in animated backgrounds and support for custom local MP4 video loops.

### 📖 Scripture Management
*   **Bilingual Support**: Display Arabic (SVD) and English (ASV/KJV/WEB) simultaneously.
*   **Hierarchical Picker**: Efficient OT/NT -> Book -> Chapter -> Verse selection logic.
*   **Integrated Search**: Instant navigation to any verse across the entire Bible.
*   **SQLite Powered**: High-performance local database access for instantaneous verse retrieval.

### 🎨 Design & Customization
*   **Total Typography Control**: Independent font families, sizes, colors, and styles (Bold/Italic) for both languages.
*   **Visual Styles**: Glassmorphism, classic banners, liturgical gold, and pure transparent modes.
*   **Bilingual Spacing**: Fine-tune the vertical gap between languages for maximum readability.
*   **Dark Mode Ready**: Optimized UI with high-contrast verse highlighting.

## 🛠 Developer Setup

### Prerequisites
*   Android Studio Ladybug or newer.
*   NDI 6 SDK for Android.
*   NDK Version `28.2.13676358` or higher.
*   Minimum SDK: 24 (Android 7.0).
*   Target SDK: 36 (Android 15 ready, supports 16 KB page sizes).

### Native Bridge
The core NDI functionality is handled by a custom JNI bridge in `app/src/main/cpp/ndi_wrapper.cpp`. It links directly with `libndi.so` to provide maximum performance without the overhead of standard Java wrappers.

### Building
1.  Ensure `libndi.so` is placed in `app/src/main/jniLibs/[ABI]/`.
2.  NDI SDK headers should be in `app/src/main/cpp/include/`.
3.  Perform a standard Gradle Sync and Build.

## 📡 Connectivity
The app hosts its own local server. Ensure your tablet and production PC are on the same local network.
*   **Overlay URL**: `http://[TABLET_IP]:8080/ndi`
*   **Projector URL**: `http://[TABLET_IP]:8080/show`
*   **Stream URL**: `http://[TABLET_IP]:8080/ndi/stream`

## ⚖️ License
This project uses the NDI® SDK. NDI® is a registered trademark of Vizrt NDI AB. Please refer to the NDI SDK License Agreement for usage terms.

---
**Version 1.0 Stable - Release Candidate 1**
