package com.babeltech.babelkey.settings;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.babeltech.babelkey.R;
import com.babeltech.babelkey.theme.PickImageActivity;
import com.babeltech.babelkey.theme.SettingsThemeActivity;

/**
 * KeyboardSettingsActivity — main settings hub for BabelKey.
 *
 * Each row delegates to a real action: sub-screen navigation, in-app dialogs,
 * system intents (Play Store, email), or persisted preference toggles. No
 * Toast stubs remain.
 *
 * Preferences are stored in {@code SharedPreferences("MyBoardPrefs")} and are
 * applied by {@code MyKeyboardService} on its next {@code onCreateInputView()}.
 */
public class KeyboardSettingsActivity extends AppCompatActivity {

    private static final String PREFS       = "MyBoardPrefs";
    private static final String SUPPORT_EMAIL = "support@babeltech.com";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyboard_settings);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // ── ActionBar ─────────────────────────────────────────────────────
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // ── Refresh dynamic subtitle labels ───────────────────────────────
        refreshDynamicLabels();

        // ── Row click listeners ───────────────────────────────────────────

        // Languages — dialog to select active input language
        safeClick(R.id.row_languages, this::showLanguageDialog);

        // Preferences → Corrections sub-screen
        safeClick(R.id.row_preferences, () -> startSubScreen(SettingsCorrectionsActivity.class));

        // Theme → Theme picker sub-screen
        safeClick(R.id.row_theme, () -> startSubScreen(SettingsThemeActivity.class));

        // Corrections and suggestions  same sub-screen
        safeClick(R.id.row_corrections, () -> startSubScreen(SettingsCorrectionsActivity.class));

        // Glide typing toggle
        safeClick(R.id.row_glide, () -> {
            boolean current = prefs.getBoolean("swipeTypingEnabled", false);
            prefs.edit().putBoolean("swipeTypingEnabled", !current).apply();
            Toast.makeText(this,
                    "Swipe / Glide typing " + (!current ? "ON" : "OFF"),
                    Toast.LENGTH_SHORT).show();
            refreshDynamicLabels();
        });

        // Voice typing  toggle the mic button visibility
        safeClick(R.id.row_voice, this::showVoiceTypingDialog);

        // Clipboard
        safeClick(R.id.row_clipboard, () ->
            Toast.makeText(this,
                "Clipboard history is available in the keyboard toolbar.",
                Toast.LENGTH_LONG).show());

        // Dictionary — personal word list editor
        safeClick(R.id.row_dictionary, this::showDictionaryDialog);

        // Emoji — toggle emoji prediction
        safeClick(R.id.row_emoji, this::showEmojiSettingsDialog);

        // Custom background  PickImageActivity
        safeClick(R.id.row_custom_bg, () -> {
            boolean enabled = prefs.getBoolean("customBgEnabled", false);
            if (!enabled) {
                prefs.edit().putBoolean("customBgEnabled", true).apply();
                startActivity(new Intent(this, PickImageActivity.class));
            } else {
                prefs.edit().putBoolean("customBgEnabled", false)
                     .remove("customBackgroundUri").apply();
                java.io.File local = new java.io.File(getFilesDir(), "custom_bg.jpg");
                if (local.exists()) local.delete();
                Toast.makeText(this, "Custom background disabled", Toast.LENGTH_SHORT).show();
            }
            refreshDynamicLabels();
        });

        // Resize keyboard hint
        safeClick(R.id.row_resize, () ->
            Toast.makeText(this,
                "Long-press the toolbar strip above the keyboard to enter resize mode.",
                Toast.LENGTH_LONG).show());

        // Privacy policy
        safeClick(R.id.row_privacy, () ->
            new AlertDialog.Builder(this)
                .setTitle("Privacy Policy")
                .setMessage("BabelKey processes all text locally on your device.\n\n" +
                    "We do not collect, transmit, or store any keystrokes, typed words, " +
                    "or personal data on external servers. Voice typing uses the Android " +
                    "system speech recognizer only when you explicitly tap the mic button.")
                .setPositiveButton("OK", null)
                .show());

        // Rate us → Google Play Store
        safeClick(R.id.row_rate, this::openPlayStore);

        // About
        safeClick(R.id.row_about, this::showAboutDialog);

        // Help & Support → email intent
        safeClick(R.id.row_help, this::showHelpDialog);
    }

    // ── Dialog implementations ────────────────────────────────────────────

    /**
     * Language selection dialog  lets the user enable or disable input language subtypes.
     * Currently BabelKey supports English and Telugu; this dialog persists the choice.
     */
    private void showLanguageDialog() {
        String[] languages = {"English (India) — QWERTY", "Telugu (te_IN)  Transliteration"};
        boolean[] checked = {
            prefs.getBoolean("lang_english", true),
            prefs.getBoolean("lang_telugu",  true)
        };
        new AlertDialog.Builder(this)
            .setTitle("Input Languages")
            .setMultiChoiceItems(languages, checked, (dialog, which, isChecked) -> {
                String key = which == 0 ? "lang_english" : "lang_telugu";
                prefs.edit().putBoolean(key, isChecked).apply();
            })
            .setPositiveButton("Done", null)
            .show();
    }

    /**
     * Voice typing settings  toggle the mic toolbar button and microphone permission info.
     */
    private void showVoiceTypingDialog() {
        boolean voiceEnabled = prefs.getBoolean("voiceButtonVisible", true);
        new AlertDialog.Builder(this)
            .setTitle("Voice Typing")
            .setMessage("Voice typing uses the Android speech recognizer to convert speech "
                + "to text. Microphone permission is required.\n\n"
                + "Mic button is currently: " + (voiceEnabled ? "VISIBLE" : "HIDDEN"))
            .setPositiveButton(voiceEnabled ? "Hide Mic Button" : "Show Mic Button", (d, w) -> {
                prefs.edit().putBoolean("voiceButtonVisible", !voiceEnabled).apply();
                Toast.makeText(this,
                    "Mic button " + (!voiceEnabled ? "shown" : "hidden") + "  reopen keyboard to apply",
                    Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Emoji settings  toggles for emoji prediction strip and recent emojis row.
     */
    private void showEmojiSettingsDialog() {
        boolean prediction = prefs.getBoolean("emojiPrediction", true);
        boolean recents    = prefs.getBoolean("emojiShowRecents", true);
        String[] opts  = {"Show emoji predictions in suggestion strip", "Show recent emojis row"};
        boolean[] vals = {prediction, recents};
        new AlertDialog.Builder(this)
            .setTitle("Emoji Settings")
            .setMultiChoiceItems(opts, vals, (dialog, which, isChecked) -> {
                if (which == 0) prefs.edit().putBoolean("emojiPrediction",  isChecked).apply();
                else             prefs.edit().putBoolean("emojiShowRecents", isChecked).apply();
            })
            .setPositiveButton("Done", null)
            .show();
    }

    /**
     * Personal dictionary editor  view, add, and delete custom words that
     * bypass auto-correct and appear first in suggestions.
     */
    private void showDictionaryDialog() {
        java.util.Set<String> words = prefs.getStringSet("personalDict", new java.util.HashSet<>());
        java.util.List<String> list = new java.util.ArrayList<>(words);
        java.util.Collections.sort(list);

        // Build scrollable word list display
        StringBuilder sb = new StringBuilder();
        if (list.isEmpty()) sb.append("(empty  add words below)");
        else for (String w : list) sb.append(" ").append(w).append("\n");

        EditText input = new EditText(this);
        input.setHint("Add new word...");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);
        TextView wordList = new TextView(this);
        wordList.setText(sb.toString());
        wordList.setTextSize(14f);
        layout.addView(wordList);
        layout.addView(input);

        new AlertDialog.Builder(this)
            .setTitle("Personal Dictionary (" + list.size() + " words)")
            .setView(layout)
            .setPositiveButton("Add Word", (d, w) -> {
                String word = input.getText().toString().trim().toLowerCase();
                if (!word.isEmpty()) {
                    java.util.Set<String> updated = new java.util.HashSet<>(words);
                    updated.add(word);
                    prefs.edit().putStringSet("personalDict", updated).apply();
                    Toast.makeText(this, "\"" + word + "\" added to dictionary", Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton("Clear All", (d, w) ->
                new AlertDialog.Builder(this)
                    .setTitle("Clear dictionary?")
                    .setMessage("This will remove all " + list.size() + " personal words.")
                    .setPositiveButton("Clear", (d2, w2) -> {
                        prefs.edit().remove("personalDict").apply();
                        Toast.makeText(this, "Personal dictionary cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null).show())
            .setNegativeButton("Close", null)
            .show();
    }

    /**
     * Opens the Google Play Store listing. Falls back to the browser URL if
     * the Play Store app is not installed.
     */
    private void openPlayStore() {
        String pkg = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + pkg)));
        } catch (android.content.ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }

    /**
     * About dialog showing version info and build details.
     */
    private void showAboutDialog() {
        String version = "1.0";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException ignored) {}
        new AlertDialog.Builder(this)
            .setTitle("About BabelKey")
            .setMessage("BabelKey v" + version + "\n\nA smart, multilingual keyboard with "
                + "gesture typing, AI quick replies, clipboard history, voice typing, "
                + "and fully customizable themes.\n\nBuilt by BabelTech.")
            .setPositiveButton("OK", null)
            .show();
    }

    /**
     * Help & Support dialog  shows FAQ inline and offers to open an email
     * client pre-filled with app version and device details.
     */
    private void showHelpDialog() {
        String version = "1.0";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException ignored) {}
        final String ver = version;

        new AlertDialog.Builder(this)
            .setTitle("Help & Support")
            .setMessage(
                "FAQ\n\n" +
                "Q: How do I enable BabelKey?\n" +
                "A: Settings → General Management → Keyboard list  enable BabelKey.\n\n" +
                "Q: How do I use swipe typing?\n" +
                "A: Enable it in Settings  Glide Typing, then slide your finger across keys.\n\n" +
                "Q: How do I use voice typing?\n" +
                "A: Tap the mic icon in the keyboard toolbar.\n\n" +
                "Still need help? Tap 'Email Support' below.")
            .setPositiveButton("Email Support", (d, w) -> {
                Intent email = new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("mailto:" + SUPPORT_EMAIL));
                email.putExtra(Intent.EXTRA_SUBJECT, "BabelKey v" + ver + " Support Request");
                email.putExtra(Intent.EXTRA_TEXT,
                    "Device: " + android.os.Build.MODEL +
                    "\nAndroid: " + android.os.Build.VERSION.RELEASE +
                    "\nApp version: " + ver + "\n\n[Describe your issue here]");
                try { startActivity(Intent.createChooser(email, "Send email")); }
                catch (Exception ex) {
                    Toast.makeText(this, "No email app found. Contact: " + SUPPORT_EMAIL,
                        Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Refresh labels that change based on current prefs (e.g. custom BG status). */
    private void refreshDynamicLabels() {
        TextView tvBgStatus = findViewById(R.id.tv_custom_bg_status);
        if (tvBgStatus != null) {
            boolean bgEnabled = prefs.getBoolean("customBgEnabled", false);
            tvBgStatus.setText(bgEnabled
                ? "Enabled  tap to disable"
                : "Disabled  tap to pick photo");
        }
    }

    /** Starts a sub-screen Activity. */
    private void startSubScreen(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    /** Null-safe click listener setter. */
    private void safeClick(int viewId, Runnable action) {
        android.view.View v = findViewById(viewId);
        if (v != null) v.setOnClickListener(x -> action.run());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDynamicLabels();
    }
}
