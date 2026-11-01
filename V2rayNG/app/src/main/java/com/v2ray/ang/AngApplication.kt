package com.v2ray.ang

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.ThemeManager

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let(ContextCompat::getContextForLanguage))
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MmkvManager.initialize(this)

        AppLocaleManager.initialize(this)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)

        // Speed display is enabled by default; applied once so later user choices are kept
        if (!MmkvManager.decodeSettingsBool("pref_speed_default_applied_v1", false)) {
            MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED, true)
            MmkvManager.encodeSettings("pref_speed_default_applied_v1", true)
        }

        // Initialize theme state from MMKV
        ThemeManager.refresh()

        // راه‌اندازی بخش Shirokhorshid (Psiphon) مطابق PsiphonApplication اصلی آن پروژه
        com.psiphon3.PsiphonApplication.initialize(this)
    }
}
