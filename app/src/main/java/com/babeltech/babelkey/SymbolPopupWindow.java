package com.babeltech.babelkey;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * SymbolPopupWindow
 *
 * Shows a horizontal row of symbol alternatives above the long-pressed key.
 * The user can slide their finger left/right without lifting to select a symbol;
 * releasing commits the currently highlighted one.
 *
 * All cells use strictly rectangular (0dp corner) backgrounds for a sharp look:
 *   normal  → bg_symbol_popup.xml   (#23282D fill, #4A5568 stroke)
 *   selected→ bg_symbol_cell_selected.xml (#4A90D9 fill)
 *
 * Usage (from MyKeyboardService):
 *   symbolPopup.show(anchorView, primaryCode, symbols, onCommit);
 */
@SuppressLint("InflateParams")
public class SymbolPopupWindow {

    /** Called when the user selects a symbol (releases finger). */
    public interface OnSymbolCommit {
        void onCommit(String symbol);
    }

    private static final int CELL_WIDTH_DP  = 48;
    private static final int CELL_HEIGHT_DP = 48;
    private static final int MAX_CELLS      = 8; // clip to keep popup within screen width

    private final Context       ctx;
    private final float         density;
    private       PopupWindow   popupWindow;
    private       LinearLayout  cellContainer;
    private       int           selectedIndex = 0;
    private       String[]      currentSymbols;
    private       OnSymbolCommit commitListener;
    private       boolean        showing = false;

    public SymbolPopupWindow(Context ctx) {
        this.ctx     = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    /**
     * Show the popup above {@code anchor}.
     *
     * @param anchor        The view the popup is anchored to (KeyboardView).
     * @param primaryCode   The long-pressed key's primary code — used to
     *                      position the popup horizontally near the key.
     * @param symbols       Ordered array of alternatives. Index 0 = primary char.
     * @param keyX          X-centre of the pressed key in window coordinates (px).
     * @param onCommit      Callback fired when the user lifts their finger.
     */
    @SuppressLint("ClickableViewAccessibility")
    public void show(View anchor, String[] symbols, int keyX, OnSymbolCommit onCommit) {
        dismiss(); // clean up any previous popup

        if (symbols == null || symbols.length == 0) return;

        // Clip to MAX_CELLS
        int count = Math.min(symbols.length, MAX_CELLS);
        currentSymbols = new String[count];
        System.arraycopy(symbols, 0, currentSymbols, 0, count);
        commitListener = onCommit;
        selectedIndex  = 0;

        // Build the horizontal cell row
        cellContainer = new LinearLayout(ctx);
        cellContainer.setOrientation(LinearLayout.HORIZONTAL);
        cellContainer.setBackground(ctx.getResources().getDrawable(R.drawable.bg_symbol_popup, ctx.getTheme()));

        int cellW = dp(CELL_WIDTH_DP);
        int cellH = dp(CELL_HEIGHT_DP);

        for (int i = 0; i < count; i++) {
            TextView cell = buildCell(currentSymbols[i], cellW, cellH);
            cellContainer.addView(cell);
        }

        // Highlight the default (first) cell
        highlightCell(0);

        // Wrap in popup
        int totalW = cellW * count;
        popupWindow = new PopupWindow(cellContainer, totalW, cellH, false);
        popupWindow.setBackgroundDrawable(null);
        popupWindow.setTouchable(false); // touch is handled by KeyboardView passthrough
        popupWindow.setFocusable(false);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setClippingEnabled(true);
        popupWindow.setElevation(8f);

        // Position: centred on the pressed key, above the keyboard
        int[] anchorLoc = new int[2];
        anchor.getLocationInWindow(anchorLoc);

        int xOff = keyX - anchorLoc[0] - totalW / 2;
        // Clamp to screen
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        xOff = Math.max(0, Math.min(xOff, screenW - totalW));

        int yOff = -cellH - dp(8); // 8dp gap above the key

        popupWindow.showAsDropDown(anchor, xOff, yOff, Gravity.TOP | Gravity.START);
        showing = true;
    }

    /**
     * Called by MyKeyboardService on every touch MOVE event while a long-press
     * popup is showing. Updates the highlighted cell based on finger X position.
     *
     * @param rawX  Finger X in window coordinates (px).
     * @param anchorX  Left edge of the popup in window coordinates.
     */
    public void onTouchMove(float rawX, int anchorX) {
        if (!showing || cellContainer == null || currentSymbols == null) return;
        int cellW = dp(CELL_WIDTH_DP);
        int relX  = (int)(rawX) - anchorX;
        int idx   = relX / cellW;
        idx = Math.max(0, Math.min(idx, currentSymbols.length - 1));
        if (idx != selectedIndex) {
            highlightCell(idx);
        }
    }

    /**
     * Called on finger UP — commits the selected symbol and dismisses.
     * @return The committed symbol, or null if popup wasn't showing.
     */
    public String commit() {
        if (!showing || currentSymbols == null) return null;
        String chosen = currentSymbols[selectedIndex];
        dismiss();
        if (commitListener != null) commitListener.onCommit(chosen);
        return chosen;
    }

    /** Dismiss the popup without committing anything. */
    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        popupWindow   = null;
        cellContainer = null;
        showing       = false;
        selectedIndex = 0;
    }

    public boolean isShowing() { return showing; }

    /**
     * Returns the left-edge X coordinate of the popup in window space.
     * Used by the caller to compute which cell the finger is over.
     */
    public int getPopupX(View anchor) {
        if (!showing || cellContainer == null) return 0;
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        return loc[0]; // caller must add the xOff used in show(); store it
    }

    // ────────────────────────────────────────────────────────────────────────
    // Internals
    // ────────────────────────────────────────────────────────────────────────

    private TextView buildCell(String symbol, int width, int height) {
        TextView tv = new TextView(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        tv.setLayoutParams(lp);
        tv.setText(symbol);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(ctx.getResources().getDrawable(R.drawable.bg_symbol_popup, ctx.getTheme()));
        return tv;
    }

    private void highlightCell(int idx) {
        if (cellContainer == null) return;
        selectedIndex = idx;
        for (int i = 0; i < cellContainer.getChildCount(); i++) {
            View child = cellContainer.getChildAt(i);
            if (i == idx) {
                child.setBackground(ctx.getResources().getDrawable(R.drawable.bg_symbol_cell_selected, ctx.getTheme()));
            } else {
                child.setBackground(ctx.getResources().getDrawable(R.drawable.bg_symbol_popup, ctx.getTheme()));
            }
        }
    }

    private int dp(int dp) { return (int)(dp * density); }
}
