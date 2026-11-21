# Arabic Bible NDI

**Your tablet is the broadcast. One tap puts the verse on the projector, the stream, or both — in Arabic and English, beautifully styled, over real NDI. Built by a pastor who preaches with it live, every service.**

> 📲 **Coming soon to Google Play.** Today, download the signed APK from the
> [Releases page](https://github.com/anesiem/Arabic-Bible-NDI/releases).

---

## Built for live. Trusted in the service.

This app was not made in an office and tested in a demo. It was built to carry
a real service, and it does — in front of a real congregation, service after
service.

- **It stays on air.** Broadcasting runs as a foreground service: the server,
  both NDI senders, and the last live verse come back on their own after an
  OS kill. The tablet can stay on the pulpit while you preach.
- **It keeps running in the background.** With the tablet screen off or the app
  in the background, the broadcast keeps going and the remote keeps changing
  verses — triggers, sermon stepping, clear, and ON AIR all keep working.
- **Triggers keep working through a background restart.** If the OS kills the
  app while it sits in the background, the remote keeps changing verses —
  the service answers triggers itself until the app is opened again.
- **It recovers by itself.** Overlays run over reconnect-safe live state — a
  projector PC that drops its connection picks the verse back up on its own.
- **It checks itself.** A remote heartbeat watches the connection between the
  tablet and every screen, and a post-service connection check shows you
  exactly what was live.
- **No internet at the venue. No account. No subscription.** Everything runs
  on your own Wi-Fi — fully offline, fully yours.
- **One tap, and the verse is live.** No menus to fight while you preach.

---

## See it live

- **Full show** — a verse live on the projector.
- **Lower third** — the same verse over video.
- **The Library** — the reader with the sermon verse tinted.
- **/remote on a phone** — triggering from anywhere in the room.

---

## One tap. On air.

1. **Prepare** — share a note from Google Keep (or any Bible app); references become your sermon list.
2. **Tap** — one tap puts the verse live.
3. **Step** — arrow keys / a foot pedal step through the sermon in order.

A volunteer can run the service after one rehearsal.

---

## Your helpers run it with you

You do not carry the service alone — and neither does the app. Anyone helping
you can open a page in the browser on their own phone and work alongside you.
No app to install, no account, nothing to set up.

- **/remote** — trigger any verse, step through the sermon, search the Bible,
  see the live verse and the ON AIR state, all from a phone anywhere in the room.
- **/stage** — the stage screen: the live verse big and clear, the sermon list
  beside it, the recents strip below — so the person on stage always knows
  what is coming next.
- **Every helper sees the same live verse.** A tap on one phone moves every
  screen — the projector, the stream, the stage, and every remote — together.
- **In their language.** The remote and stage pages speak Arabic or English,
  per device — each helper picks their own.

---

## Two real NDI sources

- **Two genuine NDI® 6 sources, simultaneous and independent** — Lower and Full, each with its own style, resolution (up to 1080p, all 16:9), frame rate (60/50/30/25/24/15/10/5), and motion settings.
- **They appear under your device's name** in NDI Studio Monitor, OBS, vMix, and any NDI receiver.
- **HTTP overlays that match NDI** — `/lower` and `/full` as transparent browser sources, live over SSE, reconnect-safe.
- **Background broadcasting** — foreground service, auto-resumes after an OS kill; server + both senders + last live verse come back on their own.

---

## A design studio, not a template picker

- **20 professionally designed templates** ship with the app — **10 lower thirds + 10 full shows** (Pure Word transparent, Royal Gold, Royal Purple, Midnight Sapphire, Emerald Garden, Rose Dawn, Desert Sand, Ocean Deep, Violet Dusk, Classic White, Crimson Liturgy). Every value is yours — card, glow, borders, alignment, transitions, motion.
- **68 bundled font families (119 files), zero downloads** — 56 Arabic families (Aref Ruqaa, Katibeh, El Messiri, Scheherazade, Kufi…) and 12 Latin families (Cinzel, Playfair Display, Lora, EB Garamond…). Import your own TTF/OTF — still fully offline.
- **Motion + video backgrounds** — 12 particle effects, 7 motion-video backgrounds, plus your own looping MP4 (CUT/FADE, opacity control).
- **Style sharing** as JSON — export, import, update in place.
- *What you see in the editor is what goes out on NDI and HTTP.*

---

## Beautiful, in your language

- **8 built-in themes** — including Midnight, the deep-navy dark theme — plus
  your own custom themes. One change of theme restyles the whole app: Library,
  sermon list, remote, stage, everything.
- **The whole app speaks Arabic or English.** The complete interface — every
  screen, every button — in the language your team reads. Switch anytime; the
  Bible text itself is untouched.
- **Direction-aware throughout** — Arabic reads right-to-left, English
  left-to-right, in the reader, the picker, and the overlays.

---

## It talks to everything in the room

**Shipped today (in 1.10.1):**

- **HTTP API** — `/api/trigger?ref=John+3:16` (Arabic refs and ranges too), `/api/verse`, `/api/clear`, `/api/onair`, `/api/status`, `/api/health`, `/api/sermons` (+ next/prev), `/api/recents`, `/api/books`, `/api/chapter`, `/api/videos`, `/api/autoclear/{pause,resume,stop}`, `/api/export/all`, `/api/import/backup`, `/api/debug`, `/api/preflight`, `/api/clientstat`.
- **Live state** — SSE `/events` (reconnect-safe), MJPEG `/stream` (transparent / chroma green / black / dark studio), snapshot PNGs (`/lower/snapshot.png`, `/full/snapshot.png`, `/api/verse/card.png`) rendered by the same native code as NDI.
- **Feeds** — `/lower` + `/full` RSS, `bibleshow.xml` + `bibleshow.json` per feed (Arabic + English as separate fields, SVD + ASV/KJV declared per slot).
- **vMix** — verse text pushed straight into vMix GT titles over the LAN (SetText), per feed.
- **ProPresenter** — Stage + Audience over its HTTP API.
- **Auto-clear** — the live verse clears itself after a set time, with a ring timer on screen. On by default; you set the minutes (1–60).
- **Keyboard-first** — full shortcut map (arrows, Enter, Space, Esc, C, O, Ctrl+F, Ctrl+1–4).

### Coming next — planned, not yet shipped

- **Facebook Live Posting** — auto-post the live verse (user-defined grace period), plus a manual "Post to Facebook" button. Planned, opt-in, off by default.
- **Sermon-listener** — the app is the hub for a Whisper listener that hears the spoken reference and fires `/api/trigger`.
- **Chromecast, AirPlay, hardwired HDMI** — already under "Coming Soon" today.

---

## Why this, not the others

*Researched 2026-11-11. Every cell is only what is verifiably true.*

**General presentation software:**

| | Arabic Bible NDI | ProPresenter | OpenLP | Quelea | FreeShow | EasyWorship | MediaShout 7 | Proclaim | VideoPsalm |
|---|---|---|---|---|---|---|---|---|---|
| Arabic Bible built in | ✅ Smith–Van Dyke, full, with tashkeel | Not confirmed | Not confirmed | Not confirmed | Not confirmed | Not confirmed | Not confirmed | Not confirmed | Not confirmed |
| Real NDI® 6 broadcast (2 independent feeds) | ✅ from a tablet/phone | ✅ (desktop) | ❌ | ❌ | ✅ (desktop) | ✅ (desktop) | Not confirmed | ✅ (desktop) | ❌ |
| HTTP overlays + full API | ✅ | Partial (no public HTTP API confirmed) | Partial (web remote; API unconfirmed) | ❌ (remote only) | Partial (remote + web output) | ❌ (remote only) | ❌ | ❌ (remote only) | ❌ |
| Fully offline, no account, no internet at venue | ✅ | Partial (local app; cloud/seat mgmt web) | ✅ | Not confirmed | Not confirmed (scripture via online API.Bible) | ✅ (local app) | ✅ (local app) | ❌ (cloud-first) | ✅ |
| Live preaching workflow (sermon list, one-tap verses, keyboard/foot-pedal stepping) | ✅ built for it | Partial (general presentation) | Partial | Partial | Partial | Partial | Partial | Partial | Partial |
| Runs on a tablet/phone you already own | ✅ Android 7.0+ | ❌ needs Mac/Windows | ❌ needs a computer | ❌ needs a computer | ❌ needs a computer | ❌ needs Windows | ❌ needs Mac/Windows | ❌ needs Windows | ❌ needs Windows |
| Free | ✅ | ❌ $29/mo or $289/yr per seat | ✅ free | ✅ free | ✅ free | ❌ from $17.50/mo | ❌ $599 one-time or $198/yr | ❌ from $269.99/yr | ✅ free |

**Arabic Bible presentation apps:**

| | Arabic Bible NDI | VerseVIEW | Tasbe7na | Verentics MultiVerse | Big Screen | Coptic Reader | VerseCAST |
|---|---|---|---|---|---|---|---|---|
| Arabic Bible built in | ✅ Smith–Van Dyke, full, tashkeel | ✅ (40+ languages incl. Arabic; translation not named) | ✅ (Bible included; translation not named) | ✅ Arabic Van Dyke | ✅ Smith Van Dyke | ✅ (translation not named) | ✅ via YouVersion (translation not named) |
| Presents songs too | ❌ (Bible only) | ✅ | ✅ (10,000+ songs — its main strength) | ❌ | ✅ | ✅ (hymns/psalmody) | ✅ |
| Real NDI® broadcast | ✅ (2 feeds) | Not confirmed | Not confirmed | Not confirmed | Not confirmed | Not confirmed | ❌ (Chromecast / smart-TV browser) |
| HTTP overlays + API | ✅ | Not confirmed | Not confirmed | ❌ (web presenter) | ❌ (web control) | Not confirmed | ❌ |
| Fully offline | ✅ | Not confirmed | Not confirmed | ❌ (web, credits) | Partial (keeps running once loaded) | ✅ once downloaded | ❌ (needs Wi-Fi + internet) |
| Platform | Android 7.0+ | Windows, Mac, Android, iOS | iOS, Android | Web | Web | Android, iOS, Windows, Mac, web | Android, iOS (phone) |
| Free | ✅ | ✅ free | ✅ free | Freemium (€0 / €5 per 20 credits / €10/mo Pro) | ❌ $15 AUD/mo | ✅ free | Freemium |

**Tasbe7na** (by Malak George, Egypt; iOS + Android; free; 10,000+ songs and the Bible) is the most widely used app for praise lyrics and Bible presentation in Egypt and Arab churches. Its strength is songs. It does not do NDI, an API, or a broadcast workflow — that is the gap this app fills.

> *ProPresenter is a presentation suite for a computer. Bible apps are for reading. Tasbe7na is for songs. This is the one tool built to put the verse on air from the tablet in the preacher's hand — in Arabic, live, over NDI.*

*ProPresenter is a trademark of Renewed Vision; OpenLP and Quelea are open-source projects; YouVersion is a trademark of Life.Church. This project is not affiliated with or endorsed by any of them. NDI® is a registered trademark of Vizrt NDI AB; this project uses the NDI 6 runtime and is not affiliated with or endorsed by Vizrt.*

---

## The Bible Library — fully offline

- Complete Arabic Bible with optional tashkeel — 31,101 verses carry full vocalization. Search stays diacritic-proof either way.
- Two English translations bundled: ASV and KJV.
- Fast offline search with Arabic normalization (alef forms, tashkeel and tatweel stripped from queries).
- Arabic + English shown together, direction-aware (RTL/LTR).

---

## Install

Requires **Android 7.0+**. Download the signed APK from the
[Releases page](https://github.com/anesiem/Arabic-Bible-NDI/releases/latest).
Google Play availability is coming soon. Application ID: `com.arabicchristianmedia`.

## Developers

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for architecture and build instructions.

## Support Through Prayer

Arabic Bible NDI is built for churches and ministries, freely given for the work of the Kingdom. There is no donation link — the best way to support this project is to **pray for it**. Prayers are deeply appreciated.

*"Freely you have received; freely give." — Matthew 10:8*
