package com.babeltech.babelkey.layout;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import java.util.List;

@SuppressWarnings("deprecation")
public final class ResponsiveKeyboardLayout extends Keyboard {
    private final int[][] original;
    private final int originalWidth;
    private final int originalHeight;
    private final int[] keyIndices;
    private int layoutWidth;
    private int layoutHeight;

    public ResponsiveKeyboardLayout(Context context, int resourceId) {
        super(context, resourceId);
        originalWidth = Math.max(1, super.getMinWidth());
        originalHeight = Math.max(1, super.getHeight());
        layoutWidth = originalWidth;
        layoutHeight = originalHeight;
        List<Key> keys = getKeys();
        original = new int[keys.size()][5];
        keyIndices = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            original[i] = new int[] {key.x, key.y, key.width, key.height, key.gap};
            keyIndices[i] = i;
        }
    }

    public void fitTo(int width, int height) {
        layoutWidth = Math.max(1, width);
        layoutHeight = height > 0 ? height : originalHeight;
        float scaleX = (float) layoutWidth / originalWidth;
        float scaleY = (float) layoutHeight / originalHeight;
        List<Key> keys = getKeys();
        for (int i = 0; i < keys.size(); i++) {
            Key key = keys.get(i);
            int[] source = original[i];
            key.x = Math.round(source[0] * scaleX);
            key.y = Math.round(source[1] * scaleY);
            key.width = Math.max(1, Math.round((source[0] + source[2]) * scaleX) - key.x);
            key.height = Math.max(1, Math.round((source[1] + source[3]) * scaleY) - key.y);
            key.gap = Math.round(source[4] * scaleX);
        }
    }

    @Override public int getMinWidth() { return layoutWidth; }
    @Override public int getHeight() { return layoutHeight; }

    @Override public int[] getNearestKeys(int x, int y) {
        // Keyboard's private proximity grid becomes stale after resizing key bounds.
        // KeyboardView still performs its normal hit testing on these candidates.
        return keyIndices;
    }
}
