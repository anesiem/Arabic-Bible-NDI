package com.arabicchristianmedia

import android.app.Application
import android.content.Context
import com.arabicchristianmedia.server.CrashRecorder
import com.arabicchristianmedia.ui.AppLanguage

/**
 * 1.9.25 (item 2, language mode): wraps the Application base context with
 * the chosen interface language so ViewModel / service getString calls
 * resolve in the app language, not the device language. On API 33+ the
 * LocaleManager override already covers the process; on older APIs this
 * wrap (with MainActivity's) is what makes the choice stick.
 */
class BibleNdiApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // 1.10.0 (item 1): install the crash recorder first — onCreate
        // runs before any Activity or Service, so the handler is in
        // place from the first moment the app can crash.
        CrashRecorder.install(this)
    }
}
