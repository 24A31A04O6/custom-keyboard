package com.babeltech.babelkey.settings;

import com.babeltech.babelkey.R;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * SettingsCorrectionsActivity — "Corrections and suggestions" sub-screen.
 *
 * Mirrors the Gboard screen shown in the user's reference image (picture 2):
 *   Automatic corrections  → Auto-correction, Auto-capitalisation
 *   Spelling and grammar   → Spell check, Grammar check
 *   Suggestions             Smart Compose, AI Replies, No offensive words
 *
 * All prefs read/written to SharedPreferences "MyBoardPrefs".
 */
public class SettingsCorrectionsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_corrections);

        prefs = getSharedPreferences("MyBoardPrefs", MODE_PRIVATE);

        // ── ActionBar ────────────────────────────────────────────────────────
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Corrections and suggestions");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // ── Wire switches ────────────────────────────────────────────────────
        wireSwitch(R.id.switch_autocorrect,  "autoCorrectEnabled",    true);
        wireSwitch(R.id.switch_autocaps,     "autoCapsEnabled",       true);
        wireSwitch(R.id.switch_spellcheck,   "spellCheckEnabled",     true);
        wireSwitch(R.id.switch_grammar,      "grammarCheckEnabled",   true);
        wireSwitch(R.id.switch_smart_compose,"smartComposeEnabled",   true);
        wireSwitch(R.id.switch_ai_replies,   "aiRepliesEnabled",      true);
        wireSwitch(R.id.switch_offensive,    "blockOffensiveEnabled", true);
    }

    /**
     * Looks up the Switch by ID, sets its initial state from prefs,
     * then saves changes back to prefs whenever it's toggled.
     */
    private void wireSwitch(int switchId, String prefKey, boolean defaultValue) {
        Switch sw = findViewById(switchId);
        if (sw == null) return;
        sw.setChecked(prefs.getBoolean(prefKey, defaultValue));
        sw.setOnCheckedChangeListener((CompoundButton btn, boolean checked) ->
                prefs.edit().putBoolean(prefKey, checked).apply());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
