package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local app search; package discovery and artwork never block a key press. */
public final class AppCenterActivity extends ToolsActivityBase {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Entry> all = new ArrayList<>(), visible = new ArrayList<>();
    private final LruCache<String, Drawable> icons = new LruCache<>(48);
    private final Set<String> loading = new HashSet<>();
    private final Map<String, String> rowStates = new LinkedHashMap<>();
    private final TextView[] filters = new TextView[3];
    private final Adapter adapter = new Adapter();
    private final Runnable search = this::filter;
    private EditText query;
    private TextView summary;
    private RecyclerView list;
    private int mode;
    private boolean ready;
    private String focusedPackage;
    private int category;
    private TextView categoryButton;

    private static final class Entry {
        final String pkg, label;
        final boolean tv;
        final int platformCategory;
        Entry(String pkg, String label, boolean tv, int platformCategory) { this.pkg = pkg; this.label = label; this.tv = tv; this.platformCategory = platformCategory; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        mode = Math.max(0, Math.min(2, state == null ? getIntent().getIntExtra("mode", 0) : state.getInt("mode", 0)));
        category = state == null ? 0 : state.getInt("category", 0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(36), dp(18), dp(36), dp(18));
        root.setBackgroundColor(0xFF080F25); setContentView(root);
        root.addView(text(getString(R.string.center_title), 26));
        query = new EditText(this); query.setSingleLine(true); query.setTextColor(Color.WHITE);
        query.setHintTextColor(0xFFB5C7E5); query.setHint(R.string.center_hint); query.setTextSize(19);
        query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        root.addView(query, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout tabs = new LinearLayout(this);
        root.addView(tabs);
        int[] titles = {R.string.center_all, R.string.center_favorites, R.string.center_recent};
        for (int i = 0; i < filters.length; i++) {
            final int value = i;
            TextView tab = text(getString(titles[i]), 17); filters[i] = tab;
            tab.setGravity(Gravity.CENTER); tab.setFocusable(true); tab.setClickable(true);
            tab.setBackgroundResource(R.drawable.erokhin_focus_tile);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1); lp.setMargins(dp(3), dp(4), dp(3), dp(4));
            tabs.addView(tab, lp);
            tab.setOnClickListener(v -> { mode = value; filter(); });
        }
        categoryButton = text("", 15); categoryButton.setFocusable(true); categoryButton.setClickable(true);
        categoryButton.setBackgroundResource(R.drawable.visual_control); root.addView(categoryButton);
        categoryButton.setOnClickListener(v -> new LauncherDialogBuilder(this).setTitle(R.string.category_title)
                .setSingleChoiceItems(getResources().getStringArray(R.array.category_choices), category, (d, which) -> { category = which; filter(); d.dismiss(); })
                .setNegativeButton(android.R.string.cancel, null).show());
        summary = text(getString(R.string.center_loading), 14); root.addView(summary);
        list = new RecyclerView(this); list.setLayoutManager(new LinearLayoutManager(this));
        list.setItemAnimator(null); list.setAdapter(adapter); list.setClipToPadding(false);
        list.setPadding(dp(4), dp(4), dp(4), dp(4)); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = text(getString(R.string.center_controls), 13); root.addView(hint);
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { main.removeCallbacks(search); main.postDelayed(search, 120); }
            public void afterTextChanged(Editable s) {}
        });
        query.setOnEditorActionListener((v, action, event) -> {
            filter();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
            list.requestFocus(); return true;
        });
        if (state != null) query.setText(state.getString("query", ""));
        filters[Math.max(0, Math.min(2, mode))].requestFocus();
        worker.execute(() -> {
            Map<String, Entry> entries = new LinkedHashMap<>();
            PackageManager pm = getPackageManager();
            try {
                for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                    for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (info.activityInfo == null || !info.activityInfo.enabled || !info.activityInfo.exported) continue;
                        String pkg = info.activityInfo.packageName;
                        if (!pkg.equals(getPackageName()) && !entries.containsKey(pkg)) {
                            entries.put(pkg, new Entry(pkg, AppLabels.label(this, info), category.equals(Intent.CATEGORY_LEANBACK_LAUNCHER), info.activityInfo.applicationInfo.category));
                        }
                    }
                }
                main.post(() -> { if (isDestroyed()) return; all.addAll(entries.values()); ready = true; filter(); });
            } catch (RuntimeException e) {
                main.post(() -> { if (!isDestroyed()) summary.setText(R.string.center_load_failed); });
            }
        });
    }

    private TextView text(String label, int size) {
        TextView t = new TextView(this); t.setText(label); t.setTextSize(size); t.setTextColor(0xFFE1EDFF);
        t.setPadding(dp(8), dp(6), dp(8), dp(6)); return t;
    }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }

    private void filter() {
        categoryButton.setText(getString(R.string.category_button, getResources().getStringArray(R.array.category_choices)[Math.max(0, Math.min(4, category))]));
        for (int i = 0; i < filters.length; i++) {
            filters[i].setTextColor(i == mode ? 0xFF76DAFF : Color.WHITE);
            filters[i].setContentDescription(filters[i].getText() + (i == mode ? getString(R.string.center_selected) : ""));
        }
        if (!ready) return;
        String input = query.getText().toString();
        List<String> favorites = PremiumPreferences.favorites(this);
        Map<String, Long> recent = new LinkedHashMap<>();
        Map<String, Integer> ranks = new LinkedHashMap<>();
        Map<String, Integer> assigned;
        try { assigned = AppCategories.parse(PremiumPreferences.get(this).getString("app_categories", "")); }
        catch (IllegalArgumentException e) { assigned = new LinkedHashMap<>(); }
        List<Entry> before = new ArrayList<>(visible);
        Map<String, String> previousStates = new LinkedHashMap<>(rowStates);
        visible.clear();
        for (Entry e : all) {
            if (category > 0 && AppCategories.resolve(e.pkg, e.platformCategory, assigned) != category) continue;
            long time = LastOpened.get(this, e.pkg); recent.put(e.pkg, time);
            if (mode == 1 && !favorites.contains(e.pkg) || mode == 2 && time <= 0) continue;
            int rank = AppSearch.rank(e.label, e.pkg, input);
            if (rank >= 0) { ranks.put(e.pkg, rank); visible.add(e); }
        }
        Collator collator = Collator.getInstance();
        visible.sort((a, b) -> {
            int order = Integer.compare(ranks.get(a.pkg), ranks.get(b.pkg));
            if (order == 0 && mode == 2) order = Long.compare(recent.get(b.pkg), recent.get(a.pkg));
            if (order == 0 && mode == 1) order = Integer.compare(favorites.indexOf(a.pkg), favorites.indexOf(b.pkg));
            if (order == 0) order = collator.compare(a.label, b.label);
            return order == 0 ? a.pkg.compareTo(b.pkg) : order;
        });
        boolean restoreFocus = list.hasFocus();
        String restorePackage = focusedPackage;
        rowStates.clear();
        for (Entry e : visible) rowStates.put(e.pkg, e.label + ":" + favorites.contains(e.pkg) + ":" + recent.get(e.pkg) + ":" + mode
                + (mode == 2 ? ":" + System.currentTimeMillis() / 60000 : ""));
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            public int getOldListSize() { return before.size(); }
            public int getNewListSize() { return visible.size(); }
            public boolean areItemsTheSame(int oldPosition, int newPosition) { return before.get(oldPosition).pkg.equals(visible.get(newPosition).pkg); }
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                String pkg = before.get(oldPosition).pkg;
                return java.util.Objects.equals(previousStates.get(pkg), rowStates.get(pkg));
            }
        });
        diff.dispatchUpdatesTo(adapter);
        if (restoreFocus && !visible.isEmpty()) {
            int at = 0;
            for (int i = 0; i < visible.size(); i++) if (visible.get(i).pkg.equals(restorePackage)) { at = i; break; }
            int position = at;
            list.scrollToPosition(position);
            list.post(() -> {
                if (isDestroyed() || !list.hasFocus()) return;
                RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
                if (holder != null) holder.itemView.requestFocus();
            });
        } else if (restoreFocus) filters[Math.max(0, Math.min(2, mode))].requestFocus();
        summary.setText(visible.isEmpty() ? getString(mode == 2 && input.isEmpty() ? R.string.center_no_recent : R.string.center_empty)
                : getString(R.string.center_count, visible.size()));
    }

    private void launch(Entry e) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(e.pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(e.pkg);
        if (intent == null) { Toast.makeText(this, R.string.center_launch_failed, Toast.LENGTH_LONG).show(); return; }
        try { startActivity(intent); LastOpened.record(this, e.pkg); }
        catch (ActivityNotFoundException | SecurityException ex) { Toast.makeText(this, R.string.center_launch_failed, Toast.LENGTH_LONG).show(); }
    }

    private void menu(Entry e) {
        boolean favorite = PremiumPreferences.favorites(this).contains(e.pkg);
        Config cfg = Config.load(this);
        boolean home = LibraryApps.visible(e.tv, cfg.hiddenApps.contains(e.pkg),
                LibraryApps.load(this).contains(e.pkg) || cfg.libraryApps.contains(e.pkg), LibraryApps.loadRemoved(this).contains(e.pkg));
        new LauncherDialogBuilder(this).setTitle(e.label).setItems(new String[]{getString(R.string.center_open),
                getString(favorite ? R.string.premium_remove_favorite : R.string.premium_add_favorite),
                getString(home ? R.string.library_remove_shortcut : R.string.center_add_home), getString(R.string.library_app_info), getString(R.string.category_assign)}, (d, which) -> {
            if (which == 4) assignCategory(e);
            if (which == 0) launch(e);
            if (which == 1) {
                if (!PremiumPreferences.toggleFavorite(this, e.pkg)) Toast.makeText(this, R.string.center_favorites_full, Toast.LENGTH_LONG).show();
                filter();
            }
            if (which == 2) {
                if (LibraryApps.save(this, e.pkg, !home)) {
                    PremiumPreferences.changed(this); Toast.makeText(this, R.string.center_home_saved, Toast.LENGTH_SHORT).show();
                } else Toast.makeText(this, R.string.library_save_failed, Toast.LENGTH_LONG).show();
            }
            if (which == 3) {
                Intent info = SystemSettings.appInfo(this, e.pkg);
                try { if (info != null) startActivity(info); else Toast.makeText(this, R.string.center_info_missing, Toast.LENGTH_SHORT).show(); }
                catch (ActivityNotFoundException | SecurityException ex) { Toast.makeText(this, R.string.center_info_missing, Toast.LENGTH_SHORT).show(); }
            }
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    @Override protected void onResume() { super.onResume(); if (ready) filter(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("mode", mode); state.putInt("category", category); state.putString("query", query.getText().toString());
    }
    private void assignCategory(Entry e) {
        String[] labels = getResources().getStringArray(R.array.category_choices); labels[0] = getString(R.string.category_automatic);
        Map<String, Integer> selected;
        try { selected = AppCategories.parse(PremiumPreferences.get(this).getString("app_categories", "")); }
        catch (IllegalArgumentException ex) { selected = new LinkedHashMap<>(); }
        Map<String, Integer> owner = selected;
        new LauncherDialogBuilder(this).setTitle(e.label).setSingleChoiceItems(labels, owner.getOrDefault(e.pkg, 0), (d, which) -> {
            if (which == 0) owner.remove(e.pkg); else owner.put(e.pkg, which);
            if (owner.size() > 128) { Toast.makeText(this, R.string.library_save_failed, Toast.LENGTH_LONG).show(); return; }
            PremiumPreferences.get(this).edit().putString("app_categories", new org.json.JSONObject(owner).toString()).apply(); filter(); d.dismiss();
        }).setNegativeButton(android.R.string.cancel, null).show();
    }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); worker.shutdownNow(); icons.evictAll(); super.onDestroy(); }

    private final class Holder extends RecyclerView.ViewHolder {
        final TextView title, detail; final ImageView icon;
        Holder(LinearLayout row, TextView title, TextView detail, ImageView icon) { super(row); this.title = title; this.detail = detail; this.icon = icon; }
    }
    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(AppCenterActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(4), dp(12), dp(4)); row.setFocusable(true); row.setClickable(true);
            row.setBackgroundResource(R.drawable.erokhin_focus_tile);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, dp(70)); lp.bottomMargin = dp(5); row.setLayoutParams(lp);
            ImageView icon = new ImageView(AppCenterActivity.this); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout names = new LinearLayout(AppCenterActivity.this); names.setOrientation(LinearLayout.VERTICAL); row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = text("", 19), detail = text("", 12);
            title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            detail.setSingleLine(true); detail.setEllipsize(android.text.TextUtils.TruncateAt.END); detail.setTextColor(0xFFAABEDB);
            names.addView(title); names.addView(detail); return new Holder(row, title, detail, icon);
        }
        @Override public int getItemCount() { return visible.size(); }
        @Override public void onBindViewHolder(Holder h, int position) {
            Entry e = visible.get(position); h.itemView.setTag(e.pkg);
            h.itemView.setOnFocusChangeListener((v, focused) -> { if (focused) focusedPackage = e.pkg; });
            h.title.setText(PremiumPreferences.favorites(AppCenterActivity.this).contains(e.pkg)
                    ? getString(R.string.center_favorite_label, e.label) : e.label);
            long time = LastOpened.get(AppCenterActivity.this, e.pkg);
            h.detail.setText(mode == 2 && time > 0 ? LastOpened.describe(AppCenterActivity.this, time) : e.pkg);
            h.itemView.setContentDescription(h.title.getText() + ", " + h.detail.getText());
            h.itemView.setOnClickListener(v -> launch(e)); h.itemView.setOnLongClickListener(v -> { menu(e); return true; });
            h.itemView.setOnKeyListener((v, code, event) -> {
                if (code != KeyEvent.KEYCODE_MENU) return false;
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) menu(e); return true;
            });
            Drawable known = icons.get(e.pkg); h.icon.setImageDrawable(known);
            if (known == null && loading.add(e.pkg)) worker.execute(() -> {
                Drawable icon = null;
                try { icon = getPackageManager().getApplicationIcon(e.pkg); } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {}
                Drawable loaded = icon;
                main.post(() -> {
                    if (isDestroyed()) return;
                    loading.remove(e.pkg);
                    if (loaded != null) icons.put(e.pkg, loaded);
                    if (e.pkg.equals(h.itemView.getTag())) h.icon.setImageDrawable(loaded);
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View row = list.getChildAt(i);
                        if (e.pkg.equals(row.getTag())) ((Holder) list.getChildViewHolder(row)).icon.setImageDrawable(loaded);
                    }
                });
            });
        }
    }
}
