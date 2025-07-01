package com.ota;

import android.content.Context;
import java.io.File;
import android.util.Log;

public class FolderUtils {
    private static final String TAG = "FolderUtils";
    private static final String OTA_DIR = "ota";
    private static final String DOWNLOAD_DIR = "download";

    public static File getOtaDirectory(Context context) {
        File otaDir = new File(context.getFilesDir(), OTA_DIR);
        if (!otaDir.exists()) {
            if (!otaDir.mkdirs()) {
                Log.e(TAG, "Failed to create OTA directory");
                return null;
            }
        }
        return otaDir;
    }

    public static File getDownloadDirectory(Context context) {
        File downloadDir = new File(getOtaDirectory(context), DOWNLOAD_DIR);
        if (!downloadDir.exists()) {
            if (!downloadDir.mkdirs()) {
                Log.e(TAG, "Failed to create download directory");
                return null;
            }
        }
        return downloadDir;
    }

    public static File getVersionDirectory(Context context, String version) {
        File versionDir = new File(getOtaDirectory(context), version);
        if (!versionDir.exists()) {
            if (!versionDir.mkdirs()) {
                Log.e(TAG, "Failed to create version directory");
                return null;
            }
        }
        return versionDir;
    }

    public static File[] getAllFilesInDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return new File[0];
        }
        File[] files = directory.listFiles();
        return files != null ? files : new File[0];
    }
} 