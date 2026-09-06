package com.babeltech.babelkey.managers;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

/**
 * VoiceTypingManager
 * Owns SpeechRecognizer lifecycle, permission checks, mic state machine,
 * and voice UI wiring. Communicates results back via ServiceCallback.
 */
public class VoiceTypingManager {

    private final Context ctx;
    private final SharedPreferences prefs;
    private final ServiceCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private Runnable restartRunnable;

    // Attached views
    private LinearLayout voiceTypingPanel;
    private TextView     voiceStatusText;
    private LinearLayout toolbarRow;

    public VoiceTypingManager(Context ctx, SharedPreferences prefs, ServiceCallback callback) {
        this.ctx      = ctx;
        this.prefs    = prefs;
        this.callback = callback;
    }

    public void attachViews(LinearLayout voicePanel, TextView statusText, LinearLayout toolbarRow) {
        this.voiceTypingPanel = voicePanel;
        this.voiceStatusText  = statusText;
        this.toolbarRow       = toolbarRow;
    }

    /** Entry point from toolbar mic button — toggles voice mode. */
    public void handleVoiceTyping() {
        if (voiceTypingPanel != null && voiceTypingPanel.getVisibility() == View.VISIBLE) {
            exitVoiceTypingMode();
        } else {
            enterVoiceTypingMode();
        }
    }

    private void enterVoiceTypingMode() {
        if (!hasRecordAudioPermission()) {
            Toast.makeText(ctx,
                "Microphone permission needed. Open the BabelKey app once and allow Microphone access.",
                Toast.LENGTH_LONG).show();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Toast.makeText(ctx, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }
        if (toolbarRow != null) toolbarRow.setVisibility(View.GONE);
        if (voiceTypingPanel != null) voiceTypingPanel.setVisibility(View.VISIBLE);
        startVoiceTyping();
    }

    public void exitVoiceTypingMode() {
        if (restartRunnable != null) handler.removeCallbacks(restartRunnable);
        stopVoiceTyping();
        if (voiceTypingPanel != null) voiceTypingPanel.setVisibility(View.GONE);
        if (toolbarRow != null) toolbarRow.setVisibility(View.VISIBLE);
    }

    private void restartListening() {
        if (voiceTypingPanel != null && voiceTypingPanel.getVisibility() == View.VISIBLE && !isListening) {
            startVoiceTyping();
        }
    }

    private void startVoiceTyping() {
        try {
            if (speechRecognizer != null) speechRecognizer.destroy();
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {
                    isListening = true; updateMicButton();
                    if (voiceStatusText != null) voiceStatusText.setText("Speak now");
                }
                @Override public void onResults(Bundle results) {
                    java.util.ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) insertVoiceResult(matches.get(0));
                    isListening = false; updateMicButton();
                    if (voiceTypingPanel != null && voiceTypingPanel.getVisibility() == View.VISIBLE) {
                        if (voiceStatusText != null) voiceStatusText.setText("Listening...");
                        restartRunnable = VoiceTypingManager.this::restartListening;
                        handler.postDelayed(restartRunnable, 300);
                    } else {
                        if (voiceStatusText != null) voiceStatusText.setText("Tap mic to speak again");
                    }
                }
                @Override public void onPartialResults(Bundle partial) {
                    java.util.ArrayList<String> p = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (p != null && !p.isEmpty() && voiceStatusText != null) voiceStatusText.setText(p.get(0));
                }
                @Override public void onError(int error) {
                    isListening = false; updateMicButton();
                    String message;
                    switch (error) {
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: message = "Microphone permission denied"; break;
                        case SpeechRecognizer.ERROR_AUDIO: message = "Microphone error"; break;
                        default: message = "Tap mic to try again";
                    }
                    CrashLogger.logError("VoiceTypingManager", "onError", "Speech recognition error: " + error, null);
                    if (voiceStatusText != null) voiceStatusText.setText(message);
                    if (voiceTypingPanel != null && voiceTypingPanel.getVisibility() == View.VISIBLE
                            && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                            && error != SpeechRecognizer.ERROR_AUDIO) {
                        restartRunnable = VoiceTypingManager.this::restartListening;
                        handler.postDelayed(restartRunnable, 800);
                    }
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float r) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {
                    isListening = false; updateMicButton();
                    if (voiceStatusText != null) voiceStatusText.setText("Processing...");
                }
                @Override public void onEvent(int t, Bundle p) {}
            });
            speechRecognizer.startListening(intent);
            isListening = true; updateMicButton();
        } catch (Exception e) {
            Toast.makeText(ctx, "Couldn't start voice typing", Toast.LENGTH_SHORT).show();
            isListening = false; updateMicButton();
        }
    }

    private void stopVoiceTyping() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isListening = false; updateMicButton();
    }

    private void insertVoiceResult(String text) {
        callback.commitText(text);
        callback.onVoiceResultInserted();
    }

    private void updateMicButton() {
        android.widget.ImageView panelMic = voiceTypingPanel != null
                ? voiceTypingPanel.findViewById(com.babeltech.babelkey.R.id.btn_voice_mic) : null;
        if (panelMic != null) {
            panelMic.setAlpha(isListening ? 1.0f : 0.5f);
        }
    }

    public boolean isListening() { return isListening; }

    public boolean hasRecordAudioPermission() {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void detach() {
        voiceTypingPanel = null; voiceStatusText = null; toolbarRow = null;
    }

    public void destroy() {
        if (restartRunnable != null) handler.removeCallbacks(restartRunnable);
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
    }
}
