package com.babeltech.babelkey.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import androidx.core.content.ContextCompat;
import com.babeltech.babelkey.R;
import java.util.List;

/**
 * Custom KeyboardView to support simultaneous multi-touch key tracking,
 * expose the protected onLongPress method, handle responsive sizing,
 * and render custom multiline keys like "12\n34".
 */
@SuppressWarnings("deprecation")
public class BabelKeyboardView extends KeyboardView {

    public interface BabelKeyboardListener {
        /** Return true if the long press was handled. */
        boolean onLongPress(Keyboard.Key key);
    }

    private BabelKeyboardListener babelListener;
    private int requestedKeyboardHeight;
    private int keyTextColor = 0xFFE8EAED;
    private TextPaint multilinePaint;

    // Simultaneous Multi-Touch Key Tracking per Pointer ID
    private final SparseArray<Keyboard.Key> activeKeysByPointer = new SparseArray<>();
    private final SparseArray<Runnable> longPressRunnables = new SparseArray<>();
    private final SparseBooleanArray longPressFired = new SparseBooleanArray<>();
    private final Handler touchHandler = new Handler(Looper.getMainLooper());

    public void setKeyboardHeight(int height) {
        requestedKeyboardHeight = Math.max(0, height);
        fitKeyboard(getWidth());
        requestLayout();
        invalidateAllKeys();
    }

    @Override
    public void setKeyboard(Keyboard keyboard) {
        super.setKeyboard(keyboard);
        fitKeyboard(getWidth());
        requestLayout();
    }

