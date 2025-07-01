package com.ota;

import androidx.annotation.NonNull;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.turbomodule.core.CallInvokerHolderImpl;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import java.io.File;
import android.content.Context;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

@ReactModule(name = OTAModule.NAME)
public class OTAModule extends ReactContextBaseJavaModule {
    public static final String NAME = "OTA";
    private static final String TAG = "OTAModule";
    private static String updateCheckUrl = null;
    private static String apiKey = null;

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build();

    private static ReactApplicationContext staticContext;

    public OTAModule(ReactApplicationContext reactContext) {
        super(reactContext);
        staticContext = reactContext;
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void getAppVersion(Promise promise) {
        try {
            PackageManager packageManager = getReactApplicationContext().getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(
                getReactApplicationContext().getPackageName(), 
                0
            );
            String version = packageInfo.versionName;
            Log.d(TAG, "App version: " + version);
            promise.resolve(version);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting app version", e);
            promise.reject("ERROR", "Failed to get app version", e);
        }
    }

    @ReactMethod
    public void initiateUpdate(Promise promise) {
        Log.d(TAG, "Initiating update");
        WritableMap result = Arguments.createMap();
        result.putBoolean("success", true);
        promise.resolve(result);
    }

    @ReactMethod
    public void setConfig(String updateCheckUrl, String apiKey) {
        Log.d(TAG, "Setting OTA config - updateCheckUrl: " + updateCheckUrl + ", apiKey: " + apiKey);
        OTAModule.updateCheckUrl = updateCheckUrl;
        OTAModule.apiKey = apiKey;
    }

    @ReactMethod
    public void checkForUpdate(Promise promise) {
        Log.d(TAG, "Checking for update");

        if (updateCheckUrl == null) {
            Log.e(TAG, "OTA configuration not set. Please call setConfig first.");
            promise.reject("CONFIG_ERROR", "OTA configuration not set. Please call setConfig first.");
            return;
        }

        // Get current bundle name using the same logic as OTABundleManager
        String currentFileName = getCurrentBundleName();

        Log.d(TAG, "Current bundle name: " + currentFileName);

        try {
            // Create URL with query parameters
            HttpUrl.Builder urlBuilder = HttpUrl.parse(updateCheckUrl).newBuilder();
            urlBuilder.addQueryParameter("filename", currentFileName);
            String requestUrl = urlBuilder.build().toString();

            Request request = new Request.Builder()
                .url(requestUrl)
                .get()
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to check for update", e);
                    promise.reject("API_ERROR", "Failed to check for update", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "API returned non-200 status code: " + response.code());
                            promise.reject("API_ERROR", 
                                "API returned status code " + response.code());
                            return;
                        }

                        if (responseBody == null) {
                            promise.reject("API_ERROR", "Empty response from server");
                            return;
                        }

                        String responseData = responseBody.string();
                        JSONObject jsonResponse = new JSONObject(responseData);
                        Log.d(TAG, "JSON response: " + jsonResponse.toString());

                        boolean updateAvailable = jsonResponse.optBoolean("updateAvailable", false);

                        if (updateAvailable) {
                            Log.d(TAG, "Update available");
                            String signedUrl = jsonResponse.getString("signedUrl");
                            String fileName = jsonResponse.getString("filename");

                            if (fileName == null || signedUrl == null) {
                                Log.e(TAG, "File name or signed URL is null");
                                promise.reject("INVALID_RESPONSE", "File name or signed URL is null");
                                return;
                            }

                            if (!signedUrl.contains(fileName)) {
                                Log.e(TAG, "Signed URL does not contain file name");
                                promise.reject("INVALID_SIGNED_URL", 
                                    "Signed URL does not contain file name");
                                return;
                            }

                            File downloadDirectory = FolderUtils.getDownloadDirectory(
                                getReactApplicationContext()
                            );
                            File fileUrl = new File(downloadDirectory, fileName + ".zip");

                            downloadPackage(signedUrl, fileUrl.getAbsolutePath(), promise);
                        } else {
                            Log.d(TAG, "No update available");
                            WritableMap result = Arguments.createMap();
                            result.putBoolean("updateAvailable", false);
                            promise.resolve(result);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing response", e);
                        promise.reject("PARSE_ERROR", "Failed to parse server response", e);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error creating request", e);
            promise.reject("REQUEST_ERROR", "Failed to create request", e);
        }
    }

    private void downloadPackage(String updatePackageUrl, String destinationPath, 
                               final Promise promise) {
        Log.d(TAG, "Downloading package from " + updatePackageUrl + " to " + destinationPath);

        // Get app version first to handle potential exception
        String appVersion;
        try {
            appVersion = getAppVersion();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app version", e);
            promise.reject("VERSION_ERROR", "Failed to get app version", e);
            return;
        }

        // Ensure download directory exists
        File destinationFile = new File(destinationPath);
        File downloadDir = destinationFile.getParentFile();
        if (!downloadDir.exists()) {
            if (!downloadDir.mkdirs()) {
                Log.e(TAG, "Failed to create download directory: " + downloadDir.getAbsolutePath());
                promise.reject("DIRECTORY_ERROR", "Failed to create download directory");
                return;
            }
            Log.d(TAG, "Created download directory: " + downloadDir.getAbsolutePath());
        }

        OTADownloadHandler downloadHandler = new OTADownloadHandler(
            destinationFile,
            progress -> Log.d(TAG, "Download progress: " + progress + "%"),
            isZip -> {
                Log.d(TAG, "Download completed, isZip: " + isZip);

                // Check if the file exists before unzipping
                if (!destinationFile.exists()) {
                    Log.e(TAG, "Downloaded file does not exist: " + destinationFile.getAbsolutePath());
                    promise.reject("FILE_ERROR", "Downloaded file does not exist");
                    return;
                }

                String fileName = destinationFile.getName().substring(0, destinationFile.getName().lastIndexOf('.'));

                // Ensure version directory exists
                File versionDir = FolderUtils.getVersionDirectory(getReactApplicationContext(), appVersion);
                if (versionDir == null) {
                    Log.e(TAG, "Failed to create version directory");
                    promise.reject("DIRECTORY_ERROR", "Failed to create version directory");
                    return;
                }

                File destinationFolder = new File(versionDir, fileName);

                unzipFile(destinationPath, destinationFolder.getAbsolutePath(), error -> {
                    if (error != null) {
                        Log.e(TAG, "Unzip failed", error);
                        promise.reject("UNZIP_ERROR", "Failed to unzip update", error);
                    } else {
                        Log.d(TAG, "Unzip completed successfully to: " + destinationFolder.getAbsolutePath());
                        
                        // List contents of the unzipped directory for debugging
                        File[] unzippedContents = destinationFolder.listFiles();
                        if (unzippedContents != null) {
                            Log.d(TAG, "Unzipped contents (" + unzippedContents.length + " items):");
                            for (File file : unzippedContents) {
                                Log.d(TAG, "  - " + file.getName() + (file.isDirectory() ? " (dir)" : " (file)"));
                            }
                        } else {
                            Log.w(TAG, "Unzipped directory is empty or not readable");
                        }
                        
                        if (destinationFile.delete()) {
                            Log.d(TAG, "Cleaned up zip file");
                        }
                        WritableMap result = Arguments.createMap();
                        result.putBoolean("success", true);
                        promise.resolve(result);
                    }
                });
            },
            error -> {
                Log.e(TAG, "Download failed", error);
                promise.reject("DOWNLOAD_ERROR", "Failed to download update", error);
            }
        );

        downloadHandler.download(updatePackageUrl);
    }

    private void unzipFile(String zipPath, String destinationPath, UnzipCallback callback) {
        Log.d(TAG, "Starting unzip process from " + zipPath + " to " + destinationPath);
        
        // Run unzip in background thread
        new Thread(() -> {
            try {
                // Create destination directory if it doesn't exist
                File destDir = new File(destinationPath);
                if (!destDir.exists() && !destDir.mkdirs()) {
                    throw new IOException("Failed to create destination directory");
                }

                // Setup ZIP input stream
                try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipPath))) {
                    ZipEntry entry = zipIn.getNextEntry();
                    
                    // Iterate through all entries
                    while (entry != null) {
                        String filePath = destinationPath + File.separator + entry.getName();
                        if (!entry.isDirectory()) {
                            // Create parent directories if they don't exist
                            new File(filePath).getParentFile().mkdirs();
                            
                            // Extract file
                            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
                                byte[] buffer = new byte[4096];
                                int read;
                                while ((read = zipIn.read(buffer)) != -1) {
                                    bos.write(buffer, 0, read);
                                }
                            }
                        } else {
                            // Create directory if it doesn't exist
                            File dir = new File(filePath);
                            if (!dir.exists() && !dir.mkdirs()) {
                                throw new IOException("Failed to create directory: " + filePath);
                            }
                        }
                        zipIn.closeEntry();
                        entry = zipIn.getNextEntry();
                    }
                }

                Log.d(TAG, "Unzip completed successfully");
                
                // Use ReactContext instead of Activity to run on main thread
                getReactApplicationContext().runOnUiQueueThread(() -> {
                    callback.onComplete(null);
                });
            } catch (IOException e) {
                Log.e(TAG, "Unzip failed", e);
                
                // Use ReactContext instead of Activity to run on main thread
                getReactApplicationContext().runOnUiQueueThread(() -> {
                    callback.onComplete(e);
                });
            }
        }).start();
    }

    interface UnzipCallback {
        void onComplete(Exception error);
    }

    @ReactMethod
    public void restartApp() {
        Log.d(TAG, "Restarting app");
        ReactContext context = getReactApplicationContext();
        context.runOnUiQueueThread(() -> {
            // Get current activity safely
            android.app.Activity activity = context.getCurrentActivity();
            if (activity != null) {
                activity.recreate();
            } else {
                Log.w(TAG, "Could not restart app - current activity is null");
            }
        });
    }

    @ReactMethod
    public String getBundleUrl() {
        Log.d(TAG, "Getting bundle URL");
        
        String appVersion;
        try {
            appVersion = getAppVersion();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app version", e);
            return getDefaultBundleUrl();
        }

        Context context = getReactApplicationContext();
        File otaDirectory = FolderUtils.getOtaDirectory(context);
        File versionDirectory = new File(otaDirectory, appVersion);

        Log.d(TAG, "Ota directory: " + otaDirectory.getAbsolutePath());
        Log.d(TAG, "Version directory: " + versionDirectory.getAbsolutePath());
        Log.d(TAG, "Version directory exists: " + versionDirectory.exists());

        if (!versionDirectory.exists()) {
            Log.d(TAG, "Version directory does not exist, using default bundle");
            return getDefaultBundleUrl();
        }

        // Get all subdirectories in the version directory
        File[] contents = versionDirectory.listFiles();
        Log.d(TAG, "Version directory listFiles() returned: " + (contents != null ? contents.length + " items" : "null"));
        
        if (contents == null || contents.length == 0) {
            Log.d(TAG, "Version directory is empty or null, using default bundle");
            return getDefaultBundleUrl();
        }

        Log.d(TAG, "Found " + contents.length + " items in version directory:");
        for (File item : contents) {
            Log.d(TAG, "  - " + item.getName() + (item.isDirectory() ? " (dir)" : " (file)"));
        }

        // Find the most recent bundle based on timestamp in folder name
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
            Log.d(TAG, "Looking for bundle file at: " + bundleFile.getAbsolutePath());
            Log.d(TAG, "Bundle file exists: " + bundleFile.exists());
            
            // List contents of the activeBundle directory for debugging
            File[] bundleContents = activeBundle.listFiles();
            if (bundleContents != null) {
                Log.d(TAG, "Contents of " + activeBundle.getName() + ":");
                for (File file : bundleContents) {
                    Log.d(TAG, "  - " + file.getName() + (file.isDirectory() ? " (dir)" : " (file)"));
                }
            } else {
                Log.d(TAG, "activeBundle directory is empty or not readable");
            }
            
            if (bundleFile.exists()) {
                String bundleUrl = bundleFile.toURI().toString();
                Log.d(TAG, "Using bundle at: " + bundleUrl);
                return bundleUrl;
            }
        }

        Log.d(TAG, "No valid bundle found, using default bundle");
        return getDefaultBundleUrl();
    }

    private String getDefaultBundleUrl() {
        // Return the path to the default bundle in the assets folder
        return "assets://index.android.bundle";
    }

    private String getAppVersion() throws PackageManager.NameNotFoundException {
        PackageManager packageManager = getReactApplicationContext().getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(
            getReactApplicationContext().getPackageName(), 
            0
        );
        return packageInfo.versionName;
    }

    public static String getJSBundleFile(Context context) {
        return OTABundleManager.getBundleFile(context);
    }

    @ReactMethod
    public void cleanupStorage(Promise promise) {
        Log.d(TAG, "Cleaning up storage");
        
        try {
            String currentVersion = getAppVersion();
            Context context = getReactApplicationContext();
            File otaDirectory = FolderUtils.getOtaDirectory(context);
            
            if (otaDirectory == null) {
                Log.e(TAG, "Failed to get OTA directory");
                promise.reject("DIRECTORY_ERROR", "Failed to get OTA directory");
                return;
            }

            int deletedCount = OTABundleManager.cleanupOutdatedVersions(otaDirectory, currentVersion);
            
            WritableMap result = Arguments.createMap();
            result.putInt("deletedCount", deletedCount);
            result.putBoolean("success", true);
            
            Log.d(TAG, "Storage cleanup completed. Deleted " + deletedCount + " directories");
            promise.resolve(result);
            
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app version", e);
            promise.reject("VERSION_ERROR", "Failed to get app version", e);
        } catch (Exception e) {
            Log.e(TAG, "Error during storage cleanup", e);
            promise.reject("CLEANUP_ERROR", "Failed to cleanup storage", e);
        }
    }

    private String getCurrentBundleName() {
        try {
            String appVersion = getAppVersion();
            String packageName = getReactApplicationContext().getPackageName();
            
            Context context = getReactApplicationContext();
            File otaDirectory = FolderUtils.getOtaDirectory(context);
            File versionDirectory = new File(otaDirectory, appVersion);

            Log.d(TAG, "Checking for current bundle in: " + versionDirectory.getAbsolutePath());

            if (!versionDirectory.exists()) {
                Log.d(TAG, "Version directory does not exist, using initial filename");
                return String.format("android_%s_%s_0", packageName, appVersion);
            }

            File[] contents = versionDirectory.listFiles();
            if (contents == null || contents.length == 0) {
                Log.d(TAG, "Version directory is empty, using initial filename");
                return String.format("android_%s_%s_0", packageName, appVersion);
            }

            // Find the most recent bundle using the same logic as OTABundleManager
            long mostRecentTimestamp = 0;
            String mostRecentBundleName = null;

            for (File item : contents) {
                if (item.isDirectory()) {
                    String bundleName = item.getName();
                    String[] parts = bundleName.split("_");
                    if (parts.length > 0) {
                        String hexTimestamp = parts[parts.length - 1];
                        try {
                            long timestamp = Long.parseLong(hexTimestamp, 16);
                            if (timestamp > mostRecentTimestamp) {
                                mostRecentTimestamp = timestamp;
                                mostRecentBundleName = bundleName;
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Failed to parse timestamp from folder name: " + hexTimestamp, e);
                        }
                    }
                }
            }

            if (mostRecentBundleName != null) {
                Log.d(TAG, "Found most recent bundle: " + mostRecentBundleName);
                return mostRecentBundleName;
            }

            Log.d(TAG, "No valid bundle found, using initial filename");
            return String.format("android_%s_%s_0", packageName, appVersion);
            
        } catch (Exception e) {
            Log.e(TAG, "Error determining current bundle name", e);
            try {
                String appVersion = getAppVersion();
                String packageName = getReactApplicationContext().getPackageName();
                return String.format("android_%s_%s_0", packageName, appVersion);
            } catch (Exception e2) {
                Log.e(TAG, "Failed to construct fallback filename", e2);
                return "android_unknown_unknown_0";
            }
        }
    }
}