package com.v2ray.ang.ui.main

import androidx.compose.runtime.mutableStateOf
import com.v2ray.ang.handler.MmkvManager

/**
 * وضعیت تم جدید. مقدار در MMKV ذخیره می‌شود و تغییرش فوراً روی UI اعمال می‌شود.
 * پیش‌فرض: تم فعلی پروژه (false).
 */
object NewThemeState {
    private const val KEY = "pref_main_theme_v2"

    private val flag = mutableStateOf(MmkvManager.decodeSettingsString(KEY, "0") == "1")

    val enabled: Boolean
        get() = flag.value

    fun set(value: Boolean) {
        flag.value = value
        MmkvManager.encodeSettings(KEY, if (value) "1" else "0")
    }

    fun toggle() = set(!enabled)
}
