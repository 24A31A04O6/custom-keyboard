package com.babeltech.babelkey.clipboard;

import com.babeltech.babelkey.core.ServiceCallback;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.babeltech.babelkey.R;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KeyboardClipboardManager
 *
 * Owns clipboard history (up to 15 items), pinned items, OTP detection,
 * and the RecyclerView-based clipboard drawer UI.
 *
 * Named with "Keyboard" prefix to avoid collision with android.content.ClipboardManager.
 */
@SuppressWarnings("deprecation")
public class KeyboardClipboardManager {

    // ── Configuration ────────────────────────────────────────────────────────
    private static final int     MAX_HISTORY = 15;
    private static final String  SEP         = "\u2063"; // invisible separator
    private static final String  PREF_CB     = "clipboardHistory_v2";
    private static final String  PREF_PIN    = "pinnedClipboard_v2";
    private static final Pattern OTP_PAT     = Pattern.compile("\\b(\\d{4,8})\\b");

    // ── Data model ───────────────────────────────────────────────────────────

    /** A single clipboard entry. */
    public static final class ClipItem {
        public final String  text;
        public       boolean pinned;

        ClipItem(String text, boolean pinned) {
            this.text   = text;
            this.pinned = pinned;
        }
    }

    // ── Dependencies ─────────────────────────────────────────────────────────
    private final Context            ctx;
    private final SharedPreferences  prefs;
    private final ServiceCallback    cb;
    private final ClipboardManager   sys;
    private final Handler            handler = new Handler(Looper.getMainLooper());

    // ── State ────────────────────────────────────────────────────────────────
    /** Combined list: pinned items first, then history (excluding duplicates). */
    private final List<ClipItem>     items   = new ArrayList<>();
    private ClipboardManager.OnPrimaryClipChangedListener listener;
    private String  pendingOtp  = null;
    private boolean dark        = true;

    // ── Views (set via attachDrawer) ─────────────────────────────────────────
    private View                  drawerRoot;
    private RecyclerView          recyclerView;
    private TextView              emptyView;
    private ClipAdapter           adapter;

    // Legacy OTP banner views (wired from keyboard_view.xml)
    private android.widget.LinearLayout otpBanner;
    private TextView                    otpText;

    // ── Constructor ──────────────────────────────────────────────────────────

    public KeyboardClipboardManager(Context ctx, SharedPreferences prefs,
                                     ClipboardManager sys, ServiceCallback cb) {
        this.ctx = ctx; this.prefs = prefs; this.sys = sys; this.cb = cb;
    }

    // ── View attachment ──────────────────────────────────────────────────────

