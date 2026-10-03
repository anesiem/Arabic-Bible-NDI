# AI Development Guidelines & Core Preferences (v2)

> **Instructions for AI Assistants**: This document outlines my core engineering values, design principles, and quality standards. When working on my projects, strictly adhere to these principles to maintain consistency, reliability, and high standards across all developments. A short entry file (`.ai_instructions.md`) points here — this file is authoritative.

---

## 🎯 Core Engineering Pillars

### 1. ⚙️ Customizations & User Flexibility
* **Never Hardcode Options**: Whenever possible, expose parameters to the user rather than hardcoding values.
* **Independent Mode Configurations**: When an app has multiple outputs or modes (e.g., Lower Third vs. Full Show / Mobile vs. Tablet), ensure settings and templates for each mode are **independent** and do not unintentionally overwrite each other.
* **Granular Typography & Layout Controls**: Allow users to customize font family, sizes, colors, line heights, margins, and text alignment independently for each language or section.

---

### 2. ⚡ Fast Performance & Zero-Lag Optimization
* **Zero-Allocation Hot Loops**: Avoid allocating large objects (such as 1080p Bitmaps or byte arrays) repeatedly inside animation loops or frame renderers. Use pre-allocated, reusable buffer pools and bitmap caches.
* **Smart Caching & State Invalidation**: Only re-render or re-compute graphics when state data actually changes (`isCacheDirty` flags). Eliminate Garbage Collection (GC) pauses to ensure smooth 60 FPS performance and zero latency.
* **Low-Latency Networking**: Enable socket-level optimizations (`tcpNoDelay = true`, appropriate buffer sizes) for real-time network streams, SSE event pushes, or protocol feeds.

---

### 3. 🎨 UI/UX Design & High-Fidelity Previews
* **Accurate, Responsive Viewports**: Previews must accurately reflect the real live output (aspect ratio, text directions, font scaling, and alignment).
* **Non-Cluttered Layouts**: Maintain proper aspect ratios (e.g., 16:9) without artificially squishing or cutting off content. Controls should be compact and easy to reach.
* **Dark & Light Mode High Contrast**: Ensure text and interactive elements maintain high contrast and comfortable legibility in both light and dark themes.

---

### 4. 🌈 Rich Color Systems & Palette Management
* **Color Grade Spectrum**: Provide rich color selection options (preset broadcast color grades, rainbow hue spectrum cycles, and custom Hex inputs).
* **Recent Colors with Quick Deletion (`x`)**: When a user picks a color, automatically add it to a "Recent Colors" row. Every recent color chip must include a small `x` (delete) button so users can remove colors they no longer want.
* **Functional Background Simulations**: Enable real background modes in previews (e.g., 100% Transparent Checkerboard, Studio Dark, Pure Black, Chroma Green) so users can test visual transparency and keying.

---

### 5. 🌐 Language, Localization & RTL/LTR Formatting
* **Native Language Direction**: Respect native text directions (`RTL` for Arabic/Hebrew, `LTR` for English/European languages).
* **Bilingual Alignment**: Ensure bilingual layouts position primary language text correctly (RTL) while formatting secondary language text correctly (LTR). A user's LEFT/RIGHT alignment choice means *visual* left/right for both scripts — never map both choices to one side for the secondary language.
* **Language-Specific Numerals & Punctuation**: Respect language-specific digit formatting (e.g., Eastern Arabic numerals `١٢٣` vs. Western digits `123`) and localized punctuation (Arabic commas `،` and semicolons `؛`).
* **Dedicated Per-Language Typography**: Provide independent font family and font size controls for each language in bilingual displays.

---

### 6. 🛡️ Error Checking, Testing & Zero-Warning Policy
* **Zero Warnings & Errors**: Fix all compiler warnings and lint issues before finalizing any task.
* **Defensive Error Handling**: Wrap network, I/O, database, and hardware bridge calls in safe `try/catch` blocks with graceful fallbacks.
* **Automated Verification**: Always verify changes by running unit tests (`testDebugUnitTest`) and assembling builds (`assembleDebug` & `assembleRelease`) before deployment.

---

