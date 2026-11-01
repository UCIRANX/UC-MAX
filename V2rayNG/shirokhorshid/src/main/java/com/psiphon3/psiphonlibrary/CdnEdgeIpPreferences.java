package com.psiphon3.psiphonlibrary;

import android.content.Context;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

/**
 * دسترسی به فیلد «CDN edge IPs» تنظیمات Psiphon.
 * مقدار اصلی در tray (AppPreferences) نگه داشته می‌شود که بین پروسهٔ UI و daemon مشترک است
 * و همان چیزی است که TunnelManager هنگام اتصال می‌خواند. فایل موقت تنظیمات هم همگام می‌شود.
 */
public final class CdnEdgeIpPreferences {

    private CdnEdgeIpPreferences() {
    }

    public static String load(Context context) {
        return new AppPreferences(context).getString(key(context), "");
    }

    public static void save(Context context, String value) {
        new AppPreferences(context).put(key(context), value);
        context.getSharedPreferences(context.getString(R.string.moreOptionsPreferencesName), Context.MODE_PRIVATE)
                .edit()
                .putString(key(context), value)
                .apply();
    }

    private static String key(Context context) {
        return context.getString(R.string.cdnFrontingCustomIpListPreference);
    }
}
