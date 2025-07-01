package com.ota;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

public class OTADownloadHandler {
    private static final String TAG = "OTADownloadHandler";
    private static String BASE_URL = null;

    private final File destinationFile;
    private final ProgressCallback progressCallback;
    private final CompletionCallback completionCallback;
    private final ErrorCallback errorCallback;
    private final OkHttpClient client;

    public interface ProgressCallback {
        void onProgress(float progress);
    }

    public interface CompletionCallback {
        void onComplete(boolean isZip);
    }

    public interface ErrorCallback {
        void onError(Exception error);
    }

    public OTADownloadHandler(File destinationFile, 
                            ProgressCallback progressCallback,
                            CompletionCallback completionCallback,
                            ErrorCallback errorCallback) {
        this.destinationFile = destinationFile;
        this.progressCallback = progressCallback;
        this.completionCallback = completionCallback;
        this.errorCallback = errorCallback;

        this.client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    public void download(String url) {
        Request request = new Request.Builder()
            .url(url)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Download failed", e);
                errorCallback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    errorCallback.onError(new IOException("Unexpected response " + response));
                    return;
                }

                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        errorCallback.onError(new IOException("Empty response body"));
                        return;
                    }

                    long contentLength = responseBody.contentLength();
                    InputStream inputStream = responseBody.byteStream();
                    FileOutputStream outputStream = new FileOutputStream(destinationFile);

                    byte[] buffer = new byte[4096];
                    long totalBytesRead = 0;
                    int bytesRead;
                    boolean isZip = false;
                    boolean headerChecked = false;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (!headerChecked && totalBytesRead == 0) {
                            isZip = buffer[0] == 0x50 && buffer[1] == 0x4B && 
                                   buffer[2] == 0x03 && buffer[3] == 0x04;
                            headerChecked = true;
                        }

                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        if (contentLength > 0) {
                            float progress = (totalBytesRead * 100f) / contentLength;
                            progressCallback.onProgress(progress);
                        }
                    }

                    outputStream.close();
                    completionCallback.onComplete(isZip);
                }
            }
        });
    }

    public static void setBaseUrl(String baseUrl) {
        Log.d(TAG, "Setting base URL to: " + baseUrl);
        BASE_URL = baseUrl;
    }

    public static String getBaseUrl() {
        if (BASE_URL == null) {
            Log.e(TAG, "Base URL not set. Please call setConfig first.");
            throw new IllegalStateException("Base URL not set. Please call setConfig first.");
        }
        return BASE_URL;
    }
} 