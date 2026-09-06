package com.babeltech.babelkey;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * SuggestionView
 *
 * Manages the horizontal suggestion bar above the keyboard.
 * Displays up to 3 word suggestions as tappable chips.
 * Chips are sized to their content (not stretched) and centered
 * in the bar so they never wrap to a second line.
 */
public class SuggestionView {

    public interface SuggestionClickListener {
        void onSuggestionClicked(String word, int index);
    }

    private final LinearLayout container;
    private final SuggestionClickListener listener;
    private boolean isDark = false;

    public SuggestionView(LinearLayout container, SuggestionClickListener listener, boolean isDark) {
        this.container = container;
        this.listener = listener;
        this.isDark = isDark;
    }

    /**
     * Updates the suggestion bar with a new list of words.
     * Clears existing chips and creates new ones.
     *
     * @param suggestions list of suggestion strings (max 3 shown)
     */
    public void setSuggestions(List<String> suggestions) {
        container.removeAllViews();
        int count = Math.min(suggestions.size(), 3);
        for (int i = 0; i < count; i++) {
            final String word = suggestions.get(i);
            final int idx = i;
            TextView chip = createChip(word, i == 0); // first chip is bold (auto-correct)
            chip.setOnClickListener(v -> listener.onSuggestionClicked(word, idx));
            container.addView(chip);

            // Divider between chips: visible "|" symbol to clearly separate suggestions
            if (i < count - 1) {
                TextView divider = new TextView(container.getContext());
                divider.setText("|");
                divider.setTextSize(14f);
                divider.setGravity(Gravity.CENTER);
                divider.setTextColor(isDark ? androidx.core.content.ContextCompat.getColor(container.getContext(), R.color.suggestion_divider_color) : Color.parseColor("#B0B0B0"));
                divider.setPadding(2, 0, 2, 0);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
                divider.setLayoutParams(dividerParams);
                container.addView(divider);
            }
        }
    }

    public void clear() {
        container.removeAllViews();
    }

    /**
     * Creates a styled chip TextView for a suggestion word.
     * Uses WRAP_CONTENT width and forces single line so chips
     * never wrap to two lines and always sit centered in the bar.
     */
    private TextView createChip(String word, boolean isBold) {
        Context ctx = container.getContext();
        TextView tv = new TextView(ctx);
        tv.setText(word);
        tv.setTextSize(15f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(24, 0, 24, 0);
        tv.setSingleLine(true);
        tv.setMaxLines(1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);

        // Color & style
        int textColor = isDark ? androidx.core.content.ContextCompat.getColor(container.getContext(), R.color.suggestion_chip_text) : androidx.core.content.ContextCompat.getColor(container.getContext(), R.color.key_text_color);
        if (isBold) textColor = isDark ? androidx.core.content.ContextCompat.getColor(container.getContext(), R.color.suggestion_chip_text_bold) : Color.parseColor("#1A73E8"); // Gboard blue
        tv.setTextColor(textColor);
        if (isBold) tv.setTypeface(null, Typeface.BOLD);

        // Bounded ripple effect on press — replaces the legacy list_selector_background
        tv.setBackgroundResource(R.drawable.suggestion_ripple);

        // WRAP_CONTENT width so the chip sizes to its text, not stretched/wrapped
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        tv.setLayoutParams(params);
        return tv;
    }
}