package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;

/** Reads an installed app's Russian label without changing the language of the TV or the app itself. */
final class AppLabels {
    private AppLabels() {}
    static String label(Context context, ResolveInfo info) {
        String pkg = info.activityInfo.packageName;
        String known = knownTitle(pkg);
        if (known != null) return known;
        int id = info.activityInfo.labelRes != 0 ? info.activityInfo.labelRes : info.activityInfo.applicationInfo.labelRes;
        String localized = resource(context, pkg, id);
        return Cards.cleanLabel(localized != null ? localized : String.valueOf(info.loadLabel(context.getPackageManager())), pkg);
    }
    static String application(Context context, ApplicationInfo info) {
        String known = knownTitle(info.packageName);
        if (known != null) return known;
        String localized = resource(context, info.packageName, info.labelRes);
        return Cards.cleanLabel(localized != null ? localized : String.valueOf(context.getPackageManager().getApplicationLabel(info)), info.packageName);
    }
    private static String resource(Context context, String pkg, int id) {
        if (id == 0) return null;
        try {
            Context app = context.createPackageContext(pkg, 0);
            Configuration config = new Configuration(app.getResources().getConfiguration()); config.setLocale(LauncherLocale.RUSSIAN);
            return app.createConfigurationContext(config).getResources().getText(id).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return null; }
    }
    static String knownTitle(String pkg) {
        if (pkg == null) return null;
        switch (pkg) {
            case "com.android.traceur": return "Системная трассировка";
            case "com.google.android.videos": case "com.lego.android.tvleanback": return "Видео Google";
            case "com.android.tv.settings": case "com.android.settings": return "Настройки телевизора";
            case "com.android.documentsui": case "com.google.android.documentsui": return "Файлы";
            case "com.android.browser": return "Браузер";
            case "com.android.tv": return "Телеканалы";
            default: return null;
        }
    }
}
