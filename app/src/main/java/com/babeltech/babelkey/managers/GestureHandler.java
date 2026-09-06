package com.babeltech.babelkey.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * GestureHandler — owns all touch-event processing on KeyboardView:
 * trackpad, backspace gesture, spacebar cursor swipe, probabilistic swipe typing.
 */
@SuppressWarnings("deprecation")
public class GestureHandler {
    private static final int   RESAMPLE_COUNT    = 40;
    private static final float ANCHOR_RADIUS     = 120f;
    private static final float SIGMA             = 80f;
    private static final float ALPHA             = 2.5f;
    private static final int   MAX_CANDIDATES    = 5;
    private static final int   SWIPE_KEY_THR     = 30;
    private static final int   SWIPE_THR         = 20;
    private static final int   TRACKPAD_SENS     = 15;
    private static final long  LONG_PRESS_MS     = 400;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final ServiceCallback cb;
    private final Map<String, List<String>> dict;
    private final Map<String, List<String>> teluguDict;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private android.inputmethodservice.KeyboardView kv;
    private View trackpadOverlay;
    private PopupWindow previewPopup;
    private TextView previewText;
    private com.babeltech.babelkey.SymbolPopupWindow symbolPopup;

    // Trackpad
    private boolean inTrackpad = false;
    private float   trackpadX = 0, trackpadY = 0;
    private Runnable trackpadActivator;

    // Spacebar cursor glide
    private float   spaceStartX = 0;
    private boolean isSpaceSwiping = false;
    /** Accumulator: how many cursor-step units have been moved since last haptic tick. */
    private float   spaceAccum = 0;

    // Double-tap space → period
    /** Timestamp of the most recent space commit (ms). -1 if no space has been tapped yet. */
    private long    lastSpaceTapMs = -1;
    private static final long DOUBLE_TAP_WINDOW_MS = 300;

    // Backspace gesture (swipe left to delete word/sentence)
    private boolean bsDown = false, bsGestureUsed = false;
    private float   bsStartX = 0, bsStartY = 0;
    /** Words deleted so far during an ongoing backspace swipe — used to track live deletion. */
    private int     bsWordsDeleted = 0;

    // Swipe typing
    private boolean        isSwiping = false;
    private List<Character> swipePath  = new ArrayList<>();
    private List<float[]>   swipePts   = new ArrayList<>();
    private float swipeLastX = 0, swipeLastY = 0;

    // State refs
    private int shiftState = 0;
    private int currentMode = 0;

    // Preview handler
    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private Runnable previewHide;

    public GestureHandler(Context ctx, SharedPreferences prefs, ServiceCallback cb,
                          Map<String, List<String>> dict, Map<String, List<String>> teluguDict) {
        this.ctx=ctx; this.prefs=prefs; this.cb=cb; this.dict=dict; this.teluguDict=teluguDict;
    }

    public void attachKeyboardView(android.inputmethodservice.KeyboardView kv, View overlay) {
        this.kv=kv; this.trackpadOverlay=overlay;
        setupTouch();
    }
    public void attachPreview(PopupWindow popup, TextView text) { previewPopup=popup; previewText=text; }
    public void attachSymbolPopup(com.babeltech.babelkey.SymbolPopupWindow popup) { symbolPopup = popup; }
    public void setShiftState(int s) { shiftState=s; }
    public void setCurrentMode(int m) { currentMode=m; }
    public boolean isInTrackpad()    { return inTrackpad; }
    public boolean isBsGestureUsed() { return bsGestureUsed; }
    public boolean isSpaceSwiping()  { return isSpaceSwiping; }
    public boolean isSwipeTyping()   { return isSwiping; }

    /**
     * Returns true if the current space tap qualifies as a double-tap (within
     * {@link #DOUBLE_TAP_WINDOW_MS} of the previous space tap). Call this from
     * {@code handleSpace()} in MyKeyboardService before recording the new tap.
     */
    public boolean isDoubleTapSpace() {
        if (lastSpaceTapMs < 0) return false;
        return (System.currentTimeMillis() - lastSpaceTapMs) <= DOUBLE_TAP_WINDOW_MS;
    }

