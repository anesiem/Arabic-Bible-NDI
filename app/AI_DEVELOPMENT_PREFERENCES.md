# AI Development Guidelines & Core Preferences

> **Instructions for AI Assistants**: This document outlines my core engineering values, design
principles, and quality standards. When working on my projects, strictly adhere to these principles
to maintain consistency, reliability, and high standards across all developments.

---

* **Never Hardcode Options**: Whenever possible, expose parameters to the user rather than
  hardcoding values.
* **Independent Mode Configurations**: When an app has multiple outputs or modes (e.g., Lower Third
  vs. Full Show / Mobile vs. Tablet), ensure settings and templates for each mode are **independent
  ** and do not unintentionally overwrite each other.
* **Granular Typography & Layout Controls**: Allow users to customize font family, sizes, colors,
  line heights, margins, and text alignment independently for each language or section.

---

* **Zero-Allocation Hot Loops**: Avoid allocating large objects (such as 1080p Bitmaps or byte
  arrays) repeatedly inside animation loops or frame renderers. Use pre-allocated, reusable buffer
  pools and bitmap caches.
* **Smart Caching & State Invalidation**: Only re-render or re-compute graphics when state data
  actually changes (`isCacheDirty` flags). Eliminate Garbage Collection (GC) pauses to ensure smooth
  60 FPS performance and zero latency.
* **Low-Latency Networking**: Enable socket-level optimizations (`tcpNoDelay = true`, appropriate
  buffer sizes) for real-time network streams, SSE event pushes, or protocol feeds.

---

* **Accurate, Responsive Viewports**: Previews must accurately reflect the real live output (aspect
  ratio, text directions, font scaling, and alignment).
* **Non-Cluttered Layouts**: Maintain proper aspect ratios (e.g., 16:9) without artificially
  squishing or cutting off content. Controls should be compact and easy to reach.
* **Dark & Light Mode High Contrast**: Ensure text and interactive elements maintain high contrast
  and comfortable legibility in both light and dark themes.

---

* **Color Grade Spectrum**: Provide rich color selection options (preset broadcast color grades,
  rainbow hue spectrum cycles, and custom Hex inputs).
* **Recent Colors with Quick Deletion (`x`)**: When a user picks a color, automatically add it to
  a "Recent Colors" row. Every recent color chip must include a small `x` (delete) button so users
  can remove colors they no longer want.
* **Functional Background Simulations**: Enable real background modes in previews (e.g., 100%
  Transparent Checkerboard, Studio Dark, Pure Black, Chroma Green) so users can test visual
  transparency and keying.

---

* **Native Language Direction**: Respect native text directions (`RTL` for Arabic/Hebrew, `LTR` for
  English/European languages).
* **Bilingual Alignment**: Ensure bilingual layouts position primary language text correctly (RTL)
  while formatting secondary language text correctly (LTR).
* **Language-Specific Numerals & Punctuation**: Respect language-specific digit formatting (e.g.,
  Eastern Arabic numerals `١٢٣` vs. Western digits `123`) and localized punctuation (Arabic commas
  `،` and semicolons `؛`).
* **Dedicated Per-Language Typography**: Provide independent font family and font size controls for
  each language in bilingual displays.

---

* **Zero Warnings & Errors**: Fix all compiler warnings and lint issues before finalizing any task.
* **Defensive Error Handling**: Wrap network, I/O, database, and hardware bridge calls in safe
  `try/catch` blocks with graceful fallbacks.
* **Automated Verification**: Always verify changes by running unit tests (`testDebugUnitTest`) and
  assembling builds (`assembleDebug` & `assembleRelease`) before deployment.

---

* **Step-by-Step Instructions**: Keep `README.md` and `DEVELOPER_GUIDE.md` clear, detailed, and easy
  to follow for new developers or users.
* **Comprehensive Release Notes**: Document all new features, bug fixes, and best practices in
  release notes.
* **Visual Best Practices**: Embed clear screenshots, UI diagrams, and network connectivity guides
  in documentation.

---

* **Clean Architecture**: Follow Clean Architecture (UI -> ViewModel -> Repository -> Network/Data
  Source) so code is modular and easy to extend.
* **Forward Compatibility**: Ensure project configurations meet future Android requirements (e.g.,
  Android 15 16 KB memory page size alignment, target SDK updates, modern Jetpack Compose APIs).
* **Extensible Extensions**: Structure code so adding new templates, languages, network protocols,
  or visual themes requires minimal modification of core logic.

---


When assigned a task in any project, the AI assistant should follow this sequence:

1. **Understand Intent & Scope**: Clarify requirements and verify existing code architecture before
   modifying files.
2. **Implement Cleanly**: Apply targeted edits following the 8 pillars above.
3. **Eliminate Warnings & Errors**: Verify code compiles cleanly with zero warnings or errors.
4. **Run Tests**: Execute unit tests and build tasks to verify stability.
5. **Update Documentation**: Update `README.md` and technical guides to reflect version changes and
   new features.
6. **Deploy & Ask Before Publishing**: Deploy and verify on connected devices/tablets, then ask for
   explicit user approval before publishing releases to GitHub or remote repositories.
