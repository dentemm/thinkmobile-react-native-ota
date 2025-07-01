package com.ota;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import java.io.File;

public class OTABundleManager {
    private static final String TAG = "OTABundleManager";
    private static final String DEFAULT_BUNDLE = "assets://index.android.bundle";
    
    public static String getBundleFile(Context context) {
        Log.d(TAG, "Getting JS bundle file path");
        
        try {
            // Get app version using context
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                context.getPackageName(), 
                0
            );
            String appVersion = packageInfo.versionName;

            File otaDirectory = FolderUtils.getOtaDirectory(context);
            
            // Add cleanup of outdated versions
            cleanupOutdatedVersions(otaDirectory, appVersion);

            File versionDirectory = new File(otaDirectory, appVersion);

            Log.d(TAG, "Checking version directory: " + versionDirectory.getAbsolutePath());

            if (!versionDirectory.exists()) {
                Log.d(TAG, "Version directory does not exist, using default bundle");
                return DEFAULT_BUNDLE;
            }

            File[] contents = versionDirectory.listFiles();
            if (contents == null || contents.length == 0) {
                Log.d(TAG, "Version directory is empty, using default bundle");
                return DEFAULT_BUNDLE;
            }

            // Find the most recent bundle
            long mostRecentTimestamp = 0;
            File activeBundle = null;

            for (File item : contents) {
                if (item.isDirectory()) {
                    String[] parts = item.getName().split("_");
                    if (parts.length > 0) {
                        String hexTimestamp = parts[parts.length - 1];
                        try {
                            long timestamp = Long.parseLong(hexTimestamp, 16);
                            if (timestamp > mostRecentTimestamp) {
                                mostRecentTimestamp = timestamp;
                                activeBundle = item;
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Failed to parse timestamp from folder name: " + hexTimestamp, e);
                        }
                    }
                }
            }

            if (activeBundle != null) {
                File bundleFile = new File(activeBundle, "index.android.bundle");
                if (bundleFile.exists()) {
                    String bundlePath = bundleFile.getAbsolutePath();
                    Log.d(TAG, "Using bundle at: " + bundlePath);
                    return bundlePath;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting bundle file", e);
        }

        Log.d(TAG, "Using default bundle");
        return DEFAULT_BUNDLE;
    }

    public static int cleanupOutdatedVersions(File otaDirectory, String currentVersion) {
        File[] subdirectories = otaDirectory.listFiles(File::isDirectory);
        if (subdirectories == null) {
            return 0;
        }

        int deletedCount = 0;
        
        for (File subdir : subdirectories) {
            String version = subdir.getName();
            if (version.equals(currentVersion)) {
                // Clean up old files within current version directory
                deletedCount += cleanupOldBundles(subdir);
            } else if (!version.equals("download")) {
                // Remove entire directory for old versions
                Log.d(TAG, "Removing outdated directory: " + subdir.getAbsolutePath());
                if (deleteRecursive(subdir)) {
                    deletedCount++;
                }
            }
        }
        return deletedCount;
    }

    private static int cleanupOldBundles(File versionDirectory) {
        File[] bundleDirs = versionDirectory.listFiles(File::isDirectory);
        if (bundleDirs == null || bundleDirs.length <= 1) {
            return 0;
        }

        // Find the most recent bundle
        long mostRecentTimestamp = 0;
        File mostRecentBundle = null;

        for (File bundleDir : bundleDirs) {
            String[] parts = bundleDir.getName().split("_");
            if (parts.length > 0) {
                String hexTimestamp = parts[parts.length - 1];
                try {
                    long timestamp = Long.parseLong(hexTimestamp, 16);
                    if (timestamp > mostRecentTimestamp) {
                        mostRecentTimestamp = timestamp;
                        mostRecentBundle = bundleDir;
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse timestamp from folder name: " + hexTimestamp, e);
                }
            }
        }

        // Delete all bundles except the most recent one
        int deletedCount = 0;
        for (File bundleDir : bundleDirs) {
            if (!bundleDir.equals(mostRecentBundle)) {
                Log.d(TAG, "Removing old bundle: " + bundleDir.getAbsolutePath());
                if (deleteRecursive(bundleDir)) {
                    deletedCount++;
                }
            }
        }

        return deletedCount;
    }

    private static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        boolean deleted = fileOrDirectory.delete();
        if (!deleted) {
            Log.e(TAG, "Failed to delete: " + fileOrDirectory.getAbsolutePath());
        }
        return deleted;
    }
} 