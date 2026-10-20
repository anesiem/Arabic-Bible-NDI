# Arabic Bible NDI

**Professional Arabic/English Bible broadcast control for Android.**

Turn your tablet or phone into a broadcast studio source: put any verse on air in
seconds — beautifully styled, in Arabic and English — over **NDI®**, **HTTP
overlays for OBS & vMix**, and a full remote-control API. Built for churches,
ministries, and live production. **Fully offline** — fonts, Bible texts, and
every resource are bundled on the device; no internet is needed at the venue.

> 📲 **Coming soon to Google Play.** Today, download the signed APK from the
> [Releases page](https://github.com/anesiem/Arabic-Bible-NDI/releases).

---

## ✨ What It Does

You prepare the service. During the service, one tap puts the verse on the
projector, the stream, or both — in a designed lower-third or a full-screen
show, with motion backgrounds, your church's colors, and your choice of Arabic
calligraphy. Switch verses live, in reading order, without ever touching the
production computer.

---

## 📺 Broadcast Outputs

### Two genuine NDI® 6 sources
- **`… - Bible-NDI-Lower`** — a designed lower third for streams and IMAG.
- **`… - Bible-NDI-Full`** — a full-screen show for projectors and screens.
- Both run **simultaneously and independently**: separate styles, resolutions
  (up to **1080p**, all 16:9), frame rates (60/50/30/25/24/15/10/5 fps), and
  Motion settings per feed.
- Real NDI discovery: the sources appear under your device's name in NDI Studio
  Monitor, OBS, vMix, and any NDI receiver on your network.

### HTTP overlays for OBS & vMix
- `/lower` and `/full` — transparent browser-source overlays that match the NDI
  feeds pixel-for-purpose, updated live over SSE with reconnect-safe state.
- `/stream` — an MJPEG stream output with selectable stream backgrounds
  (transparent, chroma green, black, dark studio) for keying anywhere.
- `/remote` — a phone-friendly remote control page: trigger verses from
  anywhere in the room.
- `/bibleshow.xml` + **vMix SetText push** — verse text flows straight into
  your vMix GT titles over the LAN.

### Background broadcasting
Broadcasting runs in a foreground service: it continues with the screen off or
the app minimized, and **auto-resumes after an OS process kill** — server, both
NDI senders, and the last live verse come back on their own. Stop it from the
persistent notification or by swiping the app away.

---

## 📝 From Sermon Notes to On Air

Prepare your sermon the way you already do — in **Google Keep** or any Bible
app — then:

1. Write your references naturally: `العبرانيين ٩: ١١`, a range
   `العبرانيين ٩: ٧-٩`, or a whole chapter `المزامير ٢٣`. Short Arabic names,
   Eastern numerals, and English names/abbreviations all work.
2. **Share** the note to Arabic Bible NDI (or select text → the app appears in
   the text-selection toolbar).
3. Every reference becomes your **Sermon list**, in order, ranges expanded
   verse by verse — and the **first verse is already live**. Tap any row to
   put that verse on air.

Text always comes from the app's own database, so your styles and tashkeel
setting apply exactly as if you'd tapped the verse in the reader.

---

## 📖 The Bible Library — Fully Offline

- Complete Arabic Bible with **optional tashkeel (diacritics)** — one toggle;
  31,101 verses carry full vocalization. Search stays diacritic-proof either
  way.
- **Two English translations bundled**: ASV and KJV.
- Fast offline search across the whole text, with Arabic normalization
  (alef forms, tashkeel and tatweel stripped from queries).
- Arabic + English shown together, each in its own typography, with
  direction-aware (RTL/LTR) layout done correctly.

---

## 🎨 A Design Studio in Your Pocket

- **38 professionally designed styles** ship with the app — motion-first
  families (Gold & Glory, Heavens & Waters, Night & Stars, Royal Purple,
  Garden & Peace, Dawn & Rose, Candlelight, Broadcast & Clean): 27 lower
  thirds + 11 full shows, 30 of them animated.
- **Every one of the 61 style settings is editable**: card style and opacity,
  per-side padding, auto-fit text with a minimum size, text shadow and card
  glow, accent colors, borders, emblem (any emoji or symbol), alignment,
  transitions (fade/slide/cut, 0–2000 ms), and more.
- **Animated motion backgrounds** — divine rays, waves, silk, particle fields,
  candle glow — plus **your own looping video** backgrounds (CUT/FADE loop
  modes, opacity control). Picking a motion background auto-coordinates the
  card, opacity, and accent; adjust anything afterward.
- **69+ bundled fonts, zero downloads**: 57 Arabic display fonts (Aref Ruqaa,
  Katibeh, El Messiri, Scheherazade, Kufi…) and 12 classic Latin families
  (Cinzel, Playfair Display, Lora, EB Garamond…) — the same type renders on
  NDI, HTTP, and the editor preview.
- **Style sharing**: export/import styles as JSON, device to device;
  long-press any style to export, update from an import, or delete.
- What you see is what goes out: the editor preview, NDI, and HTTP render the
  same design — same fit, same wrapping, same spacing.

---

## 🎛️ Control & Integration

- **HTTP API** for automation: `/api/trigger?ref=John+3:16` (Arabic refs too,
  ranges included), `/api/verse`, `/api/clear`, `/api/status`, `/api/videos`.
- **Sermon list** — the service order, built from your notes or by hand,
  advanced with a tap.
- Per-feed settings persist across restarts; tab and editor state survive
  rotation; large system font sizes are fully supported.

---

## 🔮 Coming Soon

- **Chromecast broadcasting** — send the show straight to any Chromecast or
  Google TV in the room.
- **Apple AirPlay broadcasting** — mirror the output to Apple TV and
  AirPlay-enabled screens.
- **Hardwired HDMI output** — a direct wired display feed for zero-latency,
  network-free projection.

---

## 📲 Install

Requires **Android 7.0+**. Download the signed APK from the
[Releases page](https://github.com/anesiem/Arabic-Bible-NDI/releases/latest)
and install. Google Play availability is coming soon.

Application ID: `com.arabicchristianmedia`

## 🛠️ Developers

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for architecture, build
instructions, and development preferences. Release details live on the
[Releases page](https://github.com/anesiem/Arabic-Bible-NDI/releases).

## 🙏 Support Through Prayer

Arabic Bible NDI is built for churches and ministries, freely given for the
work of the Kingdom. There is no donation link — the best way to support this
project is to **pray for it**: for the churches that use it, the services it
serves, and its continued development. Prayers are deeply appreciated.

*“Freely you have received; freely give.” — Matthew 10:8*

*NDI® is a registered trademark of Vizrt NDI AB. This project uses the NDI 6
runtime; it is not affiliated with or endorsed by Vizrt.*