    private void fitKeyboard(int width) {
        Keyboard keyboard = getKeyboard();
        int available = width - getPaddingLeft() - getPaddingRight();
        if (available > 0 && keyboard instanceof com.babeltech.babelkey.layout.ResponsiveKeyboardLayout) {
            ((com.babeltech.babelkey.layout.ResponsiveKeyboardLayout) keyboard)
                    .fitTo(available, requestedKeyboardHeight);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            fitKeyboard(MeasureSpec.getSize(widthMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        fitKeyboard(width);
        invalidateAllKeys();
    }

    public BabelKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint();
    }

    public BabelKeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaint();
    }

    private void initPaint() {
        multilinePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        multilinePaint.setColor(keyTextColor);
        multilinePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        multilinePaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setCustomKeyTextColor(int color) {
        this.keyTextColor = color;
        if (multilinePaint != null) {
            multilinePaint.setColor(color);
        }
        invalidate();
    }

    public void setBabelListener(BabelKeyboardListener listener) {
        this.babelListener = listener;
    }

    @Override
    protected boolean onLongPress(Keyboard.Key popupKey) {
        if (babelListener != null && babelListener.onLongPress(popupKey)) {
            return true;
        }
        return super.onLongPress(popupKey);
    }

    /**
     * Find the key at touch coordinates (x, y).
     */
    public Keyboard.Key findKeyAt(float x, float y) {
        Keyboard keyboard = getKeyboard();
        if (keyboard == null) return null;
        List<Keyboard.Key> keys = keyboard.getKeys();
        if (keys == null) return null;

        int pl = getPaddingLeft();
        int pt = getPaddingTop();
        int rx = (int) x - pl;
        int ry = (int) y - pt;

        for (Keyboard.Key key : keys) {
            if (key != null && key.isInside(rx, ry)) {
                return key;
            }
        }
        return null;
    }

    private void invalidateKeyBounds(Keyboard.Key key) {
        if (key == null) return;
        int pl = getPaddingLeft();
        int pt = getPaddingTop();
        invalidate(key.x + pl, key.y + pt, key.x + key.width + pl, key.y + key.height + pt);
    }

    private boolean isKeyHeldByOtherPointer(int excludePointerId, Keyboard.Key targetKey) {
        for (int i = 0; i < activeKeysByPointer.size(); i++) {
            if (activeKeysByPointer.keyAt(i) != excludePointerId && activeKeysByPointer.valueAt(i) == targetKey) {
                return true;
            }
        }
        return false;
    }

    private void handlePointerDown(int pointerId, float x, float y) {
        Keyboard.Key key = findKeyAt(x, y);
        if (key == null) return;

        activeKeysByPointer.put(pointerId, key);
        key.pressed = true;
        invalidateKeyBounds(key);

        OnKeyboardActionListener listener = getOnKeyboardActionListener();
        if (listener != null && key.codes != null && key.codes.length > 0) {
            listener.onPress(key.codes[0]);
        }

        longPressFired.put(pointerId, false);
        Runnable lp = () -> {
            Keyboard.Key currentKey = activeKeysByPointer.get(pointerId);
            if (currentKey != null && currentKey.pressed) {
                if (babelListener != null && babelListener.onLongPress(currentKey)) {
                    longPressFired.put(pointerId, true);
                    currentKey.pressed = false;
                    invalidateKeyBounds(currentKey);
                }
            }
        };
        longPressRunnables.put(pointerId, lp);
        touchHandler.postDelayed(lp, 400);
    }

    private void handlePointerMove(int pointerId, float x, float y) {
        Keyboard.Key key = activeKeysByPointer.get(pointerId);
        if (key == null) return;

        int pl = getPaddingLeft();
        int pt = getPaddingTop();
        int rx = (int) x - pl;
        int ry = (int) y - pt;

        // Generous touch tolerance slop
        int slop = 24;
        boolean stillInside = (rx >= key.x - slop && rx < key.x + key.width + slop &&
                               ry >= key.y - slop && ry < key.y + key.height + slop);

        if (!stillInside) {
            Runnable r = longPressRunnables.get(pointerId);
            if (r != null) {
                touchHandler.removeCallbacks(r);
                longPressRunnables.remove(pointerId);
            }
            if (!isKeyHeldByOtherPointer(pointerId, key)) {
                key.pressed = false;
                invalidateKeyBounds(key);
            }
        }
    }

    private void handlePointerUp(int pointerId) {
        Runnable r = longPressRunnables.get(pointerId);
        if (r != null) {
            touchHandler.removeCallbacks(r);
            longPressRunnables.remove(pointerId);
        }

        Keyboard.Key key = activeKeysByPointer.get(pointerId);
        if (key != null) {
            boolean wasPressed = key.pressed;
            if (!isKeyHeldByOtherPointer(pointerId, key)) {
                key.pressed = false;
                invalidateKeyBounds(key);
            }

            boolean longFired = longPressFired.get(pointerId, false);
            if (wasPressed && !longFired) {
                OnKeyboardActionListener listener = getOnKeyboardActionListener();
                if (listener != null && key.codes != null && key.codes.length > 0) {
                    listener.onKey(key.codes[0], key.codes);
                    listener.onRelease(key.codes[0]);
                }
            }
            activeKeysByPointer.remove(pointerId);
        }
        longPressFired.delete(pointerId);
    }

    public void cleanupAllPointers() {
        for (int i = 0; i < longPressRunnables.size(); i++) {
            Runnable r = longPressRunnables.valueAt(i);
            if (r != null) touchHandler.removeCallbacks(r);
        }
        longPressRunnables.clear();

        for (int i = 0; i < activeKeysByPointer.size(); i++) {
            Keyboard.Key key = activeKeysByPointer.valueAt(i);
            if (key != null && key.pressed) {
                key.pressed = false;
                invalidateKeyBounds(key);
            }
        }
        activeKeysByPointer.clear();
        longPressFired.clear();
    }

    public void destroy() {
        touchHandler.removeCallbacksAndMessages(null);
        cleanupAllPointers();
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        int action = me.getActionMasked();
        int actionIndex = me.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                int pointerId = me.getPointerId(0);
                handlePointerDown(pointerId, me.getX(0), me.getY(0));
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int pointerId = me.getPointerId(actionIndex);
                handlePointerDown(pointerId, me.getX(actionIndex), me.getY(actionIndex));
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int pointerCount = me.getPointerCount();
                for (int i = 0; i < pointerCount; i++) {
                    int pointerId = me.getPointerId(i);
                    handlePointerMove(pointerId, me.getX(i), me.getY(i));
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int pointerId = me.getPointerId(actionIndex);
                handlePointerUp(pointerId);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                int pointerId = me.getPointerId(0);
                handlePointerUp(pointerId);
                cleanupAllPointers();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                cleanupAllPointers();
                return true;
            }
        }
        return super.onTouchEvent(me);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawCustomMultilineKeys(canvas);
    }

    private void drawCustomMultilineKeys(Canvas canvas) {
        Keyboard keyboard = getKeyboard();
        if (keyboard == null) return;
        List<Keyboard.Key> keys = keyboard.getKeys();
        if (keys == null) return;

        int pl = getPaddingLeft();
        int pt = getPaddingTop();

        for (Keyboard.Key key : keys) {
            if (key == null) continue;
            boolean isNumpadSwitch = (key.codes != null && key.codes.length > 0 && key.codes[0] == -12);
            boolean hasNewline = key.label != null && key.label.toString().contains("\n");

            if (isNumpadSwitch || hasNewline) {
                String line1 = "12";
                String line2 = "34";
                if (hasNewline) {
                    String[] parts = key.label.toString().split("\n");
                    if (parts.length >= 2) {
                        line1 = parts[0].trim();
                        line2 = parts[1].trim();
                    }
                }

                // 1. Redraw key background so any linear "1234" text drawn by super.onDraw is cleanly erased
                Drawable bg = ContextCompat.getDrawable(getContext(), R.drawable.key_background_special);
                if (bg != null) {
                    bg.setState(key.getCurrentDrawableState());
                    int l = key.x + pl;
                    int t = key.y + pt;
                    int r = l + key.width;
                    int b = t + key.height;
                    bg.setBounds(l, t, r, b);
                    bg.draw(canvas);
                }

                // 2. Draw 2-line stacked label ("12" on top, "34" underneath)
                if (multilinePaint == null) {
                    initPaint();
                }
                float textSize = Math.min(key.height * 0.26f, key.width * 0.34f);
                multilinePaint.setTextSize(textSize);
                multilinePaint.setColor(keyTextColor);

                float centerX = key.x + pl + key.width / 2.0f;
                float centerY = key.y + pt + key.height / 2.0f;

                float line1Y = centerY - textSize * 0.16f;
                float line2Y = centerY + textSize * 1.06f;

                canvas.drawText(line1, centerX, line1Y, multilinePaint);
                canvas.drawText(line2, centerX, line2Y, multilinePaint);
            }
        }
    }
}