    /**
     * Records the timestamp of a confirmed space commit so the next tap can be
     * compared against it for double-tap detection. Call after committing " ".
     */
    public void recordSpaceTap() { lastSpaceTapMs = System.currentTimeMillis(); }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void setupTouch() {
        kv.setOnTouchListener((v, ev) -> {
            if (currentMode == 3) return false; // EMOJI mode — pass through
            float x = ev.getX(), y = ev.getY();
            if (inTrackpad) { handleTrackpad(ev); return true; }
            android.inputmethodservice.Keyboard.Key pk = getKeyAt(x, y);
            int code = (pk != null && pk.codes != null && pk.codes.length > 0) ? pk.codes[0] : 0;

            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (code == 32) {
                        // ── Spacebar down ────────────────────────────────────
                        spaceStartX = x;
                        spaceAccum  = 0;
                        isSpaceSwiping = false;
                        scheduleTrackpad(x, y);
                    }
                    if (code == -5) {
                        // ── Backspace down ───────────────────────────────────
                        bsDown = true; bsStartX = x; bsStartY = y;
                        bsGestureUsed = false; bsWordsDeleted = 0;
                    }
                    if (prefs.getBoolean("swipeTypingEnabled", false)
                            && pk != null && code > 0 && Character.isLetter((char) code)) {
                        isSwiping = false; swipePath.clear(); swipePts.clear();
                        swipePath.add((char) code); swipePts.add(new float[]{x, y});
                        swipeLastX = x; swipeLastY = y;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    // ── Forward to Symbol Popup if showing ──────────────────
                    if (symbolPopup != null && symbolPopup.isShowing()) {
                        int[] loc = new int[2];
                        v.getLocationInWindow(loc);
                        symbolPopup.onTouchMove(ev.getRawX(), loc[0]);
                        return false;
                    }

                    // ── Spacebar cursor glide ────────────────────────────────
                    if (isSpaceSwiping || bsDown) {
                        float md = Math.max(Math.abs(x - spaceStartX), Math.abs(x - bsStartX));
                        if (md > 12) hidePreview();
                    }
                    if (code == 32 || isSpaceSwiping) {
                        float rawDx = x - spaceStartX;
                        if (Math.abs(rawDx) > SWIPE_THR && !inTrackpad) {
                            cancelTrackpad();
                            isSpaceSwiping = true;
                            // Accumulate movement and fire one cursor step per SWIPE_THR pixels
                            spaceAccum += rawDx;
                            int steps = (int) (spaceAccum / SWIPE_THR);
                            if (steps != 0) {
                                moveCursorBounded(steps);
                                // Haptic tick on each character step
                                if (kv != null) kv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                spaceAccum -= steps * SWIPE_THR;
                                spaceStartX += steps * SWIPE_THR;
                            }
                            return true;
                        }
                    }
                    // ── Backspace swipe-to-delete words ──────────────────────
                    if (bsDown && !bsGestureUsed) {
                        float bx = bsStartX - x; // positive = swipe left
                        if (bx > 200) {
                            // Long swipe — delete entire sentence fragment
                            bsGestureUsed = true; deleteSentence(); return true;
                        } else if (bx > 80) {
                            // Medium swipe — delete words progressively
                            // Each extra 80px deletes one more word
                            int targetWords = 1 + (int) ((bx - 80) / 80);
                            while (bsWordsDeleted < targetWords) {
                                deleteWord();
                                bsWordsDeleted++;
                                if (kv != null) kv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            }
                            bsGestureUsed = true; return true;
                        }
                    }
                    // ── Swipe typing path collection ─────────────────────────
                    if (prefs.getBoolean("swipeTypingEnabled", false)) {
                        if (isSwiping || !swipePts.isEmpty()) swipePts.add(new float[]{x, y});
                        float dist = (float) Math.hypot(x - swipeLastX, y - swipeLastY);
                        if (dist > SWIPE_KEY_THR) {
                            android.inputmethodservice.Keyboard.Key k = getKeyAt(x, y);
                            if (k != null && k.codes != null && k.codes.length > 0
                                    && k.codes[0] != 32 && Character.isLetter((char) k.codes[0])) {
                                char c = (char) k.codes[0];
                                if (swipePath.isEmpty() || swipePath.get(swipePath.size() - 1) != c)
                                    swipePath.add(c);
                                isSwiping = true; swipeLastX = x; swipeLastY = y;
                            }
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    cancelTrackpad(); hidePreview();
                    // Resolve swipe-typed word
                    if (isSwiping && swipePath.size() > 3) {
                        swipePts.add(new float[]{x, y});
                        List<String> cands = swipeToWords();
                        if (!cands.isEmpty()) {
                            cb.onSwipeSuggestionsReady(cands);
                            isSwiping = false; swipePath.clear(); swipePts.clear();
                            return true;
                        }
                    }
                    isSwiping = false; swipePath.clear(); swipePts.clear();
                    if (isSpaceSwiping) { isSpaceSwiping = false; spaceAccum = 0; return true; }
                    isSpaceSwiping = false; spaceAccum = 0;
                    if (bsDown) { bsDown = false; if (bsGestureUsed) return true; }
                    break;
            }
            return false;
        });
    }

    // Trackpad
    private void scheduleTrackpad(float x,float y) { cancelTrackpad(); trackpadActivator=()->{ if(!isSpaceSwiping) enterTrackpad(x,y); }; handler.postDelayed(trackpadActivator,LONG_PRESS_MS); }
    private void cancelTrackpad() { if(trackpadActivator!=null){handler.removeCallbacks(trackpadActivator);trackpadActivator=null;} }
    private void enterTrackpad(float x,float y) { inTrackpad=true;trackpadX=x;trackpadY=y; if(trackpadOverlay!=null){trackpadOverlay.setVisibility(View.VISIBLE);trackpadOverlay.setAlpha(0f);trackpadOverlay.animate().alpha(1f).setDuration(150).start();} if(kv!=null)kv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); }
    public void exitTrackpad() { inTrackpad=false; if(trackpadOverlay!=null) trackpadOverlay.setVisibility(View.GONE); }
    private void handleTrackpad(MotionEvent ev) {
        float x=ev.getX(),y=ev.getY();
        if (ev.getAction()==MotionEvent.ACTION_MOVE) {
            float dx=x-trackpadX,dy=y-trackpadY;
            if(Math.abs(dx)>TRACKPAD_SENS){moveCursor((int)(dx/TRACKPAD_SENS));trackpadX=x;}
            if(Math.abs(dy)>TRACKPAD_SENS){moveCursorV((int)(dy/TRACKPAD_SENS));trackpadY=y;}
        } else if(ev.getAction()==MotionEvent.ACTION_UP) exitTrackpad();
    }

    // ── Cursor movement ───────────────────────────────────────────────────────

    /**
     * Moves the cursor {@code steps} positions left (negative) or right (positive)
     * using DPAD key events — compatible with all editors including WebViews.
     */
    private void moveCursor(int steps) {
        InputConnection ic = cb.getInputConnection(); if (ic == null) return;
        int kc = steps > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        for (int i = 0; i < Math.abs(steps); i++) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, kc));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   kc));
        }
    }

    /**
     * Bounds-checked cursor move for spacebar glide — reads current selection
     * position and clamps the step so the cursor cannot go before 0 or past
     * the length of the surrounding text.
     */
    private void moveCursorBounded(int steps) {
        InputConnection ic = cb.getInputConnection(); if (ic == null) return;
        if (steps == 0) return;
        if (steps > 0) {
            // Moving right — check text after cursor
            CharSequence after = ic.getTextAfterCursor(steps, 0);
            int actualSteps = (after != null) ? Math.min(steps, after.length()) : 0;
            for (int i = 0; i < actualSteps; i++) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_DPAD_RIGHT));
            }
        } else {
            // Moving left — check text before cursor
            int magnitude = Math.abs(steps);
            CharSequence before = ic.getTextBeforeCursor(magnitude, 0);
            int actualSteps = (before != null) ? Math.min(magnitude, before.length()) : 0;
            for (int i = 0; i < actualSteps; i++) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_DPAD_LEFT));
            }
        }
    }

    private void moveCursorV(int steps) {
        InputConnection ic = cb.getInputConnection(); if (ic == null) return;
        int kc = steps > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP;
        for (int i = 0; i < Math.abs(steps); i++) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, kc));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,   kc));
        }
    }
    private void deleteWord() { InputConnection ic=cb.getInputConnection();if(ic==null)return;CharSequence b=ic.getTextBeforeCursor(50,0);if(b==null)return;int len=0,i=b.length()-1;while(i>=0&&Character.isWhitespace(b.charAt(i))){len++;i--;}while(i>=0&&!Character.isWhitespace(b.charAt(i))){len++;i--;}if(len>0)ic.deleteSurroundingText(len,0); }
    private void deleteSentence() { InputConnection ic=cb.getInputConnection();if(ic==null)return;CharSequence b=ic.getTextBeforeCursor(200,0);if(b==null)return;int len=0;for(int i=b.length()-1;i>=0;i--){char c=b.charAt(i);if(c=='.'||c=='!'||c=='?'||c=='\n')break;len++;}if(len>0)ic.deleteSurroundingText(len,0); }

    // Gboard preview
    public void showPreview(int code) {
        if(previewPopup==null||kv==null||currentMode==3) return;
        if(code<0||code==32||code==10||code==127) return;
        android.inputmethodservice.Keyboard kb=kv.getKeyboard();if(kb==null)return;
        android.inputmethodservice.Keyboard.Key mk=null;
        for(android.inputmethodservice.Keyboard.Key k:kb.getKeys()){if(k.codes!=null&&k.codes.length>0&&k.codes[0]==code){mk=k;break;}}
        if(mk==null) return;
        String label=String.valueOf((char)code); if(shiftState!=0&&Character.isLetter(label.charAt(0))) label=label.toUpperCase();
        previewText.setText(label);
        previewText.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int pw=previewText.getMeasuredWidth(),ph=previewText.getMeasuredHeight();
        int[]loc=new int[2];kv.getLocationInWindow(loc);
        int px=loc[0]+mk.x+(mk.width/2)-(pw/2), py=loc[1]+mk.y-ph-dpToPx(4);
        int sw=ctx.getResources().getDisplayMetrics().widthPixels; px=Math.max(0,Math.min(px,sw-pw));
        if(kv.getWindowToken()==null) return;
        if(previewPopup.isShowing()) previewPopup.update(px,py,pw,ph); else previewPopup.showAtLocation(kv,android.view.Gravity.NO_GRAVITY,px,py);
        if(previewHide!=null) previewHandler.removeCallbacks(previewHide);
    }
    public void hidePreview() {
        if(previewHide==null) previewHide=()->{ if(previewPopup!=null&&previewPopup.isShowing()) previewPopup.dismiss(); };
        previewHandler.removeCallbacks(previewHide); previewHandler.postDelayed(previewHide,60);
    }

    // Key lookup
    public android.inputmethodservice.Keyboard.Key getKeyAt(float x,float y) {
        if(kv==null) return null; android.inputmethodservice.Keyboard kb=kv.getKeyboard();if(kb==null)return null;
        for(android.inputmethodservice.Keyboard.Key k:kb.getKeys()) if(x>=k.x&&x<=k.x+k.width&&y>=k.y&&y<=k.y+k.height) return k;
        return null;
    }
    private float[] centerOf(char c) {
        if(kv==null) return null; android.inputmethodservice.Keyboard kb=kv.getKeyboard();if(kb==null)return null;
        char lc=Character.toLowerCase(c);
        for(android.inputmethodservice.Keyboard.Key k:kb.getKeys()) if(k.codes!=null&&k.codes.length>0&&Character.toLowerCase((char)k.codes[0])==lc) return new float[]{k.x+k.width/2f,k.y+k.height/2f};
        return null;
    }

    // Swipe engine
    private List<float[]> resample(List<float[]> pts, int n) {
        if(pts.size()<2) return new ArrayList<>(pts);
        double total=0; for(int i=1;i<pts.size();i++){double dx=pts.get(i)[0]-pts.get(i-1)[0],dy=pts.get(i)[1]-pts.get(i-1)[1];total+=Math.sqrt(dx*dx+dy*dy);}
        double interval=total/(n-1); List<float[]> r=new ArrayList<>(); r.add(pts.get(0).clone());
        double acc=0; int idx=1;
        while(r.size()<n&&idx<pts.size()){
            float[]p=pts.get(idx-1),c=pts.get(idx); double dx=c[0]-p[0],dy=c[1]-p[1],seg=Math.sqrt(dx*dx+dy*dy);
            if(acc+seg>=interval){double t=(interval-acc)/seg;float nx=(float)(p[0]+t*dx),ny=(float)(p[1]+t*dy);r.add(new float[]{nx,ny});pts.set(idx-1,new float[]{nx,ny});acc=0;}else{acc+=seg;idx++;}
        }
        while(r.size()<n) r.add(pts.get(pts.size()-1).clone()); return r;
    }
    private double spatialCost(List<float[]> res, List<float[]> kcs) {
        if(kcs.isEmpty()) return Double.MAX_VALUE; double s2=SIGMA*SIGMA,tot=0;
        for(float[]pt:res){double min=Double.MAX_VALUE;for(float[]kc:kcs){double dx=pt[0]-kc[0],dy=pt[1]-kc[1],d2=dx*dx+dy*dy;if(d2<min)min=d2;}tot+=min/s2;}
        return tot/res.size();
    }
    private List<float[]> keyCenters(String word) {
        List<float[]> cs=new ArrayList<>(); char last=0;
        for(char c:word.toCharArray()){if(c==last)continue;float[]ct=centerOf(c);if(ct!=null){cs.add(ct);last=c;}}
        return cs;
    }
    private List<String> swipeToWords() {
        if(swipePts.size()<4||swipePath.isEmpty()) return Collections.emptyList();
        List<float[]> copy=new ArrayList<>(swipePts);
        List<float[]> res=resample(copy,RESAMPLE_COUNT); if(res.isEmpty()) return Collections.emptyList();
        float[]sp=res.get(0),ep=res.get(res.size()-1);
        java.util.Map<String,Integer> cands=new java.util.LinkedHashMap<>();
        for(java.util.Map.Entry<String,List<String>>e:dict.entrySet()){List<String>ws=e.getValue();for(int r=0;r<ws.size();r++){String w=ws.get(r).trim().toLowerCase();if(w.length()>=2&&!cands.containsKey(w))cands.put(w,ws.size()-r);}}
        for(java.util.Map.Entry<String,List<String>>e:teluguDict.entrySet()){List<String>ws=e.getValue();for(int r=0;r<ws.size();r++){String w=ws.get(r).trim().toLowerCase();if(w.length()>=2&&!cands.containsKey(w))cands.put(w,ws.size()-r);}}
        char fc=swipePath.get(0),lc=swipePath.get(swipePath.size()-1);
        float[]fkc=centerOf(fc),lkc=centerOf(lc);
        List<String> anchored=new ArrayList<>();
        for(String w:cands.keySet()){if(w.isEmpty())continue;if(fkc!=null){float[]wfc=centerOf(w.charAt(0));if(wfc!=null&&Math.hypot(wfc[0]-sp[0],wfc[1]-sp[1])>ANCHOR_RADIUS)continue;}if(lkc!=null){float[]wlc=centerOf(w.charAt(w.length()-1));if(wlc!=null&&Math.hypot(wlc[0]-ep[0],wlc[1]-ep[1])>ANCHOR_RADIUS)continue;}anchored.add(w);}
        if(anchored.isEmpty()) anchored=new ArrayList<>(cands.keySet());
        List<float[]> scored=new ArrayList<>();
        for(int i=0;i<anchored.size();i++){String w=anchored.get(i);List<float[]>kc=keyCenters(w);if(kc.isEmpty())continue;double cost=spatialCost(res,kc);double score=-cost+ALPHA*Math.log(Math.max(cands.getOrDefault(w,1),1));scored.add(new float[]{(float)score,i});}
        scored.sort((a,b)->Double.compare(b[0],a[0]));
        List<String> out=new ArrayList<>(); for(int i=0;i<Math.min(MAX_CANDIDATES,scored.size());i++) out.add(anchored.get((int)scored.get(i)[1]));
        if(out.isEmpty()){StringBuilder sb=new StringBuilder();char last=0;for(char c:swipePath)if(c!=last){sb.append(c);last=c;}String fb=sb.toString().toLowerCase();if(fb.length()>=2)out.add(fb);}
        return out;
    }

    private int dpToPx(int dp) { return (int)(dp*ctx.getResources().getDisplayMetrics().density); }
    public void detach() { kv=null;trackpadOverlay=null;previewPopup=null;previewText=null; }
    public void destroy() { cancelTrackpad();handler.removeCallbacksAndMessages(null); }
}
