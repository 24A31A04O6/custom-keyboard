package com.babeltech.babelkey;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * SettingsThemeActivity — Theme + Sound sub-screen.
 *
 * Six colour-preview cards for themes (Light, Dark, Black, AMOLED, Blue, Green),
 * a custom photo option, and the sound pack + volume controls.
 * All saved to SharedPreferences "MyBoardPrefs" (key "selectedTheme"  0-6).
 */
public class SettingsThemeActivity extends AppCompatActivity {

    // Theme constant indices (match MyKeyboardService.THEME_*)
    private static final int THEME_LIGHT  = 0;
    private static final int THEME_DARK   = 1;
    private static final int THEME_BLACK  = 2;
    private static final int THEME_AMOLED = 3;
    private static final int THEME_BLUE   = 4;
    private static final int THEME_GREEN  = 5;
    private static final int THEME_CUSTOM = 6;

    private SharedPreferences prefs;

    // Check-mark TextViews for each theme card
    private final int[] checkIds = {
        R.id.check_light, R.id.check_dark, R.id.check_black,
        R.id.check_amoled, R.id.check_blue, R.id.check_green, R.id.check_custom
    };
    // Card layout IDs in same order
    private final int[] cardIds = {
        R.id.theme_light, R.id.theme_dark, R.id.theme_black,
        R.id.theme_amoled, R.id.theme_blue, R.id.theme_green, R.id.theme_custom
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_theme);

        prefs = getSharedPreferences("MyBoardPrefs", MODE_PRIVATE);

        // ── ActionBar ────────────────────────────────────────────────────────
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Theme");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // ── Theme cards ──────────────────────────────────────────────────────
        int currentTheme = prefs.getInt("selectedTheme", THEME_DARK);
        selectThemeCard(currentTheme); // show checkmark on current

        // Wire card click listeners
        int[] themes = {THEME_LIGHT, THEME_DARK, THEME_BLACK,
                        THEME_AMOLED, THEME_BLUE, THEME_GREEN};
        for (int i = 0; i < themes.length; i++) {
            final int themeId = themes[i];
            View card = findViewById(cardIds[i]);
            if (card != null) {
                card.setOnClickListener(v -> applyTheme(themeId));
            }
        }

        // Custom photo card  launches PickImageActivity
        View customCard = findViewById(R.id.theme_custom);
        if (customCard != null) {
            customCard.setOnClickListener(v -> {
                prefs.edit()
                     .putInt("selectedTheme", THEME_CUSTOM)
                     .putBoolean("customBgEnabled", true)
                     .apply();
                selectThemeCard(THEME_CUSTOM);
                startActivity(new Intent(this, PickImageActivity.class));
            });
        }

        // Update custom photo label
        TextView tvCustomLabel = findViewById(R.id.tv_custom_photo_label);
        if (tvCustomLabel != null) {
            boolean hasPhoto = prefs.contains("customBackgroundUri");
            tvCustomLabel.setText(hasPhoto ? "Photo selected  tap to change" : "Tap to pick from gallery");
        }

        // ── Sound pack ───────────────────────────────────────────────────────
        RadioGroup rgSounds = findViewById(R.id.rg_sounds);
        int[] soundRadioIds = {
            R.id.rb_sound0, R.id.rb_sound1, R.id.rb_sound2, R.id.rb_sound3,
            R.id.rb_sound4, R.id.rb_sound5, R.id.rb_sound6
        };
        int currentSound = prefs.getInt("soundPack", 0);
        if (currentSound >= 0 && currentSound < soundRadioIds.length) {
            RadioButton rb = findViewById(soundRadioIds[currentSound]);
            if (rb != null) rb.setChecked(true);
        }
        if (rgSounds != null) {
            rgSounds.setOnCheckedChangeListener((group, checkedId) -> {
                for (int i = 0; i < soundRadioIds.length; i++) {
                    if (checkedId == soundRadioIds[i]) {
                        prefs.edit().putInt("soundPack", i).apply();
                        break;
                    }
                }
            });
        }

        // ── Volume seekbar ───────────────────────────────────────────────────
        SeekBar seekVolume = findViewById(R.id.seek_volume);
        if (seekVolume != null) {
            seekVolume.setProgress(prefs.getInt("soundVolume", 70));
            seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser) prefs.edit().putInt("soundVolume", progress).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }
        
        // ── AI Auto-Replies ──────────────────────────────────────────────────
        android.widget.Switch switchAi = findViewById(R.id.switch_ai_replies);
        if (switchAi != null) {
            switchAi.setChecked(prefs.getBoolean("aiRepliesEnabled", false));
            switchAi.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("aiRepliesEnabled", isChecked).apply();
            });
        }
    }

    private void applyTheme(int themeId) {
        prefs.edit().putInt("selectedTheme", themeId).apply();
        selectThemeCard(themeId);
        Toast.makeText(this, "Theme saved  takes effect next time you open the keyboard",
                Toast.LENGTH_SHORT).show();
    }

    /** Shows checkmark on the selected card, hides all others. */
    private void selectThemeCard(int themeId) {
        for (int i = 0; i < checkIds.length; i++) {
            View check = findViewById(checkIds[i]);
            if (check != null)
                check.setVisibility(i == themeId ? View.VISIBLE : View.GONE);
        }
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
