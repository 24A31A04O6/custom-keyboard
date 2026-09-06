package com.babeltech.babelkey.managers;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TranslationService {
    private static final String TRANSLATE_API_URL = "https://api.babeltech.com/translate";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface TranslationCallback {
        void onSuccess(String translatedText);
        void onFailure(String error);
    }

    public static void translateTeluguToEnglish(String sourceText, TranslationCallback callback) {
        executor.execute(() -> {
            try {
                // Ensure correct UTF-8 encoding for Telugu Unicode script
                JSONObject payload = new JSONObject();
                payload.put("source_language", "te");
                payload.put("target_language", "en");
                payload.put("text", sourceText);

                byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);

                URL url = new URL(TRANSLATE_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String translated = jsonResponse.optString("translated_text", null);
                    
                    if (translated != null && !translated.trim().isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(translated));
                    } else {
                        throw new Exception("Empty translation result");
                    }
                } else {
                    throw new Exception("HTTP error code: " + responseCode);
                }
                conn.disconnect();
            } catch (Exception e) {
                CrashLogger.logError("TranslationService", "translateTeluguToEnglish", e.getMessage(), e);
                mainHandler.post(() -> callback.onFailure(e.getMessage()));
            }
        });
    }
}
