package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class MemoryCleaner {
    private static WeakReference<MainActivity> home = new WeakReference<>(null);
    static void register(MainActivity activity) { home = new WeakReference<>(activity); }
    /** UI thread: the hidden launcher can rebuild its artwork when it returns. */
    static boolean releaseHomeCache() {
        MainActivity activity = home.get();
        return activity != null && !activity.isDestroyed() && activity.releaseUserCaches();
    }
    static long available(Context c) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info); return info.availMem;
    }
    static int requestCachedProcesses(Context c) {
        if (Build.VERSION.SDK_INT >= 34) return 0;
        ActivityManager manager = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = c.getPackageManager(); Set<String> protect = new HashSet<>(), requested = new HashSet<>();
        protect.add(c.getPackageName());
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
            protect.add(r.activityInfo.packageName);
        android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE);
        for (android.view.inputmethod.InputMethodInfo item : ime.getInputMethodList()) protect.add(item.getPackageName());
        String enabled = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled != null) for (String component : enabled.split(":")) { int slash = component.indexOf('/'); if (slash > 0) protect.add(component.substring(0, slash)); }
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes == null) return 0;
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.pkgList == null || process.pkgList.length == 0) continue;
            boolean eligible = true;
            for (String pkg : process.pkgList) {
                try {
                    ApplicationInfo app = pm.getApplicationInfo(pkg, 0);
                    boolean system = (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                    if (!MemoryCleanupPolicy.eligible(Build.VERSION.SDK_INT, process.importance, process.uid, Process.myUid(), system, protect.contains(pkg))) eligible = false;
                } catch (PackageManager.NameNotFoundException | SecurityException e) { eligible = false; }
            }
            if (!eligible) continue;
            for (String pkg : process.pkgList) if (!requested.contains(pkg)) {
                try { manager.killBackgroundProcesses(pkg); requested.add(pkg); }
                catch (SecurityException ignored) { /* Firmware may deny background process control. */ }
            }
        }
        return requested.size();
    }
}
