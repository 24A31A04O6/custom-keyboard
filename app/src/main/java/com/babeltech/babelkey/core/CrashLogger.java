package com.babeltech.babelkey.core;

import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CrashLogger {
    private static final String TAG = "BabelKeyCrashLogger";
    private static final String BUG_REPORT_URL = "https://api.babeltech.com/log_bug";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void logError(String component, String methodName, String errorMessage, Throwable stackTrace) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()).format(new Date());
        
        StringBuilder trace = new StringBuilder();
        if (stackTrace != null) {
            for (StackTraceElement element : stackTrace.getStackTrace()) {
                trace.append(element.toString()).append("\n");
            }
        }

        // 1. Log to Logcat
        Log.e(TAG, String.format("[%s] %s::%s - %s\n%s", timestamp, component, methodName, errorMessage, trace.toString()));

        // 2. Dispatch to endpoint
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("timestamp", timestamp);
                payload.put("component", component);
                payload.put("methodName", methodName);
                payload.put("errorMessage", errorMessage);
                payload.put("stackTrace", trace.toString());

                byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);

                URL url = new URL(BUG_REPORT_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200 && responseCode != 201) {
                    Log.e(TAG, "Failed to send bug report. Response Code: " + responseCode);
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending bug report", e);
            }
        });
    }
}
