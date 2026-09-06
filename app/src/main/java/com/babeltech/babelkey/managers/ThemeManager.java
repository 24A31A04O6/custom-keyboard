package com.babeltech.babelkey.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.babeltech.babelkey.R;

/**
 * ThemeManager — owns all theme loading, persistence, and application to keyboard views.
 */
@SuppressWarnings("deprecation")
public class ThemeManager {
    private static final String TAG = "ThemeManager";
    public static final int THEME_LIGHT = 0, THEME_DARK = 1, THEME_BLACK = 2;
    public static final int THEME_AMOLED = 3, THEME_BLUE = 4, THEME_GREEN = 5, THEME_CUSTOM_IMAGE = 6;
    private static final String PREF_THEME = "selectedTheme", PREF_BG_URI = "customBackgroundUri", PREF_BG_ON = "customBgEnabled";

    private final Context ctx;
    private final SharedPreferences prefs;
    private final ServiceCallback cb;
    private int currentTheme;
    private boolean isDark;
    private String bgUri;

    private LinearLayout root;
    private android.inputmethodservice.KeyboardView kv;
    private TextView themeLabel, keyPreviewText;
    private LinearLayout voicePanel, clipPanel;
    private View phrasesPanel;
    private ScrollView statsPanel, settingsPanel;

    public ThemeManager(Context ctx, SharedPreferences prefs, ServiceCallback cb) {
        this.ctx = ctx; this.prefs = prefs; this.cb = cb;
        currentTheme = prefs.getInt(PREF_THEME, THEME_DARK);
        bgUri = prefs.getString(PREF_BG_URI, null);
        isDark = (currentTheme != THEME_LIGHT);
    }

    public void attachViews(LinearLayout root, android.inputmethodservice.KeyboardView kv,
            TextView themeLabel, LinearLayout voicePanel, LinearLayout clipPanel,
            View phrasesPanel, ScrollView statsPanel, ScrollView settingsPanel, TextView keyPreviewText) {
        this.root = root; this.kv = kv; this.themeLabel = themeLabel;
        this.voicePanel = voicePanel; this.clipPanel = clipPanel;
        this.phrasesPanel = phrasesPanel; this.statsPanel = statsPanel;
        this.settingsPanel = settingsPanel; this.keyPreviewText = keyPreviewText;
    }

    public void applyCurrentTheme() {
        if (root == null || kv == null) return;
        root.setBackground(null);
        switch (currentTheme) {
            case THEME_LIGHT:  flat("#F0F0F0","#F0F0F0","#D8D8D8","#E8E8E8",R.drawable.key_preview_bg,false); break;
            case THEME_DARK:   flat("#23282D","#23282D","#1B2127","#23282D",R.drawable.key_preview_bg,true); break;
            case THEME_BLACK:  flat("#000000","#000000","#0A0A0A","#111111",R.drawable.key_preview_bg_dark,true); break;
            case THEME_AMOLED: flat("#000000","#000000","#000000","#000000",R.drawable.key_preview_bg_dark,true); break;
            case THEME_BLUE:   flat("#0D2A4A","#0D2A4A","#0A1F38","#102E50",R.drawable.key_preview_bg_blue,true); break;
            case THEME_GREEN:  flat("#0D2E10","#0D2E10","#0A2410","#103818",R.drawable.key_preview_bg_green,true); break;
            case THEME_CUSTOM_IMAGE: applyCustomImage(); break;
        }
        if (clipPanel != null) clipPanel.setBackgroundColor(Color.parseColor(isDark ? "#2A2A2A" : "#EFEFEF"));
        if (phrasesPanel != null) phrasesPanel.setBackgroundColor(Color.parseColor(isDark ? "#1E3020" : "#E8F5E9"));
        if (statsPanel != null) statsPanel.setBackgroundColor(Color.parseColor(isDark ? "#2A2A1A" : "#FFF8E1"));
        if (settingsPanel != null) settingsPanel.setBackgroundColor(Color.parseColor(isDark ? "#1E1E1E" : "#F5F5F5"));
        updateLabel();
        cb.onThemeChanged(isDark);
    }

    private void flat(String rc, String kc, String tc, String sc, int previewRes, boolean dark) {
        isDark = dark;
        root.setBackgroundColor(Color.parseColor(rc));
        kv.setBackgroundColor(Color.parseColor(kc));
        applyToolbarColor(tc); applySuggestionBarColor(sc);
        if (voicePanel != null) voicePanel.setBackgroundColor(Color.parseColor(tc));
        if (keyPreviewText != null) keyPreviewText.setBackgroundResource(previewRes);
    }

