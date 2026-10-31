package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

final class PremiumPreferences {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("premium_home", Context.MODE_PRIVATE); }
    static List<String> favorites(Context c) {
        return FavoriteOrder.parse(get(c).getString("favorites",
                "com.google.android.youtube.tv\ncom.vkontakte.android\nru.kinopoisk\nru.ivi.client\nru.okko.androidtv\nru.wink.tv"));
    }
    static void saveFavorites(Context c, List<String> packages) {
        get(c).edit().putString("favorites", FavoriteOrder.encode(packages)).apply();
    }
    static boolean toggleFavorite(Context c, String pkg) {
        List<String> list = favorites(c);
        if (!list.remove(pkg)) {
            if (list.size() >= FavoriteOrder.LIMIT) return false;
            list.add(pkg);
        }
        saveFavorites(c, list);
        return true;
    }
    static void changed(Context c) {
        SharedPreferences p = c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        p.edit().putLong(Config.SETTINGS_REVISION_KEY, p.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }
}
