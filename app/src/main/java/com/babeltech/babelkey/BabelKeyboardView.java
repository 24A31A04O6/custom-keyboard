package com.babeltech.babelkey;

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
