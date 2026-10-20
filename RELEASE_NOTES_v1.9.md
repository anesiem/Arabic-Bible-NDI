# Release Notes — v1.9

**Version:** 1.9 (versionCode 9)

## What's New

### 📝 Sermon Notes Sharing — the headline feature

Prepare your sermon in Google Keep (or any notes / Bible app), then put it on
air in two taps:

- Write your bullets the way you already do — reference plus text, e.g.
  `العبرانيين ٩: ١١ …` — or share a whole range: `العبرانيين ٩: ٧-٩`.
- **Share** the note to Bible NDI (or select the text and use the text-selection
  toolbar entry). The app reads the **references only** and pulls the canonical
  text from its own database — your tashkeel setting applies.
- Every reference found becomes a row in the new **Sermon (القائمة)** tab, in
  note order, ranges expanded verse by verse. The **first verse goes LIVE
  immediately** on NDI and both HTTP overlays; tap any row to put that verse
  on air.
- Whole chapters work too (`المزامير ٢٣` → all 6 verses). Short book names
  work (`العبرانيين` finds `رسالة العبرانيين`) — in shared notes *and* in
  `/api/trigger`.
- Text with no reference shows a polite message and leaves your current list
  untouched. The list survives rotation and tab switches; the trash icon
  clears it.

This Sermon list is the first version of the service playlist planned for v2.0.

### 🔤 Tashkeel — Arabic Diacritics On Demand

- The Arabic database now carries diacritics for 31,101 of 31,102 verses
  (the one gap is a psalm heading, which falls back to plain text — never blank).
- One toggle at the **top of the Setup tab** switches tashkeel on or off;
  the current chapter reloads instantly, and the reader, both NDI feeds, and
  both HTTP overlays all follow. Off by default.
- **Search is tashkeel-proof:** diacritics and tatweel are stripped from your
  query before matching, so plain and vocalized spellings find the same verse.

### 📖 A Second English Bible: KJV

- The English picker in Setup now offers **ASV and KJV**, both bundled and
  fully offline.

### 🎨 38 Redesigned Shipped Styles

The whole factory library was redesigned as one motion-first set — real card
styles (glass, liturgical, banner, pill) instead of everything transparent:

- **Lower Thirds (27)** in themed families: Gold & Glory, Heavens & Waters,
  Night & Stars, Royal Purple, Garden & Peace, Dawn & Rose, Candlelight,
  plus a Broadcast & Clean group for the classic keyer look.
- **Full Shows (11)** — immersive, large centered text for the full screen.
- 30 of 38 use animated backgrounds (rays, waves, silk, particles, candle glow).
- Arabic display fonts (Aref Ruqaa, Katibeh, El Messiri, Mirza, Markazi,
  Scheherazade, Kufi…) are paired with classic Latin church fonts (Cinzel,
  Playfair Display, Lora, EB Garamond…) — **all bundled, fully offline**.
- Your own saved/imported styles are never touched; the old shipped set is
  replaced on first launch and your highlighted style migrates to its new
  equivalent.

### 🎛️ Every Style Setting Is Now Editable

- All **61 style fields** have editor controls — including the six that
  previously had none: English font, bilingual spacing, Arabic punctuation,
  verse transition + duration, and stream background mode.
- **12 Latin fonts** bundled for the English text, alongside the Arabic library.
- Style-card thumbnails now show a **miniature live preview** of the style,
  and transparent styles show their transparency %.
- **Reset** now gives you a clean, simple starting template instead of
  reverting to the highlighted style.
- Picking a motion background auto-coordinates card style, opacity, and accent
  (particles → transparent, waves → frosted glass, silk → liturgical…);
  everything stays adjustable afterward and saves with the style.

### 📺 NDI ↔ HTTP Parity — What You See Is What Goes Out

A full audit of all 38 styles × all 61 fields × all three renderers, fixed in
one batch:

- **Auto-fit is identical** on NDI and HTTP — same budgets, same shrink, same
  minimum size (the minimum is a floor: it binds on long verses).
- **Line breaking matches** — NDI now wraps greedily like the browser instead
  of balancing lines, so a verse takes the same number of lines everywhere.
- **Padding is honest on NDI** — an emblem offset that silently shifted
  right-aligned verses is gone; horizontal and vertical insets match HTTP.
- **Particles are visible on NDI** — Golden/Holy Particles render at full
  strength on transparent styles, with a real animated particle field on the
  HTTP overlays too.
- **Verse transitions now run on NDI** — FADE and slide styles crossfade over
  the style's transition duration (0–2000 ms), exactly like HTTP. CUT styles
  stay instant.
- Corner radius, text shadow, bilingual spacing, and Classic Banner chrome all
  scale identically on every surface.

### 🔋 Background Broadcasting (Foreground Service)

- Broadcasting continues when the app is minimized or the screen turns off.
- **Auto-resume after an OS process kill:** server, both NDI senders, and the
  last live verse return on their own — no need to open the app.
- Swiping the app away, or **Stop** in the persistent "NDI Live" notification,
  ends the broadcast cleanly.
- Senders are named `<Tablet> - Bible-NDI-Lower` / `<Tablet> - Bible-NDI-Full`
  in NDI Studio Monitor and other receivers.

### 🧹 Editor & Setup Cleanup

- Five editor sections instead of six (Design Style merged into
  Background & Effects); Quick Setup de-duplicated to the controls that
  aren't repeated in Typography, with exactly one save button.
- The "How it works" box collapses and remembers "Don't show again"; the ⓘ in
  the header brings it back. Preview is capped and centered on tablets.
- Tab state (main tab + editor Lower/Full) survives rotation and tab switches.
- The settings tab is renamed **Setup**, with the tashkeel toggle on top and
  the API URLs under Advanced.
- Custom-video loop mode: **CUT or FADE** (default FADE, fades through black).

### 🐞 Fixes Your Testing Caught

- **Verses no longer clear while flipping chapters** — outputs hold the last
  verse until you tap a new one.
- **Genesis 1:1 restored** — its database row stored the numbers as Eastern
  Arabic numerals (١), so it never matched a query.
- NDI card **border now draws for every background type** (was animated-only).
- NDI **citation budget** fixed — English text no longer crowds the card's
  bottom padding.
- **Crossfade actually crossfades** (reworked to draw-time on NDI and HTTP).
- Search placeholder no longer wraps in RTL; style changes push a frame
  immediately (no slider nudge needed).
- HTTP fixes from earlier in the cycle: card padding sliders (a `pxpx` typo
  silently disabled them), citation re-shrink loop, true black rendering as
  navy, post-crash overlay freeze.

### 🔧 Production Hardening

- **R8 minification** for release builds, with keep rules for the JNI bridge,
  service, and models.
- Thread-safe frame timing, notification Stop syncs the UI, auto-resume logs
  its completion.
- Verified end-to-end on device: debug + release builds, full unit-test suite,
  background/screen-off broadcasting, process-death recovery, swipe-to-stop,
  large font scales, and a complete NDI/HTTP parity pass.
