package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.Locale;

/** Search rules shared by every entry point. No Android or network dependencies. */
final class AppSearch {
    private static final String CYRILLIC = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final String[] LATIN = {"a","b","v","g","d","e","e","zh","z","i","i","k","l","m","n","o","p","r","s","t","u","f","kh","ts","ch","sh","shch","","y","","e","yu","ya"};

    private AppSearch() {}

    static String normalize(String text) {
        if (text == null) return "";
        String folded = Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('ё', 'е'), Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < folded.length(); i++) {
            char c = folded.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            if (Character.isLetterOrDigit(c)) out.append(c);
            else if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') out.append(' ');
        }
        return out.toString().trim();
    }

    static String transliterate(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = CYRILLIC.indexOf(c);
            if (index >= 0) out.append(LATIN[index]); else out.append(c);
        }
        return out.toString();
    }

    /** Lower is better; -1 means no match. All query words must occur. */
    static int rank(String label, String pkg, String query) {
        String q = transliterate(normalize(query));
        if (q.isEmpty()) return 0;
        String name = transliterate(normalize(label));
        String id = transliterate(normalize(pkg));
        if (name.equals(q)) return 0;
        if (name.startsWith(q)) return 10;
        if (name.contains(q)) return 20;
        String searchable = name + " " + id;
        for (String word : q.split(" +")) if (!searchable.contains(word)) return -1;
        return id.contains(q) ? 40 : 30;
    }
}
