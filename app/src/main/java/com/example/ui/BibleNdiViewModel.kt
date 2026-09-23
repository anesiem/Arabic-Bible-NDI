package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.data.BibleRepository
import com.example.data.TemplateRepository
import com.example.model.AppThemeMode
import com.example.model.BibleBook
import com.example.model.BibleVerse
import com.example.model.BibleVersion
import com.example.model.BroadcastTextAlignment
import com.example.model.LowerThirdTemplate
import com.example.model.Testament
import com.example.server.NdiBroadcastServer
import com.example.server.NdiDiscoveryBeacon
import com.example.server.NdiNativeSender
import com.example.server.NetworkHelper
import com.example.server.NetworkInterfaceInfo
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
    val isMdnsActive: Boolean = true,
    val isNdiProtocolEnabled: Boolean = true,
    val isNativeNdiActive: Boolean = false,
    val isNativeShowActive: Boolean = false,
    val ndiLowerThirdEnabled: Boolean = true,
    val ndiFullShowEnabled: Boolean = false,
    val templates: List<LowerThirdTemplate> = emptyList(),
    val activeTemplate: LowerThirdTemplate = TemplateRepository.DEFAULT_TEMPLATES[0],
    val activeShowTemplate: LowerThirdTemplate = TemplateRepository.DEFAULT_TEMPLATES[0].copy(
        id = "default_show",
        name = "Full Screen Projector",
        isFullScreen = true,
        bgOpacity = 1.0f,
        bgColorHex = "#0A1128",
        verseFontSize = 65,
        referenceFontSize = 40,
        alignment = BroadcastTextAlignment.CENTER,
        isPureTransparentBackground = false
    ),
    val statusMessage: String? = null,
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val readerFontSize: Int = 18
)

class BibleNdiViewModel(application: Application) : AndroidViewModel(application) {

    private val templateRepo = TemplateRepository(application)
    private val broadcastServer = NdiBroadcastServer(application, 8080)
    private val discoveryBeacon = NdiDiscoveryBeacon(application)
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
        loadInitialData()
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

