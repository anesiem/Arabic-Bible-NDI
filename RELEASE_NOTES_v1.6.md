# Release Notes — Arabic Bible NDI v1.6

**versionCode 6 · versionName "1.6" · applicationId `com.arabicchristianmedia`**

Installs cleanly over v1.5 (versionCode 5). v1.5 changed the application ID, so v1.5/v1.6 install as a separate app from v1.4 and earlier (templates do not migrate automatically).

## Highlights

* **Two genuine NDI sources.** The app now exposes exactly `… - Bible-NDI-Lower` (Lower Third) and `… - Bible-NDI-Full` (Full Show). The old "HX" tiers were removed: without the paid NDI Advanced SDK they were plain smaller NDI streams, not true NDI|HX encoding, and the name was misleading.
* **Fake discovery beacon deleted.** The in-app mDNS/UDP/TCP beacon never spoke the real NDI discovery protocol. Real discovery is handled by the NDI 6 runtime — both senders appear in NDI Studio Monitor under your tablet's model name. HTTP overlays and NDI sending work exactly as before.
* **Per-feed Motion toggle.** Each feed can now continuously re-render animated template backgrounds (capped at 15fps internally to protect battery; NDI metadata still reports your chosen frame rate). Off by default, keeping zero-bandwidth dirty-frame sending + 2s heartbeat.
* **Independent style system.** Lower Third and Full Show have fully separate style collections: selecting a style restores all its values, edits go live immediately and show a "Modified" badge, **Save As New** never overwrites an existing style, long-press deletes with confirmation, and reset restores the selected style (or factory defaults). Everything persists across restarts.
* **Independent text shadow & card glow.** Separate switches and colors for each (both default black). Card glow only applies where a card exists — never on full-bleed Full Show. Old `showDropShadow` settings migrate automatically.
* **Trailing-edge SSE + state versions.** Slider drags in the editor now always deliver their final state to HTTP overlays (~100ms after the last change), and every SSE message carries a monotonic `stateVersion` so browser sources re-sync correctly after a reconnect.
* **More resolutions & frame rates.** 1920×1080, 1600×900, 1360×768, 1280×720, 960×540, 854×480, 640×360 (all labeled 16:9) and 60/50/30/25/24/15/10/5 fps — each feed configured and persisted independently.
* **Thread-safe NDI sending.** Per-feed locks serialize frame rendering + sending so rapid verse changes during Motion mode can never tear or recycle a frame mid-send.

## For OBS / vMix users

* Find exactly two sources in NDI Studio Monitor / OBS / vMix: `<YourTabletModel> - Bible-NDI-Lower` and `<YourTabletModel> - Bible-NDI-Full`.
* HTTP browser-source overlays are unchanged: `/ndi` (Lower Third), `/show` (Full Show), plus MJPEG and snapshot endpoints listed in the app's Broadcast tab.

## Known notes

* v1.7 will add HDMI Presentation output; a freeform visual designer is planned for v2.0.
* The v1.5 release APK was built with stale version metadata (code 5 / "1.4"); v1.6 corrects this (code 6 / "1.6"). No v1.5 re-cut is needed since v1.6 upgrades over it.