    /**
     * Attach the RecyclerView clipboard drawer.
     * Call this in onCreateInputView() after inflating keyboard_view.xml.
     */
    public void attachDrawer(View drawerRootView) {
        this.drawerRoot  = drawerRootView;
        if (drawerRootView == null) return;

        recyclerView = drawerRootView.findViewById(R.id.rv_clipboard);
        emptyView    = drawerRootView.findViewById(R.id.tv_clip_empty);
        TextView btnClear = drawerRootView.findViewById(R.id.btn_clip_clear_all);

        if (recyclerView != null) {
            adapter = new ClipAdapter();
            recyclerView.setLayoutManager(
                    new LinearLayoutManager(ctx, LinearLayoutManager.VERTICAL, false));
            recyclerView.setAdapter(adapter);
            recyclerView.setHasFixedSize(false);
        }
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> clearAll());
        }
    }

    /** Attach legacy OTP banner views from keyboard_view.xml. */
    public void attachOtpBanner(android.widget.LinearLayout banner, TextView text) {
        this.otpBanner = banner;
        this.otpText   = text;
    }

    public void onThemeChanged(boolean isDark) { this.dark = isDark; }

    // ── Clipboard listening ──────────────────────────────────────────────────

    public void startListening() {
        listener = this::onClipChanged;
        if (sys != null) sys.addPrimaryClipChangedListener(listener);
    }

    public void stopListening() {
        if (sys != null && listener != null) sys.removePrimaryClipChangedListener(listener);
    }

    private void onClipChanged() {
        ClipData clip;
        try { clip = sys.getPrimaryClip(); } catch (Exception e) { return; }
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence raw = clip.getItemAt(0).getText();
        if (raw == null) return;
        String text = raw.toString().trim();
        if (text.isEmpty()) return;

        addToHistory(text);

        // OTP detection
        Matcher m = OTP_PAT.matcher(text);
        if (m.find()) {
            pendingOtp = m.group(1);
            handler.post(this::showOtpBanner);
        }
    }

    // ── History management ───────────────────────────────────────────────────

    public void addToHistory(String text) {
        if (text == null || text.trim().isEmpty()) return;

        // Deduplication: remove existing entry with same text
        items.removeIf(item -> !item.pinned && item.text.equals(text));

        // Also check pinned — if pinned, don't add duplicate to history
        for (ClipItem it : items) {
            if (it.pinned && it.text.equals(text)) return;
        }

        // Insert at the first non-pinned position
        int insertAt = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).pinned) { insertAt = i; break; }
            insertAt = i + 1;
        }
        items.add(insertAt, new ClipItem(text, false));

        // Trim to MAX_HISTORY (pinned don't count toward limit)
        trimHistory();
        save();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void trimHistory() {
        int historyCount = 0;
        for (java.util.Iterator<ClipItem> iterator = items.iterator(); iterator.hasNext();) {
            ClipItem item = iterator.next();
            if (!item.pinned && ++historyCount > MAX_HISTORY) {
                iterator.remove();
            }
        }
    }

    private void pinItem(int position) {
        if (position < 0 || position >= items.size()) return;
        ClipItem item = items.get(position);
        item.pinned = !item.pinned;
        // Re-sort: pinned go to top
        items.remove(position);
        if (item.pinned) {
            items.add(0, item);
        } else {
            // Find insertion point after pinned items
            int ins = 0;
            for (ClipItem it : items) { if (it.pinned) ins++; else break; }
            items.add(ins, item);
        }
        trimHistory();
        save();
        savePinned();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
        Toast.makeText(ctx, item.pinned ? "Pinned" : "Unpinned", Toast.LENGTH_SHORT).show();
    }

    private void deleteItem(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        save(); savePinned();
        if (adapter != null) {
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, items.size());
        }
        updateEmptyState();
    }

    private void clearAll() {
        items.clear();
        prefs.edit().remove(PREF_CB).remove(PREF_PIN).apply();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
        Toast.makeText(ctx, "Clipboard cleared", Toast.LENGTH_SHORT).show();
    }

    // ── Drawer toggle ────────────────────────────────────────────────────────

    public void toggleDrawer() {
        if (drawerRoot == null) return;
        boolean visible = drawerRoot.getVisibility() == View.VISIBLE;
        drawerRoot.setVisibility(visible ? View.GONE : View.VISIBLE);
        if (!visible) {
            // Refresh when opening
            if (adapter != null) adapter.notifyDataSetChanged();
            updateEmptyState();
        }
    }

    public void hideDrawer() {
        if (drawerRoot != null) drawerRoot.setVisibility(View.GONE);
    }

    // ── OTP banner ───────────────────────────────────────────────────────────

    private void showOtpBanner() {
        if (otpBanner == null || pendingOtp == null) return;
        otpText.setText("OTP: " + pendingOtp + "  (Tap to paste)");
        otpBanner.setVisibility(View.VISIBLE);
    }

    public void hideOtpBanner() {
        if (otpBanner != null) otpBanner.setVisibility(View.GONE);
        pendingOtp = null;
    }

    public void pasteOtp() {
        if (pendingOtp == null) return;
        cb.commitText(pendingOtp);
        hideOtpBanner();
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (ClipItem it : items) {
            if (!it.pinned) sb.append(it.text).append(SEP);
        }
        prefs.edit().putString(PREF_CB, sb.toString()).apply();
    }

    private void savePinned() {
        StringBuilder sb = new StringBuilder();
        for (ClipItem it : items) {
            if (it.pinned) sb.append(it.text).append(SEP);
        }
        prefs.edit().putString(PREF_PIN, sb.toString()).apply();
    }

    public void loadHistory() {
        items.clear();
        java.util.Set<String> seen = new java.util.HashSet<>();
        // Load pinned first
        String pinStr = prefs.getString(PREF_PIN, "");
        if (!pinStr.isEmpty()) {
            for (String s : pinStr.split(Pattern.quote(SEP))) {
                if (!s.isEmpty() && seen.add(s)) items.add(new ClipItem(s, true));
            }
        }
        // Load history
        String histStr = prefs.getString(PREF_CB, "");
        if (!histStr.isEmpty()) {
            for (String s : histStr.split(Pattern.quote(SEP))) {
                if (!s.isEmpty() && seen.add(s)) items.add(new ClipItem(s, false));
            }
        }
        trimHistory();
        save();
        savePinned();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    // kept for back-compat call in MyKeyboardService
    public void loadPinned() { /* merged into loadHistory() */ }

    // ── Utility ──────────────────────────────────────────────────────────────

    private void updateEmptyState() {
        if (emptyView == null || recyclerView == null) return;
        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    public void detach() {
        drawerRoot = null; recyclerView = null; emptyView = null;
        adapter = null; otpBanner = null; otpText = null;
    }

    public void destroy() { stopListening(); handler.removeCallbacksAndMessages(null); detach(); }

    // ── Legacy compat (panel toggle used by old toolbar wiring) ─────────────

    /** @deprecated Use {@link #toggleDrawer()} */
    @Deprecated
    public void togglePanel() { toggleDrawer(); }

    /** @deprecated Kept for any old references. */
    @Deprecated
    public void refresh() {
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  RecyclerView Adapter
    // ════════════════════════════════════════════════════════════════════════

    private final class ClipAdapter extends RecyclerView.Adapter<ClipAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_clipboard_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ClipItem item = items.get(position);

            // Show pin indicator prefix for pinned items
            String display = item.pinned ? "📌 " + item.text : item.text;
            h.tvText.setText(display);
            h.tvText.setTextColor(dark ? Color.WHITE : Color.parseColor("#1A1A1A"));

            // Update pin button appearance
            h.btnPin.setText(item.pinned ? "📌" : "📎");
            h.btnPin.setAlpha(item.pinned ? 1f : 0.5f);

            // Click: commit text and dismiss drawer
            h.itemView.setOnClickListener(v -> {
                cb.commitText(item.text);
                hideDrawer();
            });

            // Long-press: alternate way to pin
            h.itemView.setOnLongClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_ID) pinItem(pos);
                return true;
            });

            // Pin button
            h.btnPin.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_ID) pinItem(pos);
            });

            // Delete button
            h.btnDelete.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_ID) deleteItem(pos);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        final class VH extends RecyclerView.ViewHolder {
            final TextView tvText, btnPin, btnDelete;
            VH(@NonNull View v) {
                super(v);
                tvText    = v.findViewById(R.id.tv_clip_text);
                btnPin    = v.findViewById(R.id.btn_clip_pin);
                btnDelete = v.findViewById(R.id.btn_clip_delete);
            }
        }
    }
}
