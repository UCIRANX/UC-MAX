package com.psiphon3;

/**
 * Library-owned FileProvider for upgrade APKs.
 * A distinct subclass avoids a manifest clash with the app's androidx.core.content.FileProvider.
 */
public class UpgradeFileProvider extends androidx.core.content.FileProvider {
}
