package com.babeltech.babelkey.core;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;

/**
 * Custom KeyboardView to expose the protected onLongPress method to our service.
 */
@SuppressWarnings("deprecation")
public class BabelKeyboardView extends KeyboardView {

    public interface BabelKeyboardListener {
        /** Return true if the long press was handled. */
        boolean onLongPress(Keyboard.Key key);
    }

    private BabelKeyboardListener babelListener;
    private int requestedKeyboardHeight;

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
    }

    public BabelKeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
}
