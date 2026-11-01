package com.v2ray.ang.core

import com.v2ray.ang.dto.entities.ProfileItem

/**
 * پروفایل‌های داخلی UC MAX که فقط در حافظه ساخته می‌شوند و در لیست سرورها نمایش داده نمی‌شوند.
 * MmkvManager.decodeServerConfig برای این شناسه‌ها این پروفایل‌ها را برمی‌گرداند.
 */
object VirtualProfiles {
    const val PSIPHON_HOP_GUID = "uc-max-virtual-psiphon-hop"
    const val MANAGED_CHAIN_GUID = "uc-max-virtual-managed-chain"

    fun resolve(guid: String): ProfileItem? = when (guid) {
        PSIPHON_HOP_GUID -> PsiphonCoreManager.buildHopProfile()
        MANAGED_CHAIN_GUID -> ConnectionModeManager.buildManagedChain()
        else -> null
    }
}
