package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.ConnectionMode
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager

/**
 * تصمیم‌گیری حالت اتصال: کدام پروفایل Xray اجرا شود و Psiphon چطور بالا بیاید.
 *
 * ترتیب زنجیره (PROXYCHAIN): عضو اول خروجی نهایی است و عضو آخر اولین هاپ از سمت گوشی.
 * بنابراین برای «V2ray از طریق Psiphon» ترتیب = [پروفایل V2ray، هاپ Psiphon] است.
 * پروفایل‌های داخلی (هاپ و زنجیره) در لیست سرورها نمایش داده نمی‌شوند.
 */
object ConnectionModeManager {

    /** نام ثابت پروفایل زنجیرهٔ مدیریت‌شدهٔ UC MAX */
    const val CHAIN_REMARKS = "UC MAX chain"

    /** شناسهٔ ثابت زنجیرهٔ داخلی؛ در لیست سرورها دیده نمی‌شود */
    const val CHAIN_GUID = "uc-max-chain"

    class Plan(
        /** شناسهٔ پروفایلی که Xray با آن اجرا می‌شود */
        val guid: String,
        /** آیا Psiphon باید بالا بیاید؟ */
        val usePsiphon: Boolean,
        /** اگر true باشد، Psiphon بعد از Xray و با upstream از SOCKS محلی Xray بالا می‌آید */
        val psiphonViaXray: Boolean,
    )

    fun currentMode(): ConnectionMode =
        ConnectionMode.fromInt(
            MmkvManager.decodeSettingsString(AppConfig.PREF_CONNECTION_MODE, "0")?.toIntOrNull() ?: 0
        )

    fun setMode(mode: ConnectionMode) {
        MmkvManager.encodeSettings(AppConfig.PREF_CONNECTION_MODE, mode.value.toString())
    }

    /** نقشهٔ اجرا بر اساس حالت فعلی و پروفایل V2ray انتخاب‌شده */
    fun plan(selectedGuid: String): Plan = when (currentMode()) {
        ConnectionMode.V2RAY_ONLY -> Plan(selectedGuid, usePsiphon = false, psiphonViaXray = false)
        ConnectionMode.PSIPHON_ONLY -> Plan(
            PsiphonCoreManager.createHopProfile(), usePsiphon = true, psiphonViaXray = false
        )
        ConnectionMode.PSIPHON_VIA_V2RAY -> Plan(selectedGuid, usePsiphon = true, psiphonViaXray = true)
        ConnectionMode.V2RAY_VIA_PSIPHON -> Plan(
            ensureChain(selectedGuid, PsiphonCoreManager.createHopProfile()),
            usePsiphon = true,
            psiphonViaXray = false,
        )
    }

    /** ساخت یا به‌روزرسانی پروفایل زنجیرهٔ داخلی: خروجی = V2ray، ورودی = Psiphon */
    private fun ensureChain(v2rayGuid: String, hopGuid: String): String {
        val members = "$v2rayGuid,$hopGuid"
        // Remove visible chain copies created by previous versions.
        MmkvManager.decodeAllServerList().forEach { guid ->
            val isOldChain = MmkvManager.decodeServerConfig(guid)?.let {
                it.configType == EConfigType.PROXYCHAIN && it.remarks == CHAIN_REMARKS
            } == true
            if (guid != CHAIN_GUID && isOldChain) {
                MmkvManager.removeServer(guid)
            }
        }
        val profile = MmkvManager.decodeServerConfig(CHAIN_GUID)
            ?: ProfileItem.create(EConfigType.PROXYCHAIN).apply { remarks = CHAIN_REMARKS }
        profile.proxyChainProfiles = members
        return MmkvManager.encodeInternalServerConfig(CHAIN_GUID, profile)
    }
}
