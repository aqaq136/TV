package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Ordered package names; preserves an intentionally empty list and removes duplicates. */
final class FavoriteOrder {
    static final int LIMIT = 24;
    static List<String> parse(String saved) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (saved != null) for (String value : saved.split("\\n")) {
            String pkg = value.trim();
            if (pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) unique.add(pkg);
            if (unique.size() == LIMIT) break;
        }
        return new ArrayList<>(unique);
    }
    static String encode(List<String> packages) { return String.join("\n", parse(String.join("\n", packages))); }
    static List<String> move(List<String> packages, String pkg, int direction) {
        List<String> result = new ArrayList<>(packages);
        int at = result.indexOf(pkg), next = at + direction;
        if (at >= 0 && next >= 0 && next < result.size()) java.util.Collections.swap(result, at, next);
        return result;
    }
}