### 7. 📖 Clear Documentation & Easy-to-Follow Guides
* **Step-by-Step Instructions**: Keep `README.md` and `DEVELOPER_GUIDE.md` clear, detailed, and easy to follow for new developers or users.
* **Comprehensive Release Notes**: Document all new features, bug fixes, and best practices in release notes.
* **Visual Best Practices**: Embed clear screenshots, UI diagrams, and network connectivity guides in documentation.

---

### 8. 🔮 Future Updates & Forward Compatibility
* **Clean Architecture**: Follow Clean Architecture (UI -> ViewModel -> Repository -> Network/Data Source) so code is modular and easy to extend.
* **Forward Compatibility**: Ensure project configurations meet future Android requirements (e.g., Android 15 16 KB memory page size alignment, target SDK updates, modern Jetpack Compose APIs).
* **Extensible Extensions**: Structure code so adding new templates, languages, network protocols, or visual themes requires minimal modification of core logic.

---

### 9. 📴 Offline-First Operation
* **No runtime downloads**: fonts, Bible data, and all required resources must be bundled in the app. The app must work fully offline (airplane mode) — user media comes from device storage only.
* **LAN is fine, WAN is not**: local HTTP/SSE/NDI communication on the LAN is expected; nothing may require the public internet at runtime.
* **No fake capabilities**: never label a stream with a protocol tier it doesn't genuinely implement (e.g., no "NDI|HX" unless it is true HX encoding), and never advertise a discovery service that isn't genuine. No paid-SDK features or references without an explicit license.

---

### 10. 📦 Small, Shippable Releases
* **Prefer small releases**: ship independently testable increments; never bundle a big-bang rewrite.
* **Record deferred work**: anything cut from the release becomes a GitHub issue for an upcoming release — never silently dropped.
* **One concern per commit**: small commits with clear "what + why" messages on a release branch; no pushing until the work is complete and separately approved.

---

### 11. 🔍 Verify Before Changing
* **Audit first**: inspect the actual code path before diagnosing or fixing. Never guess a fix for a device-only report you cannot reproduce statically.
* **Ask for evidence**: when a bug can't be reproduced, hand over the exact isolating checks (expected vs. observed, which surface, which build) instead of changing code blindly.
* **Defensive networking, I/O, database, JNI, and lifecycle handling**; reuse hot-loop buffers; keep per-feed state independent.

---

### 12. 📱 Compact-Screen & Large-Font Accessibility
* **Layouts must survive 1.3–1.5× system font scale** on compact phones (this is a mandatory test condition, not an edge case).
* **Responsive breakpoints**: weighted/bounded titles with ellipsis, compact icon actions on narrow widths, no letter-by-letter text crushing, status-bar inset ownership, intrinsic-width badges.
* **Tablets stay first-class**: phone fixes must not regress the tablet layout — verify both.

---

### 13. 🤝 Plan-First Collaboration & Approval Gates
* **Plan before implementing**: for anything beyond a trivial fix, present the plan (scope, defaults for open questions, what stays out) and wait for explicit approval.
* **Approval gates**: ask before any code change once scope is locked, and before every GitHub operation — commit, push, issue, PR, tag, release, or publication. A "go ahead" for implementation is not a "go ahead" for GitHub.
* **Surface open questions with recommendations**: when the user delegates ("I trust your recommendation"), give a direct call with a one-line reason and record the default in the plan.

---

## 📋 AI Assistant Workflow Checklist

When assigned a task in any project, the AI assistant should follow this sequence:

1. **Understand Intent & Scope**: Clarify requirements and verify existing code architecture before modifying files.
2. **Plan & Get Approval**: Present the plan (scope, open questions with recommended defaults, explicit non-goals) and wait for the user's go-ahead.
3. **Implement Cleanly**: Apply targeted edits following the 13 pillars above, one concern per commit.
4. **Eliminate Warnings & Errors**: Verify code compiles cleanly with zero warnings or errors.
5. **Run Tests**: Execute unit tests and build tasks to verify stability.
6. **Update Documentation**: Update `README.md` and technical guides to reflect version changes and new features.
7. **Ask Before GitHub**: Never commit, push, open issues/PRs, tag, or publish releases without explicit approval for that specific operation.
