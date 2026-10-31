package com.v2ray.ang.enums

/** حالت‌های اتصال: موتور Xray (V2ray) و هستهٔ Psiphon به‌صورت تکی یا زنجیره‌ای */
enum class ConnectionMode(val value: Int) {
    V2RAY_ONLY(0),
    PSIPHON_ONLY(1),
    PSIPHON_VIA_V2RAY(2),
    V2RAY_VIA_PSIPHON(3);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: V2RAY_ONLY
    }
}