    private void applyCustomImage() {
        isDark = true;
        java.io.File f = new java.io.File(ctx.getFilesDir(), "custom_bg.jpg");
        if (f.exists()) { try { Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath()); if (b != null) { applyBitmap(b); return; } } catch (Exception ignored) {} }
        if (bgUri == null) { flat("#2B2B2B","#2B2B2B","#1A1A1A","#1E1E1E",R.drawable.key_preview_bg_dark,true); Toast.makeText(ctx,"No custom image selected.",Toast.LENGTH_SHORT).show(); return; }
        try {
            Uri uri = Uri.parse(bgUri);
            Bitmap bm = MediaStore.Images.Media.getBitmap(ctx.getContentResolver(), uri);
            if (bm != null) { try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { bm.compress(Bitmap.CompressFormat.JPEG,90,o); } catch (Exception ignored) {} applyBitmap(bm); }
        } catch (SecurityException e) {
            Log.w(TAG,"BG URI expired"); bgUri = null; prefs.edit().remove(PREF_BG_URI).apply();
            flat("#2B2B2B","#2B2B2B","#1A1A1A","#1E1E1E",R.drawable.key_preview_bg_dark,true);
            Toast.makeText(ctx,"Custom image permission expired. Pick again.",Toast.LENGTH_LONG).show();
        } catch (Exception e) { flat("#2B2B2B","#2B2B2B","#1A1A1A","#1E1E1E",R.drawable.key_preview_bg_dark,true); }
    }

    private void applyBitmap(Bitmap raw) {
        int tw = ctx.getResources().getDisplayMetrics().widthPixels, th = (int)(tw * 0.45f);
        float sw = (float) raw.getWidth() / tw, sh = (float) raw.getHeight() / th;
        int is = Math.max(1, (int) Math.min(sw, sh));

        Bitmap bm;
        if (is > 1) {
            // Downsample: decode at reduced inSampleSize into a new, smaller Bitmap
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = is;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            raw.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] ba = baos.toByteArray();
            bm = BitmapFactory.decodeByteArray(ba, 0, ba.length, opts);

            // Recycle the temporary raw bitmap immediately — it is no longer needed
            // now that bm holds the downsampled copy. Guard against the edge case
            // where decodeByteArray returns the same object as raw.
            if (raw != bm && !raw.isRecycled()) {
                raw.recycle();
                raw = null;
            }
        } else {
            // No downsampling needed — use raw directly (caller may pass its own ref)
            bm = raw;
        }

        BitmapDrawable bd = new BitmapDrawable(ctx.getResources(), bm) {
            @Override public int getIntrinsicWidth()  { return -1; }
            @Override public int getIntrinsicHeight() { return -1; }
        };
        bd.setGravity(Gravity.FILL);
        View sc = root.findViewWithTag("bg_scrim");
        if (sc != null) root.removeView(sc);
        root.setBackground(bd);
        kv.setBackgroundColor(Color.TRANSPARENT);
        try { kv.setKeyboard(kv.getKeyboard()); kv.setAlpha(0.92f); } catch (Exception ignored) {}
        applyToolbarColor("#AA1A1A1A");
        applySuggestionBarColor("#881E1E1E");
        if (keyPreviewText != null) keyPreviewText.setBackgroundResource(R.drawable.key_preview_bg_dark);
        if (voicePanel != null) voicePanel.setBackgroundColor(Color.TRANSPARENT);
    }

    private void updateLabel() {
        if (themeLabel==null) return;
        String[] names = {"Light","Dark","Black","AMOLED Black","Blue","Green","Custom Image"};
        String n = currentTheme>=0&&currentTheme<names.length?names[currentTheme]:"Light";
        themeLabel.setText("Current: "+n);
        themeLabel.setTextColor(isDark?Color.WHITE:Color.parseColor("#555555"));
        themeLabel.setBackgroundColor(Color.parseColor(isDark?"#1E1E1E":"#FFFFFF"));
    }

    public void applyToolbarColor(String hex) {
        if (root==null) return;
        View t=root.findViewById(R.id.toolbar_row); if(t!=null) t.setBackgroundColor(Color.parseColor(hex));
        View s=root.findViewById(R.id.toolbar_strip); if(s!=null) s.setBackgroundColor(Color.parseColor(hex));
    }
    public void applySuggestionBarColor(String hex) { if(root==null) return; View b=root.findViewById(R.id.suggestion_scroll); if(b!=null) b.setBackgroundColor(Color.parseColor(hex)); }

    public void setTheme(int theme) { currentTheme=theme; prefs.edit().putInt(PREF_THEME,theme).apply(); applyCurrentTheme(); }
    public void setCustomBgUri(String uri) { bgUri=uri; prefs.edit().putString(PREF_BG_URI,uri).apply(); if(currentTheme==THEME_CUSTOM_IMAGE) applyCurrentTheme(); }
    public int getCurrentTheme() { return currentTheme; }
    public boolean isDark() { return isDark; }
    public String getBgUri() { return bgUri; }

    public void detach() { root=null;kv=null;themeLabel=null;voicePanel=null;clipPanel=null;phrasesPanel=null;statsPanel=null;settingsPanel=null;keyPreviewText=null; }
}
