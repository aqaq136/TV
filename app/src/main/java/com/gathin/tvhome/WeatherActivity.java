package com.gathin.tvhome;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Weather setup and provider attribution, usable with a TV remote. */
public final class WeatherActivity extends ToolsActivityBase {
    private TextView status, source, refresh;
    private WeatherController weather;
    private final ExecutorService searchWorker = Executors.newSingleThreadExecutor();
    private ToolNetwork search;
    private boolean foreground;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(0xFF080F25);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setPadding(dp(36), dp(18), dp(36), dp(24));
        scroll.addView(rows); setContentView(scroll);
        text(rows, "Погода", 28, Color.WHITE);
        source = text(rows, "", 16, 0xFF7DE3FF); source.setId(R.id.weatherSource);
        status = text(rows, "", 22, Color.WHITE); status.setId(R.id.weatherStatus);
        status.setMinHeight(dp(86));
        text(rows, "Обновление автоматически, пока лаунчер открыт. При сбое сохраняется последняя сводка с отметкой времени.", 14, 0xFFB9CBE5);
        LinearLayout pair = new LinearLayout(this); rows.addView(pair);
        LinearLayout left = column(pair), right = column(pair);
        refresh = button(left, "Обновить сейчас", () -> {
            PremiumPreferences.get(this).edit().putBoolean("weather_hidden", false).apply(); weather.refresh(true);
        }); refresh.setId(R.id.weatherUpdate);
        button(left, "Выбрать город", this::chooseCity).setId(R.id.weatherCity);
        button(left, "Погода на сайте MET Norway", () -> open("https://www.met.no/en"));
        button(right, "Скрыть / показать виджет погоды", () -> {
            boolean hidden = PremiumPreferences.get(this).getBoolean("weather_hidden", false);
            PremiumPreferences.get(this).edit().putBoolean("weather_hidden", !hidden).apply(); restart();
        });
        button(right, "Изменить город", this::chooseCity);
        text(rows, "Погода работает без регистрации и ключей. Источники: MET Norway и Open-Meteo. При недоступности одного автоматически используется другой. Нужен доступ в интернет.", 15, 0xFFB9CBE5);
        text(rows, "MET Norway: прогноз на ближайший час. Данные MET Norway и Open-Meteo — CC BY 4.0. Источник и время получения указаны в сводке.", 14, 0xFFB9CBE5);
        button(rows, "Источник Open-Meteo", () -> open("https://open-meteo.com/"));
        weather = new WeatherController(this, this::render);
        render(); refresh.requestFocus();
        if (state == null && getIntent().getBooleanExtra("choose_city", false)) scroll.post(this::chooseCity);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout rows, String label, int size, int color) {
        TextView v = new TextView(this); v.setText(label); v.setTextSize(size); v.setTextColor(color); v.setPadding(0, dp(4), 0, dp(6)); rows.addView(v); return v;
    }
    private LinearLayout column(LinearLayout pair) {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(0, dp(6), dp(10), dp(8)); pair.addView(c, lp); return c;
    }
    private TextView button(LinearLayout rows, String label, Runnable action) {
        TextView v = new TextView(this); v.setText(label); v.setTextSize(17); v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(16), dp(10), dp(16), dp(10)); v.setMinHeight(dp(48));
        v.setFocusable(true); v.setClickable(true); v.setBackgroundResource(R.drawable.erokhin_focus_tile);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(6); rows.addView(v, lp);
        v.setOnClickListener(view -> action.run()); return v;
    }
    private void render() {
        source.setText("Без ключей · " + PremiumPreferences.get(this).getString("weather_source", "автоматический выбор источника"));
        status.setText(WeatherStore.summary(this, true));
        refresh.setText(weather != null && weather.loading() ? "Загрузка погоды…" : "Обновить сейчас");
    }
    private void restart() { weather.pause(); render(); if (foreground) weather.resume(); }
    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException | SecurityException e) { status.setText("Для открытия страницы нужен браузер на телевизоре."); }
    }
    private EditText input(String hint) {
        EditText field = new EditText(this); field.setSingleLine(true); field.setHint(hint); field.setTextColor(Color.WHITE); field.setHintTextColor(0xFFB9CBE5); return field;
    }
    private void chooseCity() {
        new LauncherDialogBuilder(this).setTitle("Выбрать город")
                .setItems(new String[]{"Города из списка — без онлайн-поиска", "Найти другой город", "Ввести координаты"}, (d, which) -> {
                    if (which == 0) showCities(WeatherData.presets()); else if (which == 1) searchCity(); else coordinates();
                }).setNegativeButton(R.string.action_cancel, null).show();
    }
    private void showCities(List<WeatherData.City> cities) {
        String[] titles = new String[cities.size()]; for (int i = 0; i < titles.length; i++) titles[i] = cities.get(i).title;
        new LauncherDialogBuilder(this).setTitle("Уточните город").setItems(titles, (d, which) -> {
            WeatherStore.select(this, cities.get(which)); restart();
        }).setNegativeButton(R.string.action_cancel, null).show();
    }
    private void searchCity() {
        EditText field = input("Название города");
        new LauncherDialogBuilder(this).setTitle("Поиск города").setView(field).setPositiveButton("Найти", (d, w) -> {
            String name = field.getText().toString().trim();
            if (name.length() < 2) { status.setText("Введите хотя бы два символа."); return; }
            cancelSearch(); ToolNetwork request = new ToolNetwork(); search = request; status.setText("Поиск города…");
            searchWorker.execute(() -> {
                try {
                    List<WeatherData.City> cities = WeatherData.cities(request.json("https://geocoding-api.open-meteo.com/v1/search?name="
                            + java.net.URLEncoder.encode(name, "UTF-8") + "&count=8&language=ru&format=json"));
                    runOnUiThread(() -> { if (search != request || !foreground) return; search = null;
                        if (cities.isEmpty()) status.setText("Город не найден. Выберите из списка или введите координаты."); else showCities(cities); });
                } catch (Exception e) { runOnUiThread(() -> { if (search == request && foreground) { search = null; status.setText("Поиск города недоступен. Выберите город из списка или введите координаты — они не требуют онлайн-поиска."); } }); }
                finally { request.close(); }
            });
        }).setNegativeButton(R.string.action_cancel, null).show();
    }
    private void coordinates() {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(dp(20), 0, dp(20), 0);
        EditText name = input("Название места"), lat = input("Широта, например 55.7558"), lon = input("Долгота, например 37.6173");
        int numeric = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        lat.setInputType(numeric); lon.setInputType(numeric); fields.addView(name); fields.addView(lat); fields.addView(lon);
        new LauncherDialogBuilder(this).setTitle("Погода по координатам").setView(fields).setPositiveButton("Сохранить", (d, w) -> {
            try {
                String title = name.getText().toString().trim();
                WeatherData.City city = new WeatherData.City(title.isEmpty() ? "Моё место" : title,
                        Double.parseDouble(lat.getText().toString().trim().replace(',', '.')), Double.parseDouble(lon.getText().toString().trim().replace(',', '.')));
                WeatherStore.select(this, city); restart();
            } catch (RuntimeException e) { status.setText("Координаты не сохранены. Широта от −90 до 90, долгота от −180 до 180."); }
        }).setNegativeButton(R.string.action_cancel, null).show();
    }
    private void cancelSearch() { if (search != null) search.close(); search = null; }
    @Override protected void onResume() { super.onResume(); foreground = true; weather.resume(); }
    @Override protected void onPause() { foreground = false; cancelSearch(); weather.pause(); super.onPause(); }
    @Override protected void onDestroy() { weather.close(); cancelSearch(); searchWorker.shutdownNow(); super.onDestroy(); }
}
