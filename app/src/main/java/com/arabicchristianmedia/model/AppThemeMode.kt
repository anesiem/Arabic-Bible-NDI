package com.arabicchristianmedia.model

import androidx.annotation.StringRes
import com.arabicchristianmedia.R

enum class AppThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.s11),
    LIGHT(R.string.s12),
    DARK(R.string.s13)
}
