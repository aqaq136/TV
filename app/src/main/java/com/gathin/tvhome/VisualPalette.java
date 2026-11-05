package com.gathin.tvhome;

/** Small shared palette, used by the real renderer, panels and the visual studio. */
final class VisualPalette {
    static final String[] NAMES = {"cosmos", "ocean", "emerald", "sunset", "rose"};
    final int primary, secondary, background;
    private VisualPalette(int primary, int secondary, int background) {
        this.primary = primary; this.secondary = secondary; this.background = background;
    }
    static VisualPalette of(String name) {
        if ("ocean".equals(name)) return new VisualPalette(0xFF42D8FF, 0xFF2468F4, 0xFF040C1C);
        if ("emerald".equals(name)) return new VisualPalette(0xFF51F2CB, 0xFF277FF5, 0xFF040F16);
        if ("sunset".equals(name)) return new VisualPalette(0xFFFFB16B, 0xFFD749B9, 0xFF160B1A);
        if ("rose".equals(name)) return new VisualPalette(0xFFFF81C8, 0xFF9272FF, 0xFF100B1C);
        return new VisualPalette(0xFF63DFFF, 0xFF9B6AFF, 0xFF070B1B);
    }
    static int alpha(int color, int alpha) { return (color & 0xFFFFFF) | Math.max(0, Math.min(255, alpha)) << 24; }
    static int blend(int a, int b, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | blue;
    }
}
