package com.arabicchristianmedia.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arabicchristianmedia.data.BibleRepository
import com.arabicchristianmedia.data.TemplateRepository
import com.arabicchristianmedia.model.AppThemeMode
import com.arabicchristianmedia.model.AnimatedBackgroundType
import com.arabicchristianmedia.model.BibleBook
import com.arabicchristianmedia.model.BibleVerse
import com.arabicchristianmedia.model.BibleVersion
import com.arabicchristianmedia.model.BroadcastTextAlignment
import com.arabicchristianmedia.model.LanguageMode
import com.arabicchristianmedia.model.LowerThirdTemplate
import com.arabicchristianmedia.model.Testament
import com.arabicchristianmedia.server.NdiBroadcastServer
import com.arabicchristianmedia.server.NdiErrorEvent
import com.arabicchristianmedia.server.NdiNativeSender
import com.arabicchristianmedia.server.NdiSourceSpec
import com.arabicchristianmedia.server.NetworkHelper
import com.arabicchristianmedia.server.NetworkInterfaceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BibleNdiUiState(
    val selectedTestament: Testament = Testament.NEW_TESTAMENT,
    val selectedBook: BibleBook = BibleRepository.getBookById("jhn") ?: BibleRepository.allBooks.first(),
    val selectedChapter: Int = 3,
    val bibleVersion: BibleVersion = BibleVersion.ARABIC_SVD,
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
    val readerFontSize: Int = 18,
    val isKeepScreenOn: Boolean = true,
    // True while the Bible databases are being copied/loaded on first launch.
    val isLoadingBible: Boolean = false
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
    }

    private val templateRepo = TemplateRepository(application)
    private val broadcastServer = NdiBroadcastServer(application, 8080)
    private val nativeSender = NdiNativeSender()

    private val _uiState = MutableStateFlow(BibleNdiUiState())
    val uiState: StateFlow<BibleNdiUiState> = _uiState.asStateFlow()

    init {
        BibleRepository.initialize(application)
        nativeSender.setFrameProvider { isFullScreen ->
            if (isFullScreen) {
                // Use a different rendering context or the same broadcastServer with different template
                // To keep it simple, I'll update the broadcastServer to support dual rendering if needed,
                // or just swap templates temporarily.
                // Better: broadcastServer.renderCurrentFrame(1920, 1080, tpl = _uiState.value.activeShowTemplate)
                broadcastServer.renderCurrentFrame(1920, 1080, isForStream = false, overrideTemplate = _uiState.value.activeShowTemplate)
            } else {
                broadcastServer.renderCurrentFrame(1920, 1080, isForStream = false)
            }
        }
        // Dirty-frame detection: NDI senders only push when the version changes.
        nativeSender.setFrameVersionProvider { broadcastServer.frameVersion }
        // Motion mode: force-rerender animated backgrounds on a capped tick, reusing
        // dedicated bitmaps (no per-tick allocation). Only ticks when the current
        // template actually has animated content.
        nativeSender.setMotionFrameProvider { isFullScreen ->
            if (isFullScreen) {
                broadcastServer.renderMotionFrame(overrideTemplate = _uiState.value.activeShowTemplate)
            } else {
                broadcastServer.renderMotionFrame()
            }
        }
        nativeSender.setMotionContentProvider { isFullScreen ->
            val tpl = if (isFullScreen) _uiState.value.activeShowTemplate else _uiState.value.activeTemplate
            tpl.animatedBackground != AnimatedBackgroundType.NONE
        }
        loadInitialDataAsync()
        refreshNetworkInterfaces()
        // startBroadcastServer() // DO NOT auto-start as per request 8? 
        // User said: "on Library tab, don't start the on air, be sure it's off air till a user click on a verse"
        // This might mean isLive = false, not necessarily server off. 
        // But usually, servers start on init. I'll just set isLive = false.
        startBroadcastServer()
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
        if (enabled && _uiState.value.isServerRunning) {
            syncNativeSources()
            triggerAllFrames()
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
            ?: if (!hasLowerWorking) "tpl_transparent_alpha" else null
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

        broadcastServer.currentVerse = null
        broadcastServer.isLive = false // Ensure server starts off-air
        broadcastServer.currentTemplate = lowerWorking
        broadcastServer.currentShowTemplate = showWorking
    }

    fun startBroadcastServer(port: Int = 8080) {
        refreshNetworkInterfaces()
        _uiState.value = _uiState.value.copy(isNdiProtocolEnabled = true)
        broadcastServer.start(
            onStarted = { url ->
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
        ) { err ->
            _uiState.value = _uiState.value.copy(
                isServerRunning = false,
                statusMessage = "Server Error: $err"
            )
        }
    }

    fun startNdiFeeds() {
        _uiState.value = _uiState.value.copy(
            ndiLowerEnabled = true,
            ndiFullShowEnabled = true
        )
        persistNdiEnabled(NdiNativeSender.FEED_LOWER, true)
        persistNdiEnabled(NdiNativeSender.FEED_FULL, true)
        syncNativeSources()
        triggerAllFrames()
        _uiState.value = _uiState.value.copy(statusMessage = "NDI Sources (Lower Third + Full Show) Started")
    }

    fun stopNdiFeeds() {
        _uiState.value = _uiState.value.copy(
            ndiLowerEnabled = false,
            ndiFullShowEnabled = false
        )
        persistNdiEnabled(NdiNativeSender.FEED_LOWER, false)
        persistNdiEnabled(NdiNativeSender.FEED_FULL, false)
        nativeSender.stopAll()
        _uiState.value = _uiState.value.copy(
            isNativeNdiActive = false,
            isNativeShowActive = false,
            statusMessage = "All NDI Sources Stopped"
        )
    }

    private fun applyNdiSource(spec: NdiSourceSpec, enabled: Boolean, isFullScreen: Boolean): Boolean {
        return if (enabled) {
            nativeSender.startSource(spec.feedKey, isFullScreen, spec)
        } else {
            nativeSender.stopSource(spec.feedKey)
            false
        }
    }

    private fun syncNativeSources() {
        val state = _uiState.value
        val specs = state.ndiSourceSpecs
        val lower = applyNdiSource(
            specs[NdiNativeSender.FEED_LOWER]
                ?: NdiNativeSender.defaultSpec(NdiNativeSender.FEED_LOWER),
            state.ndiLowerEnabled, false
        )
        val full = applyNdiSource(
            specs[NdiNativeSender.FEED_FULL]
                ?: NdiNativeSender.defaultSpec(NdiNativeSender.FEED_FULL),
            state.ndiFullShowEnabled, true
        )

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
        updateNdiSourceSpec(feedKey, current.width, current.height, current.fps, !current.motionEnabled)
    }

    fun toggleAdvancedNdi() {
        _uiState.value = _uiState.value.copy(showAdvancedNdi = !_uiState.value.showAdvancedNdi)
        refreshNdiDiagnostics()
    }

    fun refreshNdiDiagnostics() {
        _uiState.value = _uiState.value.copy(ndiErrorEvents = nativeSender.getErrorEvents())
    }

    fun clearNdiDiagnostics() {
        nativeSender.clearErrorEvents()
        _uiState.value = _uiState.value.copy(ndiErrorEvents = emptyList())
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
        syncNativeSources()
        triggerAllFrames()
    }

    private fun triggerAllFrames() {
        nativeSender.triggerFrame(NdiNativeSender.FEED_LOWER, false)
        nativeSender.triggerFrame(NdiNativeSender.FEED_FULL, true)
    }

    fun stopBroadcastServer() {
        nativeSender.stopAll()
        broadcastServer.stop()
        _uiState.value = _uiState.value.copy(
            isServerRunning = false,
            isNativeNdiActive = false,
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
            activeVerse = null
        )
        broadcastServer.setLiveState(false)
    }

    fun selectChapter(chapter: Int) {
        val book = _uiState.value.selectedBook
        val verses = BibleRepository.getVerses(book.id, chapter)
        _uiState.value = _uiState.value.copy(
            selectedChapter = chapter,
            displayedVerses = verses,
            activeVerse = null
        )
        broadcastServer.setLiveState(false)
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

    fun onSearchQueryChanged(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
            return
        }
        val results = BibleRepository.searchVerses(query)
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchResults = results,
            isSearching = true
        )
    }

    fun clearSearch() {
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
        
        _uiState.value = _uiState.value.copy(
            activeVerse = verse,
            isLiveOnAir = true,
            statusMessage = "Cued & Live: ${verse.getFormattedArabicCitation()}",
            isSearching = false // Close search view on click
        )

        if (needsNavigation) {
            val book = BibleRepository.allBooks.find { it.id == verse.bookId } ?: _uiState.value.selectedBook
            selectBook(book, verse.chapter)
        }

        broadcastServer.updateVerse(verse, live = true)
        triggerAllFrames()
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

    fun clearBroadcast() {
        _uiState.value = _uiState.value.copy(
            isLiveOnAir = false,
            statusMessage = "Lower Third Cleared (100% Transparent Screen)"
        )
        broadcastServer.setLiveState(false)
        triggerAllFrames()
    }

    fun nextVerse() {
        val current = _uiState.value.activeVerse ?: return
        val list = _uiState.value.displayedVerses
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex < list.size - 1) {
            val next = list[currentIndex + 1]
            onVerseClicked(next)
        }
    }

    fun prevVerse() {
        val current = _uiState.value.activeVerse ?: return
        val list = _uiState.value.displayedVerses
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex > 0) {
            val prev = list[currentIndex - 1]
            onVerseClicked(prev)
        }
    }

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
        triggerAllFrames()
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
        nativeSender.triggerFrame(NdiNativeSender.FEED_FULL, true)
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
     * working values. Saved styles are never updated in place. The new style
     * is highlighted and used immediately.
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
     * Export a saved style as shareable JSON. Null when the id is unknown.
     */
    fun exportTemplateJson(templateId: String): String? {
        val tpl = _uiState.value.templates.firstOrNull { it.id == templateId } ?: return null
        return templateRepo.templateToJsonString(tpl)
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
     * Reset for the selected tab only. If a style is highlighted, the working
     * values revert to that style's saved values (same as re-tapping the style)
     * and the highlight stays. If no style is highlighted, the working values
     * revert to the factory defaults for that tab.
     */
    fun resetTemplate(isFullScreen: Boolean) {
        if (isFullScreen) {
            val highlightId = _uiState.value.highlightedShowStyleId
            val savedStyle = _uiState.value.templates.firstOrNull { it.id == highlightId && it.isFullScreen }
            val working = (savedStyle ?: defaultShowTemplate()).copy(isFullScreen = true)
            _uiState.value = _uiState.value.copy(
                activeShowTemplate = working,
                showWorkingDirty = false
            )
            templateRepo.saveWorkingTemplate(working)
            templateRepo.setWorkingDirty(true, false)
            broadcastServer.updateShowTemplate(working)
            nativeSender.triggerFrame(NdiNativeSender.FEED_FULL, true)
        } else {
            val highlightId = _uiState.value.highlightedLowerStyleId
            val savedStyle = _uiState.value.templates.firstOrNull { it.id == highlightId && !it.isFullScreen }
            val working = (savedStyle ?: TemplateRepository.DEFAULT_TEMPLATES[0]).copy(isFullScreen = false)
            _uiState.value = _uiState.value.copy(
                activeTemplate = working,
                lowerWorkingDirty = false
            )
            templateRepo.saveWorkingTemplate(working)
            templateRepo.setWorkingDirty(false, false)
            broadcastServer.updateTemplate(working)
            triggerAllFrames()
        }
    }

    fun increaseFontSize() {
        val current = _uiState.value.readerFontSize
        if (current < 36) {
            _uiState.value = _uiState.value.copy(readerFontSize = current + 2)
        }
    }

    fun decreaseFontSize() {
        val current = _uiState.value.readerFontSize
        if (current > 12) {
            _uiState.value = _uiState.value.copy(readerFontSize = current - 2)
        }
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        _uiState.value = _uiState.value.copy(appThemeMode = mode)
    }

    fun toggleKeepScreenOn() {
        val newKeep = !_uiState.value.isKeepScreenOn
        _uiState.value = _uiState.value.copy(
            isKeepScreenOn = newKeep,
            statusMessage = if (newKeep) "تم تفعيل إبقاء الشاشة مضاءة (Keep Screen Awake)" else "تم إيقاف إبقاء الشاشة مضاءة"
        )
    }

    fun dismissStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        nativeSender.stopAll()
        broadcastServer.stop()
    }
}
