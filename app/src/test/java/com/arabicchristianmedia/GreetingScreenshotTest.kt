package com.arabicchristianmedia

import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.arabicchristianmedia.data.BibleRepository
import com.arabicchristianmedia.data.TemplateRepository
import com.arabicchristianmedia.ui.BroadcastPreviewViewport
import com.arabicchristianmedia.ui.PreviewBackground
import com.arabicchristianmedia.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    BibleRepository.initialize(ApplicationProvider.getApplicationContext())
  }

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
          BroadcastPreviewViewport(
            template = TemplateRepository.DEFAULT_TEMPLATES[0],
            verse = BibleRepository.getVerses("jhn", 3).first { it.verse == 16 },
            previewBg = PreviewBackground.CHECKERBOARD_TRANSPARENT
          )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
