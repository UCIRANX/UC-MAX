package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.ConnectionMode
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager

/**
 * تصمیم‌گیری حالت اتصال: کدام پروفایل Xray اجرا شود و Psiphon چطور بالا بیاید.
 *
 * ترتیب زنجیره مطابق UI: عضو اول ورودی (نزدیک گوشی) و عضو آخر خروجی (به سرور نهایی) است.
 * - «V2ray از Psiphon»: [هاپ Psiphon، V2ray]  → گوشی → Psiphon → سرور V2ray
 * - «Psiphon از V2ray»: outbound پیش‌فرض = هاپ Psiphon و upstream آن = V2ray (با exitGuid)
 */
object ConnectionModeManager {

    /** نام ثابت پروفایل زنجیرهٔ مدیریت‌شدهٔ UC MAX */
    const val CHAIN_REMARKS = "UC MAX chain"

    class Plan(
        /** شناسهٔ پروفایلی که Xray با آن اجرا می‌شود */
        val guid: String,
        /** آیا Psiphon باید بالا بیاید؟ */
        val usePsiphon: Boolean,
        /** true یعنی Psiphon بعد از Xray و با upstream از V2ray بالا می‌آید */
        val psiphonViaXray: Boolean,
        /** پروفایل V2ray که باید خروجی مسیر Psiphon باشد (فقط در «Psiphon از V2ray») */
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
    fun plan(selectedGuid: String): Plan = when (currentMode()) {
        ConnectionMode.V2RAY_ONLY -> Plan(selectedGuid, usePsiphon = false, psiphonViaXray = false)
        ConnectionMode.PSIPHON_ONLY -> Plan(
            PsiphonCoreManager.createHopProfile(), usePsiphon = true, psiphonViaXray = false
        )
        ConnectionMode.PSIPHON_VIA_V2RAY -> Plan(
            PsiphonCoreManager.createHopProfile(),
            usePsiphon = true,
            psiphonViaXray = true,
            exitGuid = selectedGuid,
        )
        ConnectionMode.V2RAY_VIA_PSIPHON -> Plan(
            ensureChain(hopGuid = PsiphonCoreManager.createHopProfile(), v2rayGuid = selectedGuid),
            usePsiphon = true,
            psiphonViaXray = false,
        )
    }

    /** ساخت یا به‌روزرسانی پروفایل زنجیرهٔ مدیریت‌شده: ورودی = Psiphon، خروجی = V2ray */
    private fun ensureChain(hopGuid: String, v2rayGuid: String): String {
        val members = listOf(hopGuid, v2rayGuid)
        val existingGuid = MmkvManager.decodeAllServerList().firstOrNull { guid ->
            MmkvManager.decodeServerConfig(guid)?.let {
                it.configType == EConfigType.PROXYCHAIN && it.remarks == CHAIN_REMARKS
            } == true
        }
        val profile = existingGuid?.let { MmkvManager.decodeServerConfig(it) }
            ?: ProfileItem.create(EConfigType.PROXYCHAIN)
        profile.remarks = CHAIN_REMARKS
        profile.proxyChainProfiles = members.joinToString(",")
        profile.description = members.joinToString(" -> ") { id ->
            MmkvManager.decodeServerConfig(id)?.remarks ?: id
        }
        return MmkvManager.encodeServerConfig(existingGuid.orEmpty(), profile)
    }
}
