package com.babeltech.babelkey.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import com.babeltech.babelkey.R;

/**
 * SoundHapticManager
 * Owns SoundPool audio playback and haptic feedback.
 * Lifecycle: init() on service create, destroy() on service destroy.
 */
public class SoundHapticManager {

    private static final String TAG = "SoundHapticManager";

    public static final int SOUND_OFF        = 0;
    public static final int SOUND_MECHANICAL = 1;
    public static final int SOUND_TYPEWRITER = 2;
    public static final int SOUND_BUBBLE     = 3;
    public static final int SOUND_GAMING     = 4;
    public static final int SOUND_SOFT       = 5;
    public static final int SOUND_CRYSTAL    = 6;

    private static final String PREF_SOUND_PACK   = "soundPack";
    private static final String PREF_SOUND_VOLUME = "soundVolume";

    private final Context ctx;
    private final SharedPreferences prefs;

    private SoundPool soundPool;
    private final java.util.Map<Integer, Integer> soundIds = new java.util.HashMap<>();
    private AudioManager audioManager;

    private int currentSound;
    private int soundVolume;

    public SoundHapticManager(Context ctx, SharedPreferences prefs) {
        this.ctx    = ctx;
        this.prefs  = prefs;
        currentSound = prefs.getInt(PREF_SOUND_PACK, SOUND_OFF);
        soundVolume  = prefs.getInt(PREF_SOUND_VOLUME, 70);
    }

    public void init() {
        audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        soundPool = new SoundPool.Builder().setMaxStreams(3).build();
        try {
            soundIds.put(SOUND_MECHANICAL, soundPool.load(ctx, R.raw.sound_mechanical, 1));
            soundIds.put(SOUND_TYPEWRITER, soundPool.load(ctx, R.raw.sound_typewriter, 1));
            soundIds.put(SOUND_BUBBLE,     soundPool.load(ctx, R.raw.sound_bubble,     1));
            soundIds.put(SOUND_GAMING,     soundPool.load(ctx, R.raw.sound_gaming,     1));
            soundIds.put(SOUND_SOFT,       soundPool.load(ctx, R.raw.sound_soft,       1));
            soundIds.put(SOUND_CRYSTAL,    soundPool.load(ctx, R.raw.sound_crystal,    1));
            Log.i(TAG, "SoundPool loaded 6 packs");
        } catch (Exception e) {
            Log.e(TAG, "SoundPool load error", e);
        }
    }

    public void playKeySound() {
        if (currentSound == SOUND_OFF) return;
        float vol = soundVolume / 100f;
        Integer soundId = soundIds.get(currentSound);
        if (soundId != null && soundId > 0) {
            soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
            return;
        }
        if (audioManager == null) return;
        switch (currentSound) {
            case SOUND_MECHANICAL:
            case SOUND_TYPEWRITER:
                audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, vol); break;
            case SOUND_BUBBLE:
            case SOUND_SOFT:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, vol); break;
            case SOUND_GAMING:
            case SOUND_CRYSTAL:
                audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR, vol); break;
        }
    }

    public void playHaptic(View v, int hapticConstant) {
        if (v != null) v.performHapticFeedback(hapticConstant);
    }

    public void setSoundPack(int pack) {
        currentSound = pack;
        prefs.edit().putInt(PREF_SOUND_PACK, pack).apply();
    }

    public int getCurrentPack() { return currentSound; }

    public void setVolume(int vol) {
        soundVolume = vol;
        prefs.edit().putInt(PREF_SOUND_VOLUME, vol).apply();
    }

    public int getVolume() { return soundVolume; }

    public String getSoundName(int pack) {
        switch (pack) {
            case SOUND_MECHANICAL: return "Mechanical";
            case SOUND_TYPEWRITER: return "Typewriter";
            case SOUND_BUBBLE:     return "Bubble";
            case SOUND_GAMING:     return "Gaming";
            case SOUND_SOFT:       return "Soft";
            case SOUND_CRYSTAL:    return "Crystal";
            default:               return "Off";
        }
    }

    public void destroy() {
        if (soundPool != null) { soundPool.release(); soundPool = null; }
    }
}
