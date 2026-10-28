package com.arabicchristianmedia.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arabicchristianmedia.data.BibleRepository
import com.arabicchristianmedia.data.RecentRef
import com.arabicchristianmedia.data.RecentStore
import com.arabicchristianmedia.data.SermonStore
import com.arabicchristianmedia.data.TemplateRepository
import com.arabicchristianmedia.model.AppThemeMode
import com.arabicchristianmedia.model.ColorTheme
import com.arabicchristianmedia.model.ColorThemes
import com.arabicchristianmedia.model.colorThemeFromJson
import com.arabicchristianmedia.model.migratedThemeFromPairs
import com.arabicchristianmedia.model.toJson
import org.json.JSONArray
import org.json.JSONObject
import com.arabicchristianmedia.model.BibleBook
import com.arabicchristianmedia.model.BibleVerse
import com.arabicchristianmedia.model.BibleVersion
import com.arabicchristianmedia.model.BroadcastTextAlignment
import com.arabicchristianmedia.model.CustomFont
import com.arabicchristianmedia.model.CustomFonts
import com.arabicchristianmedia.model.LanguageMode
import com.arabicchristianmedia.model.LowerThirdTemplate
import com.arabicchristianmedia.model.BackupImportResult
import com.arabicchristianmedia.model.ReferenceScanner
import com.arabicchristianmedia.model.SavedSermon
import com.arabicchristianmedia.model.SermonCreateResult
import com.arabicchristianmedia.model.SermonEntry
import com.arabicchristianmedia.model.SermonGrouping
import com.arabicchristianmedia.model.SermonNavigator
import com.arabicchristianmedia.model.SermonRef
import com.arabicchristianmedia.model.SermonStepOutcome
import com.arabicchristianmedia.model.Testament
import com.arabicchristianmedia.server.FontStore
import com.arabicchristianmedia.server.NdiBroadcastServer
import com.arabicchristianmedia.server.NdiService
import com.arabicchristianmedia.server.NdiErrorEvent
import com.arabicchristianmedia.server.NdiNativeSender
import com.arabicchristianmedia.server.NdiSourceSpec
import com.arabicchristianmedia.server.NetworkHelper
import com.arabicchristianmedia.server.NetworkInterfaceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BibleNdiUiState(
    val selectedTestament: Testament = Testament.NEW_TESTAMENT,
    val selectedBook: BibleBook = BibleRepository.getBookById("jhn") ?: BibleRepository.allBooks.first(),
    val selectedChapter: Int = 3,
    val bibleVersion: BibleVersion = BibleVersion.ARABIC_SVD,
    val tashkeelEnabled: Boolean = false,
    val englishVersion: String = "asv",
    val displayedVerses: List<BibleVerse> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<BibleVerse> = emptyList(),
    val isSearching: Boolean = false,
    val activeVerse: BibleVerse? = null,
    val isLiveOnAir: Boolean = true,
    val isServerRunning: Boolean = false,
    val serverUrl: String = "",
    val streamUrl: String = "",
    val serverPort: Int = 8080,
    val availableInterfaces: List<NetworkInterfaceInfo> = emptyList(),
    val selectedInterface: NetworkInterfaceInfo? = null,
    val activeIpAddress: String = "",
    val isNdiProtocolEnabled: Boolean = true,
    val isNativeNdiActive: Boolean = false,
    val isNativeShowActive: Boolean = false,
    // Independent per-feed source switches (Lower Third / Full Show)
    val ndiLowerEnabled: Boolean = true,
    val ndiFullShowEnabled: Boolean = true,
    // User-customizable per-source resolution + frame rate + motion (dropdowns in NDI tab).
    // Persisted across restarts (see ViewModel NDI prefs).
    val ndiSourceSpecs: Map<String, NdiSourceSpec> = mapOf(
        NdiNativeSender.FEED_LOWER to NdiNativeSender.defaultSpec(NdiNativeSender.FEED_LOWER),
        NdiNativeSender.FEED_FULL to NdiNativeSender.defaultSpec(NdiNativeSender.FEED_FULL)
    ),
    // Advanced diagnostics (last tab): when on, NDI errors are shown to the user
    val showAdvancedNdi: Boolean = false,
    val ndiErrorEvents: List<NdiErrorEvent> = emptyList(),
    // v1.9.1: HTTP overlay (SSE) health, filled by refreshNdiDiagnostics()
    // from the broadcast server's session journal.
    val sseConnectedNow: Int = 0,
    val sseConnectTotal: Int = 0,
    val sseDropTotal: Int = 0,
    val sseLastDropReason: String = "",
    val sseLastDropAgoSec: Long = -1,
    // v1.9.1 (#16): advertise overlay URLs by the tablet's network name
    // instead of its DHCP-changing IP; tips card collapse state.
    val advertiseByName: Boolean = false,
    val advertiseName: String = "",
    val advertiseTipsCollapsed: Boolean = false,
    val templates: List<LowerThirdTemplate> = emptyList(),
    val activeTemplate: LowerThirdTemplate = TemplateRepository.DEFAULT_TEMPLATES[0],
    val activeShowTemplate: LowerThirdTemplate = BibleNdiViewModel.defaultShowTemplate(),
    // Style system: highlighted (selected) style id per tab, null = none highlighted
    // (user edited without saving). Persisted across restarts.
    val highlightedLowerStyleId: String? = null,
    val highlightedShowStyleId: String? = null,
    // True when the working template has unsaved edits (drives the "modified" badge).
    val lowerWorkingDirty: Boolean = false,
    val showWorkingDirty: Boolean = false,
    val statusMessage: String? = null,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    // App color theme: selected theme id (built-in or custom) + user custom themes.
    val selectedThemeId: String = "classic",
    val customThemes: List<ColorTheme> = emptyList(),
    val readerFontSize: Int = 18,
    val isKeepScreenOn: Boolean = true,
    // True while the Bible databases are being copied/loaded on first launch.
    val isLoadingBible: Boolean = false,
    // v2.0: sermon playlist built from text shared from other apps
    // (Google Keep notes, Bible apps) — see handleSharedText.
    // v1.9.1: entries carry their share-group + shade (per-reference
    // grouping), the list persists across restarts, and named sermons can
    // be saved/opened (see SermonStore).
    val sermonPlaylist: List<SermonEntry> = emptyList(),
    // v2.0: bumped on every successful share so MainActivity opens the list tab.
    val sermonListNonce: Long = 0,
    // v1.9.1: named saved sermons + which one the working list belongs to.
    val savedSermons: List<SavedSermon> = emptyList(),
    val currentSermonId: String? = null,
    val currentSermonName: String? = null,
    // v1.9.1: verse the reader should focus (highlight + scroll to) when the
    // operator jumps from a sermon row's book icon. Distinct from the live
    // verse: focusing never changes what is on air.
    val readerFocusVerseId: String? = null,
    // v1.9.1: bumped when the reader should open at the focused verse.
    val readerJumpNonce: Long = 0,
    // Item 10: recents — verses opened (reader/search/sermon/remote),
    // most recent first, capped at 20. Persisted like the sermon list
    // (references only, see RecentStore); cleared only by the user.
    val recentVerses: List<BibleVerse> = emptyList(),
    // v1.9.1: user-imported fonts (TTF/OTF), shown in both font dropdowns.
    val customFonts: List<CustomFont> = emptyList()
)

class BibleNdiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Factory default for the Full Show tab (not a saved style). */
        fun defaultShowTemplate(): LowerThirdTemplate =
            TemplateRepository.DEFAULT_TEMPLATES[0].copy(
                id = "default_show",
                name = "Full Screen Projector",
                isFullScreen = true,
                bgOpacity = 1.0f,
                bgColorHex = "#0A1128",
                verseFontSize = 65,
                referenceFontSize = 40,
                alignment = BroadcastTextAlignment.CENTER,
                isPureTransparentBackground = false
            )

        /**
         * "Simple" reset target (Ashraf's spec): 100% transparent background,
         * no motion, no custom video, no text shadow, no accent border, no card
         * glow — everything else at data-class defaults. Not a saved style:
         * pressing Reset clears the style highlight and marks the tab modified.
         */
        fun simpleDefaultTemplate(isFullScreen: Boolean): LowerThirdTemplate =
            LowerThirdTemplate(
                id = "simple_default",
                name = "Simple",
                isFullScreen = isFullScreen,
                style = com.arabicchristianmedia.model.TemplateStyle.TRANSPARENT_OUTLINE,
                textShadowEnabled = false,
                cardGlowEnabled = false
            ).let {
                if (isFullScreen) it.copy(
                    verseFontSize = 65,
                    referenceFontSize = 40,
                    alignment = BroadcastTextAlignment.CENTER,
                    // Audit fix (D): the class default box (45%) is the Lower
                    // budget; a reset Full style must fit against the whole
                    // frame like every factory Full style (100%).
                    boxHeightPercent = 100
                ) else it
            }
    }

    private val templateRepo = TemplateRepository(getApplication())
    /** v1.9.1: sermon list persistence (working list + saved sermons). */
    private val sermonStore = SermonStore(getApplication())
    /** Item 10: recents persistence (same reference-only pattern). */
    private val recentStore = RecentStore(getApplication())
    /**
     * v1.9: the broadcast server is owned by [NdiService] (foreground service) so
     * broadcasting survives backgrounding. Always resolve through the service
     * holder — never keep a second instance.
     */
    private val broadcastServer: NdiBroadcastServer
        get() = NdiService.ensureServer(getApplication())
    /**
     * v1.9: the NDI senders live in [NdiService] (foreground service) so the feeds
     * survive backgrounding and OS-kill auto-resume. Always resolve through the
     * service holder — never keep a second instance.
     */
    private val nativeSender: NdiNativeSender
        get() = NdiService.ensureSender(getApplication())

    private val _uiState = MutableStateFlow(BibleNdiUiState())
    val uiState: StateFlow<BibleNdiUiState> = _uiState.asStateFlow()

    init {
        BibleRepository.initialize(getApplication())
        // v2.0: restore tashkeel and English version preferences
        _uiState.value = _uiState.value.copy(
            tashkeelEnabled = BibleRepository.isTashkeelEnabled(),
            englishVersion = BibleRepository.getEnglishVersion()
        )
        // v1.9: frame providers are wired by NdiService (they resolve the server
        // lazily and capture no ViewModel state, so the feeds render headlessly
        // after an OS process kill).
        // v1.9: if the broadcast is stopped from the notification while the app
        // is open, the service tells us here so the UI doesn't stay stale.
        viewModelScope.launch {
            NdiService.externalStopEvents.collect {
                _uiState.value = _uiState.value.copy(
                    isServerRunning = false,
                    isNativeNdiActive = false,
                    isNativeShowActive = false,
                    isLiveOnAir = false,
                    statusMessage = "NDI Server Stopped"
                )
            }
        }
        loadInitialDataAsync()
        loadAdvertiseSettings()
        refreshNetworkInterfaces()
        // startBroadcastServer() // DO NOT auto-start as per request 8? 
        // User said: "on Library tab, don't start the on air, be sure it's off air till a user click on a verse"
        // This might mean isLive = false, not necessarily server off. 
        // But usually, servers start on init. I'll just set isLive = false.
        startBroadcastServer()
        // v1.8: wire the HTTP remote API (/api/trigger, /api/clear, /api/videos).
        broadcastServer.onTriggerVerse = { bookId, chapter, verseNum, target ->
            triggerVerseByReference(bookId, chapter, verseNum, target)
        }
        broadcastServer.onClearVerse = {
            clearBroadcast()
        }
        // Item 19: the remote page's light poll reports whether any NDI
        // sender is rendering, read live from state on every poll.
        broadcastServer.ndiActiveProvider = {
            _uiState.value.isNativeNdiActive || _uiState.value.isNativeShowActive
        }
        // Item 25: /debug feed states (live sender sessions, not UI
        // flags) + sermon counts.
        broadcastServer.ndiFeedStatesProvider = {
            nativeSender.isSourceActive(NdiNativeSender.FEED_LOWER) to
                nativeSender.isSourceActive(NdiNativeSender.FEED_FULL)
        }
        broadcastServer.sermonCountsProvider = {
            _uiState.value.sermonPlaylist.size to _uiState.value.savedSermons.size
        }
        broadcastServer.onSetOnAir = { on ->
            setOnAir(on)
        }
        broadcastServer.onListVideos = {
            listBackgroundVideos()
        }
        // Sermon APIs (next/prev first): the server calls back into the
        // ViewModel so API, remote page, and presenter keys share one
        // sermon state — the server never keeps its own sermon copy.
        broadcastServer.sermonCurrentProvider = { sermonCurrentState() }
        broadcastServer.onSermonStep = { step -> stepSermon(step) }
        broadcastServer.onSermonCreate = { name, text, activate -> createSermonFromText(name, text, activate) }
        broadcastServer.onSermonActivate = { id, merge -> activateSermonApi(id, merge) }
        broadcastServer.onSermonRename = { id, name -> renameSermonApi(id, name) }
        broadcastServer.onSermonDelete = { id -> deleteSermonApi(id) }
        broadcastServer.onImportBackup = { json, dryRun -> importBackupApi(json, dryRun) }
    }

    fun refreshNetworkInterfaces() {
        val interfaces = NetworkHelper.getAvailableInterfaces()
        val currentSelected = _uiState.value.selectedInterface
        val chosen = if ((currentSelected != null) && interfaces.any { it.id == currentSelected.id }) {
            currentSelected
        } else {
            interfaces.firstOrNull { it.isRecommended }
                ?: interfaces.firstOrNull { it.reachableFromLan }
                ?: interfaces.firstOrNull()
        }

        if (chosen != null) {
            broadcastServer.customIpOverride = chosen.ip
        }

        val ip = broadcastServer.getLocalIpAddress()
        val url = broadcastServer.getServerUrl()
        val streamUrl = broadcastServer.getStreamUrl()

        _uiState.value = _uiState.value.copy(
            availableInterfaces = interfaces,
            selectedInterface = chosen,
            activeIpAddress = ip,
            serverUrl = url,
            streamUrl = streamUrl
        )
    }

    fun selectNetworkInterface(info: NetworkInterfaceInfo) {
        broadcastServer.customIpOverride = info.ip
        val url = broadcastServer.getServerUrl()
        val streamUrl = broadcastServer.getStreamUrl()

        _uiState.value = _uiState.value.copy(
            selectedInterface = info,
            activeIpAddress = info.ip,
            serverUrl = url,
            streamUrl = streamUrl,
            statusMessage = "تم تعيين العنوان: ${info.ip}"
        )
    }

    fun setNdiProtocolEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isNdiProtocolEnabled = enabled,
            statusMessage = if (enabled) "تم تفعيل بث بروتوكول Full NDI الشفاف" else "تم إيقاف بث NDI لتوفير استهلاك شبكة الواي فاي (Bandwidth Saver)"
        )
        if (enabled) {
            // v1.9.1: enabling must NEVER stop senders. The old else-branch
            // did stopAll() whenever the server wasn't (yet) marked running
            // — in the post-restart window the service may have already
            // auto-resumed the feeds, and flipping this switch on killed
            // them while the per-feed toggles still showed "enabled".
            if (_uiState.value.isServerRunning) {
                syncNativeSources()
                triggerAllFrames()
            }
            // Not running yet: the server-start path syncs on start.
        } else {
            nativeSender.stopAll()
            _uiState.value = _uiState.value.copy(
                isNativeNdiActive = false,
                isNativeShowActive = false
            )
        }
    }

    /**
     * First-launch database copy (16MB of assets) runs on Dispatchers.IO so the
     * UI never freezes; the reader shows a loading indicator meanwhile.
     */
    private fun loadInitialDataAsync() {
        _uiState.value = _uiState.value.copy(isLoadingBible = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                loadInitialData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "تعذر تحميل الكتاب المقدس: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingBible = false)
            }
        }
    }

    private fun loadInitialData() {
        val allTpls = templateRepo.getAllTemplates()

        // Lower Third tab: restore working values + highlighted style.
        // A stored null highlight stays null (dirty edits clear it); only a true
        // fresh install (no working template ever saved) falls back to the default.
        val hasLowerWorking = templateRepo.getWorkingTemplate(false) != null
        val lowerHighlightId = templateRepo.getActiveTemplateId()
            ?: if (!hasLowerWorking) "tpl_lt_pure_word" else null
        val lowerStyle = allTpls.firstOrNull { it.id == lowerHighlightId && !it.isFullScreen }
        val lowerWorking = (templateRepo.getWorkingTemplate(false)
            ?: lowerStyle
            ?: allTpls.firstOrNull { !it.isFullScreen }
            ?: TemplateRepository.DEFAULT_TEMPLATES[0]).copy(isFullScreen = false)

        // Full Show tab: restore working values + highlighted style (independent of Lower).
        val showHighlightId = templateRepo.getActiveShowTemplateId()
        val showStyle = allTpls.firstOrNull { it.id == showHighlightId && it.isFullScreen }
        val showWorking = (templateRepo.getWorkingTemplate(true)
            ?: showStyle
            ?: allTpls.firstOrNull { it.isFullScreen }
            ?: defaultShowTemplate()).copy(isFullScreen = true)

        val initialBook = BibleRepository.getBookById("jhn") ?: BibleRepository.allBooks.first()
        val initialChapter = 3
        val verses = BibleRepository.getVerses(initialBook.id, initialChapter)
        // v1.8: no verse selected on launch — the user picks one.

        _uiState.value = _uiState.value.copy(
            selectedBook = initialBook,
            selectedChapter = initialChapter,
            displayedVerses = verses,
            templates = allTpls,
            activeTemplate = lowerWorking,
            activeShowTemplate = showWorking,
            highlightedLowerStyleId = lowerStyle?.id,
            highlightedShowStyleId = showStyle?.id,
            lowerWorkingDirty = templateRepo.isWorkingDirty(false),
            showWorkingDirty = templateRepo.isWorkingDirty(true),
            activeVerse = null,
            isLiveOnAir = false, // Start OFF AIR as per request 8
            statusMessage = "Ready. Tap a verse to go LIVE."
        )
        loadPersistedNdiSpecs()
        loadThemePrefs()
        loadUiPrefs()
        // v1.9.1: restore the persisted sermon working list + saved sermons.
        restoreSermonState()
        restoreRecentState()
        refreshCustomFonts()

        // v1.9: if the service is mid auto-resume (flag set but the server isn't
        // up yet — rehydration runs on its own thread), give it a moment to finish
        // before deciding whether the server state below gets reset or kept.
        // This runs on Dispatchers.IO; the wait is bounded and rare.
        if (NdiService.isBroadcastActive(getApplication()) && !broadcastServer.isRunning) {
            val deadline = android.os.SystemClock.uptimeMillis() + 3000
            while (!broadcastServer.isRunning && android.os.SystemClock.uptimeMillis() < deadline) {
                try { Thread.sleep(100) } catch (e: InterruptedException) { break }
            }
        }

        if (broadcastServer.isRunning) {
            // v1.9: the service auto-resumed the broadcast while the app was dead
            // (verse/styles/live rehydrated). Keep the server's state — do NOT
            // force off-air — and sync the UI from it so the screen matches the
            // NDI output.
            val resumedVerse = broadcastServer.currentVerse
            // 2026-10-28 cold-start divergence fix: a running server that
            // claims live with NO verse is not live — it is the blank
            // output the recheck saw (UI OFF AIR, server claiming live).
            // Clear the server flag too, not just the UI copy, so
            // /api/debug, NDI and HTTP all agree on OFF AIR.
            if (resumedVerse == null && broadcastServer.isLive) {
                broadcastServer.setLiveState(false)
            }
            val resumedBook = resumedVerse?.let { BibleRepository.getBookById(it.bookId) }
            val resumedVerses = resumedVerse?.let {
                try { BibleRepository.getVerses(it.bookId, it.chapter) } catch (e: Exception) { verses }
            } ?: verses
            _uiState.value = _uiState.value.copy(
                activeTemplate = broadcastServer.currentTemplate,
                activeShowTemplate = broadcastServer.currentShowTemplate,
                activeVerse = resumedVerse,
                isLiveOnAir = broadcastServer.isLive && resumedVerse != null,
                selectedBook = resumedBook ?: initialBook,
                selectedChapter = resumedVerse?.chapter ?: initialChapter,
                displayedVerses = resumedVerses,
                statusMessage = if (resumedVerse != null && broadcastServer.isLive)
                    "Broadcast auto-resumed after restart."
                else "Ready. Tap a verse to go LIVE."
            )
        } else {
            broadcastServer.currentVerse = null
            broadcastServer.isLive = false // Ensure server starts off-air
            broadcastServer.currentTemplate = lowerWorking
            broadcastServer.currentShowTemplate = showWorking
        }
    }

    fun startBroadcastServer(port: Int = 8080) {
        refreshNetworkInterfaces()
        _uiState.value = _uiState.value.copy(isNdiProtocolEnabled = true)
        // v1.9: run under the foreground service so the broadcast survives backgrounding.
        NdiService.start(getApplication())
        val handleStarted: (String) -> Unit = { url ->
            val ip = broadcastServer.getLocalIpAddress()
            val streamUrl = broadcastServer.getStreamUrl()

            // Native Sender management
            syncNativeSources()

            _uiState.value = _uiState.value.copy(
                isServerRunning = true,
                isNdiProtocolEnabled = true,
                serverUrl = url,
                streamUrl = streamUrl,
                activeIpAddress = ip,
                serverPort = port,
                statusMessage = "Broadcast Server Active"
            )
        }
        val handleError: (String) -> Unit = { err ->
            _uiState.value = _uiState.value.copy(
                isServerRunning = false,
                statusMessage = "Server Error: $err"
            )
        }
        if (broadcastServer.isRunning) {
            // The service auto-resumed the server after an OS process kill:
            // start() would no-op without firing callbacks, so run the
            // post-start work directly (this restarts the NDI senders).
            handleStarted(broadcastServer.getServerUrl())
        } else {
            broadcastServer.start(onStarted = handleStarted, onError = handleError)
        }
    }

    fun startNdiFeeds() {
        // Item 18: the protocol switch gates all NDI starts. While it is
        // OFF, Start must not resurrect senders (nor start the service
        // as a side effect) — it only explains why nothing started.
        if (!_uiState.value.isNdiProtocolEnabled) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "بث NDI متوقف — فعّل بروتوكول NDI من الإعدادات لبدء المصادر"
            )
            return
        }
        // Preserve the user's per-feed selection instead of forcing both
        // ON: Start re-syncs whatever is enabled. Only when NOTHING is
        // enabled does Start enable both, so the button always starts
        // something.
        if (!_uiState.value.ndiLowerEnabled && !_uiState.value.ndiFullShowEnabled) {
            _uiState.value = _uiState.value.copy(
                ndiLowerEnabled = true,
                ndiFullShowEnabled = true
            )
            persistNdiEnabled(NdiNativeSender.FEED_LOWER, true)
            persistNdiEnabled(NdiNativeSender.FEED_FULL, true)
        }
        // The service must be properly (re)started FIRST: after a
        // notification-Stop, broadcast_active is false, and a bare
        // refreshNotification would make the service stopSelf() and kill
        // the just-started senders (and the HTTP server) in onDestroy.
        // ACTION_START re-affirms the broadcast and brings HTTP back up
        // if it is down (idempotent when already up).
        NdiService.start(getApplication())
        syncNativeSources()
        triggerAllFrames()
        // v1.9: keep the foreground notification's feed list truthful.
        NdiService.refreshNotification(getApplication())
        val s = _uiState.value
        _uiState.value = s.copy(
            statusMessage = when {
                s.ndiLowerEnabled && s.ndiFullShowEnabled -> "NDI Sources (Lower Third + Full Show) Started"
                s.ndiLowerEnabled -> "NDI Source (Lower Third) Started"
                else -> "NDI Source (Full Show) Started"
            }
        )
    }

    fun stopNdiFeeds() {
        // Preserve the per-feed selection: Stop stops the senders, it
        // does not rewrite the enabled flags — a later Start restores
        // exactly the previous selection. What Stop DOES clear is the
        // auto-resume flag, so an OS kill after a user Stop no longer
        // brings the broadcast back: Stop is a real stop, not a pause
        // that the next process death undoes.
        nativeSender.stopAll()
        NdiService.clearBroadcastActive(getApplication())
        _uiState.value = _uiState.value.copy(
            isNativeNdiActive = false,
            isNativeShowActive = false,
            statusMessage = "All NDI Sources Stopped"
        )
        // refreshNotification is a safe no-op while the flag is false
        // (see NdiService), so the notification is left as-is rather
        // than letting REFRESH stopSelf() the service and kill the
        // still-running HTTP server in onDestroy.
        NdiService.refreshNotification(getApplication())
    }

    /**
     * v1.9: the senders live in NdiService; this delegates the start/stop work
     * there (from the persisted feed specs) and reflects the result in UI state.
     */
    private fun syncNativeSources() {
        val (lower, full) = NdiService.syncNativeSources(getApplication())
        _uiState.value = _uiState.value.copy(
            isNativeNdiActive = lower,
            isNativeShowActive = full
        )
        refreshNdiDiagnostics()
    }

    /** User changed a source's resolution/fps in the dropdowns: apply + restart if active. */
    fun updateNdiSourceSpec(feedKey: String, width: Int, height: Int, fps: Int) {
        val current = _uiState.value.ndiSourceSpecs[feedKey]
            ?: NdiNativeSender.defaultSpec(feedKey)
        // Preserve the motion toggle; only resolution/fps change here.
        updateNdiSourceSpec(feedKey, width, height, fps, current.motionEnabled)
    }

    /**
     * Full spec update (resolution, fps, motion). Persists across restarts and
     * restarts the live source so the change takes effect immediately.
     */
    fun updateNdiSourceSpec(feedKey: String, width: Int, height: Int, fps: Int, motionEnabled: Boolean) {
        val spec = NdiSourceSpec(feedKey, width, height, fps, motionEnabled)
        _uiState.value = _uiState.value.copy(
            ndiSourceSpecs = _uiState.value.ndiSourceSpecs + (feedKey to spec)
        )
        persistNdiSpec(spec)
        val s = _uiState.value
        val enabled = when (feedKey) {
            NdiNativeSender.FEED_LOWER -> s.ndiLowerEnabled
            else -> s.ndiFullShowEnabled
        }
        if (enabled && s.isNdiProtocolEnabled) {
            // Restart the source so the new spec takes effect immediately.
            nativeSender.stopSource(feedKey)
            syncNativeSources()
            triggerAllFrames()
        } else {
            refreshNdiDiagnostics()
        }
    }

    /** Flip the per-feed motion toggle (persisted; applies on next tick or restart). */
    fun toggleNdiMotion(feedKey: String) {
        val current = _uiState.value.ndiSourceSpecs[feedKey]
            ?: NdiNativeSender.defaultSpec(feedKey)
        val newMotion = !current.motionEnabled
        updateNdiSourceSpec(feedKey, current.width, current.height, current.fps, newMotion)
        // v1.8: When motion is turned ON, kick the video renderer so it starts
        // immediately without needing an OFF/ON cycle.
        if (newMotion) {
            val isFull = feedKey == NdiNativeSender.FEED_FULL
            try {
                broadcastServer.kickVideoRenderer(isFull)
            } catch (e: Exception) {
                // Non-fatal.
            }
        }
    }

    fun toggleAdvancedNdi() {
        _uiState.value = _uiState.value.copy(showAdvancedNdi = !_uiState.value.showAdvancedNdi)
        refreshNdiDiagnostics()
    }

    // ---- v1.9.1 (#16): advertise-by-name setting ----

    private fun loadAdvertiseSettings() {
        val prefs = getNdiPrefs()
        val byName = prefs?.getBoolean("advertise_by_name", false) ?: false
        var name = prefs?.getString("advertise_name", "") ?: ""
        if (name.isBlank()) {
            // Prefill with the tablet's system device name; the field stays
            // editable because the router may answer to a different name.
            name = try {
                android.provider.Settings.Global.getString(
                    getApplication<Application>().contentResolver, "device_name"
                ) ?: ""
            } catch (e: Exception) { "" }
        }
        val tipsCollapsed = prefs?.getBoolean("advertise_tips_collapsed", false) ?: false
        _uiState.value = _uiState.value.copy(
            advertiseByName = byName,
            advertiseName = name,
            advertiseTipsCollapsed = tipsCollapsed
        )
        applyAdvertise()
    }

    private fun applyAdvertise() {
        val st = _uiState.value
        broadcastServer.advertiseName = if (st.advertiseByName) st.advertiseName.trim() else null
        _uiState.value = st.copy(
            serverUrl = broadcastServer.getServerUrl(),
            streamUrl = broadcastServer.getStreamUrl()
        )
    }

    fun setAdvertiseByName(on: Boolean) {
        _uiState.value = _uiState.value.copy(advertiseByName = on)
        try { getNdiPrefs()?.edit()?.putBoolean("advertise_by_name", on)?.apply() } catch (e: Exception) {}
        applyAdvertise()
    }

    fun setAdvertiseName(name: String) {
        val clean = name.trim()
        _uiState.value = _uiState.value.copy(advertiseName = clean)
        try { getNdiPrefs()?.edit()?.putString("advertise_name", clean)?.apply() } catch (e: Exception) {}
        applyAdvertise()
    }

    fun toggleAdvertiseTips() {
        val now = !_uiState.value.advertiseTipsCollapsed
        _uiState.value = _uiState.value.copy(advertiseTipsCollapsed = now)
        try { getNdiPrefs()?.edit()?.putBoolean("advertise_tips_collapsed", now)?.apply() } catch (e: Exception) {}
    }

    fun refreshNdiDiagnostics() {
        val srv = broadcastServer
        val lastDropAt = srv.sseLastDropAtMs
        // 2026-10-28 diagnostics split: a viewer leaving is not an
        // error, so the old "write-error" reason (and the new
        // "viewer-left" token) both read as "viewer left" here.
        val dropReason = when (srv.sseLastDropReason) {
            "write-error", "viewer-left" -> "viewer left"
            else -> srv.sseLastDropReason
        }
        _uiState.value = _uiState.value.copy(
            ndiErrorEvents = nativeSender.getErrorEvents(),
            sseConnectedNow = srv.sseConnectedNow,
            sseConnectTotal = srv.sseConnectTotal,
            sseDropTotal = srv.sseDropTotal,
            sseLastDropReason = dropReason,
            sseLastDropAgoSec = if (lastDropAt == 0L) -1
                else (System.currentTimeMillis() - lastDropAt) / 1000
        )
    }

    fun clearNdiDiagnostics() {
        nativeSender.clearErrorEvents()
        // 2026-10-28: Clear clears BOTH the NDI error log AND the
        // HTTP viewer totals (previously only the error log cleared,
        // so the last-drop line looked like it never reset).
        broadcastServer.resetSseTotals()
        _uiState.value = _uiState.value.copy(
            ndiErrorEvents = emptyList(),
            sseConnectTotal = 0,
            sseDropTotal = 0,
            sseLastDropReason = "",
            sseLastDropAgoSec = -1
        )
    }

    // ---- NDI spec persistence (resolution / fps / motion / enabled per feed) ----

    private fun getNdiPrefs(): SharedPreferences? {
        return try {
            getApplication<Application>()?.getSharedPreferences("bible_ndi_source_specs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
    }

    /** Load persisted NDI specs into state. Called once from [loadInitialData]. */
    private fun loadPersistedNdiSpecs() {
        val prefs = getNdiPrefs()
        val specs = NdiNativeSender.ALL_FEEDS.associateWith { feedKey ->
            val d = NdiNativeSender.defaultSpec(feedKey)
            val w = prefs?.getInt("${feedKey}_w", d.width) ?: d.width
            val h = prefs?.getInt("${feedKey}_h", d.height) ?: d.height
            val fps = prefs?.getInt("${feedKey}_fps", d.fps) ?: d.fps
            val motion = prefs?.getBoolean("${feedKey}_motion", d.motionEnabled) ?: d.motionEnabled
            // Validate against the allowed option lists; stale values fall back to defaults.
            val res = NdiNativeSender.RESOLUTION_OPTIONS.firstOrNull { it.first == w && it.second == h }
                ?: (d.width to d.height)
            val validFps = if (NdiNativeSender.FPS_OPTIONS.contains(fps)) fps else d.fps
            NdiSourceSpec(feedKey, res.first, res.second, validFps, motion)
        }
        _uiState.value = _uiState.value.copy(
            ndiSourceSpecs = specs,
            ndiLowerEnabled = prefs?.getBoolean("${NdiNativeSender.FEED_LOWER}_enabled", true) ?: true,
            ndiFullShowEnabled = prefs?.getBoolean("${NdiNativeSender.FEED_FULL}_enabled", true) ?: true
        )
    }

    private fun persistNdiSpec(spec: NdiSourceSpec) {
        getNdiPrefs()?.edit()
            ?.putInt("${spec.feedKey}_w", spec.width)
            ?.putInt("${spec.feedKey}_h", spec.height)
            ?.putInt("${spec.feedKey}_fps", spec.fps)
            ?.putBoolean("${spec.feedKey}_motion", spec.motionEnabled)
            ?.apply()
    }

    // v1.9.1: sermon shade-pair picks (Sermon tab palette dialog).
    private fun getSermonPrefs(): SharedPreferences? {
        return try {
            getApplication<Application>()?.getSharedPreferences("sermon_ui_prefs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
    }

    // App color themes (own prefs file; the old sermon pair keys stay
    // readable only for the one-time migration below).
    private fun getThemePrefs(): SharedPreferences? {
        return try {
            getApplication<Application>()?.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            null
        }
    }

    private fun persistThemePrefs() {
        val prefs = getThemePrefs() ?: return
        val customsJson = JSONArray()
        for (theme in _uiState.value.customThemes) {
            customsJson.put(theme.toJson())
        }
        prefs.edit()
            .putString("selected_theme_id", _uiState.value.selectedThemeId)
            .putString("custom_themes_json", customsJson.toString())
            .apply()
    }

    private fun getUiPrefs(): SharedPreferences? = try {
        getApplication<Application>()?.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    } catch (e: Exception) { null }

    /**
     * Item 16: reader font size, theme mode, and keep-awake are user
     * settings — they must survive a restart like every other setting.
     * Stored in their own ui_prefs file (defaults match the UiState
     * defaults: 18 / SYSTEM / true).
     */
    private fun loadUiPrefs() {
        val prefs = getUiPrefs()
        val fontSize = (prefs?.getInt("reader_font_size", 18) ?: 18).coerceIn(12, 36)
        val mode = try {
            AppThemeMode.valueOf(prefs?.getString("app_theme_mode", null) ?: "SYSTEM")
        } catch (e: Exception) { AppThemeMode.SYSTEM }
        val keepOn = prefs?.getBoolean("keep_screen_on", true) ?: true
        _uiState.value = _uiState.value.copy(
            readerFontSize = fontSize,
            appThemeMode = mode,
            isKeepScreenOn = keepOn
        )
    }

    private fun persistUiPrefs() {
        val state = _uiState.value
        getUiPrefs()?.edit()
            ?.putInt("reader_font_size", state.readerFontSize)
            ?.putString("app_theme_mode", state.appThemeMode.name)
            ?.putBoolean("keep_screen_on", state.isKeepScreenOn)
            ?.apply()
    }

    private fun loadThemePrefs() {
        val prefs = getThemePrefs()
        val customs = mutableListOf<ColorTheme>()
        try {
            val raw = prefs?.getString("custom_themes_json", null)
            if (raw != null) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    colorThemeFromJson(arr.getJSONObject(i))?.let { customs.add(it) }
                }
            }
        } catch (e: Exception) {
            customs.clear()
        }
        var selectedId = prefs?.getString("selected_theme_id", "classic") ?: "classic"
        // One-time migration: a user with non-default pair picks keeps
        // their exact look as a custom theme (Classic chrome + picked
        // bands + teal recents); everyone else lands on Classic.
        if (prefs?.getBoolean("theme_migrated_v1", false) != true) {
            val sermonPrefs = getSermonPrefs()
            val darkId = sermonPrefs?.getString("sermon_pair_dark", "d1") ?: "d1"
            val lightId = sermonPrefs?.getString("sermon_pair_light", "l1") ?: "l1"
            migratedThemeFromPairs(darkId, lightId)?.let { migrated ->
                customs.removeAll { it.id == migrated.id }
                customs.add(migrated)
                selectedId = migrated.id
            }
            prefs?.edit()?.putBoolean("theme_migrated_v1", true)?.apply()
        }
        _uiState.value = _uiState.value.copy(
            selectedThemeId = selectedId,
            customThemes = customs
        )
        // Persist the migrated selection/customs so the server (which reads
        // these prefs directly) sees the same theme as the app.
        persistThemePrefs()
    }

    fun selectTheme(themeId: String) {
        _uiState.value = _uiState.value.copy(selectedThemeId = themeId)
        persistThemePrefs()
    }

    /** Save-as-new: a working copy becomes a new custom theme and is selected. */
    fun saveNewCustomTheme(theme: ColorTheme) {
        val customs = _uiState.value.customThemes.toMutableList()
        customs.removeAll { it.id == theme.id }
        customs.add(theme)
        _uiState.value = _uiState.value.copy(
            selectedThemeId = theme.id,
            customThemes = customs
        )
        persistThemePrefs()
    }

    /** Update: a custom theme's tokens are replaced (built-ins cannot be updated). */
    fun updateCustomTheme(theme: ColorTheme) {
        if (ColorThemes.isBuiltIn(theme.id)) return
        val customs = _uiState.value.customThemes.map { if (it.id == theme.id) theme else it }
        _uiState.value = _uiState.value.copy(customThemes = customs)
        persistThemePrefs()
    }

    /** Delete: removes a custom theme (built-ins cannot be deleted); if the
     * deleted theme was selected, selection falls back to Classic. */
    fun deleteCustomTheme(themeId: String) {
        if (ColorThemes.isBuiltIn(themeId)) return
        val customs = _uiState.value.customThemes.filter { it.id != themeId }
        val selected = if (_uiState.value.selectedThemeId == themeId) "classic" else _uiState.value.selectedThemeId
        _uiState.value = _uiState.value.copy(selectedThemeId = selected, customThemes = customs)
        persistThemePrefs()
    }

    private fun persistNdiEnabled(feedKey: String, enabled: Boolean) {
        getNdiPrefs()?.edit()?.putBoolean("${feedKey}_enabled", enabled)?.apply()
    }

    fun toggleNdiSource(source: String) {
        _uiState.value = when (source) {
            "lower" -> _uiState.value.copy(ndiLowerEnabled = !_uiState.value.ndiLowerEnabled)
            "full" -> _uiState.value.copy(ndiFullShowEnabled = !_uiState.value.ndiFullShowEnabled)
            // Legacy keys from earlier versions
            "lower_full" -> _uiState.value.copy(ndiLowerEnabled = !_uiState.value.ndiLowerEnabled)
            "full_full" -> _uiState.value.copy(ndiFullShowEnabled = !_uiState.value.ndiFullShowEnabled)
            else -> _uiState.value
        }
        val s = _uiState.value
        persistNdiEnabled(NdiNativeSender.FEED_LOWER, s.ndiLowerEnabled)
        persistNdiEnabled(NdiNativeSender.FEED_FULL, s.ndiFullShowEnabled)
        // Item 18: while the protocol is OFF, the toggle only records
        // the selection — syncing would restart senders, including the
        // untouched feed, since sync starts every enabled feed.
        if (s.isNdiProtocolEnabled) {
            syncNativeSources()
            triggerAllFrames()
            // v1.9: keep the foreground notification's feed list truthful.
            NdiService.refreshNotification(getApplication())
        }
    }

    private fun triggerAllFrames() {
        nativeSender.triggerFrame(NdiNativeSender.FEED_LOWER, false)
        nativeSender.triggerFrame(NdiNativeSender.FEED_FULL, true)
    }

    /**
     * v2.0: Immediate (non-debounced) push for discrete actions like style
     * selection — the debounced triggerFrame can be cancelled by a follow-up
     * call, leaving NDI on stale state until the send-loop poll catches up.
     */
    private fun triggerAllFramesImmediate() {
        nativeSender.triggerFrameImmediate(NdiNativeSender.FEED_LOWER, false)
        nativeSender.triggerFrameImmediate(NdiNativeSender.FEED_FULL, true)
    }

    fun stopBroadcastServer() {
        nativeSender.stopAll()
        try { broadcastServer.stop() } catch (e: Exception) { /* ignore */ }
        // v1.9: clears the auto-resume flag, drops the foreground notification,
        // and stops the service.
        NdiService.stop(getApplication())
        _uiState.value = _uiState.value.copy(
            isServerRunning = false,
            isNativeNdiActive = false,
            isNativeShowActive = false,
            statusMessage = "NDI Server Stopped"
        )
    }

    fun selectTestament(testament: Testament) {
        val books = BibleRepository.allBooks.filter { it.testament == testament }
        val newBook = books.firstOrNull() ?: _uiState.value.selectedBook
        selectBook(newBook, 1)
        _uiState.value = _uiState.value.copy(selectedTestament = testament)
    }

    fun selectBook(book: BibleBook, chapter: Int = 1) {
        val verses = BibleRepository.getVerses(book.id, chapter)
        _uiState.value = _uiState.value.copy(
            selectedBook = book,
            selectedChapter = chapter,
            displayedVerses = verses,
            selectedTestament = book.testament,
            readerFocusVerseId = null
        )
        // v2.0: removed setLiveState(false) — browsing chapters must not take
        // the broadcast off-air. Outputs hold the previous verse until a new
        // one is tapped. Explicit off-air still via toggleLive()/clearBroadcast().
        // v1.9.1 (F5): activeVerse must NOT be nulled here either — the ON AIR
        // button reads (isLiveOnAir && activeVerse != null), so nulling it on
        // a chapter swipe flipped the indicator to OFF AIR while the verse
        // was still broadcasting. Browsing never touches the live verse;
        // only an explicit clear/toggle does.
    }

    fun selectChapter(chapter: Int) {
        val book = _uiState.value.selectedBook
        val verses = BibleRepository.getVerses(book.id, chapter)
        _uiState.value = _uiState.value.copy(
            selectedChapter = chapter,
            displayedVerses = verses,
            readerFocusVerseId = null
        )
        // v2.0: removed setLiveState(false) — see selectBook above.
        // v1.9.1 (F5): activeVerse stays — see selectBook.
    }

    fun nextChapter() {
        val currentBook = _uiState.value.selectedBook
        val currentChapter = _uiState.value.selectedChapter
        if (currentChapter < currentBook.totalChapters) {
            selectChapter(currentChapter + 1)
        } else {
            val allBooks = BibleRepository.allBooks
            val currentIndex = allBooks.indexOfFirst { it.id == currentBook.id }
            if (currentIndex != -1 && currentIndex < allBooks.size - 1) {
                val nextBook = allBooks[currentIndex + 1]
                selectBook(nextBook, 1)
            }
        }
    }

    fun prevChapter() {
        val currentBook = _uiState.value.selectedBook
        val currentChapter = _uiState.value.selectedChapter
        if (currentChapter > 1) {
            selectChapter(currentChapter - 1)
        } else {
            val allBooks = BibleRepository.allBooks
            val currentIndex = allBooks.indexOfFirst { it.id == currentBook.id }
            if (currentIndex > 0) {
                val prevBook = allBooks[currentIndex - 1]
                selectBook(prevBook, prevBook.totalChapters)
            }
        }
    }

    fun setBibleVersion(version: BibleVersion) {
        _uiState.value = _uiState.value.copy(bibleVersion = version)
        // v1.8: sync the three-way language mode with the reader's version picker.
        val newMode = when (version) {
            BibleVersion.DUAL_BILINGUAL -> LanguageMode.BOTH
            BibleVersion.ENGLISH_KJV, BibleVersion.ENGLISH_WEB -> LanguageMode.ENGLISH_ONLY
            else -> LanguageMode.ARABIC_ONLY
        }
        if (newMode != _uiState.value.activeTemplate.languageMode) {
            updateActiveTemplate(_uiState.value.activeTemplate.copy(languageMode = newMode))
        }
    }

    /**
     * Toggle tashkeel (Arabic diacritics) on/off. Persists the preference and
     * reloads the current chapter so verses update immediately.
     */
    fun setTashkeelEnabled(enabled: Boolean) {
        BibleRepository.setTashkeelEnabled(enabled)
        _uiState.value = _uiState.value.copy(tashkeelEnabled = enabled)
        // Reload current chapter to reflect the change
        selectChapter(_uiState.value.selectedChapter)
    }

    /**
     * Set the English Bible version (asv/kjv/web). Persists and reloads
     * the current chapter so verses update immediately.
     */
    fun setEnglishVersion(version: String) {
        BibleRepository.setEnglishVersion(version)
        _uiState.value = _uiState.value.copy(englishVersion = version)
        selectChapter(_uiState.value.selectedChapter)
    }

    private var searchJob: Job? = null

    /**
     * Item 22: searching ran a full-table LIKE scan on the main thread
     * on every keystroke. Now the typed text echoes immediately, the
     * scan is debounced (~250ms), runs on IO, and the previous query
     * is cancelled — only the latest query's results ever reach the UI
     * (a cancelled scan never posts, since withContext rethrows on
     * resume when the job was cancelled mid-scan).
     */
    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
            return
        }
        // Echo the typed text immediately (the field must not lag the
        // keyboard); only the expensive scan is debounced.
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob = viewModelScope.launch {
            delay(250)
            val results = withContext(Dispatchers.IO) {
                BibleRepository.searchVerses(query)
            }
            _uiState.value = _uiState.value.copy(
                searchQuery = query,
                searchResults = results,
                isSearching = true
            )
        }
    }

    fun clearSearch() {
        // Cancel any debounced scan still waiting, or it would post
        // stale results (and flip isSearching back on) after the clear.
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }

    /**
     * Called when the user clicks a verse in the Bible reader.
     * Feeds the verse to NDI protocol instantly and takes it live!
     */
    fun onVerseClicked(verse: BibleVerse) {
        val needsNavigation = _uiState.value.isSearching

        if (needsNavigation) {
            val book = BibleRepository.allBooks.find { it.id == verse.bookId } ?: _uiState.value.selectedBook
            // v1.8: Navigate first (selectBook clears activeVerse), then set the verse
            // so the UI auto-scrolls to it and highlights it.
            selectBook(book, verse.chapter)
        }

        // v2.0: Use the tashkeel-aware verse from the loaded chapter if available.
        // Search results carry plain text; the reloaded chapter respects the
        // tashkeel setting, so prefer that version.
        val displayVerse = _uiState.value.displayedVerses.find {
            it.bookId == verse.bookId && it.chapter == verse.chapter && it.verse == verse.verse
        } ?: verse

        _uiState.value = _uiState.value.copy(
            activeVerse = displayVerse,
            isLiveOnAir = true,
            readerFocusVerseId = null,
            statusMessage = "Cued & Live: ${displayVerse.getFormattedArabicCitation()}",
            isSearching = false, // Close search view on click
            recentVerses = (listOf(displayVerse) + _uiState.value.recentVerses.filterNot { it.id == displayVerse.id }).take(20)
        )

        // v1.9.3 patch (item 12): mirror the recents to the remote page.
        broadcastServer.setRecentVerses(_uiState.value.recentVerses)
        persistRecentState()

        broadcastServer.updateVerse(verse, live = true)
        triggerAllFrames()

        // Item 21: Library taps push to vMix like sermon/remote
        // triggers do (pushToVMix self-gates on vmix_enabled + the
        // configured fields). The displayed (tashkeel-aware) verse is
        // what goes on screen, so it is what vMix gets.
        pushToVMix(displayVerse)

        // Item 27: Library tap is a trigger source of its own.
        broadcastServer.setLastTrigger("library", displayVerse.getFormattedArabicCitation())
    }

    fun toggleLive() {
        val newLive = !_uiState.value.isLiveOnAir
        _uiState.value = _uiState.value.copy(
            isLiveOnAir = newLive,
            statusMessage = if (newLive) "LIVE ON AIR (NDI Streaming)" else "BROADCAST CLEARED (Transparent)"
        )
        broadcastServer.setLiveState(newLive)
        triggerAllFrames()
    }

    /**
     * v1.9.1: absolute ON AIR control for remote surfaces (the /remote page
     * mirrors the Library ON AIR button). Same semantics as [toggleLive]:
     * off blanks every output but retains the verse; on brings it back.
     */
    fun setOnAir(on: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLiveOnAir = on,
            statusMessage = if (on) "LIVE ON AIR (NDI Streaming)" else "BROADCAST CLEARED (Transparent)"
        )
        broadcastServer.setLiveState(on)
        triggerAllFrames()
    }

    fun clearBroadcast() {
        _uiState.value = _uiState.value.copy(
            isLiveOnAir = false,
            statusMessage = "Lower Third Cleared (100% Transparent Screen)"
        )
        broadcastServer.setLiveState(false)
        triggerAllFrames()
    }

    /**
     * v1.8: trigger a verse from the HTTP remote API (/api/trigger).
     * Looks up the verse by book/chapter/verse and takes it live.
     * Target is "lower", "show", or "both" (both = current behavior).
     * [source] is declared by the caller for the /debug last-trigger
     * record ("remote" by default; sermon passes its own) — never
     * hardcoded here, so sermon triggers are not mislabeled remote.
     * Returns true if the verse was found and triggered.
     */
    fun triggerVerseByReference(bookId: String, chapter: Int, verseNum: Int, target: String, source: String = "remote"): Boolean {
        return try {
            val verses = BibleRepository.getVerses(bookId, chapter)
            val verse = verses.firstOrNull { it.verse == verseNum } ?: return false

            // Update UI state (no navigation — this is a remote trigger).
            _uiState.value = _uiState.value.copy(
                activeVerse = verse,
                isLiveOnAir = true,
                statusMessage = "Remote trigger: ${verse.getFormattedArabicCitation()}",
                recentVerses = (listOf(verse) + _uiState.value.recentVerses.filterNot { it.id == verse.id }).take(20)
            )

            // v1.9.3 patch (item 12): mirror the recents to the remote page.
            broadcastServer.setRecentVerses(_uiState.value.recentVerses)
            persistRecentState()

            // v1.9.1 (#14): keep the Library reader in sync, exactly like a
            // sermon-row tap — the operator who walks back to the tablet
            // finds the reader on the triggered verse, highlighted live.
            // Silent: no tab switch is forced.
            syncReaderToVerse(verse)

            // Take live on the requested target(s).
            // For now, "lower"/"show"/"both" all use the shared verse state;
            // the target selects which outputs render it (both = current).
            broadcastServer.updateVerse(verse, live = true)
            triggerAllFrames()

            // vMix SetText push (if enabled).
            pushToVMix(verse)

            // Item 27: record the trigger (source declared by caller).
            broadcastServer.setLastTrigger(source, verse.getFormattedArabicCitation())

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * v2.0: text shared from another app (Google Keep, Bible apps) via
     * ACTION_SEND / ACTION_PROCESS_TEXT. Scans the text for references
     * (ReferenceScanner — book-name anchored, ranges supported) and expands
     * them against the database. Canonical text always comes from the app's
     * own database (so the tashkeel setting applies); the shared text is
     * only the carrier.
     *
     * v1.9.1 rework (Ashraf's spec): sharing STAGES the verses — nothing
     * goes on air; the operator taps a row to air it. Each REFERENCE is its
     * own group (range included) and groups alternate shades. The new
     * batch is PREPENDED to the existing list (duplicates move to the top
     * with the new batch), and the list is persisted so it survives the
     * app being closed.
     */
    fun handleSharedText(text: String) {
        val refs = com.arabicchristianmedia.model.ReferenceScanner.scan(text, BibleRepository.allBooks)
        val groups = mutableListOf<List<BibleVerse>>()
        for (ref in refs) {
            val chapterVerses = BibleRepository.getVerses(ref.book.id, ref.chapter)
            if (chapterVerses.isEmpty()) continue
            val selected = when {
                ref.wholeChapter -> chapterVerses
                ref.verseList != null -> ref.verseList.mapNotNull { v -> chapterVerses.firstOrNull { it.verse == v } }
                ref.endVerse != null -> chapterVerses.filter { it.verse in ref.verse..ref.endVerse }
                else -> chapterVerses.filter { it.verse == ref.verse }
            }
            if (selected.isNotEmpty()) groups.add(selected)
        }
        if (groups.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "لم يتم العثور على مرجع كتابي في النص المشارَك (No verse reference found in the shared text)"
            )
            return
        }
        val current = _uiState.value.sermonPlaylist
        var nextGroupId = (current.maxOfOrNull { it.groupId } ?: 0L) + 1L
        val shades = SermonGrouping.shadesForNewBatch(current.firstOrNull()?.shade, groups.size)
        val newEntries = groups.flatMapIndexed { index, verses ->
            val gid = nextGroupId++
            verses.map { SermonEntry(it, gid, shades[index]) }
        }
        // Dedupe: a verse shared again moves up with the new batch.
        val newKeys = newEntries.map { it.key }.toSet()
        val merged = newEntries + current.filterNot { it.key in newKeys }
        _uiState.value = _uiState.value.copy(
            sermonPlaylist = merged,
            sermonListNonce = _uiState.value.sermonListNonce + 1,
            statusMessage = "أُضيفت ${newEntries.size} آية إلى أعلى القائمة — اضغط أي آية لبثّها على الهواء"
        )
        persistSermonWorking()
    }

    /**
     * v1.9.1: tap on a sermon row — the verse goes on air (the exact
     * /api/trigger path) AND the reader silently syncs to it in the
     * background (book/chapter selected, chapter loaded) WITHOUT switching
     * tabs, so switching to the Library tab later lands on this verse.
     */
    fun showSermonVerse(entry: SermonEntry) {
        val verse = entry.verse
        triggerVerseByReference(verse.bookId, verse.chapter, verse.verse, "both", source = "sermon")
        syncReaderToVerse(verse)
        _uiState.value = _uiState.value.copy(
            statusMessage = "على الهواء: ${verse.getFormattedArabicCitation()}"
        )
    }

    // ── Sermon APIs (next/prev first, presenter + pedal) ──────────────
    // The HTTP server calls these through providers (see wiring in init);
    // the tablet presenter keys call stepSermon() directly. Both paths
    // share SermonNavigator, so API and clicker can never disagree on
    // position. Control endpoints are LAN-only like trigger/clear;
    // PIN/token wiring is a follow-up tied to the B2 API-PIN design —
    // no PIN system is invented here (see server comments).

    /** Current working list + live-verse index (-1 when not in the list). */
    fun sermonCurrentState(): Triple<List<SermonEntry>, Int, Boolean> {
        val state = _uiState.value
        return Triple(
            state.sermonPlaylist,
            SermonNavigator.currentIndex(state.sermonPlaylist, state.activeVerse),
            state.isLiveOnAir
        )
    }

    /**
     * Step [step] (+1 next, -1 prev) in the working sermon list and go
     * live with the target verse (same path as tapping a sermon row).
     * At either end, or on an empty list, nothing goes live and the
     * outcome carries [SermonStepOutcome.error] instead.
     */
    fun stepSermon(step: Int): SermonStepOutcome {
        val state = _uiState.value
        val outcome = SermonNavigator.step(state.sermonPlaylist, state.activeVerse, step)
        if (outcome.entry != null) showSermonVerse(outcome.entry)
        return outcome
    }

    /**
     * Create a sermon from shared-note [text] (same scanner + expansion
     * as ACTION_SEND). When [name] is non-blank the result is saved as a
     * named sermon (same-name updates in place, like saveCurrentSermon);
     * when [activate] is true it also replaces the working list.
     * Segments that look like a reference but expand to no verse are
     * returned in [SermonCreateResult.invalidRefs] — never silently
     * dropped (the comma-scanner Passover lesson).
     */
    fun createSermonFromText(name: String?, text: String, activate: Boolean): SermonCreateResult {
        val refs = ReferenceScanner.scan(text, BibleRepository.allBooks)
        val groups = mutableListOf<List<BibleVerse>>()
        val invalid = mutableListOf<String>()
        for (ref in refs) {
            val chapterVerses = BibleRepository.getVerses(ref.book.id, ref.chapter)
            val selected = when {
                ref.wholeChapter -> chapterVerses
                ref.verseList != null -> ref.verseList.mapNotNull { v -> chapterVerses.firstOrNull { it.verse == v } }
                ref.endVerse != null -> chapterVerses.filter { it.verse in ref.verse..ref.endVerse }
                else -> chapterVerses.filter { it.verse == ref.verse }
            }
            if (selected.isEmpty()) {
                invalid.add("${ref.book.englishName} ${ref.chapter}:${ref.verse}")
            } else {
                groups.add(selected)
            }
        }
        // Segments split by comma/newline/semicolon that name a book and
        // carry a chapter:verse token but produced no parsed reference
        // (e.g. a chapter/verse out of range) are invalid, not ignored.
        val parsedKeys = groups.flatten().map { "${it.bookId}:${it.chapter}:${it.verse}" }.toSet()
        for (segment in text.split(',', '\n', ';', '،')) {
            val seg = segment.trim()
            if (seg.isEmpty()) continue
            val hasBook = BibleRepository.allBooks.any { b ->
                seg.contains(b.englishName, ignoreCase = true) ||
                    seg.contains(b.englishAbbreviation, ignoreCase = true) ||
                    seg.contains(b.arabicName) || seg.contains(b.arabicAbbreviation)
            }
            val hasCv = Regex("""\d+\s*:\s*\d+""").containsMatchIn(seg)
            if (hasBook && hasCv) {
                val segRefs = ReferenceScanner.scan(seg, BibleRepository.allBooks)
                val segExpanded = segRefs.flatMap { r ->
                    BibleRepository.getVerses(r.book.id, r.chapter)
                        .filter { v -> r.verseList?.contains(v.verse) == true || v.verse == r.verse }
                        .map { "${it.bookId}:${it.chapter}:${it.verse}" }
                }
                if (segExpanded.isEmpty() || segExpanded.none { it in parsedKeys }) {
                    if (invalid.none { seg.startsWith(it) }) invalid.add(seg)
                }
            }
        }
        var nextGroupId = 1L
        val shades = SermonGrouping.shadesForNewBatch(null, groups.size)
        val entries = groups.flatMapIndexed { index, verses ->
            val gid = nextGroupId++
            verses.map { SermonEntry(it, gid, shades[index]) }
        }
        val trimmedName = name?.trim().orEmpty()
        var savedId: String? = null
        if (trimmedName.isNotEmpty() && entries.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val saved = sermonStore.loadSaved().toMutableList()
            val existingIndex = saved.indexOfFirst { it.name.equals(trimmedName, ignoreCase = true) }
            if (existingIndex >= 0) {
                savedId = saved[existingIndex].id
                saved[existingIndex] = saved[existingIndex].copy(entries = entries.map { it.toRef() }, updatedAt = now)
            } else {
                savedId = "sermon_$now"
                saved.add(0, SavedSermon(savedId, trimmedName, now, entries.map { it.toRef() }))
            }
            sermonStore.saveSaved(saved)
            _uiState.value = _uiState.value.copy(savedSermons = saved)
        }
        if (activate && entries.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                sermonPlaylist = entries,
                currentSermonId = savedId ?: _uiState.value.currentSermonId,
                currentSermonName = if (trimmedName.isNotEmpty()) trimmedName else _uiState.value.currentSermonName
            )
            if (savedId != null) sermonStore.setCurrent(savedId, trimmedName)
            persistSermonWorking()
        }
        return SermonCreateResult(savedId, trimmedName.ifEmpty { null }, entries, entries.size, invalid.distinct())
    }

    /** Load a saved sermon into the working list (replace by default, merge when [merge]). */
    fun activateSermonApi(id: String, merge: Boolean): Boolean {
        val found = _uiState.value.savedSermons.any { it.id == id } ||
            sermonStore.loadSaved().any { it.id == id }
        if (!found) return false
        openSavedSermon(id, merge)
        return true
    }

    /** Rename a saved sermon; false when the id is unknown or the name is blank. */
    fun renameSermonApi(id: String, name: String): Boolean {
        if (name.trim().isEmpty()) return false
        if (_uiState.value.savedSermons.none { it.id == id } &&
            sermonStore.loadSaved().none { it.id == id }) return false
        renameSavedSermon(id, name)
        return true
    }

    /** Delete a saved sermon (never touches the working list); false when unknown. */
    fun deleteSermonApi(id: String): Boolean {
        if (_uiState.value.savedSermons.none { it.id == id } &&
            sermonStore.loadSaved().none { it.id == id }) return false
        deleteSavedSermon(id)
        return true
    }

    /**
     * Backup import. When [dryRun] is true nothing is written — the
     * result only counts what would change and lists invalid entries.
     * Apply writes sermons, templates, theme, and recents through the
     * stores, then reloads UI state from them so the tablet screen
     * cannot go stale. Invalid sections are skipped, not fatal.
     */
    fun importBackupApi(json: String, dryRun: Boolean): BackupImportResult {
        val invalid = mutableListOf<String>()
        var sermonCount = 0
        var templateCount = 0
        var recentCount = 0
        var themeId: String? = null
        var sermonsJson: JSONArray? = null
        var templatesJson: JSONArray? = null
        var recentsJson: JSONArray? = null
        var themeJson: JSONObject? = null
        try {
            val root = JSONObject(json)
            sermonsJson = root.optJSONArray("savedSermons")
            templatesJson = root.optJSONArray("templates")
            recentsJson = root.optJSONArray("recents")
            themeJson = root.optJSONObject("theme")
            sermonCount = sermonsJson?.length() ?: 0
            templateCount = templatesJson?.length() ?: 0
            recentCount = recentsJson?.length() ?: 0
            themeId = themeJson?.optString("selectedThemeId")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            return BackupImportResult(false, 0, 0, 0, null, listOf("Invalid backup JSON: ${e.message}"))
        }
        if (dryRun) {
            return BackupImportResult(false, sermonCount, templateCount, recentCount, themeId, invalid)
        }
        try {
            if (sermonsJson != null) {
                val saved = mutableListOf<SavedSermon>()
                for (i in 0 until sermonsJson.length()) {
                    val obj = sermonsJson.getJSONObject(i)
                    val entriesArr = obj.optJSONArray("entries") ?: JSONArray()
                    val entries = (0 until entriesArr.length()).mapNotNull { j ->
                        val e = entriesArr.getJSONObject(j)
                        SermonRef(e.optString("b"), e.optInt("c"), e.optInt("v"), e.optLong("g"), e.optInt("s"))
                    }
                    saved.add(SavedSermon(obj.optString("id"), obj.optString("name"), obj.optLong("updatedAt"), entries))
                }
                sermonStore.saveSaved(saved)
                _uiState.value = _uiState.value.copy(savedSermons = saved)
            }
        } catch (e: Exception) { invalid.add("sermons: ${e.message}") }
        try {
            if (templatesJson != null) {
                val templates = mutableListOf<LowerThirdTemplate>()
                for (i in 0 until templatesJson.length()) {
                    templateRepo.templateFromJsonString(templatesJson.getJSONObject(i).toString())?.let { templates.add(it) }
                }
                if (templates.isNotEmpty()) {
                    templateRepo.saveTemplates(templates)
                    _uiState.value = _uiState.value.copy(templates = templateRepo.getAllTemplates())
                }
            }
        } catch (e: Exception) { invalid.add("templates: ${e.message}") }
        try {
            if (recentsJson != null) {
                val refs = (0 until recentsJson.length()).mapNotNull { i ->
                    val o = recentsJson.getJSONObject(i)
                    val b = o.optString("b")
                    if (b.isBlank()) null else com.arabicchristianmedia.data.RecentRef(b, o.optInt("c"), o.optInt("v"))
                }
                com.arabicchristianmedia.data.RecentStore(getApplication()).save(refs)
                val expanded = refs.mapNotNull { r ->
                    BibleRepository.getVerses(r.bookId, r.chapter).firstOrNull { it.verse == r.verse }
                }
                _uiState.value = _uiState.value.copy(recentVerses = expanded)
            }
        } catch (e: Exception) { invalid.add("recents: ${e.message}") }
        try {
            if (themeJson != null && themeId != null) {
                getThemePrefs()?.edit()
                    ?.putString("selected_theme_id", themeId)
                    ?.putString("custom_themes_json", themeJson.optJSONArray("customThemes")?.toString() ?: "[]")
                    ?.apply()
                loadThemePrefs()
            }
        } catch (e: Exception) { invalid.add("theme: ${e.message}") }
        return BackupImportResult(true, sermonCount, templateCount, recentCount, themeId, invalid)
    }

    /**
     * v1.9.1: the book icon at the end of a sermon row — open the verse in
     * the reader WITHOUT putting it on air. The reader focuses it (blue
     * focus highlight + scroll) and MainActivity switches to the Library
     * tab via [BibleNdiUiState.readerJumpNonce].
     */
    fun openSermonVerseInReader(entry: SermonEntry) {
        // Item 27: reader focus (not a trigger — nothing goes on air).
        broadcastServer.setReaderFocus(entry.verse.getFormattedArabicCitation())
        syncReaderToVerse(entry.verse, focus = true)
        _uiState.value = _uiState.value.copy(
            readerJumpNonce = _uiState.value.readerJumpNonce + 1
        )
    }

    /**
     * Recents: open a recent verse in the reader WITHOUT putting
     * it on air — same mechanism as the sermon book-icon jump (focus +
     * scroll, MainActivity switches to the Library tab via
     * [BibleNdiUiState.readerJumpNonce]).
     */
    fun openRecentVerseInReader(verse: BibleVerse) {
        // Item 27: reader focus (not a trigger — nothing goes on air).
        broadcastServer.setReaderFocus(verse.getFormattedArabicCitation())
        syncReaderToVerse(verse, focus = true)
        _uiState.value = _uiState.value.copy(
            readerJumpNonce = _uiState.value.readerJumpNonce + 1
        )
    }

    /**
     * Move the reader's position to [verse]'s book/chapter in the
     * background. Never touches the live verse or the on-air state.
     * With [focus], the verse also becomes the reader's focused verse.
     */
    private fun syncReaderToVerse(verse: BibleVerse, focus: Boolean = false) {
        val book = BibleRepository.getBookById(verse.bookId) ?: return
        val verses = BibleRepository.getVerses(book.id, verse.chapter)
        _uiState.value = _uiState.value.copy(
            selectedBook = book,
            selectedChapter = verse.chapter,
            displayedVerses = verses,
            selectedTestament = book.testament,
            readerFocusVerseId = if (focus) verse.id else _uiState.value.readerFocusVerseId
        )
    }

    /** v1.9.1: swipe-to-delete one sermon row. The UI blocks swiping the
     * live row; removing a live entry through here still does NOT take
     * the verse off air. */
    fun removeSermonEntry(entry: SermonEntry) {
        _uiState.value = _uiState.value.copy(
            sermonPlaylist = _uiState.value.sermonPlaylist.filterNot {
                it.verse.id == entry.verse.id && it.groupId == entry.groupId
            }
        )
        persistSermonWorking()
    }

    /** Item 11: undo a swipe-remove — reinsert the exact removed entry
     * (same verse, group and shade, not re-derived) at its old position.
     * The index is clamped, so undo after a clear-all or a list swap
     * still lands somewhere sensible instead of crashing. */
    fun restoreSermonEntry(entry: SermonEntry, index: Int) {
        val current = _uiState.value.sermonPlaylist.toMutableList()
        current.add(index.coerceIn(0, current.size), entry)
        _uiState.value = _uiState.value.copy(sermonPlaylist = current)
        persistSermonWorking()
    }

    /** v1.9.1: empty the working list (also clears its persisted copy and detaches it from any saved sermon). */
    fun clearSermonPlaylist() {
        _uiState.value = _uiState.value.copy(
            sermonPlaylist = emptyList(),
            currentSermonId = null,
            currentSermonName = null
        )
        sermonStore.setCurrent(null, null)
        persistSermonWorking()
    }

    /**
     * v1.9.1: save the working list under [name]. Saving with the name of
     * an existing saved sermon updates that sermon in place; a new name
     * creates a new saved sermon. The working list now belongs to it.
     */
    fun saveCurrentSermon(name: String) {
        val trimmed = name.trim()
        val entries = _uiState.value.sermonPlaylist
        if (trimmed.isEmpty() || entries.isEmpty()) return
        val now = System.currentTimeMillis()
        val refs = entries.map { it.toRef() }
        val saved = sermonStore.loadSaved().toMutableList()
        val existingIndex = saved.indexOfFirst { it.name.equals(trimmed, ignoreCase = true) }
        val id: String
        if (existingIndex >= 0) {
            id = saved[existingIndex].id
            saved[existingIndex] = saved[existingIndex].copy(entries = refs, updatedAt = now)
        } else {
            id = "sermon_$now"
            saved.add(0, SavedSermon(id, trimmed, now, refs))
        }
        sermonStore.saveSaved(saved)
        sermonStore.setCurrent(id, trimmed)
        _uiState.value = _uiState.value.copy(
            savedSermons = saved,
            currentSermonId = id,
            currentSermonName = trimmed,
            statusMessage = "تم حفظ العظة «$trimmed» (${refs.size} آية)"
        )
    }

    /**
     * v1.9.1: open a saved sermon. Replace = it becomes the working list.
     * Merge = its groups are prepended above the current list (stored
     * colors' alternation preserved, flipped only if the seam would
     * collide; duplicates in the current list are dropped in favor of the
     * merged-in copies). Merge does not change which sermon the working
     * list belongs to.
     */
    fun openSavedSermon(id: String, merge: Boolean) {
        val saved = _uiState.value.savedSermons.firstOrNull { it.id == id } ?: return
        val expanded = saved.entries.mapNotNull { ref ->
            val verse = BibleRepository.getVerses(ref.bookId, ref.chapter)
                .firstOrNull { it.verse == ref.verse } ?: return@mapNotNull null
            SermonEntry(verse, ref.groupId, ref.shade)
        }
        if (!merge) {
            _uiState.value = _uiState.value.copy(
                sermonPlaylist = expanded,
                currentSermonId = saved.id,
                currentSermonName = saved.name,
                statusMessage = "تم فتح العظة «${saved.name}» (${expanded.size} آية)"
            )
            sermonStore.setCurrent(saved.id, saved.name)
        } else {
            val groupOrder = expanded.map { it.groupId }.distinct()
            val storedShades = groupOrder.map { g -> expanded.first { it.groupId == g }.shade }
            val newShades = SermonGrouping.reshadeForMerge(
                _uiState.value.sermonPlaylist.firstOrNull()?.shade, storedShades
            )
            var nextGroupId = (_uiState.value.sermonPlaylist.maxOfOrNull { it.groupId } ?: 0L) + 1L
            val idMap = mutableMapOf<Long, Long>()
            groupOrder.forEach { g -> idMap[g] = nextGroupId++ }
            val shadeMap = groupOrder.zip(newShades).toMap()
            val incoming = expanded.map {
                SermonEntry(it.verse, idMap[it.groupId] ?: it.groupId, shadeMap[it.groupId] ?: it.shade)
            }
            val incomingKeys = incoming.map { it.key }.toSet()
            val merged = incoming + _uiState.value.sermonPlaylist.filterNot { it.key in incomingKeys }
            _uiState.value = _uiState.value.copy(
                sermonPlaylist = merged,
                statusMessage = "تم دمج العظة «${saved.name}» في أعلى القائمة"
            )
        }
        persistSermonWorking()
    }

    /** v1.9.1: rename a saved sermon (also renames the header when it is the open one). */
    fun renameSavedSermon(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val saved = sermonStore.loadSaved().map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        sermonStore.saveSaved(saved)
        val isCurrent = _uiState.value.currentSermonId == id
        if (isCurrent) sermonStore.setCurrent(id, trimmed)
        _uiState.value = _uiState.value.copy(
            savedSermons = saved,
            currentSermonName = if (isCurrent) trimmed else _uiState.value.currentSermonName
        )
    }

    /** v1.9.1: delete a saved sermon from the library (never touches the working list). */
    fun deleteSavedSermon(id: String) {
        val saved = sermonStore.loadSaved().filterNot { it.id == id }
        sermonStore.saveSaved(saved)
        val isCurrent = _uiState.value.currentSermonId == id
        if (isCurrent) sermonStore.setCurrent(null, null)
        _uiState.value = _uiState.value.copy(
            savedSermons = saved,
            currentSermonId = if (isCurrent) null else _uiState.value.currentSermonId,
            currentSermonName = if (isCurrent) null else _uiState.value.currentSermonName
        )
    }

    private fun persistSermonWorking() {
        sermonStore.saveWorking(_uiState.value.sermonPlaylist.map { it.toRef() })
    }

    // ── v1.9.1: custom font import ─────────────────────────────────────

    /** Reload the imported-font registry + UI state from the FontStore. */
    fun refreshCustomFonts() {
        val fonts = FontStore(getApplication()).list()
        CustomFonts.refresh(fonts)
        _uiState.value = _uiState.value.copy(customFonts = fonts)
    }

    /**
     * Import a user-picked TTF/OTF (content URI string from the file
     * picker) under [name]. Blank name falls back to the file's own name.
     * The font then appears in both font dropdowns and works on every
     * output (editor preview, NDI, HTTP overlays).
     */
    fun importCustomFont(uriString: String, name: String) {
        val store = FontStore(getApplication())
        val uri = try { Uri.parse(uriString) } catch (e: Exception) { null }
        if (uri == null) {
            _uiState.value = _uiState.value.copy(statusMessage = "تعذر قراءة ملف الخط (Could not read the font file)")
            return
        }
        val finalName = name.trim().ifEmpty {
            store.displayNameFor(uri)?.substringBeforeLast('.')?.trim().orEmpty()
        }
        if (finalName.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "أدخل اسماً للخط (Enter a name for the font)")
            return
        }
        if (com.arabicchristianmedia.model.BundledFonts.find(finalName) != null ||
            finalName == com.arabicchristianmedia.model.ArabicFonts.SYSTEM
        ) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "هذا الاسم محجوز لخط مدمج — اختر اسماً آخر (That name belongs to a bundled font — pick another)"
            )
            return
        }
        val imported = store.importFromUri(uri, finalName)
        if (imported == null) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "فشل استيراد الخط — تأكد أنه ملف TTF أو OTF وأن الاسم غير مكرر (Import failed: TTF/OTF only, name must be unique)"
            )
            return
        }
        refreshCustomFonts()
        broadcastServer.invalidateFittedLayoutCache()
        _uiState.value = _uiState.value.copy(
            statusMessage = "تم استيراد الخط «${imported.name}» — متاح الآن في قوائم الخطوط"
        )
    }

    /**
     * Delete an imported font. Styles that use it fall back to the system
     * typeface until another font is chosen (same as an unknown family).
     */
    fun deleteCustomFont(font: CustomFont) {
        FontStore(getApplication()).delete(font)
        refreshCustomFonts()
        broadcastServer.invalidateFittedLayoutCache()
        _uiState.value = _uiState.value.copy(
            statusMessage = "تم حذف الخط «${font.name}»"
        )
    }

    /** Item 10: restore the persisted recents at launch (same re-expansion
     * as the sermon list — references only, text from the database). */
    private fun restoreRecentState() {
        val verses = recentStore.load().mapNotNull { ref ->
            BibleRepository.getVerses(ref.bookId, ref.chapter)
                .firstOrNull { it.verse == ref.verse }
        }
        _uiState.value = _uiState.value.copy(recentVerses = verses)
        broadcastServer.setRecentVerses(verses)
    }

    private fun persistRecentState() {
        recentStore.save(
            _uiState.value.recentVerses.map {
                RecentRef(bookId = it.bookId, chapter = it.chapter, verse = it.verse)
            }
        )
    }

    /** Item 10: user reset — clears recents everywhere (app + remote). */
    fun clearRecents() {
        _uiState.value = _uiState.value.copy(recentVerses = emptyList())
        recentStore.clear()
        broadcastServer.setRecentVerses(emptyList())
    }

    /**
     * 2026-10-28: recents swipe-to-delete, matching the sermon list
     * exactly (live row guarded in the UI; Undo restores at the same
     * index). Persists like every other recents change.
     */
    fun removeRecentVerse(verse: BibleVerse) {
        val updated = _uiState.value.recentVerses.filterNot { it.id == verse.id }
        _uiState.value = _uiState.value.copy(recentVerses = updated)
        persistRecentState()
        broadcastServer.setRecentVerses(updated)
    }

    fun restoreRecentVerse(verse: BibleVerse, index: Int) {
        val current = _uiState.value.recentVerses.filterNot { it.id == verse.id }.toMutableList()
        current.add(index.coerceIn(0, current.size), verse)
        _uiState.value = _uiState.value.copy(recentVerses = current)
        persistRecentState()
        broadcastServer.setRecentVerses(current)
    }

    /** v1.9.1: restore the persisted working list + saved sermons at launch. */
    private fun restoreSermonState() {
        val refs = sermonStore.loadWorking()
        val entries = refs.mapNotNull { ref ->
            val verse = BibleRepository.getVerses(ref.bookId, ref.chapter)
                .firstOrNull { it.verse == ref.verse } ?: return@mapNotNull null
            SermonEntry(verse, ref.groupId, ref.shade)
        }
        _uiState.value = _uiState.value.copy(
            sermonPlaylist = entries,
            savedSermons = sermonStore.loadSaved(),
            currentSermonId = sermonStore.getCurrentId(),
            currentSermonName = sermonStore.getCurrentName()
        )
    }

    /**
     * v1.8: list background videos for /api/videos.
     * Returns opaque IDs with display names and sizes.
     */
    fun listBackgroundVideos(): List<NdiBroadcastServer.VideoInfo> {
        return try {
            val app = try { getApplication<Application>() } catch (e: Exception) { null } ?: return emptyList()
            val videoStore = com.arabicchristianmedia.server.VideoStore(app)
            videoStore.listVideos().map { (id, file) ->
                // Display name: try to find a template using this video, else use ID prefix.
                val displayName = findVideoDisplayName(id) ?: "Video ${id.take(8)}"
                NdiBroadcastServer.VideoInfo(
                    id = id,
                    displayName = displayName,
                    sizeBytes = file.length()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Find a friendly name for a video ID from templates that use it. */
    private fun findVideoDisplayName(videoId: String): String? {
        return try {
            val all = _uiState.value.templates + templateRepo.getAllTemplates()
            // Look for a template with this video; use its name.
            all.firstOrNull { it.customVideoId == videoId }?.let {
                "Video for '${it.name}'"
            }
        } catch (e: Exception) { null }
    }

    /**
     * v1.8: push the triggered verse to vMix via SetText (if enabled in settings).
     */
    private fun pushToVMix(verse: com.arabicchristianmedia.model.BibleVerse) {
        try {
            // Defensive nullable getter (per AGENTS.md lesson #2).
            val app = try { getApplication<Application>() } catch (e: Exception) { null } ?: return
            val prefs = app.getSharedPreferences("vmix_prefs", 0) ?: return
            val enabled = prefs.getBoolean("vmix_enabled", false)
            if (!enabled) return

            val controller = com.arabicchristianmedia.server.VMixController(
                host = prefs.getString("vmix_host", "") ?: "",
                port = prefs.getInt("vmix_port", 8088),
                input = prefs.getString("vmix_input", "") ?: ""
            )
            val arabicVerseField = prefs.getString("vmix_field_arabic_verse", "") ?: ""
            val arabicCitationField = prefs.getString("vmix_field_arabic_citation", "") ?: ""
            val englishVerseField = prefs.getString("vmix_field_english_verse", "") ?: ""
            val englishCitationField = prefs.getString("vmix_field_english_citation", "") ?: ""

            // Run on IO to avoid blocking.
            Thread {
                try {
                    if (arabicVerseField.isNotEmpty()) {
                        controller.setText(arabicVerseField, verse.arabicText)
                    }
                    if (arabicCitationField.isNotEmpty()) {
                        controller.setText(arabicCitationField, verse.getFormattedArabicCitation())
                    }
                    if (englishVerseField.isNotEmpty()) {
                        controller.setText(englishVerseField, verse.englishText)
                    }
                    if (englishCitationField.isNotEmpty()) {
                        controller.setText(englishCitationField, verse.getFormattedEnglishCitation())
                    }
                } catch (e: Exception) {
                    // Best-effort; log but don't fail the trigger.
                }
            }.start()
        } catch (e: Exception) {
            // vMix push is best-effort.
        }
    }

    // v1.9.1 (#9): nextVerse()/prevVerse() were removed with the Library's
    // verse-stepping arrows (Ashraf: "I will tap on the verse I need").

    /**
     * User selected a saved Lower Third style: the working template becomes the
     * style's full saved values (every flag, font, color, size, motion,
     * transparency and spacing), the style highlights, and the choice persists.
     */
    fun selectTemplate(template: LowerThirdTemplate) {
        val working = template.copy(isFullScreen = false)
        _uiState.value = _uiState.value.copy(
            activeTemplate = working,
            highlightedLowerStyleId = template.id,
            lowerWorkingDirty = false
        )
        templateRepo.saveWorkingTemplate(working)
        templateRepo.setActiveTemplateId(template.id)
        templateRepo.setWorkingDirty(false, false)
        broadcastServer.updateTemplate(working)
        triggerAllFramesImmediate()
    }

    /**
     * User selected a saved Full Show style. Independent of the Lower Third tab:
     * its own style list, highlight and working values.
     */
    fun selectShowTemplate(template: LowerThirdTemplate) {
        val working = template.copy(isFullScreen = true)
        _uiState.value = _uiState.value.copy(
            activeShowTemplate = working,
            highlightedShowStyleId = template.id,
            showWorkingDirty = false
        )
        templateRepo.saveWorkingTemplate(working)
        templateRepo.setActiveShowTemplateId(template.id)
        templateRepo.setWorkingDirty(true, false)
        broadcastServer.updateShowTemplate(working)
        nativeSender.triggerFrameImmediate(NdiNativeSender.FEED_FULL, true)
    }

    /**
     * User edited a Lower Third control. The edit goes live immediately
     * (preview + HTTP + NDI) and is remembered, but the saved style is NOT
     * modified. If the working values no longer match the highlighted style's
     * saved values, the highlight clears (dirty state).
     */
    fun updateActiveTemplate(template: LowerThirdTemplate) {
        val working = template.copy(isFullScreen = false)
        val highlightId = _uiState.value.highlightedLowerStyleId
        val savedStyle = _uiState.value.templates.firstOrNull { it.id == highlightId && !it.isFullScreen }
        val stillMatches = savedStyle != null && savedStyle == working
        val newHighlight = if (stillMatches) highlightId else null
        // Dirty when there is no highlight (either it just cleared, or the user
        // is editing freestyle without any selected style).
        val dirty = newHighlight == null
        _uiState.value = _uiState.value.copy(
            activeTemplate = working,
            highlightedLowerStyleId = newHighlight,
            lowerWorkingDirty = dirty
        )
        templateRepo.saveWorkingTemplate(working)
        templateRepo.setActiveTemplateId(newHighlight)
        templateRepo.setWorkingDirty(false, dirty)
        broadcastServer.updateTemplate(working)
        triggerAllFrames()
    }

    /**
     * User edited a Full Show control. Same dirty/highlight semantics as
     * [updateActiveTemplate], plus the SSE broadcast fix: Full Show edits now
     * reach HTTP /show clients live, not just NDI.
     */
    fun updateActiveShowTemplate(template: LowerThirdTemplate) {
        val working = template.copy(isFullScreen = true)
        val highlightId = _uiState.value.highlightedShowStyleId
        val savedStyle = _uiState.value.templates.firstOrNull { it.id == highlightId && it.isFullScreen }
        val stillMatches = savedStyle != null && savedStyle == working
        val newHighlight = if (stillMatches) highlightId else null
        val dirty = newHighlight == null
        _uiState.value = _uiState.value.copy(
            activeShowTemplate = working,
            highlightedShowStyleId = newHighlight,
            showWorkingDirty = dirty
        )
        templateRepo.saveWorkingTemplate(working)
        templateRepo.setActiveShowTemplateId(newHighlight)
        templateRepo.setWorkingDirty(true, dirty)
        broadcastServer.updateShowTemplate(working)
        nativeSender.triggerFrame(NdiNativeSender.FEED_FULL, true)
    }

    /**
     * "Save as": create a NEW style in the current tab's collection from the
     * working values. (v1.9.1 adds updateStyleInPlace for overwriting the
     * highlighted user style; Save As remains the way to fork a style.)
     */
    fun saveAsNewTemplate(name: String, base: LowerThirdTemplate, isFullScreen: Boolean) {
        val newTemplate = base.copy(
            id = "tpl_${System.currentTimeMillis()}",
            name = name,
            isFullScreen = isFullScreen
        )
        val newList = _uiState.value.templates + newTemplate
        templateRepo.saveTemplates(newList)
        templateRepo.saveWorkingTemplate(newTemplate)
        if (isFullScreen) {
            _uiState.value = _uiState.value.copy(
                templates = newList,
                activeShowTemplate = newTemplate,
                highlightedShowStyleId = newTemplate.id,
                showWorkingDirty = false
            )
            templateRepo.setActiveShowTemplateId(newTemplate.id)
            templateRepo.setWorkingDirty(true, false)
            broadcastServer.updateShowTemplate(newTemplate)
            nativeSender.triggerFrame(NdiNativeSender.FEED_FULL, true)
        } else {
            _uiState.value = _uiState.value.copy(
                templates = newList,
                activeTemplate = newTemplate,
                highlightedLowerStyleId = newTemplate.id,
                lowerWorkingDirty = false
            )
            templateRepo.setActiveTemplateId(newTemplate.id)
            templateRepo.setWorkingDirty(false, false)
            broadcastServer.updateTemplate(newTemplate)
            triggerAllFrames()
        }
    }

    /**
     * v1.9.1: "Update current style" (Ashraf's request — revises the v1.6
     * save-as-new-only rule). Writes the working values back into the
     * highlighted style: same id, same name, same position, highlight kept,
     * dirty cleared.
     *
     * Factory styles are re-supplied from code on every launch, so an edit
     * to one is saved as the user's own copy under the same name (the
     * editor explains this before calling us) and the highlight moves to
     * the copy.
     */
    fun updateStyleInPlace(isFullScreen: Boolean) {
        val state = _uiState.value
        val highlightId = (if (isFullScreen) state.highlightedShowStyleId
                           else state.highlightedLowerStyleId) ?: return
        val saved = state.templates.firstOrNull {
            it.id == highlightId && it.isFullScreen == isFullScreen
        } ?: return
        val working = if (isFullScreen) state.activeShowTemplate else state.activeTemplate

        if (TemplateRepository.isFactoryStyleId(highlightId)) {
            saveAsNewTemplate(saved.name, working, isFullScreen)
            _uiState.value = _uiState.value.copy(
                statusMessage = "تم حفظ تعديلاتك كنسختك الخاصة من «${saved.name}»"
            )
            return
        }

        val updated = working.copy(id = highlightId, name = saved.name, isFullScreen = isFullScreen)
        val newList = TemplateRepository.updatedTemplates(state.templates, updated)
        templateRepo.saveTemplates(newList)
        templateRepo.saveWorkingTemplate(updated)
        templateRepo.setWorkingDirty(isFullScreen, false)
        if (isFullScreen) {
            _uiState.value = _uiState.value.copy(
                templates = newList,
                activeShowTemplate = updated,
                showWorkingDirty = false,
                statusMessage = "تم تحديث النمط «${saved.name}»"
            )
            broadcastServer.updateShowTemplate(updated)
            nativeSender.triggerFrame(NdiNativeSender.FEED_FULL, true)
        } else {
            _uiState.value = _uiState.value.copy(
                templates = newList,
                activeTemplate = updated,
                lowerWorkingDirty = false,
                statusMessage = "تم تحديث النمط «${saved.name}»"
            )
            broadcastServer.updateTemplate(updated)
            triggerAllFrames()
        }
    }

    /**
     * Export a saved style as shareable JSON. Null when the id is unknown.
     */
    fun exportTemplateJson(templateId: String): String? {
        val tpl = _uiState.value.templates.firstOrNull { it.id == templateId } ?: return null
        return templateRepo.templateToJsonString(tpl)
    }

    /**
     * 2026-10-28 design menu: export the WORKING design, so a dirty
     * design exports its current values, not the stale saved style.
     * The JSON keeps the working id/name; importing it creates a new
     * id (importNewTemplate) or overwrites the target (update path),
     * so no saved style is clobbered by the round-trip.
     */
    fun exportWorkingTemplateJson(isFullScreen: Boolean): String {
        val working = if (isFullScreen) _uiState.value.activeShowTemplate else _uiState.value.activeTemplate
        return templateRepo.templateToJsonString(working)
    }

    /**
     * 2026-10-28 design menu: rename a saved style. Factory styles
     * fork to a user copy (like Update does), because a factory id is
     * re-supplied on every launch and a rename in place would vanish.
     * The working copy's name follows, so a clean design stays clean.
     */
    fun renameTemplate(templateId: String, newName: String, isFullScreen: Boolean) {
        val current = _uiState.value.templates.firstOrNull { it.id == templateId } ?: return
        if (TemplateRepository.isFactoryStyleId(templateId)) {
            val working = if (isFullScreen) _uiState.value.activeShowTemplate else _uiState.value.activeTemplate
            saveAsNewTemplate(newName, working.copy(name = newName), isFullScreen)
            return
        }
        val updated = current.copy(name = newName)
        val newList = _uiState.value.templates.map { if (it.id == templateId) updated else it }
        templateRepo.saveTemplates(newList)
        val state = _uiState.value
        val wasHighlighted = if (isFullScreen) state.highlightedShowStyleId == templateId else state.highlightedLowerStyleId == templateId
        if (wasHighlighted) {
            if (isFullScreen) {
                _uiState.value = state.copy(templates = newList, activeShowTemplate = state.activeShowTemplate.copy(name = newName))
                broadcastServer.updateShowTemplate(_uiState.value.activeShowTemplate)
            } else {
                _uiState.value = state.copy(templates = newList, activeTemplate = state.activeTemplate.copy(name = newName))
                broadcastServer.updateTemplate(_uiState.value.activeTemplate)
            }
        } else {
            _uiState.value = state.copy(templates = newList)
        }
    }

    /**
     * Update an existing style's values from imported JSON. The style id is
     * preserved (highlight/selection references stay valid) and the tab's
     * isFullScreen is enforced so collections stay partitioned. When the
     * updated style was the active/highlighted one, the new values go live.
     * Returns false when the JSON is invalid.
     */
    fun updateTemplateFromJson(templateId: String, json: String, isFullScreen: Boolean): Boolean {
        val imported = templateRepo.templateFromJsonString(json) ?: return false
        val current = _uiState.value.templates.firstOrNull { it.id == templateId } ?: return false
        // Factory styles are re-supplied from code on every launch, so
        // an import over one would be silently discarded at next launch.
        // Like Update, save the imported values as the user's own copy
        // under the same name instead.
        if (TemplateRepository.isFactoryStyleId(templateId)) {
            saveAsNewTemplate(current.name, imported.copy(isFullScreen = isFullScreen), isFullScreen)
            _uiState.value = _uiState.value.copy(
                statusMessage = "تم حفظ الاستيراد كنسختك الخاصة من «${current.name}»"
            )
            return true
        }
        val updated = imported.copy(id = current.id, isFullScreen = isFullScreen)
        val newList = _uiState.value.templates.map { if (it.id == templateId) updated else it }
        templateRepo.saveTemplates(newList)
        val wasHighlighted = if (isFullScreen) {
            _uiState.value.highlightedShowStyleId == templateId
        } else {
            _uiState.value.highlightedLowerStyleId == templateId
        }
        if (wasHighlighted) {
            // Go live with the imported values (same as selecting the style).
            if (isFullScreen) selectShowTemplate(updated) else selectTemplate(updated)
        } else {
            _uiState.value = _uiState.value.copy(templates = newList)
        }
        return true
    }

    /**
     * Import shared JSON as a brand-new style in the tab's collection.
     * The new style is highlighted and activated, like Save As New.
     * Returns false when the JSON is invalid.
     */
    fun importNewTemplate(json: String, isFullScreen: Boolean): Boolean {
        val imported = templateRepo.templateFromJsonString(json) ?: return false
        val newTemplate = imported.copy(
            id = "tpl_${System.currentTimeMillis()}",
            isFullScreen = isFullScreen
        )
        val newList = _uiState.value.templates + newTemplate
        templateRepo.saveTemplates(newList)
        _uiState.value = _uiState.value.copy(templates = newList)
        if (isFullScreen) selectShowTemplate(newTemplate) else selectTemplate(newTemplate)
        return true
    }

    /**
     * Delete a saved style. The working values are kept as-is (becoming dirty
     * if the highlighted style was deleted) — nothing is reverted silently.
     */
    fun deleteTemplate(templateId: String, isFullScreen: Boolean) {
        // Factory styles are re-supplied from code on every launch:
        // deleting one only works in-session and it silently returns
        // next launch. Guarded on both sides like built-in themes — the
        // menu hides Delete for factory styles, and this is the backstop
        // for any other caller.
        if (TemplateRepository.isFactoryStyleId(templateId)) return
        val newList = _uiState.value.templates.filterNot { it.id == templateId }
        templateRepo.saveTemplates(newList)
        if (isFullScreen) {
            val wasHighlighted = _uiState.value.highlightedShowStyleId == templateId
            _uiState.value = _uiState.value.copy(
                templates = newList,
                highlightedShowStyleId = if (wasHighlighted) null else _uiState.value.highlightedShowStyleId,
                showWorkingDirty = if (wasHighlighted) true else _uiState.value.showWorkingDirty
            )
            if (wasHighlighted) {
                templateRepo.setActiveShowTemplateId(null)
                templateRepo.setWorkingDirty(true, true)
            }
        } else {
            val wasHighlighted = _uiState.value.highlightedLowerStyleId == templateId
            _uiState.value = _uiState.value.copy(
                templates = newList,
                highlightedLowerStyleId = if (wasHighlighted) null else _uiState.value.highlightedLowerStyleId,
                lowerWorkingDirty = if (wasHighlighted) true else _uiState.value.lowerWorkingDirty
            )
            if (wasHighlighted) {
                templateRepo.setActiveTemplateId(null)
                templateRepo.setWorkingDirty(false, true)
            }
        }
    }

    /**
     * Deep-test fix (small): reload the style list from the repository,
     * which always merges the factory set (DEFAULT_TEMPLATES) with kept
     * user styles. Used by the Editor empty state's "Restore factory
     * styles" button — factory styles cannot be deleted in the UI, so an
     * empty list on a real device is a transient/loaded-state fault, and
     * reloading is the correct repair, not a data migration.
     */
    fun restoreFactoryTemplates() {
        try {
            _uiState.value = _uiState.value.copy(templates = templateRepo.getAllTemplates())
        } catch (e: Exception) {
            // Keep the current list; the empty state stays, with its hint.
        }
    }

    /**
     * Reset for the selected tab only. Resets to the "simple" template per
     * Ashraf's spec: 100% transparent, no motion, no custom video, no text
     * shadow, no accent border, no card glow. Clears the style highlight
     * (the simple template is not a saved style) and marks the tab modified.
     */
    fun resetTemplate(isFullScreen: Boolean) {
        // Ashraf's spec: Reset = "simple" — 100% transparent, no motion, no video,
        // no shadow, no border, no glow. Clears the style highlight (the simple
        // template is not a saved style) and marks the tab modified.
        val working = simpleDefaultTemplate(isFullScreen)
        if (isFullScreen) {
            _uiState.value = _uiState.value.copy(
                activeShowTemplate = working,
                highlightedShowStyleId = null,
                showWorkingDirty = true
            )
            templateRepo.setActiveShowTemplateId(null)
            templateRepo.saveWorkingTemplate(working)
            templateRepo.setWorkingDirty(true, true)
            broadcastServer.updateShowTemplate(working)
            nativeSender.triggerFrame(NdiNativeSender.FEED_FULL, true)
        } else {
            _uiState.value = _uiState.value.copy(
                activeTemplate = working,
                highlightedLowerStyleId = null,
                lowerWorkingDirty = true
            )
            templateRepo.setActiveTemplateId(null)
            templateRepo.saveWorkingTemplate(working)
            templateRepo.setWorkingDirty(false, true)
            broadcastServer.updateTemplate(working)
            triggerAllFrames()
        }
    }

    fun increaseFontSize() {
        val current = _uiState.value.readerFontSize
        if (current < 36) {
            _uiState.value = _uiState.value.copy(readerFontSize = current + 2)
            persistUiPrefs()
        }
    }

    fun decreaseFontSize() {
        val current = _uiState.value.readerFontSize
        if (current > 12) {
            _uiState.value = _uiState.value.copy(readerFontSize = current - 2)
            persistUiPrefs()
        }
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        _uiState.value = _uiState.value.copy(appThemeMode = mode)
        persistUiPrefs()
    }

    fun toggleKeepScreenOn() {
        val newKeep = !_uiState.value.isKeepScreenOn
        _uiState.value = _uiState.value.copy(
            isKeepScreenOn = newKeep,
            statusMessage = if (newKeep) "تم تفعيل إبقاء الشاشة مضاءة (Keep Screen Awake)" else "تم إيقاف إبقاء الشاشة مضاءة"
        )
        persistUiPrefs()
    }

    fun dismissStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        // v1.9: instances live in NdiService now; stop them without creating new ones.
        NdiService.stopAllIfPresent()
    }
}
