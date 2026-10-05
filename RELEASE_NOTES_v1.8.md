# Release Notes — v1.8

**Version:** 1.8 (versionCode 8)

## What's New

### 🎨 Renderer Fixes & Polish
- **No verse on launch**: The app now starts off-air with no verse selected. Tap a verse to go live.
- **English alignment**: English text stays left-aligned for LEFT and RIGHT modes; only CENTER centers it. Arabic follows your selected alignment.
- **Font size parity**: Preview, NDI, and HTTP overlays now use matching verse/citation sizes. The preview is the reference.
- **Card glow rewrite**: Replaced the blur-based glow with deterministic layered rounded rectangles for consistent rendering.
- **Directional text shadows**: Control shadow thickness (blur), distance (offset), and 8-direction compass angle per style.

### 🌐 New HTTP API & URIs
The HTTP server now uses clean, industry-standard URIs (no `/ndi` prefix):

| Endpoint | Description |
|----------|-------------|
| `/lower` | Lower third transparent overlay |
| `/full` | Full show overlay |
| `/events` | SSE verse events |
| `/snapshot.png` | PNG snapshot |
| `/stream` | MJPEG stream |
| `/api/verse` | Current verse JSON |
| `/api/trigger?ref=John+3:16` | Trigger a verse remotely |
| `/api/clear` | Clear (off-air) |
| `/api/videos` | List background videos |
| `/api/status` | Server status |
| `/video?id=...` | Serve video by ID |
| `/remote` | Phone-friendly remote control page |
| `/bibleshow.xml` | vMix BibleShow XML feed |
| `/fonts/*` | Bundled fonts |

**Remote trigger examples:**
```
/api/trigger?ref=John+3:16
/api/trigger?ref=يوحنا+3:16
/api/trigger?ref=Jn+3:16
/api/trigger?book=43&chapter=3&verse=16
/api/trigger?ref=John+3:16&target=show
/api/clear
```

### 📱 Remote Control Page (`/remote`)
- Phone-friendly, one-handed operation
- Follows your device's light/dark theme automatically
- Type a reference, choose target (Lower/Show/Both), tap to trigger
- Fully offline — served by the tablet

### 🗣️ Three-Way Language Mode
Replaces the bilingual toggle with **Arabic / English / Both**:
- **Arabic Only**: Arabic verse + citation
- **English Only**: English verse + citation
- **Both**: Arabic + English (as before)

### 🎬 vMix Integration
- **`/bibleshow.xml`**: BibleShow-compatible XML feed (text only; vMix controls formatting)
- **SetText push**: Optionally push verse text directly to your vMix title fields via LAN
  - Settings: vMix IP, port (default 8088), input name/number/GUID
  - Map fields: Arabic verse, Arabic citation, English verse, English citation
  - Test button to verify connectivity
  - Enable "Push on trigger" to auto-send on every verse

### ✨ Three New Factory Styles
1. **Sanctuary Gold** — Charcoal card, gold citation, candle-glow motion
2. **Morning Mercy** — Warm off-white, soft and highly readable, golden rays
3. **Upper Room** — Transparent, large centered text, minimal, floating particles

### 🔧 NDI Color Fix
Fixed the red/blue channel swap: `#FF0000` now appears correctly red on all NDI receivers (was showing blue). The NDI frame format declaration now correctly specifies RGBA.

### 🎥 Video Management
- Video picker shows the opaque video ID and its local URL (`/video?id=...`)
- New `GET /api/videos` lists all background videos with IDs, names, and sizes

## Editor Improvements
- **Language section**: New 3-way selector (Arabic/English/Both) with clear labels
- **Shadow controls**: Thickness slider, distance slider, and 8-direction compass picker
- **"You are editing" indicator**: Always shows which target (Lower Third / Full Show) you're editing
- **Video info**: Picker displays the video ID and direct URL for reference
- **Motion speed slider**: 0.00 to 3.00 with decimal precision (0 pauses the video)
- **Card transparent button**: One-tap "شفاف" button to make the card fully transparent
- **Layer order labels**: Editor sections now indicate background vs. foreground layers

## Motion Backgrounds & Polish
- **8 animated presets**: Royal Purple Silk, Ethereal Blue Waves, Candle Liturgical Glow, Golden Divine Rays, Emerald Garden Waves, Rose Dawn Glow, Golden Particles, Particle Stars — from dark reverent to light joyful, all with transparency support
- **Animation opacity**: Per-style opacity slider for all motion backgrounds (not just video)
- **NDI layer fix**: Card glow now renders behind the video as an outer halo (was covering the video)
- **English citation color**: In English-only mode, citations use the English color for clear design
- **Card padding**: Increased padding for better spacing between card edge and text
- **HTTP 16:9**: Overlays now maintain true 16:9 aspect ratio at any viewport size

## Technical Details
- All new style fields (shadow params, language mode) are present in all 10 factory styles
- Existing user styles migrate automatically (bilingual → Both, non-bilingual → Arabic Only)
- Shadow defaults preserve the previous appearance
- Fully offline at runtime; LAN integrations (vMix, remote API) require local network only

## Upgrade Notes
- **Breaking**: HTTP URIs have changed (no `/ndi` aliases). Update your OBS/vMix browser sources:
  - Old: `http://<IP>:8080/ndi` → New: `http://<IP>:8080/lower`
  - Old: `http://<IP>:8080/show` → New: `http://<IP>:8080/full`
  - Old: `http://<IP>:8080/ndi/stream` → New: `http://<IP>:8080/stream`
- Your custom styles migrate automatically; no action needed.
