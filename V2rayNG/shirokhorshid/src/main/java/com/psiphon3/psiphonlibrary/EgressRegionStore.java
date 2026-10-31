package com.psiphon3.psiphonlibrary;

import android.content.Context;
import android.text.TextUtils;

import com.psiphon3.R;

import net.grandcentrix.tray.AppPreferences;

import java.util.List;

/**
 * Egress-region state shared with the app's Psiphon core (PsiphonCoreManager).
 * The region list is produced by the core and must be stored where RegionListPreference reads it,
 * and the selected region must be read back when the core is started.
 */
public final class EgressRegionStore {

    private EgressRegionStore() {
    }

    /** Stores the regions reported by the core in the key RegionListPreference reads from. */
    public static void saveKnownRegions(Context context, List<String> regions) {
        AppPreferences prefs = new AppPreferences(context);
        // Same format TunnelManager used before (comma separated).
        prefs.put(RegionListPreference.KNOWN_REGIONS_PREFERENCE, TextUtils.join(",", regions));
    }

    /** Returns the region the user selected, or REGION_CODE_ANY when none is selected. */
    public static String getSelectedRegion(Context context) {
        AppPreferences prefs = new AppPreferences(context);
        return prefs.getString(context.getString(R.string.egressRegionPreference),
                PsiphonConstants.REGION_CODE_ANY);
    }
}
