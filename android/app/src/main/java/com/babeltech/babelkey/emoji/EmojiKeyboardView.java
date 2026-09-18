package com.babeltech.babelkey.emoji;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;

import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;


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
            "\uD83D\uDE00", "\u2764\uFE0F", "\uD83D\uDC36", "\uD83C\uDF4E",
            "\u26BD", "\uD83D\uDE97", "\uD83D\uDCA1", "\u2705"
    };

    /** Emoji for each category */
    private static final String[][] CATEGORY_EMOJIS = {
            // Smileys & People
            codePoints(0x1F600, 0x1F603, 0x1F604, 0x1F601, 0x1F606, 0x1F605, 0x1F602, 0x1F923,
                    0x1F60A, 0x1F607, 0x1F642, 0x1F643, 0x1F609, 0x1F60D, 0x1F618, 0x1F617,
                    0x1F60B, 0x1F61B, 0x1F61C, 0x1F61D, 0x1F60E, 0x1F913, 0x1F914, 0x1F610,
                    0x1F611, 0x1F636, 0x1F644, 0x1F60F, 0x1F623, 0x1F625, 0x1F62E, 0x1F62F,
                    0x1F62A, 0x1F62B, 0x1F634, 0x1F60C, 0x1F61E, 0x1F614, 0x1F615, 0x1F641,
                    0x1F62D, 0x1F622, 0x1F621, 0x1F620, 0x1F631, 0x1F633, 0x1F637, 0x1F912,
                    0x1F44D, 0x1F44E, 0x1F44F, 0x1F64C, 0x1F64F, 0x1F44B, 0x1F4AA, 0x1F44C),
            // Hearts & Love
            codePoints(0x2764, 0x1F49B, 0x1F49A, 0x1F499, 0x1F49C, 0x1F5A4, 0x1F494,
                    0x1F495, 0x1F496, 0x1F497, 0x1F498, 0x1F49D, 0x1F49E, 0x1F49F, 0x2763,
                    0x1F48B, 0x1F48C, 0x1F48D, 0x1F490, 0x1F339, 0x1F46B, 0x1F46C, 0x1F46D, 0x1F48F),
            // Animals
            codePoints(0x1F436, 0x1F431, 0x1F42D, 0x1F439, 0x1F430, 0x1F43B, 0x1F43C,
                    0x1F428, 0x1F42F, 0x1F981, 0x1F42E, 0x1F437, 0x1F438, 0x1F435, 0x1F648,
                    0x1F649, 0x1F64A, 0x1F414, 0x1F427, 0x1F426, 0x1F424, 0x1F986, 0x1F985,
                    0x1F989, 0x1F987, 0x1F43A, 0x1F417, 0x1F434, 0x1F984, 0x1F41D, 0x1F41B,
                    0x1F98B, 0x1F40C, 0x1F41E, 0x1F422, 0x1F40D, 0x1F419, 0x1F420, 0x1F42C, 0x1F433),
            // Food
            codePoints(0x1F34E, 0x1F34F, 0x1F350, 0x1F34A, 0x1F34B, 0x1F34C, 0x1F349,
                    0x1F347, 0x1F353, 0x1F352, 0x1F351, 0x1F34D, 0x1F95D, 0x1F345, 0x1F951,
                    0x1F346, 0x1F955, 0x1F33D, 0x1F954, 0x1F35E, 0x1F950, 0x1F9C0, 0x1F354,
                    0x1F35F, 0x1F355, 0x1F32D, 0x1F32E, 0x1F32F, 0x1F35C, 0x1F35D, 0x1F35A,
                    0x1F363, 0x1F366, 0x1F370, 0x1F382, 0x1F369, 0x1F36A, 0x1F36B, 0x2615, 0x1F375),
            // Sports
            codePoints(0x26BD, 0x1F3C0, 0x1F3C8, 0x26BE, 0x1F3BE, 0x1F3D0, 0x1F3C9,
                    0x1F3B1, 0x1F3D3, 0x1F3F8, 0x1F3D2, 0x1F3D1, 0x1F3CF, 0x26F3, 0x1F3F9,
                    0x1F3A3, 0x1F94A, 0x1F94B, 0x1F3BF, 0x1F3C2, 0x1F3C4, 0x1F3CA, 0x1F6B4, 0x1F3C6),
            // Travel
            codePoints(0x1F697, 0x1F695, 0x1F699, 0x1F68C, 0x1F68E, 0x1F3CE, 0x1F693,
                    0x1F691, 0x1F692, 0x1F69A, 0x1F69B, 0x1F69C, 0x1F6B2, 0x1F3CD, 0x1F682,
                    0x1F686, 0x1F687, 0x1F68A, 0x2708, 0x1F681, 0x1F680, 0x1F6F3, 0x26F5,
                    0x1F6A2, 0x1F5FA, 0x1F5FD, 0x1F5FC, 0x1F3F0, 0x1F3D4, 0x1F3D6, 0x1F3D5, 0x1F3E0),
            // Objects
            codePoints(0x1F4A1, 0x1F526, 0x1F56F, 0x1F4F1, 0x1F4BB, 0x2328, 0x1F5A5,
                    0x1F5A8, 0x1F5B1, 0x1F4BE, 0x1F4BF, 0x1F4F7, 0x1F4F9, 0x1F3A5, 0x1F4FA,
                    0x1F4FB, 0x1F3A4, 0x1F3A7, 0x1F4DE, 0x260E, 0x1F50B, 0x1F50C, 0x1F4DA,
                    0x1F4D6, 0x1F4DD, 0x270F, 0x1F4BC, 0x1F4B0, 0x1F4B3, 0x1F527, 0x1F528, 0x1F511),
            // Symbols
            codePoints(0x2705, 0x274C, 0x2753, 0x2757, 0x26A0, 0x1F6AB, 0x267B, 0x1F4AF,
                    0x1F4A2, 0x1F4A4, 0x1F4AC, 0x1F4AD, 0x1F514, 0x1F515, 0x1F512, 0x1F513,
                    0x1F534, 0x1F535, 0x26AB, 0x26AA, 0x1F536, 0x1F537, 0x1F538, 0x1F539,
                    0x2B06, 0x2B07, 0x2B05, 0x27A1, 0x1F504, 0x1F503, 0x2795, 0x2796)
    };

    // ── Fields ───────────────────────────────────────────────────────────────────

    private static String[] codePoints(int... values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new String(Character.toChars(values[i]));
            if (values[i] <= 0xFFFF) result[i] += "\uFE0F";
        }
        return result;
    }

    private static final String[] CATEGORY_DESCRIPTIONS = {
            "Smileys and people", "Hearts and love", "Animals", "Food",
            "Sports", "Travel", "Objects", "Symbols"
    };

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
            tab.setContentDescription(CATEGORY_DESCRIPTIONS[i]);
            tab.setMinHeight((int) (48 * context.getResources().getDisplayMetrics().density));
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
