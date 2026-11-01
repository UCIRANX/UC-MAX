package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.ConnectionMode
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager

/**
 * تصمیم‌گیری حالت اتصال: کدام پروفایل Xray اجرا شود و Psiphon چطور بالا بیاید.
 *
 * ترتیب زنجیره: عضو اول ورودی (نزدیک گوشی) و عضو آخر خروجی است.
 * - «V2ray از Psiphon»: [هاپ Psiphon، V2ray]  → گوشی → Psiphon → سرور V2ray
 * - «Psiphon از V2ray»: outbound پیش‌فرض = هاپ Psiphon و upstream آن = V2ray (exitGuid)
 * پروفایل‌های داخلی هیچ‌وقت در لیست سرورها ذخیره یا نمایش داده نمی‌شوند.
 */
object ConnectionModeManager {

    /** نام پروفایل زنجیرهٔ داخلی (فقط برای پاک‌سازی نسخه‌های قدیمی) */
    const val CHAIN_REMARKS = "UC MAX chain"

    private const val PREF_CHAIN_EXIT = "pref_uc_chain_exit"

    class Plan(
        /** شناسهٔ پروفایلی که Xray با آن اجرا می‌شود */
        val guid: String,
        /** آیا Psiphon باید بالا بیاید؟ */
        val usePsiphon: Boolean,
        /** true یعنی Psiphon بعد از Xray و با upstream از V2ray بالا می‌آید */
        val psiphonViaXray: Boolean,
        /** پروفایل V2ray که خروجی مسیر Psiphon است (فقط در «Psiphon از V2ray») */
        val exitGuid: String? = null,
    )

    fun currentMode(): ConnectionMode =
        ConnectionMode.fromInt(
            MmkvManager.decodeSettingsString(AppConfig.PREF_CONNECTION_MODE, "0")?.toIntOrNull() ?: 0
        )

    fun setMode(mode: ConnectionMode) {
        MmkvManager.encodeSettings(AppConfig.PREF_CONNECTION_MODE, mode.value.toString())
    }

    /** نقشهٔ اجرا بر اساس حالت فعلی و پروفایل V2ray انتخاب‌شده */
    fun plan(selectedGuid: String): Plan {
        purgeLegacyProfiles()
        return when (currentMode()) {
            ConnectionMode.V2RAY_ONLY -> Plan(selectedGuid, usePsiphon = false, psiphonViaXray = false)
            ConnectionMode.PSIPHON_ONLY -> Plan(
                VirtualProfiles.PSIPHON_HOP_GUID, usePsiphon = true, psiphonViaXray = false
            )
            ConnectionMode.PSIPHON_VIA_V2RAY -> Plan(
                VirtualProfiles.PSIPHON_HOP_GUID,
                usePsiphon = true,
                psiphonViaXray = true,
                exitGuid = selectedGuid,
            )
            ConnectionMode.V2RAY_VIA_PSIPHON -> {
                MmkvManager.encodeSettings(PREF_CHAIN_EXIT, selectedGuid)
                Plan(VirtualProfiles.MANAGED_CHAIN_GUID, usePsiphon = true, psiphonViaXray = false)
            }
        }
    }

    /** زنجیرهٔ داخلی «V2ray از Psiphon» که فقط در حافظه ساخته می‌شود */
    fun buildManagedChain(): ProfileItem? {
        val exitGuid = MmkvManager.decodeSettingsString(PREF_CHAIN_EXIT, "")?.takeIf { it.isNotBlank() }
            ?: return null
        val exitProfile = MmkvManager.decodeServerConfig(exitGuid) ?: return null
        return ProfileItem.create(EConfigType.PROXYCHAIN).apply {
            remarks = CHAIN_REMARKS
            proxyChainProfiles = "${VirtualProfiles.PSIPHON_HOP_GUID},$exitGuid"
            description = "${PsiphonCoreManager.buildHopProfile().remarks} -> ${exitProfile.remarks}"
        }
    }

    /** حذف نسخه‌های قدیمی هاپ و زنجیرهٔ داخلی که قبلاً در لیست ذخیره شده بودند */
    private fun purgeLegacyProfiles() {
        MmkvManager.decodeAllServerList().forEach { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@forEach
            val isLegacyHop = profile.configType == EConfigType.SOCKS &&
                profile.remarks == "Psiphon (local)"
            val isLegacyChain = profile.configType == EConfigType.PROXYCHAIN &&
                profile.remarks == CHAIN_REMARKS
            if (isLegacyHop || isLegacyChain) {
                MmkvManager.removeServer(guid)
            }
        }
    }
}
