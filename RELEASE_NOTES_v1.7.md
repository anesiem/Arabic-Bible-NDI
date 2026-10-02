# Release Notes — Arabic Bible NDI v1.7

**versionCode 7 · versionName "1.7" · applicationId `com.arabicchristianmedia`**

Installs cleanly over v1.6 (versionCode 6). No data migration needed: styles, working templates, and preferences carry over; legacy `showCrossEmblem` styles become the ✝ emblem and legacy video URLs migrate best-effort.

## Highlights

* **Custom emoji/symbol emblem.** The old cross on/off toggle is now a free text field: type any emoji or symbol with the system keyboard (empty = no emblem). Saved per style, included in style export/import, and rendered everywhere — NDI Lower + Full, HTTP `/ndi` + `/show`, Compose preview, fullscreen overlay.
* **Real animated video backgrounds on NDI.** Pick a local MP4 and it decodes and loops *behind* the card, effects, text, and emblem on the NDI canvas — fully animated, not a frozen frame. Each feed (Lower/Full) has its own decoder. Motion ON animates (up to the 30fps cap); Motion OFF freezes on the current frame. A Video Opacity slider (0–100%) appears only when the custom-video background is selected.
* **Videos stored safely.** Picked videos are copied into the app's private storage and referenced by opaque ID. The old `/ndi/video_file?path=` endpoint — which let any LAN client read arbitrary files — is removed; videos are now served by ID only, streamed in chunks with Range/seek support.
* **Motion cap raised to 30fps.** The per-feed Motion toggle now re-renders up to 30fps (was 15). Still fully in your hands: off by default, and slow tablets can leave it off.
* **Direction-aware alignment.** The Full Show citation is no longer force-centered: LEFT/CENTER/RIGHT is now obeyed consistently on NDI, HTTP `/show`, the Compose preview, and the fullscreen overlay. English text is now bidi-correct — RIGHT means visual right for English too (it used to stay left).
* **Better recent-color chips.** Wider chips with a larger swatch and the hex code on the chip. Tapping the chip body applies the color; the × stays a separate delete target.
* **Compact-phone & large-font fixes.** The editor header, the Color Grade row, and the main top bar now survive narrow screens and large system fonts: weighted titles with ellipsis, the Color Grade action collapsing to an icon on small widths, a proper status-bar inset, and a shortened subtitle. The LIVE badge keeps its intrinsic width.
* **Filtered sub-1080p NDI.** Feed resolutions below 1080p are now downscaled with bitmap filtering instead of nearest-neighbor — no more blocky output on 720p/540p/480p/360p feeds.

## Stability & security (under the hood)

* NDI sender teardown can no longer race a frame send (closed-flag checked under the feed lock); one feed's crash can't kill the other's send loop (SupervisorJob).
* SSE overlay connections are now truly persistent — they used to be closed immediately and only updated on the browser's ~3s reconnect; plus a 15s keep-alive ping and serialized writes.
* Bible database copy is atomic (temp file + rename), versioned, indexed, and loads on a background thread with a progress indicator instead of freezing first launch.
* The tablet now holds a high-performance Wi-Fi lock and a multicast lock while broadcasting (NDI discovery stays reliable on power-saving Wi-Fi).
* Frame version counter is now atomic across threads.

## For OBS / vMix users

* Still exactly two sources: `<YourTabletModel> - Bible-NDI-Lower` and `<YourTabletModel> - Bible-NDI-Full`.
* HTTP overlays unchanged in behavior: `/ndi`, `/show`, MJPEG, snapshots, and the All-URLs card. Note: `/ndi/video_file?path=` is gone (security); custom videos play via `/ndi/video?id=`.

## Known notes

* The separate "verse number stays centered" report is still under investigation — embedded verse numbers were intentionally not touched.
* A freeform visual designer is planned for v2.0. HDMI Presentation output is deferred pending a separately scoped plan.
