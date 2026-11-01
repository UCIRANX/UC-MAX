package com.psiphon3.psiphonlibrary;

import android.content.Context;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

/**
 * نمایش همان لیست انتخاب کشور Psiphon از جای دیگر برنامه.
 * کشور انتخاب‌شده در همان کلید تنظیمات Psiphon ذخیره می‌شود.
 */
public final class EgressRegionPicker {

    private EgressRegionPicker() {
    }

    public static void show(Context context, Runnable onChanged) {
        RegionListPreference picker = new RegionListPreference(context);
        picker.setOnRegionSelectedListener(regionCode -> {
            if (!regionCode.equals(EgressRegionStore.getSelectedRegion(context))) {
                new AppPreferences(context).put(
                        context.getString(R.string.egressRegionPreference), regionCode);
                onChanged.run();
            }
        });
        picker.onClick();
    }
}
