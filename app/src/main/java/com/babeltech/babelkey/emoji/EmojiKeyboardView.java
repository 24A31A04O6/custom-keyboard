package com.babeltech.babelkey.emoji;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.babeltech.babelkey.R;
/**
 * EmojiKeyboardView
 *
 * A custom View that displays an emoji panel organized into categories.
 * It inflates emoji_keyboard.xml and populates the grid with emoji strings.
 * The host service provides callbacks via the EmojiListener interface.
 */
public class EmojiKeyboardView {

    // ── Emoji data ──────────────────────────────────────────────────────────────

    /** Category names shown as tab labels */
    private static final String[] CATEGORY_NAMES = {
            "�", "❤", "�", "�", "", "�", "�", "�"
    };

    /** Emoji for each category */
    private static final String[][] CATEGORY_EMOJIS = {
            // Smileys & People
            { "�","�","�","�","�","�","�","�","�","�","�","�","�","�","�",
                    "�","�","�","�","�","�","�","�","�","�","�","�","�","�","�",
                    "�","�","�","�","�","�","�","�","�","�","�","�","�","�","�",
                    "�","�","�","�","�","�","�","☠","�","�","�","�","�","�","�" },
            // Hearts & Love
            { "❤","�","�","�","�","�","�","�","�","�","❣","�","�","�","�",
                    "�","�","�","�","☮","✝","�","�","","","","","","","",
                    "","","","","","","�","�","�","▶","","","","⏭","⏮" },
            // Animals
            { "�","�","�","�","�","�","�","�","�","�","�","�","�","�","�",
                    "�","�","�","�","�","�","�","�","�","�","�","�","�","�","�",
                    "�","�","�","�","�","�","�","�","�","�","�","�","�","�","�" },
            // Food
            { "�","�","�","�","�","�","�","�","�","�","�","�","�","�","�",
                    "�","�","�","�","�","�","�","�","�","�","�","�","�","�","�",
                    "�","�","�","�","�","�","�","�","�","�","�","�","�","�","�" },
            // Sports
            { "","�","�","","�","�","�","�","�","�","�","�","�","","�",
                    "�","�","�","�","�","�","�","⛸","�","�","⛷","�","�","🏋","�",
                    "�","�","�","⛹","�","🏌","�","�","�","�","�","�","�","�","�" },
            // Travel
            { "�","�","�","�","�","🏎","�","�","�","�","�","�","�","�","🏍",
                    "�","�","�","�","�","�","✈","🛩","�","�","🛳","⛴","�","","🛥",
                    "�","�","","🛣","🛤","🏔","⛰","�","🏕","🏖","🏜","🏝","�","🏗","🏘" },
            // Objects
            { "�","�","🕯","�","�","�","⌨","🖥","🖨","🖱","🖲","�","�","�","�",
                    "�","�","�","📽","�","☎","�","�","�","�","�","⏱","⏲","","🕰",
                    "","","�","�","�","�","�","�","�","�","�","�","�","�","�" },
            // Symbols
            { "�","","","�","�","�","�","�","�","","","�","�","�","�",
                    "�","�","�","�","�","�","�","▪","▫","","","◼","◻","�","�",
                    "�","�","�","�","","","�","�","🗝","�","�","�","�","�","🛋" }
    };

    // ── Fields ───────────────────────────────────────────────────────────────────

    private final View rootView;
    private final GridView gridView;
    private final LinearLayout categoryTabs;
    private final EmojiListener listener;
    private int currentCategory = 0;
    private boolean isDark = false;

    /** Callback interface for the host IME service */
    public interface EmojiListener {
        void onEmojiSelected(String emoji);
        void onSwitchToKeyboard();
    }

    // ── Constructor ───────────────────────────────────────────────────────────────

    public EmojiKeyboardView(Context context, EmojiListener listener, boolean isDark) {
        this.listener = listener;
        this.isDark = isDark;

        // Inflate the emoji keyboard layout
        LayoutInflater inflater = LayoutInflater.from(context);
        rootView = inflater.inflate(R.layout.emoji_keyboard, null);

        gridView = rootView.findViewById(R.id.emoji_grid);
        categoryTabs = rootView.findViewById(R.id.emoji_category_tabs);
        Button backBtn = rootView.findViewById(R.id.btn_back_to_keyboard);

        applyTheme(context);
        buildCategoryTabs(context);
        loadCategory(context, 0);

        backBtn.setOnClickListener(v -> listener.onSwitchToKeyboard());

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            String[] emojis = CATEGORY_EMOJIS[currentCategory];
            if (position < emojis.length) {
                listener.onEmojiSelected(emojis[position]);
            }
        });
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /** Returns the root view to be attached to the IME window */
    public View getView() {
        return rootView;
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private void applyTheme(Context context) {
        int bgColor    = isDark ? androidx.core.content.ContextCompat.getColor(context, R.color.keyboard_background_dark) : androidx.core.content.ContextCompat.getColor(context, R.color.keyboard_background);
        int tabBg      = isDark ? androidx.core.content.ContextCompat.getColor(context, R.color.emoji_tab_background) : androidx.core.content.ContextCompat.getColor(context, R.color.toolbar_background);
        int tabText    = isDark ? Color.WHITE : androidx.core.content.ContextCompat.getColor(context, R.color.key_text_color);
        rootView.setBackgroundColor(bgColor);
        if (categoryTabs != null) categoryTabs.setBackgroundColor(tabBg);
        // Store for tab creation
        this.tabTextColor = tabText;
    }

    private int tabTextColor = Color.WHITE;

    private void buildCategoryTabs(Context context) {
        categoryTabs.removeAllViews();
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            final int index = i;
            TextView tab = new TextView(context);
            tab.setText(CATEGORY_NAMES[i]);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            tab.setTextColor(tabTextColor);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(20, 0, 20, 0);
            tab.setOnClickListener(v -> loadCategory(context, index));
            categoryTabs.addView(tab);
        }
    }

    private void loadCategory(Context context, int categoryIndex) {
        currentCategory = categoryIndex;
        String[] emojis = CATEGORY_EMOJIS[categoryIndex];

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context, android.R.layout.simple_list_item_1, emojis) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = new TextView(context);
                    tv.setGravity(Gravity.CENTER);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
                    // Minimum touch target: 48dp × 48dp (Material Design guideline)
                    int size = (int) (52 * context.getResources().getDisplayMetrics().density);
                    tv.setMinWidth(size);
                    tv.setMinHeight(size);
                    tv.setPadding(4, 4, 4, 4);
                }
                tv.setText(emojis[position]);
                return tv;
            }
        };

        gridView.setAdapter(adapter);
    }
}