package com.arabicchristianmedia.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.ui.BibleNdiViewModel

/**
 * Batch-3: HDMI auto-display. Shows the current verse fullscreen on a
 * secondary display (HDMI) via the Android Presentation API.
 * The main screen keeps the Library controls; the external display
 * shows only the verse.
 */
class VersePresentation(
    context: Context,
    display: Display,
    private val viewModel: BibleNdiViewModel
) : Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(context).apply {
            setContent {
                val uiState by viewModel.uiState.collectAsState()
                val verse = uiState.activeVerse
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (verse != null && uiState.isLiveOnAir) {
                        Text(
                            text = verse.arabicText,
                            color = Color.White,
                            fontSize = 48.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        setContentView(composeView)
    }
}