        // Restart mDNS beacon with updated IP if NDI protocol is enabled
        if (_uiState.value.isNdiProtocolEnabled) {
            discoveryBeacon.start(serviceUrl = url, ip = info.ip, port = _uiState.value.serverPort)
        }
    }

    fun setNdiProtocolEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isNdiProtocolEnabled = enabled,
            isMdnsActive = enabled,
            statusMessage = if (enabled) "تم تفعيل بث بروتوكول Full NDI الشفاف" else "تم إيقاف بث NDI لتوفير استهلاك شبكة الواي فاي (Bandwidth Saver)"
        )
        if (enabled && _uiState.value.isServerRunning) {
            val ip = broadcastServer.getLocalIpAddress()
            val url = broadcastServer.getServerUrl()
            syncNativeSources()
            discoveryBeacon.start(serviceUrl = url, ip = ip, port = _uiState.value.serverPort)
            triggerAllFrames()
        } else {
            nativeSender.stopAll()
            discoveryBeacon.stop()
            _uiState.value = _uiState.value.copy(isNativeNdiActive = false, isNativeShowActive = false)
        }
    }

    private fun loadInitialData() {
        val allTpls = templateRepo.getAllTemplates()
        val activeTplId = templateRepo.getActiveTemplateId()
        val activeTpl = allTpls.firstOrNull { it.id == activeTplId } ?: allTpls.first()

        val initialBook = BibleRepository.getBookById("jhn") ?: BibleRepository.allBooks.first()
        val initialChapter = 3
        val verses = BibleRepository.getVerses(initialBook.id, initialChapter)
        val defaultActiveVerse = verses.firstOrNull { it.verse == 16 } ?: verses.firstOrNull()

        _uiState.value = _uiState.value.copy(
            selectedBook = initialBook,
            selectedChapter = initialChapter,
            displayedVerses = verses,
            templates = allTpls,
            activeTemplate = activeTpl,
            activeVerse = defaultActiveVerse,
            isLiveOnAir = false, // Start OFF AIR as per request 8
            statusMessage = "Ready. Tap a verse to go LIVE."
        )

        if (defaultActiveVerse != null) {
            broadcastServer.currentVerse = defaultActiveVerse
            broadcastServer.isLive = false // Ensure server starts off-air
        }
        broadcastServer.currentTemplate = activeTpl
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

                discoveryBeacon.start(serviceUrl = url, ip = ip, port = port)
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
            ndiLowerThirdEnabled = true,
            ndiFullShowEnabled = true
        )
        syncNativeSources()
        triggerAllFrames()
        _uiState.value = _uiState.value.copy(statusMessage = "All NDI Sources (Lower & Full) Started")
    }

    fun stopNdiFeeds() {
        _uiState.value = _uiState.value.copy(
            ndiLowerThirdEnabled = false,
            ndiFullShowEnabled = false
        )
        nativeSender.stopAll()
        _uiState.value = _uiState.value.copy(
            isNativeNdiActive = false,
            isNativeShowActive = false,
            statusMessage = "All NDI Sources Stopped"
        )
    }

    private fun syncNativeSources() {
        val state = _uiState.value
        if (state.ndiLowerThirdEnabled) {
            nativeSender.startSource("Bible-NDI-Lower", false)
        } else {
            nativeSender.stopSource("Bible-NDI-Lower")
        }

        if (state.ndiFullShowEnabled) {
            nativeSender.startSource("Bible-NDI-Full", true)
        } else {
            nativeSender.stopSource("Bible-NDI-Full")
        }
        
        _uiState.value = _uiState.value.copy(
            isNativeNdiActive = state.ndiLowerThirdEnabled,
            isNativeShowActive = state.ndiFullShowEnabled
        )
    }

    fun toggleNdiSource(source: String) {
        when(source) {
            "lower" -> _uiState.value = _uiState.value.copy(ndiLowerThirdEnabled = !_uiState.value.ndiLowerThirdEnabled)
            "full" -> _uiState.value = _uiState.value.copy(ndiFullShowEnabled = !_uiState.value.ndiFullShowEnabled)
        }
        syncNativeSources()
        triggerAllFrames()
    }

    private fun triggerAllFrames() {
        nativeSender.triggerFrame("Bible-NDI-Lower", false)
        nativeSender.triggerFrame("Bible-NDI-Full", true)
    }

    fun stopBroadcastServer() {
        nativeSender.stopAll()
        broadcastServer.stop()
        discoveryBeacon.stop()
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
        // If template has bilingual toggle, sync if needed
        val isDual = version == BibleVersion.DUAL_BILINGUAL
        if (isDual != _uiState.value.activeTemplate.bilingualMode) {
            updateActiveTemplate(_uiState.value.activeTemplate.copy(bilingualMode = isDual))
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

    fun selectTemplate(template: LowerThirdTemplate) {
        _uiState.value = _uiState.value.copy(activeTemplate = template)
        templateRepo.setActiveTemplateId(template.id)
        broadcastServer.updateTemplate(template)
        triggerAllFrames()
    }

    fun updateActiveTemplate(template: LowerThirdTemplate) {
        val updatedList = _uiState.value.templates.map {
            if (it.id == template.id) template else it
        }
        _uiState.value = _uiState.value.copy(
            activeTemplate = template,
            templates = updatedList
        )
        templateRepo.saveTemplates(updatedList)
        templateRepo.setActiveTemplateId(template.id)
        broadcastServer.updateTemplate(template)
        triggerAllFrames()
    }

    fun updateActiveShowTemplate(template: LowerThirdTemplate) {
        _uiState.value = _uiState.value.copy(
            activeShowTemplate = template
        )
        broadcastServer.currentShowTemplate = template
        triggerAllFrames()
    }

    fun saveAsNewTemplate(name: String, base: LowerThirdTemplate) {
        val newTemplate = base.copy(
            id = "tpl_${System.currentTimeMillis()}",
            name = name
        )
        val newList = _uiState.value.templates + newTemplate
        _uiState.value = _uiState.value.copy(
            templates = newList,
            activeTemplate = newTemplate
        )
        templateRepo.saveTemplates(newList)
        templateRepo.setActiveTemplateId(newTemplate.id)
        broadcastServer.updateTemplate(newTemplate)
    }

    fun resetTemplatesToDefault() {
        val defaults = TemplateRepository.DEFAULT_TEMPLATES
        _uiState.value = _uiState.value.copy(
            templates = defaults,
            activeTemplate = defaults[0]
        )
        templateRepo.saveTemplates(defaults)
        templateRepo.setActiveTemplateId(defaults[0].id)
        broadcastServer.updateTemplate(defaults[0])
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

    fun dismissStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        nativeSender.stopAll()
        broadcastServer.stop()
        discoveryBeacon.stop()
    }
}
