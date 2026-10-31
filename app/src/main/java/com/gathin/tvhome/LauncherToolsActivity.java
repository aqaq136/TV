package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import android.graphics.Color;
import android.view.View;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Optional operations live here; no polling or speed-test traffic in the background. */
public final class LauncherToolsActivity extends ToolsActivityBase {
    private static final int PICK_APK = 101, ALLOW_INSTALL = 102, SAVE_REPORT = 103;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ToolNetwork network;
    private Future<?> task;
    private TextView status;
    private File pendingApk;
    private String report;
    private String launchAction;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(0xFF080F25);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setPadding(dp(40), dp(20), dp(40), dp(24));
        scroll.addView(rows); setContentView(scroll);
        TextView title = new TextView(this); title.setText("ErokhinTV · Инструменты"); title.setTextColor(Color.WHITE); title.setTextSize(28); rows.addView(title);
        status = new TextView(this); status.setTextColor(0xFFC2DAFF); status.setTextSize(16); status.setPadding(0, dp(12), 0, dp(16));
        status.setText("Выберите действие. Онлайн-проверки запускаются только по вашему запросу."); rows.addView(status);
        row(rows, "Установить APK с флешки", this::pickApk);
        row(rows, "Удалить приложение…", this::chooseUninstall);
        row(rows, "Форма плиток", this::chooseShape);
        row(rows, "Погода — выбрать город", this::chooseCity);
        row(rows, "Обновить погоду", this::weather);
        row(rows, "Убрать погоду с главного экрана", () -> { PremiumPreferences.get(this).edit().remove("weather_summary").remove("weather_time").apply(); status.setText("Погода скрыта. Город сохранён."); });
        row(rows, "Подключение и доступ в интернет", this::connection);
        row(rows, "Проверить скорость загрузки", () -> new LauncherDialogBuilder(this).setTitle("Проверить скорость?")
                .setMessage("Будет скачано 10 МБ с Cloudflare. Это приблизительная скорость загрузки с одного сервера, не тарифная скорость и не проверка отдачи.")
                .setPositiveButton("Проверить", (d, w) -> speed()).setNegativeButton("Отмена", null).show());
        row(rows, "Отменить текущую проверку", () -> { cancel(); status.setText("Операция отменена"); });
        row(rows, "Звуки перемещения и нажатия", this::soundOptions);
        row(rows, "Диагностика и отчёт", this::diagnostics);
        row(rows, "Оформление и избранное", () -> startActivity(new Intent(this, PremiumSettingsActivity.class)));
        if (state != null) {
            String name = state.getString("pending_apk");
            if (name != null && name.matches("install-[a-f0-9-]+\\.apk")) pendingApk = new File(getCacheDir(), name);
            report = state.getString("report");
        }
        File[] cached = getCacheDir().listFiles((dir, name) -> name.matches("install-[a-f0-9-]+\\.apk"));
        if (cached != null) for (File f : cached) {
            if (System.currentTimeMillis() - f.lastModified() > 24L * 60 * 60 * 1000 && !f.equals(pendingApk)) f.delete();
        }
        launchAction = getIntent().getStringExtra("action");
        if (state == null && "weather".equals(launchAction)) scroll.post(this::weather);
    }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private void row(LinearLayout rows, String label, Runnable action) {
        TextView view = new TextView(this); view.setText(label); view.setTextSize(18); view.setTextColor(Color.WHITE);
        view.setPadding(dp(18), dp(12), dp(18), dp(12)); view.setMinHeight(dp(52)); view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        view.setBackgroundResource(R.drawable.erokhin_focus_tile); view.setFocusable(true); view.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(8); rows.addView(view, lp);
        view.setOnClickListener(v -> action.run());
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private interface Work { void run(ToolNetwork net) throws Exception; }
    private synchronized void cancel() {
        if (task != null) task.cancel(true);
        if (network != null) network.close();
        task = null; network = null;
    }
    private synchronized void run(String label, Work operation) {
        cancel(); status.setText(label);
        ToolNetwork current = new ToolNetwork(); network = current;
        task = worker.submit(() -> {
            try { operation.run(current); }
            catch (Exception e) { result(current, () -> status.setText(label + "\nНе удалось выполнить операцию. Проверьте подключение, выбранный файл и доступность сервиса. " + e.getClass().getSimpleName())); }
            finally { current.close(); }
        });
    }
    private void result(ToolNetwork current, Runnable action) {
        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed() && current == network) action.run(); });
    }
    private void chooseShape() {
        String[] shapes = {"wide", "square", "compact"};
        new LauncherDialogBuilder(this).setTitle("Форма плиток")
                .setSingleChoiceItems(new String[]{"Широкие · 16:9", "Квадратные · 1:1", "Компактные · низкие"},
                        Arrays.asList(shapes).indexOf(PremiumPreferences.get(this).getString("tile_shape", "wide")), (d, which) -> {
                            PremiumPreferences.get(this).edit().putString("tile_shape", shapes[which]).apply(); PremiumPreferences.changed(this);
                            status.setText("Форма сохранена. Вернитесь на главный экран."); d.dismiss();
                        }).setNegativeButton("Отмена", null).show();
    }
    private void soundOptions() {
        android.content.SharedPreferences prefs = PremiumPreferences.get(this);
        boolean[] checked = {prefs.getBoolean("sound_move", true), prefs.getBoolean("sound_click", true)};
        new LauncherDialogBuilder(this).setTitle("Звуки лаунчера")
                .setMultiChoiceItems(new String[]{"Перемещение", "Нажатие OK"}, checked, (d, which, value) -> checked[which] = value)
                .setPositiveButton("Сохранить", (d, w) -> {
                    prefs.edit().putBoolean("sound_move", checked[0]).putBoolean("sound_click", checked[1]).apply();
                    new LauncherDialogBuilder(this).setTitle("Громкость звуков")
                            .setSingleChoiceItems(new String[]{"Тихо", "Обычно", "Громче"}, -1, (dialog, which) -> {
                                prefs.edit().putInt("sound_volume", new int[]{10, 25, 50}[which]).apply(); PremiumPreferences.changed(this);
                                status.setText("Звуки сохранены. Оба выключенных пункта полностью отключают звуки лаунчера."); dialog.dismiss();
                            }).setNegativeButton("Оставить громкость", (dialog, which) -> PremiumPreferences.changed(this)).show();
                }).setNegativeButton("Отмена", null).show();
    }
    private void chooseCity() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Например, Москва");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        new LauncherDialogBuilder(this).setTitle("Город · Open-Meteo").setView(input)
                .setMessage("Название города отправится в Open-Meteo. Доступ к геолокации не требуется.")
                .setPositiveButton("Найти", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.length() < 2) { message("Введите хотя бы два символа"); return; }
                    run("Поиск города…", net -> {
                        List<WeatherData.City> cities = WeatherData.cities(net.json("https://geocoding-api.open-meteo.com/v1/search?name="
                                + java.net.URLEncoder.encode(name, "UTF-8") + "&count=8&language=ru&format=json"));
                        result(net, () -> {
                            if (cities.isEmpty()) { status.setText("Город не найден. Попробуйте другое название."); return; }
                            String[] labels = new String[cities.size()]; for (int i = 0; i < labels.length; i++) labels[i] = cities.get(i).title;
                            new LauncherDialogBuilder(this).setTitle("Уточните город").setItems(labels, (dialog, which) -> {
                                WeatherData.City c = cities.get(which);
                                PremiumPreferences.get(this).edit().putString("weather_city", c.title).putString("latitude", Double.toString(c.latitude))
                                        .putString("longitude", Double.toString(c.longitude)).remove("weather_summary").apply(); weather();
                            }).setNegativeButton("Отмена", null).show();
                        });
                    });
                }).setNegativeButton("Отмена", null).show();
    }
    private void weather() {
        android.content.SharedPreferences p = PremiumPreferences.get(this);
        if (!p.contains("latitude")) { chooseCity(); return; }
        final WeatherData.City city;
        try { city = new WeatherData.City(p.getString("weather_city", "Город"), Double.parseDouble(p.getString("latitude", "")), Double.parseDouble(p.getString("longitude", ""))); }
        catch (RuntimeException e) { chooseCity(); return; }
        run("Получение погоды…", net -> {
            String summary = WeatherData.current(net.json("https://api.open-meteo.com/v1/forecast?latitude=" + city.latitude
                    + "&longitude=" + city.longitude + "&current=temperature_2m,weather_code,wind_speed_10m&timezone=auto"));
            result(net, () -> {
                p.edit().putString("weather_summary", summary).putLong("weather_time", System.currentTimeMillis()).apply();
                status.setText(city.title + "\n" + summary + "\nИсточник: Open-Meteo · обновлено сейчас. На главном экране показывается сохранённая сводка.");
            });
        });
    }
    private String localConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network active = cm.getActiveNetwork(); NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        if (caps == null) return "Нет активного подключения";
        String transport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" : "Другая сеть";
        return transport + (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? " · VPN" : "") + "\n"
                + (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "Android подтверждает доступ в интернет" : "Android не подтвердил доступ в интернет");
    }
    private void connection() {
        String local = localConnection();
        run(local + "\nПроверка HTTPS-доступа к Cloudflare…", net -> {
            long[] check = net.download(64);
            result(net, () -> status.setText(local + "\nCloudflare доступен по HTTPS · ответ " + check[2] + " мс (включая установку соединения)."));
        });
    }
    private void speed() {
        run("Загрузка 10 МБ с Cloudflare…", net -> {
            long[] data = net.download(10_000_000);
            result(net, () -> status.setText(String.format(Locale.getDefault(), "Оценка загрузки: %.1f Мбит/с\nПолучено: %.1f МБ · %.2f с\nСервер: Cloudflare. Результат зависит от маршрута, сервера и размера файла.",
                    data[0] * 8.0 / (data[1] * 1000.0), data[0] / 1_000_000.0, data[1] / 1000.0)));
        });
    }
    private void chooseUninstall() {
        Map<String, String> apps = new TreeMap<>();
        for (android.content.pm.ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            if ((r.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !r.activityInfo.packageName.equals(getPackageName())) apps.put(r.activityInfo.packageName, r.loadLabel(getPackageManager()).toString());
        }
        for (android.content.pm.ResolveInfo r : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            if ((r.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && !r.activityInfo.packageName.equals(getPackageName())) apps.put(r.activityInfo.packageName, r.loadLabel(getPackageManager()).toString());
        }
        if (apps.isEmpty()) { message("Нет доступных пользовательских приложений"); return; }
        List<String> packages = new ArrayList<>(apps.keySet()); packages.sort((a, b) -> apps.get(a).compareToIgnoreCase(apps.get(b)));
        String[] labels = new String[packages.size()]; for (int i = 0; i < labels.length; i++) labels[i] = apps.get(packages.get(i));
        new LauncherDialogBuilder(this).setTitle("Какое приложение удалить?").setItems(labels, (d, which) -> confirmUninstall(this, packages.get(which)))
                .setNegativeButton("Отмена", null).show();
    }
    static void confirmUninstall(android.app.Activity activity, String pkg) {
        try {
            ApplicationInfo app = activity.getPackageManager().getApplicationInfo(pkg, 0);
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || pkg.equals(activity.getPackageName())) {
                Toast.makeText(activity, "Системные приложения и сам лаунчер здесь не удаляются", Toast.LENGTH_LONG).show(); return;
            }
            new LauncherDialogBuilder(activity).setTitle("Удалить «" + activity.getPackageManager().getApplicationLabel(app) + "»?")
                    .setMessage(pkg + "\nПриложение и его локальные данные будут удалены. Системный установщик запросит подтверждение.")
                    .setPositiveButton("Продолжить", (d, w) -> {
                        try { activity.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))); }
                        catch (ActivityNotFoundException | SecurityException e) { Toast.makeText(activity, "Удаление недоступно в этой прошивке", Toast.LENGTH_LONG).show(); }
                    }).setNegativeButton("Отмена", null).show();
        } catch (PackageManager.NameNotFoundException e) { Toast.makeText(activity, "Приложение уже удалено", Toast.LENGTH_SHORT).show(); }
    }
    private void pickApk() {
        new LauncherDialogBuilder(this).setTitle("Установить APK с флешки")
                .setMessage("В следующем окне выберите USB-накопитель и APK. Установка потребует подтверждения Android. Нужен обычный APK, не XAPK/APKM и не набор split-APK.")
                .setPositiveButton("Выбрать файл", (d, w) -> {
                    try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), PICK_APK); }
                    catch (ActivityNotFoundException e) { status.setText("В прошивке нет выбора файлов. Откройте APK в файловом менеджере телевизора."); }
                }).setNegativeButton("Отмена", null).show();
    }
    private void copyApk(Uri uri) {
        run("Копирование выбранного APK…", net -> {
            File file = new File(getCacheDir(), "install-" + UUID.randomUUID() + ".apk");
            boolean success = false;
            try {
                try (InputStream in = getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(file)) {
                    if (in == null) throw new IOException("Cannot open file");
                    byte[] buffer = new byte[65536]; long size = 0; int n;
                    while ((n = in.read(buffer)) >= 0) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                        size += n; if (size > 512L * 1024 * 1024) throw new IOException("APK larger than 512 MB");
                        out.write(buffer, 0, n);
                    }
                }
                PackageInfo info = getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
                if (info == null) throw new IOException("Not a supported APK");
                info.applicationInfo.sourceDir = file.getAbsolutePath(); info.applicationInfo.publicSourceDir = file.getAbsolutePath();
                String name = getPackageManager().getApplicationLabel(info.applicationInfo).toString();
                result(net, () -> {
                    pendingApk = file;
                    new LauncherDialogBuilder(this).setTitle("Установить «" + name + "»?")
                            .setMessage("Версия: " + info.versionName + "\n" + info.packageName + "\nПродолжение откроет системный установщик Android.")
                            .setPositiveButton("Продолжить", (d, w) -> installApk()).setNegativeButton("Отмена", (d, w) -> { file.delete(); pendingApk = null; }).show();
                    status.setText("APK выбран: " + name);
                });
                success = true;
            } finally { if (!success) file.delete(); }
        });
    }
    private void installApk() {
        if (pendingApk == null || !pendingApk.isFile()) { message("Выберите APK заново"); return; }
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())), ALLOW_INSTALL); return;
            }
            Uri uri = Uri.parse("content://" + getPackageName() + ".installer/" + pendingApk.getName());
            Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.setClipData(android.content.ClipData.newRawUri("APK", uri)); startActivity(install);
            status.setText("Открыт системный установщик. Результат установки смотрите в его окне.");
        } catch (ActivityNotFoundException | SecurityException e) { status.setText("Установка APK недоступна. Используйте штатный файловый менеджер или ADB."); }
    }
    private String makeReport() {
        String version = "?";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (PackageManager.NameNotFoundException ignored) {}
        android.os.StatFs fs = new android.os.StatFs(getFilesDir().getAbsolutePath());
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return "ErokhinTV diagnostic report\nTime: " + new Date() + "\nVersion: " + version + "\nPackage: " + getPackageName()
                + "\nManufacturer: " + android.os.Build.MANUFACTURER + "\nModel: " + android.os.Build.MODEL + "\nAndroid: " + android.os.Build.VERSION.RELEASE
                + " (API " + android.os.Build.VERSION.SDK_INT + ")\nDisplay: " + dm.widthPixels + "x" + dm.heightPixels + " density=" + dm.density
                + "\nFree storage: " + fs.getAvailableBytes() / 1048576 + " MB\nTile shape: " + PremiumPreferences.get(this).getString("tile_shape", "wide")
                + "\n" + localConnection() + "\n\nОтчёт не содержит серийного номера, IP/MAC, паролей, списка приложений или системных логов. Никуда не отправлен автоматически.";
    }
    private void diagnostics() {
        report = makeReport();
        new LauncherDialogBuilder(this).setTitle("Диагностика").setMessage(report)
                .setPositiveButton("Сохранить TXT", (d, w) -> {
                    try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("text/plain").putExtra(Intent.EXTRA_TITLE, "ErokhinTV-diagnostics.txt"), SAVE_REPORT); }
                    catch (ActivityNotFoundException e) { message("В прошивке нет сохранения файлов. Используйте «Поделиться» или фотографию отчёта."); }
                }).setNeutralButton("Поделиться", (d, w) -> {
                    try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Отправить отчёт")); }
                    catch (ActivityNotFoundException e) { message("Нет приложений для отправки отчёта"); }
                }).setNegativeButton("Закрыть", null).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ALLOW_INSTALL) {
            if (getPackageManager().canRequestPackageInstalls()) installApk(); else message("Разрешение не выдано. Установка отменена."); return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK_APK) copyApk(data.getData());
        if (request == SAVE_REPORT && report != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IOException(); out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8)); status.setText("Отчёт сохранён");
            } catch (IOException | SecurityException e) { message("Не удалось сохранить отчёт"); }
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (pendingApk != null) state.putString("pending_apk", pendingApk.getName());
        state.putString("report", report);
    }
    @Override protected void onDestroy() { cancel(); worker.shutdownNow(); super.onDestroy(); }
}
